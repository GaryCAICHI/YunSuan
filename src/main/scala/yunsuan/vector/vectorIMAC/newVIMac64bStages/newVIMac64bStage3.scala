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
    val sumFinalS2 = Input(UInt(152.W))
    val highHalfS2 = Input(Bool())
    val uopIdxS2   = Input(UInt(6.W))
    val widenS2    = Input(Bool())
    val vxrmS2     = Input(UInt(2.W))
    val isFixPS2   = Input(Bool())
    val sewIs8S2   = Input(Bool())
    val sewIs16S2  = Input(Bool())
    val sewIs32S2  = Input(Bool())
    val sewIs64S2  = Input(Bool())

    val vd    = Output(UInt(64.W))
    val vxsat = Output(UInt(8.W))
  })

  val sumFinalS2 = io.sumFinalS2
  val highHalfS2 = io.highHalfS2
  val uopIdxS2   = io.uopIdxS2
  val widenS2    = io.widenS2
  val vxrmS2     = io.vxrmS2
  val isFixPS2   = io.isFixPS2
  val sewIs8S2   = io.sewIs8S2
  val sewIs16S2  = io.sewIs16S2
  val sewIs32S2  = io.sewIs32S2
  val sewIs64S2  = io.sewIs64S2
  
  // 9.get non fixed-point vd
  val vdNonFixP = Wire(UInt(64.W))

  val vdNonFixPGen = Module(new vdNonFixPGenerator())
  vdNonFixPGen.io.sumFinal := sumFinalS2
  vdNonFixPGen.io.highHalf := highHalfS2
  vdNonFixPGen.io.widen    := widenS2
  vdNonFixPGen.io.uopIdx   := uopIdxS2
  vdNonFixPGen.io.sewIs8   := sewIs8S2
  vdNonFixPGen.io.sewIs16  := sewIs16S2
  vdNonFixPGen.io.sewIs32  := sewIs32S2
  vdNonFixPGen.io.sewIs64  := sewIs64S2
  vdNonFixP := vdNonFixPGen.io.vdNonFixP
  // ----------- fixed-point instruction result handling  -----------
  // 10.generate fixed-point input
  val vdRndIn = Wire(UInt(64.W))

  val vdRndInputGen = Module(new vdRndInputGenerator())
  vdRndInputGen.io.sumFinal := sumFinalS2
  vdRndInputGen.io.sewIs8   := sewIs8S2
  vdRndInputGen.io.sewIs16  := sewIs16S2
  vdRndInputGen.io.sewIs32  := sewIs32S2
  vdRndInputGen.io.sewIs64  := sewIs64S2
  vdRndIn := vdRndInputGen.io.vdRndIn

  // 11.get vxsat bits
  val vxsat = Wire(UInt(8.W))

  val vxsatGen = Module(new vxsatGenerator())
  vxsatGen.io.sumFinal := sumFinalS2
  vxsatGen.io.sewIs8   := sewIs8S2
  vxsatGen.io.sewIs16  := sewIs16S2
  vxsatGen.io.sewIs32  := sewIs32S2
  vxsatGen.io.sewIs64  := sewIs64S2
  vxsat := vxsatGen.io.vxsat

  // 12.get rounding increment bits
  val rndIncVec = Wire(UInt(8.W))

  val rndIncVecGen = Module(new rndIncVecGenerator())
  rndIncVecGen.io.sumFinal := sumFinalS2
  rndIncVecGen.io.vxrm     := vxrmS2
  rndIncVecGen.io.sewIs8   := sewIs8S2
  rndIncVecGen.io.sewIs16  := sewIs16S2
  rndIncVecGen.io.sewIs32  := sewIs32S2
  rndIncVecGen.io.sewIs64  := sewIs64S2
  rndIncVec := rndIncVecGen.io.rndIncVec

  // 13.get rounding result
  val vdRndOut = Wire(Vec(8, UInt(8.W)))

  val vdRndGen = Module(new vdRndGenerator())
  vdRndGen.io.vdRndIn   := vdRndIn
  vdRndGen.io.rndIncVec := rndIncVec
  vdRndGen.io.sewIs8   := sewIs8S2
  vdRndGen.io.sewIs16  := sewIs16S2
  vdRndGen.io.sewIs32  := sewIs32S2
  vdRndGen.io.sewIs64  := sewIs64S2
  vdRndOut := vdRndGen.io.vdRndOut

  // 14.get fixed-point vd
  val vdFixP = Wire(UInt(64.W))
  
  val vdFixPGen = Module(new vdFixPGenerator())
  vdFixPGen.io.vdRndOut := vdRndOut
  vdFixPGen.io.vxsat    := vxsat
  vdFixPGen.io.sewIs8   := sewIs8S2
  vdFixPGen.io.sewIs16  := sewIs16S2
  vdFixPGen.io.sewIs32  := sewIs32S2
  vdFixPGen.io.sewIs64  := sewIs64S2
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

