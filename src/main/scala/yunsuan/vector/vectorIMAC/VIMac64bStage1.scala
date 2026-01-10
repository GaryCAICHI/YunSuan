package yunsuan.vector.mac

import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage._

import chisel3._
import chisel3.util._
import yunsuan.vector._
import yunsuan.util._

class VIMac64bStage1Input extends Bundle {
  val info     = new VIFuInfo
  val srcType  = Vec(2, UInt(4.W))
  val vdType   = UInt(4.W)
  val vs1      = UInt(64.W)
  val vs2      = UInt(64.W)
  val oldVd    = UInt(64.W) 
  val highHalf = Bool()
  val isMacc   = Bool() // (w)macc(nmsac)/madd(nmsub)
  val isSub    = Bool()
  val widen    = Bool()
  val isFixP   = Bool()
  val isComp   = Bool()
  val isConj   = Bool()
}

class VIMac64bStage1Output extends Bundle {
  val compStage1ResultsS1    = Vec(7, UInt(152.W))
  val complexSumVecS1        = Vec(4, UInt(33.W))
  val complexCoutVecS1       = Vec(4, UInt(33.W))
  val wallaceLine34NonFixPS1 = UInt(152.W)
  val wallaceLine34FixPS1    = UInt(152.W)
  val highHalfS1             = Bool()
  val uopIdxS1               = UInt(6.W)
  val widenS1                = Bool()
  val vxrmS1                 = UInt(2.W)
  val isFixPS1               = Bool()
  val isCompS1               = Bool()
  val sewIs8S1               = Bool()
  val sewIs16S1              = Bool()
  val sewIs32S1              = Bool()
  val sewIs64S1              = Bool()
}

class VIMac64bStage1 extends Module {
  val io = IO(new Bundle {
    val in  = Input(new VIMac64bStage1Input)
    val out = Output(new VIMac64bStage1Output)
  })

  val vs2 = io.in.vs2
  val vs1 = io.in.vs1
  val oldVd = io.in.oldVd
  val vs2_is_signed = io.in.srcType(0)(2) // vs2 & vd should be signed numbers for vmadd/vnmsub
  val vs1_is_signed = io.in.srcType(1)(2)
  val vd_is_signed  = io.in.vdType(2)
  val eewVs2 = SewOH(io.in.srcType(0)(1, 0))

  val sew = eewVs2
  val sewIs64 = sew.is64
  val sewIs32 = sew.is32
  val sewIs16 = sew.is16
  val sewIs8 = sew.is8

  val isSub    = io.in.isSub
  val highHalf = io.in.highHalf
  val isMacc   = io.in.isMacc
  val widen    = io.in.widen
  val isFixP   = io.in.isFixP
  val isComp   = io.in.isComp
  val isConj   = io.in.isConj
  val uopIdx   = io.in.info.uopIdx
  val vxrm     = io.in.info.vxrm

  val vs1Set1 = Wire(UInt(64.W))
  val vs2Set1 = Wire(UInt(64.W))
  val vs1Set2 = Wire(UInt(64.W))
  val vs2Set2 = Wire(UInt(64.W))

  dontTouch(vs2Set2)

  // 1.source slicing
  vs1Set1 := Mux(isComp, Cat(Fill(2, vs1(47,32)), Fill(2, vs1(15,0))), vs1)
  vs2Set1 := vs2
  vs1Set2 := Mux(isComp, Cat(Fill(2, vs1(63,48)) ,Fill(2, vs1(31,16))), 0.U(64.W))
  vs2Set2 := Mux(isComp, Cat(vs2(47,32), vs2(63,48), vs2(15,0), vs2(31,16)), 0.U(64.W))

  // 2.vs1 booth encoding
  val vs1Set1BoothPos  = Wire(Vec(32, Bool()))
  val vs1Set1BoothNeg  = Wire(Vec(32, Bool()))
  val vs1Set1BoothDoZ  = Wire(Vec(32, Bool()))
  val vs1Set1BoothNonZ = Wire(Vec(32, Bool()))

  val vs1Set2BoothPos  = Wire(Vec(32, Bool()))
  val vs1Set2BoothNeg  = Wire(Vec(32, Bool()))
  val vs1Set2BoothDoZ  = Wire(Vec(32, Bool()))
  val vs1Set2BoothNonZ = Wire(Vec(32, Bool()))

  val vs1Set1BoothEncode = Module(new vs1Booth())
  vs1Set1BoothEncode.io.vs1     := vs1Set1
  vs1Set1BoothEncode.io.sewIs8  := sewIs8
  vs1Set1BoothEncode.io.sewIs16 := sewIs16
  vs1Set1BoothEncode.io.sewIs32 := sewIs32
  vs1Set1BoothEncode.io.sewIs64 := sewIs64
  vs1Set1BoothPos  := vs1Set1BoothEncode.io.positive
  vs1Set1BoothNeg  := vs1Set1BoothEncode.io.negative
  vs1Set1BoothDoZ  := vs1Set1BoothEncode.io.doubleOrZero
  vs1Set1BoothNonZ := vs1Set1BoothEncode.io.nonZero

  val vs1Set2BoothEncode = Module(new vs1Booth())
  vs1Set2BoothEncode.io.vs1     := vs1Set2
  vs1Set2BoothEncode.io.sewIs8  := sewIs8
  vs1Set2BoothEncode.io.sewIs16 := sewIs16
  vs1Set2BoothEncode.io.sewIs32 := sewIs32
  vs1Set2BoothEncode.io.sewIs64 := sewIs64
  vs1Set2BoothPos  := vs1Set2BoothEncode.io.positive
  vs1Set2BoothNeg  := vs1Set2BoothEncode.io.negative
  vs1Set2BoothDoZ  := vs1Set2BoothEncode.io.doubleOrZero
  vs1Set2BoothNonZ := vs1Set2BoothEncode.io.nonZero
  
