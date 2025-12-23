package yunsuan.vector.mac

import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage._
import chisel3.util._
import yunsuan.vector._
import yunsuan.util._
import yunsuan.encoding.Opcode.VimacOpcode.isMacc
import yunsuan.VidivType.vdiv
import yunsuan.encoding.Opcode.VimacOpcode.isFixP

class newVIMac64bStage3 extends Module {
  val io = IO(new Bundle {
    val sumFinalNonFixPS2 = Input(UInt(152.W))
    val sumFinalFixPS2    = Input(UInt(152.W))
    val highHalfS2        = Input(Bool())
    val uopIdxS2          = Input(UInt(6.W))
    val widenS2           = Input(Bool())
    val vxrmS2            = Input(UInt(2.W))
    val isFixPS2          = Input(Bool())
    val sewIs8S2          = Input(Bool())
    val sewIs16S2         = Input(Bool())
    val sewIs32S2         = Input(Bool())
    val sewIs64S2         = Input(Bool())

    val vd    = Output(UInt(64.W))
    val vxsat = Output(UInt(8.W))
  })

  val sumFinalNonFixPS2 = io.sumFinalNonFixPS2
  val sumFinalFixPS2    = io.sumFinalFixPS2
  val highHalfS2        = io.highHalfS2
  val uopIdxS2          = io.uopIdxS2
  val widenS2           = io.widenS2
  val vxrmS2            = io.vxrmS2
  val isFixPS2          = io.isFixPS2
  val sewIs8S2          = io.sewIs8S2
  val sewIs16S2         = io.sewIs16S2
  val sewIs32S2         = io.sewIs32S2
  val sewIs64S2         = io.sewIs64S2
  
  // 9.get non fixed-point vd
  val vdNonFixP = Wire(UInt(64.W))

  val vdNonFixPGen = Module(new vdNonFixPGenerator())
  vdNonFixPGen.io.sumFinalNonFixP := sumFinalNonFixPS2
  vdNonFixPGen.io.highHalf        := highHalfS2
  vdNonFixPGen.io.widen           := widenS2
  vdNonFixPGen.io.uopIdx          := uopIdxS2
  vdNonFixPGen.io.sewIs8          := sewIs8S2
  vdNonFixPGen.io.sewIs16         := sewIs16S2
  vdNonFixPGen.io.sewIs32         := sewIs32S2
  vdNonFixPGen.io.sewIs64         := sewIs64S2
  vdNonFixP := vdNonFixPGen.io.vdNonFixP
  
  // ----------- fixed-point instruction result handling  -----------
  // 10.get vxsat bits
  val vdSat = Wire(UInt(64.W))
  val vxsat = Wire(UInt(8.W))

  val vxsatGen = Module(new vxsatGenerator())
  vxsatGen.io.sumFinalNonFixP := sumFinalNonFixPS2
  vxsatGen.io.sewIs8          := sewIs8S2
  vxsatGen.io.sewIs16         := sewIs16S2
  vxsatGen.io.sewIs32         := sewIs32S2
  vxsatGen.io.sewIs64         := sewIs64S2
  vdSat := vxsatGen.io.vdSat
  vxsat := vxsatGen.io.vxsat

  // 11.get rounding increment bits
  val rndIncVec = Wire(UInt(8.W))

  val rndIncVecGen = Module(new rndIncVecGenerator())
  rndIncVecGen.io.sumFinalNonFixP := sumFinalNonFixPS2
  rndIncVecGen.io.vxrm            := vxrmS2
  rndIncVecGen.io.sewIs8          := sewIs8S2
  rndIncVecGen.io.sewIs16         := sewIs16S2
  rndIncVecGen.io.sewIs32         := sewIs32S2
  rndIncVecGen.io.sewIs64         := sewIs64S2
  rndIncVec := rndIncVecGen.io.rndIncVec

  // 12.get rounding vd
  val vdRndInc    = Wire(UInt(64.W))

  val vdRndGen = Module(new vdRndGenerator())
  vdRndGen.io.sumFinalFixP    := sumFinalFixPS2
  vdRndGen.io.sewIs8   := sewIs8S2
  vdRndGen.io.sewIs16  := sewIs16S2
  vdRndGen.io.sewIs32  := sewIs32S2
  vdRndGen.io.sewIs64  := sewIs64S2
  vdRndInc    := vdRndGen.io.vdRndInc

  // 14.get fixed-point vd
  val vdFixP = Wire(UInt(64.W))
  
  val vdFixPGen = Module(new vdFixPGenerator())
  vdFixPGen.io.vdRndInc    := vdRndInc
  vdFixPGen.io.vdSat       := vdSat
  vdFixPGen.io.rndIncVec   := rndIncVec
  vdFixPGen.io.sewIs8      := sewIs8S2
  vdFixPGen.io.sewIs16     := sewIs16S2
  vdFixPGen.io.sewIs32     := sewIs32S2
  vdFixPGen.io.sewIs64     := sewIs64S2
  vdFixP := vdFixPGen.io.vdFixP

  // 15.get final output
  val outputMux = Module(new outputSelect())
  outputMux.io.vdNonFixP := vdNonFixP
  outputMux.io.vdFixP    := vdFixP
  outputMux.io.vxsat     := vxsat
  outputMux.io.isFixP    := isFixPS2
  
  // Connect Output
  io.vd    := outputMux.io.vdOut
  io.vxsat := outputMux.io.vxsatOut
}

object newVIMac64bStage3 extends App {
  (new ChiselStage).execute(args, Seq(
    ChiselGeneratorAnnotation(() => new newVIMac64bStage3()), FirtoolOption("--lowering-options=explicitBitcast")
  ))
}

