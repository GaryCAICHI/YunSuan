package yunsuan.vector.mac

import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage._
import chisel3.util._
import yunsuan.vector._
import yunsuan.util._

class newVIMac64bStage1 extends Module {
  val io = IO(new Bundle {
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

    val compStage1ResultsS1    = Output(Vec(7, UInt(152.W)))
    val wallaceLine34NonFixPS1 = Output(UInt(152.W))
    val wallaceLine34FixPS1    = Output(UInt(152.W))
    val highHalfS1             = Output(Bool())
    val uopIdxS1               = Output(UInt(6.W))
    val widenS1                = Output(Bool())
    val vxrmS1                 = Output(UInt(2.W))
    val isFixPS1               = Output(Bool())
    val sewIs8S1               = Output(Bool())
    val sewIs16S1              = Output(Bool())
    val sewIs32S1              = Output(Bool())
    val sewIs64S1             = Output(Bool())
  })

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

  val isSub    = io.isSub
  val highHalf = io.highHalf
  val widen    = io.widen
  val isFixP   = io.isFixP
  val uopIdx   = io.info.uopIdx
  val vxrm     = io.info.vxrm
  
  // Start of First Pipeline Stage
  //-----------------------------------------------------------------------------
  // 1.vs1 booth encoding
  val vs1BoothPos  = Wire(Vec(32, Bool()))
  val vs1BoothNeg  = Wire(Vec(32, Bool()))
  val vs1BoothDoZ  = Wire(Vec(32, Bool()))
  val vs1BoothNonZ = Wire(Vec(32, Bool()))

  val vs1BoothEncode = Module(new vs1Booth())
  vs1BoothEncode.io.vs1     := vs1
  vs1BoothEncode.io.sewIs8  := sewIs8
  vs1BoothEncode.io.sewIs16 := sewIs16
  vs1BoothEncode.io.sewIs32 := sewIs32
  vs1BoothEncode.io.sewIs64 := sewIs64
  vs1BoothPos  := vs1BoothEncode.io.positive
  vs1BoothNeg  := vs1BoothEncode.io.negative
  vs1BoothDoZ  := vs1BoothEncode.io.doubleOrZero
  vs1BoothNonZ := vs1BoothEncode.io.nonZero
  
  // 2.generate vs2 signed bits
  val sgnVs2 = Wire(Vec(8, UInt(1.W)))
  
  val vs2SignGen = Module(new vs2SgnGenerator())
  vs2SignGen.io.vs2            := vs2
  vs2SignGen.io.vs2_is_signed  := vs2_is_signed
  vs2SignGen.io.sewIs8         := sewIs8
  vs2SignGen.io.sewIs16        := sewIs16
  vs2SignGen.io.sewIs32        := sewIs32
  vs2SignGen.io.sewIs64        := sewIs64
  sgnVs2 := vs2SignGen.io.sgnVs2
 
  // 3.generate signed and carry bits for partial products
  val partProdCin = Wire(Vec(32, UInt(1.W)))
  val partProdSgn = Wire(Vec(32, UInt(1.W)))

  val ppSgnAndCBGen = Module(new partProdSgnAndCarryBitGen())
  ppSgnAndCBGen.io.isSub            := isSub
  ppSgnAndCBGen.io.vs2_is_signed    := vs2_is_signed
  ppSgnAndCBGen.io.sewIs8           := sewIs8
  ppSgnAndCBGen.io.sewIs16          := sewIs16
  ppSgnAndCBGen.io.sewIs32          := sewIs32
  ppSgnAndCBGen.io.sewIs64          := sewIs64
  ppSgnAndCBGen.io.sgnVs2           := sgnVs2
  ppSgnAndCBGen.io.vs1BoothPos      := vs1BoothPos
  ppSgnAndCBGen.io.vs1BoothNeg      := vs1BoothNeg
  ppSgnAndCBGen.io.vs1BoothNonZ     := vs1BoothNonZ
  partProdCin := ppSgnAndCBGen.io.partProdCin
  partProdSgn := ppSgnAndCBGen.io.partProdSgn

  // 4.generate partial product
  val partProd = Wire(Vec(32, UInt(68.W)))
  
  val partProdGen = Module(new partProdGenerator())
  partProdGen.io.partProdCin    := partProdCin
  partProdGen.io.partProdSgn    := partProdSgn
  partProdGen.io.vs1BoothDoZ    := vs1BoothDoZ
  partProdGen.io.vs1BoothNonZ   := vs1BoothNonZ
  partProdGen.io.sewIs8         := sewIs8
  partProdGen.io.sewIs16        := sewIs16
  partProdGen.io.sewIs32        := sewIs32
  partProdGen.io.sewIs64        := sewIs64
  partProdGen.io.vs2            := vs2
  partProd := partProdGen.io.partProd

  
  // 5.generate wallace tree
  val wallaceTree = Wire(Vec(33, UInt(152.W)))
  val wallaceLine34NonFixP = Wire(UInt(152.W))
  val wallaceLine34FixP    = Wire(UInt(152.W))

  val wallaceTreeGen = Module(new wallaceTreeGenerator())
  wallaceTreeGen.io.partProd      := partProd
  wallaceTreeGen.io.vs1           := vs1
  wallaceTreeGen.io.vs2           := vs2
  wallaceTreeGen.io.oldVd         := oldVd
  wallaceTreeGen.io.partProdCin   := partProdCin
  wallaceTreeGen.io.vs1_is_signed := vs1_is_signed
  wallaceTreeGen.io.vs2_is_signed := vs2_is_signed
  wallaceTreeGen.io.vd_is_signed  := vd_is_signed
  wallaceTreeGen.io.widen         := widen
  wallaceTreeGen.io.isMacc        := io.isMacc
  wallaceTreeGen.io.sewIs8        := sewIs8
  wallaceTreeGen.io.sewIs16       := sewIs16
  wallaceTreeGen.io.sewIs32       := sewIs32
  wallaceTreeGen.io.sewIs64       := sewIs64
  wallaceTree          := wallaceTreeGen.io.wallaceTree
  wallaceLine34NonFixP := wallaceTreeGen.io.wallaceLine34NonFixP
  wallaceLine34FixP    := wallaceTreeGen.io.wallaceLine34FixP

  // 6.wallace compress stage 1
  val compStage1Results = Wire(Vec(7, UInt(152.W)))
  
  val wallace3to2CompStage1 = Module(new wallace3to2CompressorStage1())
  wallace3to2CompStage1.io.wallaceTree := wallaceTree
  compStage1Results := wallace3to2CompStage1.io.compStage1Results

  io.compStage1ResultsS1    := compStage1Results
  io.wallaceLine34NonFixPS1 := wallaceLine34NonFixP
  io.wallaceLine34FixPS1    := wallaceLine34FixP
  io.highHalfS1          := highHalf
  io.uopIdxS1            := uopIdx
  io.widenS1             := widen
  io.vxrmS1              := vxrm
  io.isFixPS1            := isFixP
  io.sewIs8S1            := sewIs8
  io.sewIs16S1           := sewIs16
  io.sewIs32S1           := sewIs32
  io.sewIs64S1           := sewIs64
}

object newVIMac64bStage1 extends App {
  (new ChiselStage).execute(args, Seq(
    ChiselGeneratorAnnotation(() => new newVIMac64bStage1()), FirtoolOption("--lowering-options=explicitBitcast")
  ))
}