  // 3.generate vs2 signed bits
  val sgnVs2Set1 = Wire(Vec(8, UInt(1.W)))
  val sgnVs2Set2 = Wire(Vec(8, UInt(1.W)))
  
  val vs2Set1SignGen = Module(new vs2SgnGenerator())
  vs2Set1SignGen.io.vs2            := vs2Set1
  vs2Set1SignGen.io.vs2_is_signed  := vs2_is_signed
  vs2Set1SignGen.io.sewIs8         := sewIs8
  vs2Set1SignGen.io.sewIs16        := sewIs16
  vs2Set1SignGen.io.sewIs32        := sewIs32
  vs2Set1SignGen.io.sewIs64        := sewIs64
  sgnVs2Set1 := vs2Set1SignGen.io.sgnVs2

  val vs2Set2SignGen = Module(new vs2SgnGenerator())
  vs2Set2SignGen.io.vs2            := vs2Set2
  vs2Set2SignGen.io.vs2_is_signed  := vs2_is_signed
  vs2Set2SignGen.io.sewIs8         := sewIs8
  vs2Set2SignGen.io.sewIs16        := sewIs16
  vs2Set2SignGen.io.sewIs32        := sewIs32
  vs2Set2SignGen.io.sewIs64        := sewIs64
  sgnVs2Set2 := vs2Set2SignGen.io.sgnVs2
 
  // 4.generate signed and carry bits for partial products
  val partProdCinSet1 = Wire(Vec(32, UInt(1.W)))
  val partProdSgnSet1 = Wire(Vec(32, UInt(1.W)))
  
  val partProdCinSet2 = Wire(Vec(32, UInt(1.W)))
  val partProdSgnSet2 = Wire(Vec(32, UInt(1.W)))

  val ppSgnAndCBGenSet1 = Module(new partProdSgnAndCarryBitGen(true))
  ppSgnAndCBGenSet1.io.isSub            := isSub
  ppSgnAndCBGenSet1.io.isComp           := isComp
  ppSgnAndCBGenSet1.io.isConj           := isConj
  ppSgnAndCBGenSet1.io.vs2_is_signed    := vs2_is_signed
  ppSgnAndCBGenSet1.io.sewIs8           := sewIs8
  ppSgnAndCBGenSet1.io.sewIs16          := sewIs16
  ppSgnAndCBGenSet1.io.sewIs32          := sewIs32
  ppSgnAndCBGenSet1.io.sewIs64          := sewIs64
  ppSgnAndCBGenSet1.io.sgnVs2           := sgnVs2Set1
  ppSgnAndCBGenSet1.io.vs1BoothPos      := vs1Set1BoothPos
  ppSgnAndCBGenSet1.io.vs1BoothNeg      := vs1Set1BoothNeg
  ppSgnAndCBGenSet1.io.vs1BoothNonZ     := vs1Set1BoothNonZ
  partProdCinSet1 := ppSgnAndCBGenSet1.io.partProdCin
  partProdSgnSet1 := ppSgnAndCBGenSet1.io.partProdSgn

  val ppSgnAndCBGenSet2 = Module(new partProdSgnAndCarryBitGen(false))
  ppSgnAndCBGenSet2.io.isSub            := isSub
  ppSgnAndCBGenSet2.io.isComp           := isComp
  ppSgnAndCBGenSet2.io.isConj           := isConj
  ppSgnAndCBGenSet2.io.vs2_is_signed    := vs2_is_signed
  ppSgnAndCBGenSet2.io.sewIs8           := sewIs8
  ppSgnAndCBGenSet2.io.sewIs16          := sewIs16
  ppSgnAndCBGenSet2.io.sewIs32          := sewIs32
  ppSgnAndCBGenSet2.io.sewIs64          := sewIs64
  ppSgnAndCBGenSet2.io.sgnVs2           := sgnVs2Set2
  ppSgnAndCBGenSet2.io.vs1BoothPos      := vs1Set2BoothPos
  ppSgnAndCBGenSet2.io.vs1BoothNeg      := vs1Set2BoothNeg
  ppSgnAndCBGenSet2.io.vs1BoothNonZ     := vs1Set2BoothNonZ
  partProdCinSet2 := ppSgnAndCBGenSet2.io.partProdCin
  partProdSgnSet2 := ppSgnAndCBGenSet2.io.partProdSgn

  dontTouch(ppSgnAndCBGenSet2.partProdCin)
  dontTouch(ppSgnAndCBGenSet2.vs1BoothNeg)
  dontTouch(ppSgnAndCBGenSet2.vs1BoothPos)
  dontTouch(ppSgnAndCBGenSet2.isSub)
  dontTouch(ppSgnAndCBGenSet2.isConj)
  dontTouch(ppSgnAndCBGenSet2.isComp)

  // 5.generate partial product
  val partProd       = Wire(Vec(32, UInt(68.W)))
  val partProdCompE1 = Wire(Vec(8,  UInt(20.W)))
  val partProdCompE2 = Wire(Vec(8,  UInt(20.W)))
  val partProdCompE3 = Wire(Vec(8,  UInt(20.W)))
  val partProdCompE4 = Wire(Vec(8,  UInt(20.W)))
  
  val partProdGen = Module(new partProdGenerator())
  partProdGen.io.partProdCin    := partProdCinSet1
  partProdGen.io.partProdSgn    := partProdSgnSet1
  partProdGen.io.vs1BoothDoZ    := vs1Set1BoothDoZ
  partProdGen.io.vs1BoothNonZ   := vs1Set1BoothNonZ
  partProdGen.io.sewIs8         := sewIs8
  partProdGen.io.sewIs16        := sewIs16
  partProdGen.io.sewIs32        := sewIs32
  partProdGen.io.sewIs64        := sewIs64
  partProdGen.io.vs2            := vs2Set1
  partProd := partProdGen.io.partProd

