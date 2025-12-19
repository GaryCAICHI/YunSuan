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

class newVIMac64bStage2 extends Module {
  val io = IO(new Bundle {
    val compStage1ResultsS1 = Input(Vec(11, UInt(152.W)))
    val fireS1              = Input(Bool())
    val highHalfS1          = Input(Bool())
    val uopIdxS1            = Input(UInt(6.W))
    val widenS1             = Input(Bool())
    val vxrmS1              = Input(UInt(2.W))
    val isFixPS1            = Input(Bool())
    val sewIs8S1            = Input(Bool())
    val sewIs16S1           = Input(Bool())
    val sewIs32S1           = Input(Bool())
    val sewIs64S1           = Input(Bool())

    val sumFinalS2 = Output(UInt(152.W))
    val highHalfS2 = Output(Bool())
    val uopIdxS2   = Output(UInt(6.W))
    val widenS2    = Output(Bool())
    val vxrmS2     = Output(UInt(2.W))
    val isFixPS2   = Output(Bool())
    val sewIs8S2   = Output(Bool())
    val sewIs16S2  = Output(Bool())
    val sewIs32S2  = Output(Bool())
    val sewIs64S2  = Output(Bool())
  })
  
  val compStage1ResultsS1 = io.compStage1ResultsS1
  val fireS1              = io.fireS1
  val highHalfS1          = io.highHalfS1
  val uopIdxS1            = io.uopIdxS1
  val widenS1             = io.widenS1
  val vxrmS1              = io.vxrmS1
  val isFixPS1            = io.isFixPS1
  val sewIs8S1            = io.sewIs8S1
  val sewIs16S1           = io.sewIs16S1
  val sewIs32S1           = io.sewIs32S1
  val sewIs64S1           = io.sewIs64S1

  val sum34to2  = Wire(UInt(152.W))
  val cout34to2 = Wire(UInt(152.W))

  val wallace3to2CompStage2 = Module(new wallace3to2CompressorStage2())
  wallace3to2CompStage2.io.compStage1Results := compStage1ResultsS1
  sum34to2  := wallace3to2CompStage2.io.sum34to2
  cout34to2 := wallace3to2CompStage2.io.cout34to2

  // 8.get final sum
  val sumFinal  = Wire(UInt(152.W))
  
  val finalSumAdder = Module(new fullAdder152b())
  finalSumAdder.io.sum34to2  := sum34to2
  finalSumAdder.io.cout34to2 := cout34to2
  sumFinal := finalSumAdder.io.sumFinal

  io.sumFinalS2 := RegEnable(sumFinal, fireS1)
  io.highHalfS2 := RegEnable(highHalfS1, fireS1)
  io.uopIdxS2   := RegEnable(uopIdxS1, fireS1)
  io.widenS2    := RegEnable(widenS1, fireS1)
  io.vxrmS2     := RegEnable(vxrmS1, fireS1)
  io.isFixPS2   := RegEnable(isFixPS1, fireS1)
  io.sewIs8S2   := RegEnable(sewIs8S1, fireS1)
  io.sewIs16S2  := RegEnable(sewIs16S1, fireS1)
  io.sewIs32S2  := RegEnable(sewIs32S1, fireS1)
  io.sewIs64S2  := RegEnable(sewIs64S1, fireS1)
}

object newVIMac64bStage2 extends App {
  (new ChiselStage).execute(args, Seq(
    ChiselGeneratorAnnotation(() => new newVIMac64bStage2()), FirtoolOption("--lowering-options=explicitBitcast")
  ))
}