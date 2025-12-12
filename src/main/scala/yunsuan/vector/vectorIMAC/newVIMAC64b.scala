package yunsuan.vector.mac

import chisel3.stage._
import circt.stage._
import chisel3._
import chisel3.util._
import yunsuan.vector._
import yunsuan.util._
import yunsuan.encoding.Opcode.VimacOpcode.isMacc
import yunsuan.VidivType.vdiv
import yunsuan.encoding.Opcode.VimacOpcode.isFixP

class newVIMac64b extends Module {
  val io = IO(new Bundle {
    val fire = Input(Bool())
    // val opcode = Input(new VIMacOpcode)
    val info = Input(new VIFuInfo)
    val srcType = Input(Vec(2, UInt(4.W)))
    val vdType  = Input(UInt(4.W))
    val vs1 = Input(UInt(64.W))
    val vs2 = Input(UInt(64.W))
    val oldVd = Input(UInt(64.W)) 
    val highHalf = Input(Bool())
    val isMacc = Input(Bool()) // (w)macc(nmsac)/madd(nmsub)
    val isSub = Input(Bool())
    val widen = Input(Bool())
    val isFixP = Input(Bool())

    val vd = Output(UInt(64.W))
    val vxsat = Output(UInt(8.W))
  })

  val fire = io.fire
  val fireS1 = GatedValidRegNext(fire)
  val vs2 = io.vs2
  val vs1 = io.vs1
  val oldVd = io.oldVd
  val vs2_is_signed = io.srcType(0)(2) // vs2 & vd should be signed numbers for vmadd/vnmsub
  val vs1_is_signed = io.srcType(1)(2)
  val vd_is_signed  = io.vdType(2)
  val eewVs2 = SewOH(io.srcType(0)(1, 0))
  
  val sew = eewVs2
  val sewIs64 = sew.is64
  val sewIs32 = sew.is32
  val sewIs16 = sew.is16
  val sewIs8 = sew.is8

  val isSub  = io.isSub
  val uopIdx = io.info.uopIdx
  val vxrm   = io.info.vxrm

  // Booth encoding
  class boothEncode(d: UInt) {
    val positive = (d(1) | d(0)) & ~d(2)
    val negative = (~d(1) | ~d(0)) & d(2)
    val doubleOrZero = ~(d(1) ^ d(0))
    val one = d(1) ^ d(0)
  }

  val vs1Booth3b = Wire(Vec(32,UInt(3.W)))
  vs1Booth3b(0) := Cat(vs1(1,0), 0.U(1.W))
  for (i <- 1 until 32) {
    if (i % 4 != 0) {
      vs1Booth3b(i) := vs1(2*i+1, 2*i-1)
    } else if (i == 16) {
      vs1Booth3b(i) := Cat(vs1(2*i+1, 2*i), sewIs64 & vs1(2*i-1))
    } else if (i % 8 == 0) {
      vs1Booth3b(i) := Cat(vs1(2*i+1, 2*i), (sewIs32 | sewIs64) & vs1(2*i-1))
    } else {
      vs1Booth3b(i) := Cat(vs1(2*i+1, 2*i), ~sewIs8 & vs1(2*i-1))
    }
  }

  val vs1Booth = Seq.tabulate(32)(i => new boothEncode(vs1Booth3b(i)))

  // get sign bit for each element
  val sgnVs2 = Wire(Vec(8, UInt(1.W)))
  for(i <- 0 until 8) {
    val sgnSew64 = 63
    val sgnSew32 = 32*(i/4)+31
    val sgnSew16 = 16*(i/2)+15
    val sgnSew8  = 8*i+7
    sgnVs2(i) := (vs2_is_signed) & (sewIs64 & vs2(sgnSew64) | sewIs32 & vs2(sgnSew32) | sewIs16 & vs2(sgnSew16) | sewIs8 & vs2(sgnSew8))
  }

  // decide if partial product is zero
  val partProdNonZero = Wire(Vec(32, UInt(1.W)))
  for (i <- 0 until 32) {
    partProdNonZero(i) := vs1Booth(i).negative | vs1Booth(i).positive
  }