  val partProdCompGen1 = Module(new partProdCompGenerator())
  partProdCompGen1.io.partProdCinSet2  := VecInit(partProdCinSet2.slice(0,8))
  partProdCompGen1.io.partProdSgnSet2  := VecInit(partProdSgnSet2.slice(0,8))
  partProdCompGen1.io.vs1Set2BoothDoZ  := VecInit(vs1Set2BoothDoZ.slice(0,8))
  partProdCompGen1.io.vs1Set2BoothNonZ := VecInit(vs1Set2BoothNonZ.slice(0,8))
  partProdCompGen1.io.vs2ElementSet2   := vs2Set2(15,0)
  partProdCompE1 := partProdCompGen1.io.partProdComp

  val partProdCompGen2 = Module(new partProdCompGenerator())
  partProdCompGen2.io.partProdCinSet2  := VecInit(partProdCinSet2.slice(8,16))
  partProdCompGen2.io.partProdSgnSet2  := VecInit(partProdSgnSet2.slice(8,16))
  partProdCompGen2.io.vs1Set2BoothDoZ  := VecInit(vs1Set2BoothDoZ.slice(8,16))
  partProdCompGen2.io.vs1Set2BoothNonZ := VecInit(vs1Set2BoothNonZ.slice(8,16))
  partProdCompGen2.io.vs2ElementSet2   := vs2Set2(31,16)
  partProdCompE2 := partProdCompGen2.io.partProdComp

  val partProdCompGen3 = Module(new partProdCompGenerator())
  partProdCompGen3.io.partProdCinSet2  := VecInit(partProdCinSet2.slice(16,24))
  partProdCompGen3.io.partProdSgnSet2  := VecInit(partProdSgnSet2.slice(16,24))
  partProdCompGen3.io.vs1Set2BoothDoZ  := VecInit(vs1Set2BoothDoZ.slice(16,24))
  partProdCompGen3.io.vs1Set2BoothNonZ := VecInit(vs1Set2BoothNonZ.slice(16,24))
  partProdCompGen3.io.vs2ElementSet2   := vs2Set2(47,32)
  partProdCompE3 := partProdCompGen3.io.partProdComp
  
  val partProdCompGen4 = Module(new partProdCompGenerator())
  partProdCompGen4.io.partProdCinSet2  := VecInit(partProdCinSet2.slice(24,32))
  partProdCompGen4.io.partProdSgnSet2  := VecInit(partProdSgnSet2.slice(24,32))
  partProdCompGen4.io.vs1Set2BoothDoZ  := VecInit(vs1Set2BoothDoZ.slice(24,32))
  partProdCompGen4.io.vs1Set2BoothNonZ := VecInit(vs1Set2BoothNonZ.slice(24,32))
  partProdCompGen4.io.vs2ElementSet2   := vs2Set2(63,48)
  partProdCompE4 := partProdCompGen4.io.partProdComp

  
  // 6.generate wallace tree
  val wallaceTree = Wire(Vec(33, UInt(152.W)))
  val wallaceLine34NonFixP = Wire(UInt(152.W))
  val wallaceLine34FixP    = Wire(UInt(152.W))

  val wallaceTreeCompE1 = Wire(Vec(9, UInt(33.W)))
  val wallaceTreeCompE2 = Wire(Vec(9, UInt(33.W)))
  val wallaceTreeCompE3 = Wire(Vec(9, UInt(33.W)))
  val wallaceTreeCompE4 = Wire(Vec(9, UInt(33.W)))

  val wallaceTreeGen = Module(new wallaceTreeGenerator())
  wallaceTreeGen.io.partProd      := partProd
  wallaceTreeGen.io.vs1           := vs1Set1
  wallaceTreeGen.io.vs2           := vs2Set1
  wallaceTreeGen.io.oldVd         := oldVd
  wallaceTreeGen.io.partProdCin   := partProdCinSet1
  wallaceTreeGen.io.vs1_is_signed := vs1_is_signed
  wallaceTreeGen.io.vs2_is_signed := vs2_is_signed
  wallaceTreeGen.io.vd_is_signed  := vd_is_signed
  wallaceTreeGen.io.widen         := widen
  wallaceTreeGen.io.isMacc        := isMacc
  wallaceTreeGen.io.isComp        := isComp
  wallaceTreeGen.io.sewIs8        := sewIs8
  wallaceTreeGen.io.sewIs16       := sewIs16
  wallaceTreeGen.io.sewIs32       := sewIs32
  wallaceTreeGen.io.sewIs64       := sewIs64
  wallaceTree          := wallaceTreeGen.io.wallaceTree
  wallaceLine34NonFixP := wallaceTreeGen.io.wallaceLine34NonFixP
  wallaceLine34FixP    := wallaceTreeGen.io.wallaceLine34FixP

  val wallaceTreeCompGen1 = Module(new wallaceTreeCompGenerator())
  wallaceTreeCompGen1.io.partProdComp    := partProdCompE1
  wallaceTreeCompGen1.io.partProdCinSet2 := VecInit(partProdCinSet2.slice(0,8))
  wallaceTreeCompE1 := wallaceTreeCompGen1.io.wallaceTreeComp

  val wallaceTreeCompGen2 = Module(new wallaceTreeCompGenerator())
  wallaceTreeCompGen2.io.partProdComp    := partProdCompE2
  wallaceTreeCompGen2.io.partProdCinSet2 := VecInit(partProdCinSet2.slice(8,16))
  wallaceTreeCompE2 := wallaceTreeCompGen2.io.wallaceTreeComp
  
