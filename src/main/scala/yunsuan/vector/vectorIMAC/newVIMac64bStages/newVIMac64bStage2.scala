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
    val compStage1ResultsS1    = Input(Vec(7, UInt(152.W)))
    val wallaceLine34NonFixPS1 = Input(UInt(152.W))
    val wallaceLine34FixPS1    = Input(UInt(152.W))
    val highHalfS1             = Input(Bool())
    val uopIdxS1               = Input(UInt(6.W))
    val widenS1                = Input(Bool())
    val vxrmS1                 = Input(UInt(2.W))
    val isFixPS1               = Input(Bool())
    val sewIs8S1               = Input(Bool())
    val sewIs16S1              = Input(Bool())
    val sewIs32S1              = Input(Bool())
    val sewIs64S1              = Input(Bool())

    val sumFinalNonFixPS2  = Output(UInt(152.W))
    val sumFinalFixPS2     = Output(UInt(152.W))
    val highHalfS2         = Output(Bool())
    val uopIdxS2           = Output(UInt(6.W))
    val widenS2            = Output(Bool())
    val vxrmS2             = Output(UInt(2.W))
    val isFixPS2           = Output(Bool())
    val sewIs8S2           = Output(Bool())
    val sewIs16S2          = Output(Bool())
    val sewIs32S2          = Output(Bool())
    val sewIs64S2          = Output(Bool())
  })
  
  val compStage1ResultsS1    = io.compStage1ResultsS1
  val wallaceLine34NonFixPS1 = io.wallaceLine34NonFixPS1
  val wallaceLine34FixPS1    = io.wallaceLine34FixPS1
  val highHalfS1             = io.highHalfS1
  val uopIdxS1               = io.uopIdxS1
  val widenS1                = io.widenS1
  val vxrmS1                 = io.vxrmS1
  val isFixPS1               = io.isFixPS1
  val sewIs8S1               = io.sewIs8S1
  val sewIs16S1              = io.sewIs16S1
  val sewIs32S1              = io.sewIs32S1
  val sewIs64S1              = io.sewIs64S1

  val sum34to2NonFixP  = Wire(UInt(152.W))
  val cout34to2NonFixP = Wire(UInt(152.W))
  val sum34to2FixP     = Wire(UInt(152.W))
  val cout34to2FixP    = Wire(UInt(152.W))

  val wallace3to2CompStage2 = Module(new wallace3to2CompressorStage2())
  wallace3to2CompStage2.io.compStage1Results := compStage1ResultsS1
  wallace3to2CompStage2.io.wallaceLine34NonFixP := wallaceLine34NonFixPS1
  wallace3to2CompStage2.io.wallaceLine34FixP    := wallaceLine34FixPS1
  sum34to2NonFixP  := wallace3to2CompStage2.io.sum34to2NonFixP
  cout34to2NonFixP := wallace3to2CompStage2.io.cout34to2NonFixP
  sum34to2FixP     := wallace3to2CompStage2.io.sum34to2FixP
  cout34to2FixP    := wallace3to2CompStage2.io.cout34to2FixP

  // 8.get final sum
  val sumFinalNonFixP  = Wire(UInt(152.W))
  val sumFinalFixP     = Wire(UInt(152.W))

  val finalSumAdderNonFixP = Module(new fullAdder152b())
  finalSumAdderNonFixP.io.sum34to2  := sum34to2NonFixP
  finalSumAdderNonFixP.io.cout34to2 := cout34to2NonFixP
  sumFinalNonFixP                   := finalSumAdderNonFixP.io.sumFinal

  val finalSumAdderFixP = Module(new fullAdder152b())
  finalSumAdderFixP.io.sum34to2  := sum34to2FixP
  finalSumAdderFixP.io.cout34to2 := cout34to2FixP
  sumFinalFixP                   := finalSumAdderFixP.io.sumFinal

  io.sumFinalFixPS2     := sumFinalFixP
  io.sumFinalNonFixPS2  := sumFinalNonFixP
  io.highHalfS2         := highHalfS1
  io.uopIdxS2           := uopIdxS1
  io.widenS2            := widenS1
  io.vxrmS2             := vxrmS1
  io.isFixPS2           := isFixPS1
  io.sewIs8S2           := sewIs8S1
  io.sewIs16S2          := sewIs16S1
  io.sewIs32S2          := sewIs32S1
  io.sewIs64S2          := sewIs64S1
}

object newVIMac64bStage2 extends App {
  (new ChiselStage).execute(args, Seq(
    ChiselGeneratorAnnotation(() => new newVIMac64bStage2()), FirtoolOption("--lowering-options=explicitBitcast")
  ))
}