  // mark partial products need to be negated and generate corresponding correction bit
  val partProdCin = Wire(Vec(32, UInt(1.W)))
  for (i <- 0 until 32) {
    partProdCin(i) := vs1Booth(i).negative & ~isSub | vs1Booth(i).positive & isSub
  }

  // calculate sign bit for each partial product
  val partProdSgn = Wire(Vec(32, UInt(1.W)))
  for (i <- 0 until 32) {
    val sgnVs2Index = i/4
    partProdSgn(i) := (partProdCin(i) ^ sgnVs2(sgnVs2Index)) & partProdNonZero(i)
  }

  // first partial product in each multiplication has a different sign bits generation format
  val isFirstPartProd = Wire(Vec(32, UInt(1.W)))
  for (i <- 0 until 32) {
    if (i == 0) {
      isFirstPartProd(i) := 1.U
    }
    else if (i % 16 == 0) {
      isFirstPartProd(i) := sewIs32 | sewIs16 | sewIs8
    }
    else if (i % 8 == 0) {
      isFirstPartProd(i) := sewIs16 | sewIs8
    }
    else if (i % 4 == 0) {
      isFirstPartProd(i) := sewIs8
    }
    else {
      isFirstPartProd(i) := 0.U
    }
  }
 
  // generate partial product
  val partProd = Wire(Vec(32, UInt(68.W)))
  for(i <- 0 until 32) {
    val sew8IndexL  = (i/4)*8
    val sew8IndexH  = (i/4)*8   + 7
    val sew16IndexL = (i/8)*16
    val sew16IndexH = (i/8)*16  + 15
    val sew32IndexL = (i/16)*32
    val sew32IndexH = (i/16)*32 + 31
    val sew64IndexL = 0
    val sew64IndexH = 63
    partProd(i) := Mux1H(Seq(
  //                                                                                                                              sign generate <- | -> sign extended element
      sewIs8  -> Cat(0.U(56.W), isFirstPartProd(i) & ~partProdSgn(i), ~isFirstPartProd(i) | partProdSgn(i), ~(isFirstPartProd(i) ^ partProdSgn(i)), Fill(9,  partProdNonZero(i)) & Mux(vs1Booth(i).doubleOrZero, Cat(Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL),  partProdCin(i)), Cat(partProdSgn(i), Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL )))),
      sewIs16 -> Cat(0.U(48.W), isFirstPartProd(i) & ~partProdSgn(i), ~isFirstPartProd(i) | partProdSgn(i), ~(isFirstPartProd(i) ^ partProdSgn(i)), Fill(17, partProdNonZero(i)) & Mux(vs1Booth(i).doubleOrZero, Cat(Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL)))),
      sewIs32 -> Cat(0.U(32.W), isFirstPartProd(i) & ~partProdSgn(i), ~isFirstPartProd(i) | partProdSgn(i), ~(isFirstPartProd(i) ^ partProdSgn(i)), Fill(33, partProdNonZero(i)) & Mux(vs1Booth(i).doubleOrZero, Cat(Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL)))),
      sewIs64 -> Cat(           isFirstPartProd(i) & ~partProdSgn(i), ~isFirstPartProd(i) | partProdSgn(i), ~(isFirstPartProd(i) ^ partProdSgn(i)), Fill(65, partProdNonZero(i)) & Mux(vs1Booth(i).doubleOrZero, Cat(Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL))))                    
    ))
  }

  val wallaceTree = Wire(Vec(34, UInt(152.W)))
  def wallaceTreeGen(i: Int, wallaceLine: UInt, partProd: UInt, partProdCin: UInt, isFirstPartProd: UInt, sewIs8: Bool, sewIs16: Bool, sewIs32: Bool, sewIs64: Bool): Unit = {
    val elementIndex8b  = i/4
    val elementIndex16b = i/8
    val elementIndex32b = i/16
    val shift8b  = i % 4
    val shift16b = i % 8
    val shift32b = i % 16
    val shift64b = i
    wallaceLine := Mux1H(Seq(
      sewIs8  -> Cat(0.U((152 - elementIndex8b*19  - shift8b*2  - 12).W), partProd(11, 0), 0.U(1.W), partProdCin & (~isFirstPartProd), 0.U((elementIndex8b*19  + shift8b*2 - 2).W)),
      sewIs16 -> Cat(0.U((152 - elementIndex16b*38 - shift16b*2 - 20).W), partProd(19, 0), 0.U(1.W), partProdCin & (~isFirstPartProd), 0.U((elementIndex16b*38 + shift16b*2 - 2).W)),
      sewIs32 -> Cat(0.U((152 - elementIndex32b*76 - shift32b*2 - 36).W), partProd(35, 0), 0.U(1.W), partProdCin & (~isFirstPartProd), 0.U((elementIndex32b*76 + shift32b*2 - 2).W)),
      sewIs64 -> Cat(0.U((152                      - shift64b*2 - 68).W), partProd,        0.U(1.W), partProdCin                     , 0.U((shift64b*2 - 2).W))
    ))
  }

  // wallace Tree
  wallaceTree(0) := Cat(0.U(84.W), partProd(0))
  for (i <- 1 until 32) {
    wallaceTreeGen(i, wallaceTree(i), partProd(i), partProdCin(i-1), isFirstPartProd(i), sewIs8, sewIs16, sewIs32, sewIs64)
  }
  
  // add unsigned partial product and correction bit for oldVd as well as last partial product of each element
  wallaceTree(32) := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(vs2, 8 ).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(2.W),  Mux(~vs1_is_signed & vs1(63 - 8*index),  BitsExtend(x,   9 , vs2_is_signed), 0.U(9.W)) , 0.U(1.W), partProdCin(31 - 4*index),  0.U(6.W))}),
    sewIs16 -> Cat(UIntSplit(vs2, 16).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(5.W),  Mux(~vs1_is_signed & vs1(63 - 16*index), BitsExtend(x,   17, vs2_is_signed), 0.U(17.W)), 0.U(1.W), partProdCin(31 - 8*index),  0.U(14.W))}),
    sewIs32 -> Cat(UIntSplit(vs2, 32).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(11.W), Mux(~vs1_is_signed & vs1(63 - 32*index), BitsExtend(x,   33, vs2_is_signed), 0.U(33.W)), 0.U(1.W), partProdCin(31 - 16*index), 0.U(30.W))}),
    sewIs64 ->                                                                    Cat(0.U(23.W), Mux(~vs1_is_signed & vs1(63),            BitsExtend(vs2, 65, vs2_is_signed), 0.U(35.W)), 0.U(1.W), partProdCin(31)           , 0.U(62.W))
  ))

  // oldVd
  wallaceTree(33) := Mux(io.isMacc, Mux1H(Seq(
    sewIs8  -> Mux(io.widen, Cat(UIntSplit(Cat(oldVd, oldVd), 16).reverse.map(x => Cat(0.U(2.W),  BitsExtend(x, 17, vd_is_signed)))), Cat(UIntSplit(oldVd, 8 ).map(x => Cat(0.U(2.W),  BitsExtend(x, 17, vd_is_signed))).reverse)),
    sewIs16 -> Mux(io.widen, Cat(UIntSplit(Cat(oldVd, oldVd), 32).reverse.map(x => Cat(0.U(5.W),  BitsExtend(x, 33, vd_is_signed)))), Cat(UIntSplit(oldVd, 16).map(x => Cat(0.U(5.W),  BitsExtend(x, 33, vd_is_signed))).reverse)),
    sewIs32 -> Mux(io.widen, Cat(UIntSplit(Cat(oldVd, oldVd), 64).reverse.map(x => Cat(0.U(11.W), BitsExtend(x, 65, vd_is_signed)))), Cat(UIntSplit(oldVd, 32).map(x => Cat(0.U(11.W), BitsExtend(x, 65, vd_is_signed))).reverse)),
    sewIs64 -> Cat(0.U(23.W), BitsExtend(oldVd, 129, vd_is_signed))
  )), 0.U)

  def compressor3to2(a: UInt, b: UInt, c: UInt): (UInt, UInt) = {
    val sum  = Wire(UInt(152.W))
    val cout = Wire(UInt(152.W))
    sum  := a ^ b ^ c
    cout := (a & b) | (b & c) | (c & a)
    (sum, cout)
  }

  def wallaceCompress(compressIn: Seq[UInt]): Seq[UInt] = {
    if (compressIn.size == 2) {
      compressIn
    }
    else {
      val compressGroupNum = compressIn.size / 3
      val sum =  Wire(Vec(compressGroupNum, UInt(152.W)))
      val cout = Wire(Vec(compressGroupNum, UInt(152.W)))
      for(i <- 0 until compressGroupNum) {
        sum(i)  := compressor3to2(compressIn(3*i), compressIn(3*i+1), compressIn(3*i+2))._1
        cout(i) := Cat(compressor3to2(compressIn(3*i), compressIn(3*i+1), compressIn(3*i+2))._2(150,0), 0.U(1.W))
      }
      wallaceCompress(sum ++ cout ++ compressIn.drop(3*compressGroupNum))
    }
  }

  val result = wallaceCompress(wallaceTree)
  val sum34to2  = Wire(UInt(152.W))
  val cout34to2 = Wire(UInt(152.W))
  val sumFinal  = Wire(UInt(152.W))
  
  sum34to2  := result(0)
  cout34to2 := result(1)

  sumFinal := sum34to2 + cout34to2
  
  // non fixed-point instructions
  val vdNonFixP = Wire(UInt(64.W))
  vdNonFixP := Mux1H(Seq(
    sewIs64 -> Mux(io.highHalf, sumFinal(127,64), sumFinal(63,0)),
    sewIs32 -> Mux(io.widen, Mux(uopIdx(0), sumFinal(140, 77), sumFinal(63,0)), Cat(UIntSplit(sumFinal, 76).reverse.map(x => Mux(io.highHalf, x(63,32), x(31,0))))),
    sewIs16 -> Mux(io.widen, Cat(UIntSplit(Mux(uopIdx(0), sumFinal(151, 76), sumFinal(75, 0)), 38).reverse.map(x => x(31,0))), Cat(UIntSplit(sumFinal, 38).reverse.map(x => Mux(io.highHalf, x(31,16), x(15,0))))),
    sewIs8  -> Mux(io.widen, Cat(UIntSplit(Mux(uopIdx(0), sumFinal(151, 76), sumFinal(75, 0)), 19).reverse.map(x => x(15,0))), Cat(UIntSplit(sumFinal, 19).reverse.map(x => Mux(io.highHalf, x(15,8),  x(7, 0)))))
  ))

  // vsmul
  // 1.saturate
  val vxsat = Wire(UInt(8.W))
  vxsat := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(sumFinal, 19).reverse.map(x => x(15,14) === 1.U(2.W))),
    sewIs16 -> Cat(UIntSplit(sumFinal, 38).reverse.map(x => Fill(2, x(31,30) === 1.U(2.W)))),
    sewIs32 -> Cat(UIntSplit(sumFinal, 76).reverse.map(x => Fill(4, x(63,62) === 1.U(2.W)))),
    sewIs64 -> Fill(8, sumFinal(127,126) === 1.U(2.W))
  ))

  // 2.rounding
  val vdRndIn = Wire(UInt(64.W))
  val vdRndOut = Wire(Vec(8, UInt(8.W)))
  val rndIncVec = Wire(UInt(8.W))

  class fullAdder8b(in: UInt, cin: Bool) {
    val out  = Wire(UInt(8.W))
    val cout = Wire(Bool())

    out  := in + cin.asUInt
    cout := (in === "b1111_1111".U) & cin
  }

  def rndIncGen(v_d: Bool, v_d_1: Bool, tail: UInt): Bool = {
    Mux1H(Seq((vxrm === 0.U) -> v_d_1,
              (vxrm === 1.U) -> (v_d_1 && (tail =/= 0.U || v_d)),
              (vxrm === 2.U) -> false.B,
              (vxrm === 3.U) -> (!v_d && Cat(v_d_1, tail) =/= 0.U) ))
  }
  
  vdRndIn := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(sumFinal, 19).reverse.map(x => x(14, 7))),
    sewIs16 -> Cat(UIntSplit(sumFinal, 38).reverse.map(x => x(30, 15))),
    sewIs32 -> Cat(UIntSplit(sumFinal, 76).reverse.map(x => x(62, 31))),
    sewIs64 -> sumFinal(126, 63)
  ))

  rndIncVec := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(sumFinal, 19).reverse.map(x =>               rndIncGen(x(7),  x(6),  x(5, 0)))),
    sewIs16 -> Cat(UIntSplit(sumFinal, 38).reverse.map(x => Cat(0.U(1.W), rndIncGen(x(15), x(14), x(13, 0))))),
    sewIs32 -> Cat(UIntSplit(sumFinal, 76).reverse.map(x => Cat(0.U(3.W), rndIncGen(x(31), x(30), x(29, 0))))),
    sewIs64 -> Cat(0.U(7.W), rndIncGen(sumFinal(63), sumFinal(62), sumFinal(61,0)))
  ))
  
  val adderChainCin = Wire(Vec(8, Bool()))
  val adderChain = Seq.tabulate(8)(i => new fullAdder8b(vdRndIn(8*i+7, 8*i), adderChainCin(i)))
  
  for (i <- 0 until 8) {
    if (i == 0) {
      adderChainCin(i) := rndIncVec(i)
    }
    else if(i % 4 == 0) {
      adderChainCin(i) := Mux(sewIs64, adderChain(i-1).cout, rndIncVec(i))
    }
    else if(i % 2 == 0) {
      adderChainCin(i) := Mux(sewIs64 | sewIs32, adderChain(i-1).cout, rndIncVec(i))
    }
    else {
      adderChainCin(i) := Mux(sewIs8, rndIncVec(i), adderChain(i-1).cout)
    }
    vdRndOut(i) := adderChain(i).out
  }

  // 3.fixed point vd
  val vdFixP = Wire(UInt(64.W))
  vdFixP := Mux1H(Seq(
    sewIs8  -> Cat(vdRndOut.reverse.zip(                                                               UIntSplit(vxsat, 1).map(x => x(0)).reverse).map{ case(rndData: UInt, satFlag: Bool) => Mux(satFlag, "h7F".U(8.W), rndData)}),
    sewIs16 -> Cat(vdRndOut.reverse.grouped(2).map{ case Seq(a, b)       => Cat(a, b)      }.toSeq.zip(UIntSplit(vxsat, 2).map(x => x(0)).reverse).map{ case(rndData: UInt, satFlag: Bool) => Mux(satFlag, "h7FFF".U(16.W), rndData)}),
    sewIs32 -> Cat(vdRndOut.reverse.grouped(4).map{ case Seq(a, b, c, d) => Cat(a, b, c, d)}.toSeq.zip(UIntSplit(vxsat, 4).map(x => x(0)).reverse).map{ case(rndData: UInt, satFlag: Bool) => Mux(satFlag, "h7FFF_FFFF".U(32.W), rndData)}),
    sewIs64 -> Mux(vxsat(0), "h7FFF_FFFF_FFFF_FFFF".U(64.W), Cat(vdRndOut.reverse))
  ))

  // connect output
  io.vd := Mux(io.isFixP, vdFixP, vdNonFixP)
  io.vxsat := Mux(io.isFixP, vxsat, 0.U)

  dontTouch(partProd)
  dontTouch(wallaceTree)
  dontTouch(sumFinal)
  dontTouch(vdRndIn)
  dontTouch(adderChainCin)
  dontTouch(rndIncVec)
  dontTouch(vdRndOut)
  dontTouch(vdFixP)
}

object newVIMAC64bTimingTest extends App {
  val path = """./build/verilog"""
  val params = List(1)
  for (i <- params) {
    (new ChiselStage).execute(
      Array("--target-dir", path),
      Seq(
        ChiselGeneratorAnnotation(() => new newVIMac64b),
        FirtoolOption("--disable-all-randomization"),
        FirtoolOption("--lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none"),
        circt.stage.CIRCTTargetAnnotation(circt.stage.CIRCTTarget.Verilog)
      )
    )
  }
}