  val wallaceTreeCompGen3 = Module(new wallaceTreeCompGenerator())
  wallaceTreeCompGen3.io.partProdComp    := partProdCompE3
  wallaceTreeCompGen3.io.partProdCinSet2 := VecInit(partProdCinSet2.slice(16,24))
  wallaceTreeCompE3 := wallaceTreeCompGen3.io.wallaceTreeComp
  
  val wallaceTreeCompGen4 = Module(new wallaceTreeCompGenerator())
  wallaceTreeCompGen4.io.partProdComp    := partProdCompE4
  wallaceTreeCompGen4.io.partProdCinSet2 := VecInit(partProdCinSet2.slice(24,32))
  wallaceTreeCompE4 := wallaceTreeCompGen4.io.wallaceTreeComp

  // 7.wallace compress stage 1
  val compStage1Results = Wire(Vec(7, UInt(152.W)))
  val complexSumVec  = Wire(Vec(4, UInt(33.W)))
  val complexCoutVec = Wire(Vec(4, UInt(33.W)))
  
  val wallace3to2CompStage1 = Module(new wallace3to2CompressorStage1())
  wallace3to2CompStage1.io.wallaceTree := wallaceTree
  compStage1Results := wallace3to2CompStage1.io.compStage1Results

  val wallace3to2ComplexComp1 = Module(new wallace3to2ComplexCompressor())
  wallace3to2ComplexComp1.io.wallaceTreeComp := wallaceTreeCompE1
  complexSumVec(0)  := wallace3to2ComplexComp1.io.complexSum
  complexCoutVec(0) := wallace3to2ComplexComp1.io.complexCout

  val wallace3to2ComplexComp2 = Module(new wallace3to2ComplexCompressor())
  wallace3to2ComplexComp2.io.wallaceTreeComp := wallaceTreeCompE2
  complexSumVec(1)  := wallace3to2ComplexComp2.io.complexSum
  complexCoutVec(1) := wallace3to2ComplexComp2.io.complexCout

  val wallace3to2ComplexComp3 = Module(new wallace3to2ComplexCompressor())
  wallace3to2ComplexComp3.io.wallaceTreeComp := wallaceTreeCompE3
  complexSumVec(2)  := wallace3to2ComplexComp3.io.complexSum
  complexCoutVec(2) := wallace3to2ComplexComp3.io.complexCout

  val wallace3to2ComplexComp4 = Module(new wallace3to2ComplexCompressor())
  wallace3to2ComplexComp4.io.wallaceTreeComp := wallaceTreeCompE4
  complexSumVec(3)  := wallace3to2ComplexComp4.io.complexSum
  complexCoutVec(3) := wallace3to2ComplexComp4.io.complexCout

  io.out.compStage1ResultsS1    := compStage1Results
  io.out.complexSumVecS1        := complexSumVec
  io.out.complexCoutVecS1       := complexCoutVec
  io.out.wallaceLine34NonFixPS1 := wallaceLine34NonFixP
  io.out.wallaceLine34FixPS1    := wallaceLine34FixP
  io.out.highHalfS1             := highHalf
  io.out.uopIdxS1               := uopIdx
  io.out.widenS1                := widen
  io.out.vxrmS1                 := vxrm
  io.out.isFixPS1               := isFixP
  io.out.isCompS1               := isComp
  io.out.sewIs8S1               := sewIs8
  io.out.sewIs16S1              := sewIs16
  io.out.sewIs32S1              := sewIs32
  io.out.sewIs64S1              := sewIs64
}

class vs1Booth extends Module {
  val io = IO(new Bundle {
    val sewIs8  = Input(Bool())
    val sewIs16 = Input(Bool())
    val sewIs32 = Input(Bool())
    val sewIs64 = Input(Bool())
    val vs1     = Input(UInt(64.W))

    val positive     = Output(Vec(32, Bool()))
    val negative     = Output(Vec(32, Bool()))
    val doubleOrZero = Output(Vec(32, Bool()))
    val nonZero      = Output(Vec(32, Bool()))
  })
  
  val vs1 = io.vs1
  val sewIs64 = io.sewIs64
  val sewIs32 = io.sewIs32
  val sewIs16 = io.sewIs16
  val sewIs8 =  io.sewIs8
  
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

  io.positive     := vs1Booth.map(_.positive)
  io.negative     := vs1Booth.map(_.negative)
  io.doubleOrZero := vs1Booth.map(_.doubleOrZero)
  io.nonZero      := vs1Booth.map(_.nonZero)
}

class boothEncode(d: UInt) {
    val positive = (d(1) | d(0)) & ~d(2)
    val negative = (~d(1) | ~d(0)) & d(2)
    val doubleOrZero = ~(d(1) ^ d(0))
    val nonZero = positive | negative
}

class vs2SgnGenerator extends Module {
  val io = IO(new Bundle {
    val vs2_is_signed = Input(Bool())
    val sewIs8        = Input(Bool())
    val sewIs16       = Input(Bool())
    val sewIs32       = Input(Bool())
    val sewIs64       = Input(Bool())
    val vs2           = Input(UInt(64.W))

    val sgnVs2  = Output(Vec(8, UInt(1.W)))
  })
  
  val vs2_is_signed = io.vs2_is_signed
  val vs2     = io.vs2
  val sewIs64 = io.sewIs64
  val sewIs32 = io.sewIs32
  val sewIs16 = io.sewIs16
  val sewIs8  = io.sewIs8

  for(i <- 0 until 8) {
    val sgnSew64 = 63
    val sgnSew32 = 32*(i/4)+31
    val sgnSew16 = 16*(i/2)+15
    val sgnSew8  = 8*i+7
    io.sgnVs2(i) := (vs2_is_signed) & (sewIs64 & vs2(sgnSew64) | sewIs32 & vs2(sgnSew32) | sewIs16 & vs2(sgnSew16) | sewIs8 & vs2(sgnSew8))
  }
}

