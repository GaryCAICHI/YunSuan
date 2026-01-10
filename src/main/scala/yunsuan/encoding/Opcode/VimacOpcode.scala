package yunsuan.encoding.Opcode

import chisel3._

object VimacOpcode {
  def width = 4
  def vmul    = "b0000".U(width.W)
  def vmulh   = "b0001".U(width.W)
  def vmacc   = "b0010".U(width.W)
  def vnmsac  = "b0011".U(width.W)
  def vmadd   = "b0100".U(width.W)
  def vnmsub  = "b0101".U(width.W)
  def vsmul   = "b0110".U(width.W)

  // zvw
  def vscmul    = "b0111".U(width.W)
  def vscmulcj  = "b1000".U(width.W)
  def vscmacc   = "b1001".U(width.W)
  def vscmacccj = "b1010".U(width.W)

  def highHalf(opcode: UInt) = Seq(vmulh).map(_ === opcode).reduce(_ || _)

  def isMacc(opcode: UInt) = Seq(vmacc, vnmsac, vmadd, vnmsub, vscmacc, vscmacccj).map(_ === opcode).reduce(_ || _)

  def isSub(opcode: UInt) = Seq(vnmsac, vnmsub).map(_ === opcode).reduce(_ || _)

  def isFixP(opcode: UInt) = Seq(vsmul, vscmul, vscmulcj, vscmacc, vscmacccj).map(_ === opcode).reduce(_ || _)

  def isComp(opcode: UInt) = Seq(vscmul, vscmulcj, vscmacc, vscmacccj).map(_ === opcode).reduce(_ || _)

  def isConj(opcode: UInt) = Seq(vscmulcj, vscmacccj).map(_ === opcode).reduce(_ || _)

  def overWriteMultiplicand(opcode: UInt) = Seq(vmadd, vnmsub).map(_ === opcode).reduce(_ || _)
}