class partProdSgnAndCarryBitGen(isSet1: Boolean) extends Module {
  val io = IO(new Bundle {
    val isSub         = Input(Bool())
    val isComp        = Input(Bool())
    val isConj        = Input(Bool())
    val vs2_is_signed = Input(Bool())
    val sewIs8        = Input(Bool())
    val sewIs16       = Input(Bool())
    val sewIs32       = Input(Bool())
    val sewIs64       = Input(Bool())
    val sgnVs2        = Input(Vec(8,  UInt(1.W)))
    val vs1BoothPos   = Input(Vec(32, Bool()))
    val vs1BoothNeg   = Input(Vec(32, Bool()))
    val vs1BoothNonZ  = Input(Vec(32, Bool()))

    val partProdSgn  = Output(Vec(32, UInt(1.W)))
    val partProdCin  = Output(Vec(32, UInt(1.W)))
  })

  val isSub         = io.isSub
  val isComp        = io.isComp
  val isConj        = io.isConj
  val vs2_is_signed = io.vs2_is_signed
  val sewIs64       = io.sewIs64
  val sewIs32       = io.sewIs32
  val sewIs16       = io.sewIs16
  val sewIs8        = io.sewIs8
  val sgnVs2        = io.sgnVs2
  val vs1BoothPos   = io.vs1BoothPos
  val vs1BoothNeg   = io.vs1BoothNeg
  val vs1BoothNonZ  = io.vs1BoothNonZ
  
  val partProdCin   = Wire(Vec(32, UInt(1.W)))
  for (i <- 0 until 32) {
    if(isSet1){
      if(((i >= 8) && (i <= 15)) || (i >= 24)) {
        partProdCin(i) := vs1BoothNeg(i) & ((~isSub & ~isComp) | (~isConj & isComp)) | vs1BoothPos(i) & ((isSub & ~isComp) | (isConj & isComp))
      } else {
        partProdCin(i) := vs1BoothNeg(i) & ~isSub | vs1BoothPos(i) & isSub
      }
    } else {
      if ((i <= 7) || ((i >= 16) && (i <= 23))) {
        partProdCin(i) := vs1BoothNeg(i) & ((~isSub & ~isComp) | (isConj & isComp)) | vs1BoothPos(i) & ((isSub & ~isComp) | (~isConj & isComp))
      } else {
        partProdCin(i) := vs1BoothNeg(i) & ~isSub | vs1BoothPos(i) & isSub
      }
    }
  }
  io.partProdCin := partProdCin

  for (i <- 0 until 32) {
    val sgnVs2Index = i/4
    io.partProdSgn(i) := (partProdCin(i) ^ sgnVs2(sgnVs2Index)) & vs1BoothNonZ(i)
  }
}

class partProdGenerator extends Module {
   val io = IO(new Bundle {
    val partProdCin   = Input(Vec(32, UInt(1.W)))
    val partProdSgn   = Input(Vec(32, UInt(1.W)))
    val vs1BoothDoZ   = Input(Vec(32, Bool()))
    val vs1BoothNonZ  = Input(Vec(32, Bool()))
    val sewIs8        = Input(Bool())
    val sewIs16       = Input(Bool())
    val sewIs32       = Input(Bool())
    val sewIs64       = Input(Bool())
    val vs2           = Input(UInt(64.W))

    val partProd = Output(Vec(32, UInt(68.W)))
  })

  val partProdCin   = io.partProdCin
  val partProdSgn   = io.partProdSgn
  val vs1BoothDoZ   = io.vs1BoothDoZ
  val vs1BoothNonZ  = io.vs1BoothNonZ
  val sewIs64       = io.sewIs64
  val sewIs32       = io.sewIs32
  val sewIs16       = io.sewIs16
  val sewIs8        = io.sewIs8
  val vs2           = io.vs2

  for(i <- 0 until 32) {
    val sew8IndexL  = (i/4)*8
    val sew8IndexH  = (i/4)*8   + 7
    val sew16IndexL = (i/8)*16
    val sew16IndexH = (i/8)*16  + 15
    val sew32IndexL = (i/16)*32
    val sew32IndexH = (i/16)*32 + 31
    val sew64IndexL = 0
    val sew64IndexH = 63
    if (i == 0) { 
      io.partProd(i) := Mux1H(Seq(
        sewIs8  -> Cat(0.U(56.W), ~partProdSgn(i), partProdSgn(i), partProdSgn(i), Fill(9,  vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL),  partProdCin(i)), Cat(partProdSgn(i), Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL )))),
        sewIs16 -> Cat(0.U(48.W), ~partProdSgn(i), partProdSgn(i), partProdSgn(i), Fill(17, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL)))),
        sewIs32 -> Cat(0.U(32.W), ~partProdSgn(i), partProdSgn(i), partProdSgn(i), Fill(33, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL)))),
        sewIs64 -> Cat(           ~partProdSgn(i), partProdSgn(i), partProdSgn(i), Fill(65, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL))))                    
      ))
    } else if (i == 16) {
      io.partProd(i) := Mux1H(Seq(
        sewIs8  -> Cat(0.U(56.W), ~partProdSgn(i), partProdSgn(i),  partProdSgn(i), Fill(9,  vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL),  partProdCin(i)), Cat(partProdSgn(i), Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL )))),
        sewIs16 -> Cat(0.U(48.W), ~partProdSgn(i), partProdSgn(i),  partProdSgn(i), Fill(17, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL)))),
        sewIs32 -> Cat(0.U(32.W), ~partProdSgn(i), partProdSgn(i),  partProdSgn(i), Fill(33, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL)))),
        sewIs64 -> Cat(           0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(65, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL))))                    
      ))
    } else if (i % 8 == 0) {
      io.partProd(i) := Mux1H(Seq(
        sewIs8  -> Cat(0.U(56.W), ~partProdSgn(i), partProdSgn(i),  partProdSgn(i), Fill(9,  vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL),  partProdCin(i)), Cat(partProdSgn(i), Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL )))),
        sewIs16 -> Cat(0.U(48.W), ~partProdSgn(i), partProdSgn(i),  partProdSgn(i), Fill(17, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL)))),
        sewIs32 -> Cat(0.U(32.W), 0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(33, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL)))),
        sewIs64 -> Cat(           0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(65, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL))))                    
      ))
    } else if (i % 4 == 0) {
       io.partProd(i) := Mux1H(Seq(
        sewIs8  -> Cat(0.U(56.W), ~partProdSgn(i), partProdSgn(i),  partProdSgn(i), Fill(9,  vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL),  partProdCin(i)), Cat(partProdSgn(i), Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL )))),
        sewIs16 -> Cat(0.U(48.W), 0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(17, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL)))),
        sewIs32 -> Cat(0.U(32.W), 0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(33, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL)))),
        sewIs64 -> Cat(           0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(65, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL))))                    
      ))
    } else {
      io.partProd(i) := Mux1H(Seq(
        sewIs8  -> Cat(0.U(56.W), 0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(9,  vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL),  partProdCin(i)), Cat(partProdSgn(i), Fill(8,  partProdCin(i)) ^ vs2(sew8IndexH,  sew8IndexL )))),
        sewIs16 -> Cat(0.U(48.W), 0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(17, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(16, partProdCin(i)) ^ vs2(sew16IndexH, sew16IndexL)))),
        sewIs32 -> Cat(0.U(32.W), 0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(33, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(32, partProdCin(i)) ^ vs2(sew32IndexH, sew32IndexL)))),
        sewIs64 -> Cat(           0.U(1.W),        1.U(1.W),       ~partProdSgn(i), Fill(65, vs1BoothNonZ(i)) & Mux(vs1BoothDoZ(i), Cat(Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL), partProdCin(i)), Cat(partProdSgn(i), Fill(64, partProdCin(i)) ^ vs2(sew64IndexH, sew64IndexL))))                    
      ))
    }
  }
}

class partProdCompGenerator extends Module {
  val io = IO(new Bundle {
    val partProdCinSet2   = Input(Vec(8, UInt(1.W)))
    val partProdSgnSet2   = Input(Vec(8, UInt(1.W)))
    val vs1Set2BoothDoZ   = Input(Vec(8, Bool()))
    val vs1Set2BoothNonZ  = Input(Vec(8, Bool()))
    val vs2ElementSet2    = Input(UInt(16.W))

    val partProdComp = Output(Vec(8, UInt(20.W)))
  })

  val partProdCinSet2  = io.partProdCinSet2
  val partProdSgnSet2  = io.partProdSgnSet2
  val vs1Set2BoothDoZ  = io.vs1Set2BoothDoZ
  val vs1Set2BoothNonZ = io.vs1Set2BoothNonZ
  val vs2ElementSet2   = io.vs2ElementSet2

  val partProdComp = Wire(Vec(8, UInt(20.W)))
  partProdComp(0) := Cat(~partProdSgnSet2(0), partProdSgnSet2(0), partProdSgnSet2(0), Fill(17, vs1Set2BoothNonZ(0)) & Mux(vs1Set2BoothDoZ(0), Cat(Fill(16, partProdCinSet2(0)) ^ vs2ElementSet2, partProdCinSet2(0)), Cat(partProdSgnSet2(0), Fill(16, partProdCinSet2(0)) ^ vs2ElementSet2)))
  for (i <-1 until 8) {
    partProdComp(i) := Cat(1.U(2.W), ~partProdSgnSet2(i), Fill(17, vs1Set2BoothNonZ(i)) & Mux(vs1Set2BoothDoZ(i), Cat(Fill(16, partProdCinSet2(i)) ^ vs2ElementSet2, partProdCinSet2(i)), Cat(partProdSgnSet2(i), Fill(16, partProdCinSet2(i)) ^ vs2ElementSet2)))
  }

  io.partProdComp := partProdComp
}

class wallaceTreeGenerator extends Module {
  val io = IO(new Bundle {
    val partProd      = Input(Vec(32, UInt(68.W)))
    val vs1           = Input(UInt(64.W))
    val vs2           = Input(UInt(64.W))
    val oldVd         = Input(UInt(64.W))
    val partProdCin   = Input(Vec(32, UInt(1.W)))
    val vs1_is_signed = Input(Bool())
    val vs2_is_signed = Input(Bool())
    val vd_is_signed  = Input(Bool())
    val widen         = Input(Bool())
    val isMacc        = Input(Bool())
    val isComp        = Input(Bool())
    val sewIs8        = Input(Bool())
    val sewIs16       = Input(Bool())
    val sewIs32       = Input(Bool())
    val sewIs64       = Input(Bool())

    val wallaceTree          = Output(Vec(33, UInt(152.W)))
    val wallaceLine34NonFixP = Output(UInt(152.W))
    val wallaceLine34FixP    = Output(UInt(152.W))
  })

  def wallaceTreeGen(i: Int, wallaceLine: UInt, partProd: UInt, partProdCin: UInt, sewIs8: Bool, sewIs16: Bool, sewIs32: Bool, sewIs64: Bool): Unit = {
    val elementIndex8b  = i/4
    val elementIndex16b = i/8
    val elementIndex32b = i/16
    val shift8b  = i % 4
    val shift16b = i % 8
    val shift32b = i % 16
    val shift64b = i
    if (i == 0) {
        wallaceTree(i) := Cat(0.U(84.W), partProd)
    } else if (i == 16) {
        wallaceLine := Mux1H(Seq(
          sewIs8  -> Cat(0.U((152 - elementIndex8b*19  - shift8b*2  - 12).W), partProd(11, 0),                        0.U((elementIndex8b*19  + shift8b*2).W)),
          sewIs16 -> Cat(0.U((152 - elementIndex16b*38 - shift16b*2 - 20).W), partProd(19, 0),                        0.U((elementIndex16b*38 + shift16b*2).W)),
          sewIs32 -> Cat(0.U((152 - elementIndex32b*76 - shift32b*2 - 36).W), partProd(35, 0),                        0.U((elementIndex32b*76 + shift32b*2).W)),
          sewIs64 -> Cat(0.U((152                      - shift64b*2 - 68).W), partProd,        0.U(1.W), partProdCin, 0.U((shift64b*2 - 2).W))
      ))
    } else if (i % 8 == 0) {
      wallaceLine := Mux1H(Seq(
          sewIs8  -> Cat(0.U((152 - elementIndex8b*19  - shift8b*2  - 12).W), partProd(11, 0),                        0.U((elementIndex8b*19  + shift8b*2).W)),
          sewIs16 -> Cat(0.U((152 - elementIndex16b*38 - shift16b*2 - 20).W), partProd(19, 0),                        0.U((elementIndex16b*38 + shift16b*2).W)),
          sewIs32 -> Cat(0.U((152 - elementIndex32b*76 - shift32b*2 - 36).W), partProd(35, 0), 0.U(1.W), partProdCin, 0.U((elementIndex32b*76 + shift32b*2 - 2).W)),
          sewIs64 -> Cat(0.U((152                      - shift64b*2 - 68).W), partProd,        0.U(1.W), partProdCin, 0.U((shift64b*2 - 2).W))
      ))
    } else if (i % 4 == 0) {
      wallaceLine := Mux1H(Seq(
          sewIs8  -> Cat(0.U((152 - elementIndex8b*19  - shift8b*2  - 12).W), partProd(11, 0),                        0.U((elementIndex8b*19  + shift8b*2).W)),
          sewIs16 -> Cat(0.U((152 - elementIndex16b*38 - shift16b*2 - 20).W), partProd(19, 0), 0.U(1.W), partProdCin, 0.U((elementIndex16b*38 + shift16b*2 - 2).W)),
          sewIs32 -> Cat(0.U((152 - elementIndex32b*76 - shift32b*2 - 36).W), partProd(35, 0), 0.U(1.W), partProdCin, 0.U((elementIndex32b*76 + shift32b*2 - 2).W)),
          sewIs64 -> Cat(0.U((152                      - shift64b*2 - 68).W), partProd,        0.U(1.W), partProdCin, 0.U((shift64b*2 - 2).W))
      ))
    } else {
      wallaceLine := Mux1H(Seq(
          sewIs8  -> Cat(0.U((152 - elementIndex8b*19  - shift8b*2  - 12).W), partProd(11, 0), 0.U(1.W), partProdCin, 0.U((elementIndex8b*19  + shift8b*2  - 2).W)),
          sewIs16 -> Cat(0.U((152 - elementIndex16b*38 - shift16b*2 - 20).W), partProd(19, 0), 0.U(1.W), partProdCin, 0.U((elementIndex16b*38 + shift16b*2 - 2).W)),
          sewIs32 -> Cat(0.U((152 - elementIndex32b*76 - shift32b*2 - 36).W), partProd(35, 0), 0.U(1.W), partProdCin, 0.U((elementIndex32b*76 + shift32b*2 - 2).W)),
          sewIs64 -> Cat(0.U((152                      - shift64b*2 - 68).W), partProd,        0.U(1.W), partProdCin, 0.U((shift64b*2 - 2).W))
      ))
    }
  }

  val partProd      = io.partProd
  val vs1           = io.vs1
  val vs2           = io.vs2
  val oldVd         = io.oldVd
  val partProdCin   = io.partProdCin
  val vs1_is_signed = io.vs1_is_signed
  val vs2_is_signed = io.vs2_is_signed
  val vd_is_signed  = io.vd_is_signed
  val widen         = io.widen
  val isMacc        = io.isMacc
  val isComp        = io.isComp
  val sewIs8        = io.sewIs8
  val sewIs16       = io.sewIs16
  val sewIs32       = io.sewIs32
  val sewIs64       = io.sewIs64

  val wallaceTree = Wire(Vec(33, UInt(152.W)))
  val wallaceLine34NonFixP = Wire(UInt(152.W))
  val wallaceLine34FixP    = Wire(UInt(152.W))
  wallaceTreeGen(0, wallaceTree(0), partProd(0), 0.U(1.W), sewIs8, sewIs16, sewIs32, sewIs64)
  for (i <- 1 until 32) {
    wallaceTreeGen(i, wallaceTree(i), partProd(i), partProdCin(i-1), sewIs8, sewIs16, sewIs32, sewIs64)
  }

  wallaceTree(32) := Mux(io.isMacc, Mux1H(Seq(
    sewIs8  -> Mux(io.widen, Cat(UIntSplit(Cat(oldVd, oldVd), 16).reverse.map(x => Cat(0.U(2.W),  BitsExtend(x, 17, vd_is_signed)))), Cat(UIntSplit(oldVd, 8 ).map(x => Cat(0.U(2.W),  BitsExtend(x, 17, vd_is_signed))).reverse)),
    sewIs16 -> Mux(io.widen, Cat(UIntSplit(Cat(oldVd, oldVd), 32).reverse.map(x => Cat(0.U(5.W),  BitsExtend(x, 33, vd_is_signed)))), Cat(UIntSplit(oldVd, 16).map(x => Mux(isComp, Cat(0.U(5.W), BitsExtend(x, 18, true.B), 0.U(15.W)), Cat(0.U(5.W), BitsExtend(x, 33, vd_is_signed)))).reverse)),
    sewIs32 -> Mux(io.widen, Cat(UIntSplit(Cat(oldVd, oldVd), 64).reverse.map(x => Cat(0.U(11.W), BitsExtend(x, 65, vd_is_signed)))), Cat(UIntSplit(oldVd, 32).map(x => Cat(0.U(11.W), BitsExtend(x, 65, vd_is_signed))).reverse)),
    sewIs64 -> Cat(0.U(23.W), BitsExtend(oldVd, 129, vd_is_signed))
  )), 0.U)

  wallaceLine34NonFixP := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(vs2, 8 ).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(2.W),  Mux(~vs1_is_signed & vs1(63 - 8*index),  BitsExtend(x,   9 , vs2_is_signed), 0.U(9.W)) , 0.U(1.W), partProdCin(31 - 4*index),  0.U(6.W))}),
    sewIs16 -> Cat(UIntSplit(vs2, 16).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(5.W),  Mux(~vs1_is_signed & vs1(63 - 16*index), BitsExtend(x,   17, vs2_is_signed), 0.U(17.W)), 0.U(1.W), partProdCin(31 - 8*index),  0.U(14.W))}),
    sewIs32 -> Cat(UIntSplit(vs2, 32).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(11.W), Mux(~vs1_is_signed & vs1(63 - 32*index), BitsExtend(x,   33, vs2_is_signed), 0.U(33.W)), 0.U(1.W), partProdCin(31 - 16*index), 0.U(30.W))}),
    sewIs64 ->                                                                    Cat(0.U(23.W), Mux(~vs1_is_signed & vs1(63),            BitsExtend(vs2, 65, vs2_is_signed), 0.U(35.W)), 0.U(1.W), partProdCin(31)           , 0.U(62.W))
  ))

  wallaceLine34FixP := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(vs2, 8 ).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(2.W),  Mux(~vs1_is_signed & vs1(63 - 8*index),  BitsExtend(x,   9 , vs2_is_signed), 0.U(9.W)) , 1.U(1.W), partProdCin(31 - 4*index),  0.U(6.W))}),
    sewIs16 -> Cat(UIntSplit(vs2, 16).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(5.W),  Mux(~vs1_is_signed & vs1(63 - 16*index), BitsExtend(x,   17, vs2_is_signed), 0.U(17.W)), 1.U(1.W), partProdCin(31 - 8*index),  0.U(14.W))}),
    sewIs32 -> Cat(UIntSplit(vs2, 32).reverse.zipWithIndex.map{ case(x, index) => Cat(0.U(11.W), Mux(~vs1_is_signed & vs1(63 - 32*index), BitsExtend(x,   33, vs2_is_signed), 0.U(33.W)), 1.U(1.W), partProdCin(31 - 16*index), 0.U(30.W))}),
    sewIs64 ->                                                                    Cat(0.U(23.W), Mux(~vs1_is_signed & vs1(63),            BitsExtend(vs2, 65, vs2_is_signed), 0.U(35.W)), 1.U(1.W), partProdCin(31)           , 0.U(62.W))
  ))

  io.wallaceTree := wallaceTree
  io.wallaceLine34NonFixP := wallaceLine34NonFixP
  io.wallaceLine34FixP    := wallaceLine34FixP
}

class wallaceTreeCompGenerator extends Module {
  val io = IO(new Bundle {
    val partProdComp    = Input(Vec(8, UInt(20.W)))
    val partProdCinSet2 = Input(Vec(8, UInt(1.W)))

    val wallaceTreeComp = Output(Vec(9, UInt(33.W)))
  })

  val partProdComp    = io.partProdComp
  val partProdCinSet2 = io.partProdCinSet2

  val wallaceTreeComp = Wire(Vec(9, UInt(33.W)))
  wallaceTreeComp(0) := Cat(0.U(13.W), partProdComp(0))
  for (i <- 1 until 8) {
    wallaceTreeComp(i) := Cat(0.U((14-2*i).W), partProdComp(i)(18,0), 0.U(1.W), partProdCinSet2(i-1), 0.U((2*i-2).W))
  }
  wallaceTreeComp(8) := Cat(0.U(18.W), partProdCinSet2(7), 0.U(14.W))
  
  io.wallaceTreeComp := wallaceTreeComp
}

class wallace3to2CompressorStage1 extends Module with wallace3to2Compressor {
  override val width = 152
  val io = IO(new Bundle {
    val wallaceTree = Input(Vec(33, UInt(152.W)))

    val compStage1Results = Output(Vec(7, UInt(152.W)))
  })

  val wallaceTree = io.wallaceTree
  val result = wallaceCompress(7, wallaceTree)
  io.compStage1Results := result
}

class wallace3to2ComplexCompressor extends Module with wallace3to2Compressor {
  override val width = 33
   val io = IO(new Bundle {
    val wallaceTreeComp = Input(Vec(9, UInt(33.W)))

    val complexSum  = Output(UInt(33.W))
    val complexCout = Output(UInt(33.W))
  })

  val wallaceTreeComp = io.wallaceTreeComp
  val result = wallaceCompress(2, wallaceTreeComp)
  
  io.complexSum  := result(0)
  io.complexCout := result(1)
}

object VIMac64bStage1 extends App {
  (new ChiselStage).execute(args, Seq(
    ChiselGeneratorAnnotation(() => new VIMac64bStage1()), FirtoolOption("--lowering-options=explicitBitcast")
  ))
}