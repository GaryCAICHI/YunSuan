package yunsuan.vector.mac

import chisel3._
import chisel3.util._
import yunsuan.vector._
import yunsuan.util._

class VIMac64bStage3Input extends Bundle {
  val sumFinalNonFixPS2 = UInt(152.W)
  val sumFinalFixPS2    = UInt(152.W)
  val highHalfS2        = Bool()
  val uopIdxS2          = UInt(6.W)
  val widenS2           = Bool()
  val vxrmS2            = UInt(2.W)
  val isFixPS2          = Bool()
  val isCompS2          = Bool()
  val sewIs8S2          = Bool()
  val sewIs16S2         = Bool()
  val sewIs32S2         = Bool()
  val sewIs64S2         = Bool()
}

class VIMac64bStage3Output extends Bundle {
  val vd    = UInt(64.W)
  val vxsat = UInt(8.W)
}

class VIMac64bStage3 extends Module {
  val io = IO(new Bundle {
    val in  = Input(new VIMac64bStage3Input)
    val out = Output(new VIMac64bStage3Output)
  })

  val sumFinalNonFixPS2 = io.in.sumFinalNonFixPS2
  val sumFinalFixPS2    = io.in.sumFinalFixPS2
  val highHalfS2        = io.in.highHalfS2
  val uopIdxS2          = io.in.uopIdxS2
  val widenS2           = io.in.widenS2
  val vxrmS2            = io.in.vxrmS2
  val isFixPS2          = io.in.isFixPS2
  val isCompS2          = io.in.isCompS2
  val sewIs8S2          = io.in.sewIs8S2
  val sewIs16S2         = io.in.sewIs16S2
  val sewIs32S2         = io.in.sewIs32S2
  val sewIs64S2         = io.in.sewIs64S2

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
  // 10.generate vxsat bits and saturated vd
  val vdSat = Wire(UInt(64.W))
  val vdCompInc = Wire(UInt(64.W))
  val vdCompNonInc = Wire(UInt(64.W))
  val vxsatNonComp = Wire(UInt(8.W))
  val vxsatCompInc = Wire(UInt(8.W))
  val vxsatCompNonInc = Wire(UInt(8.W))

  val vxsatGen = Module(new vxsatGenerator())
  vxsatGen.io.sumFinalNonFixP := sumFinalNonFixPS2
  vxsatGen.io.sumFinalFixP    := sumFinalFixPS2
  vxsatGen.io.sewIs8          := sewIs8S2
  vxsatGen.io.sewIs16         := sewIs16S2
  vxsatGen.io.sewIs32         := sewIs32S2
  vxsatGen.io.sewIs64         := sewIs64S2
  vdSat                       := vxsatGen.io.vdSat
  vdCompInc                   := vxsatGen.io.vdCompInc
  vdCompNonInc                := vxsatGen.io.vdCompNonInc
  vxsatNonComp                := vxsatGen.io.vxsatNonComp
  vxsatCompInc                := vxsatGen.io.vxsatCompInc
  vxsatCompNonInc             := vxsatGen.io.vxsatCompNonInc

  // 11.generate rounding increment bits
  val rndIncVec = Wire(UInt(8.W))

  val rndIncVecGen = Module(new rndIncVecGenerator())
  rndIncVecGen.io.sumFinalNonFixP := sumFinalNonFixPS2
  rndIncVecGen.io.vxrm            := vxrmS2
  rndIncVecGen.io.sewIs8          := sewIs8S2
  rndIncVecGen.io.sewIs16         := sewIs16S2
  rndIncVecGen.io.sewIs32         := sewIs32S2
  rndIncVecGen.io.sewIs64         := sewIs64S2
  rndIncVec := rndIncVecGen.io.rndIncVec

  // 12.generate rounding increment vd
  val vdRndInc    = Wire(UInt(64.W))

  val vdRndGen = Module(new vdRndGenerator())
  vdRndGen.io.sumFinalFixP    := sumFinalFixPS2
  vdRndGen.io.sewIs8   := sewIs8S2
  vdRndGen.io.sewIs16  := sewIs16S2
  vdRndGen.io.sewIs32  := sewIs32S2
  vdRndGen.io.sewIs64  := sewIs64S2
  vdRndInc    := vdRndGen.io.vdRndInc

  // 14.choose between rounding increment vd and saturated vd and generate final fixed-point vd
  val vdFixP = Wire(UInt(64.W))
  val vxsat  = Wire(UInt(8.W))
  
  val vdFixPGen = Module(new vdFixPGenerator())
  vdFixPGen.io.vdRndInc        := vdRndInc
  vdFixPGen.io.vdSat           := vdSat
  vdFixPGen.io.vdCompInc       := vdCompInc
  vdFixPGen.io.vdCompNonInc    := vdCompNonInc
  vdFixPGen.io.vxsatNonComp    := vxsatNonComp
  vdFixPGen.io.vxsatCompInc    := vxsatCompInc
  vdFixPGen.io.vxsatCompNonInc := vxsatCompNonInc
  vdFixPGen.io.rndIncVec       := rndIncVec
  vdFixPGen.io.isComp          := isCompS2
  vdFixPGen.io.sewIs8          := sewIs8S2
  vdFixPGen.io.sewIs16         := sewIs16S2
  vdFixPGen.io.sewIs32         := sewIs32S2
  vdFixPGen.io.sewIs64         := sewIs64S2
  vdFixP := vdFixPGen.io.vdFixP
  vxsat  := vdFixPGen.io.vxsat

  // 15.generate final output
  val outputMux = Module(new outputSelect())
  outputMux.io.vdNonFixP := vdNonFixP
  outputMux.io.vdFixP    := vdFixP
  outputMux.io.vxsat     := vxsat
  outputMux.io.isFixP    := isFixPS2
  
  // Connect Output
  io.out.vd    := outputMux.io.vdOut
  io.out.vxsat := outputMux.io.vxsatOut
}

class vdNonFixPGenerator extends Module {
  val io = IO(new Bundle {
    val sumFinalNonFixP   = Input(UInt(152.W))
    val highHalf          = Input(Bool())
    val widen             = Input(Bool())
    val uopIdx            = Input(UInt(6.W))
    val sewIs8            = Input(Bool())
    val sewIs16           = Input(Bool())
    val sewIs32           = Input(Bool())
    val sewIs64           = Input(Bool())

    val vdNonFixP  = Output(UInt(64.W))
  })

  val sumFinalNonFixP = io.sumFinalNonFixP
  val highHalf        = io.highHalf
  val widen           = io.widen
  val uopIdx          = io.uopIdx
  val sewIs8          = io.sewIs8
  val sewIs16         = io.sewIs16
  val sewIs32         = io.sewIs32
  val sewIs64         = io.sewIs64

  io.vdNonFixP := Mux1H(Seq(
    sewIs64 -> Mux(highHalf, sumFinalNonFixP(127,64), sumFinalNonFixP(63,0)),
    sewIs32 -> Mux(widen, Mux(uopIdx(0), sumFinalNonFixP(139, 76), sumFinalNonFixP(63,0)), Cat(UIntSplit(sumFinalNonFixP, 76).reverse.map(x => Mux(highHalf, x(63,32), x(31,0))))),
    sewIs16 -> Mux(widen, Cat(UIntSplit(Mux(uopIdx(0), sumFinalNonFixP(151, 76), sumFinalNonFixP(75, 0)), 38).reverse.map(x => x(31,0))), Cat(UIntSplit(sumFinalNonFixP, 38).reverse.map(x => Mux(highHalf, x(31,16), x(15,0))))),
    sewIs8  -> Mux(widen, Cat(UIntSplit(Mux(uopIdx(0), sumFinalNonFixP(151, 76), sumFinalNonFixP(75, 0)), 19).reverse.map(x => x(15,0))), Cat(UIntSplit(sumFinalNonFixP, 19).reverse.map(x => Mux(highHalf, x(15,8),  x(7, 0)))))
  ))
}

class vxsatGenerator extends Module {
  val io = IO(new Bundle {
    val sumFinalNonFixP = Input(UInt(152.W))
    val sumFinalFixP    = Input(UInt(152.W))
    val sewIs8          = Input(Bool())
    val sewIs16         = Input(Bool())
    val sewIs32         = Input(Bool())
    val sewIs64         = Input(Bool())

    val vdSat           = Output(UInt(64.W))
    val vdCompInc       = Output(UInt(64.W))
    val vdCompNonInc    = Output(UInt(64.W))
    val vxsatNonComp    = Output(UInt(8.W))
    val vxsatCompInc    = Output(UInt(8.W))
    val vxsatCompNonInc = Output(UInt(8.W))
  })

  val sumFinalNonFixP = io.sumFinalNonFixP
  val sumFinalFixP    = io.sumFinalFixP
  val sewIs64  = io.sewIs64
  val sewIs32  = io.sewIs32
  val sewIs16  = io.sewIs16
  val sewIs8   = io.sewIs8
  
  val vdSat           = Wire(UInt(64.W))
  val vdCompInc       = Wire(UInt(64.W))
  val vdCompNonInc    = Wire(UInt(64.W))

  val vxsatNonComp    = Wire(UInt(8.W))
  val vxsatCompInc    = Wire(UInt(8.W))
  val vxsatCompNonInc = Wire(UInt(8.W))
  
  val compOFInc       = Wire(UInt(8.W))
  val compUFInc       = Wire(UInt(8.W))
  val compOFNonInc    = Wire(UInt(8.W))
  val compUFNonInc    = Wire(UInt(8.W))
  
  vxsatNonComp := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(sumFinalNonFixP, 19).reverse.map(x => x(15,14) === 1.U(2.W))),
    sewIs16 -> Cat(UIntSplit(sumFinalNonFixP, 38).reverse.map(x => Fill(2, x(31,30) === 1.U(2.W)))),
    sewIs32 -> Cat(UIntSplit(sumFinalNonFixP, 76).reverse.map(x => Fill(4, x(63,62) === 1.U(2.W)))),
    sewIs64 -> Fill(8, sumFinalNonFixP(127,126) === 1.U(2.W))
  ))

  vdSat := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(sumFinalNonFixP, 19).reverse.zip(UIntSplit(vxsatNonComp, 1).map(x => x(0)).reverse).map{ case(x, vxsat) => Mux(vxsat, "h7F".U(8.W),        x(14,7))}),
    sewIs16 -> Cat(UIntSplit(sumFinalNonFixP, 38).reverse.zip(UIntSplit(vxsatNonComp, 2).map(x => x(0)).reverse).map{ case(x, vxsat) => Mux(vxsat, "h7FFF".U(16.W),     x(30,15))}),
    sewIs32 -> Cat(UIntSplit(sumFinalNonFixP, 76).reverse.zip(UIntSplit(vxsatNonComp, 4).map(x => x(0)).reverse).map{ case(x, vxsat) => Mux(vxsat, "h7FFFFFFF".U(32.W), x(62,31))}),
    sewIs64 -> Mux(vxsatNonComp(0), "h7FFFFFFFFFFFFFFF".U(64.W), sumFinalNonFixP(126,63))
  ))

  compOFInc    := Cat(UIntSplit(sumFinalFixP,    38).reverse.map(x => Fill(2, ~(x(32)) &  (x(31) | x(30)))))
  compUFInc    := Cat(UIntSplit(sumFinalFixP,    38).reverse.map(x => Fill(2,   x(32)  & ~(x(31) & x(30)))))
  compOFNonInc := Cat(UIntSplit(sumFinalNonFixP, 38).reverse.map(x => Fill(2, ~(x(32)) &  (x(31) | x(30)))))
  compUFNonInc := Cat(UIntSplit(sumFinalNonFixP, 38).reverse.map(x => Fill(2,   x(32)  & ~(x(31) & x(30)))))

  vxsatCompInc    := compOFInc    | compUFInc
  vxsatCompNonInc := compOFNonInc | compUFNonInc

  vdCompInc    := Cat(UIntSplit(sumFinalFixP, 38).reverse.lazyZip(UIntSplit(compOFInc, 2).map(x => x(0)).reverse).lazyZip(UIntSplit(compUFInc, 2).map(x => x(0)).reverse).map{
    case(compIncData, oFFlag, uFFlag) => Mux(oFFlag, "h7fff".U(16.W), Mux(uFFlag, "h8000".U(16.W), compIncData(30,15)))
  })

  vdCompNonInc := Cat(UIntSplit(sumFinalNonFixP, 38).reverse.lazyZip(UIntSplit(compOFNonInc, 2).map(x => x(0)).reverse).lazyZip(UIntSplit(compUFNonInc, 2).map(x => x(0)).reverse).map{
    case(compNonIncData, oFFlag, uFFlag) => Mux(oFFlag, "h7fff".U(16.W), Mux(uFFlag, "h8000".U(16.W), compNonIncData(30,15)))
  })

  io.vdSat           := vdSat
  io.vdCompInc       := vdCompInc
  io.vdCompNonInc    := vdCompNonInc
  io.vxsatNonComp    := vxsatNonComp
  io.vxsatCompInc    := vxsatCompInc
  io.vxsatCompNonInc := vxsatCompNonInc
}

class rndIncVecGenerator extends Module {
  val io = IO(new Bundle {
    val sumFinalNonFixP = Input(UInt(152.W))
    val vxrm            = Input(UInt(2.W))
    val sewIs8          = Input(Bool())
    val sewIs16         = Input(Bool())
    val sewIs32         = Input(Bool())
    val sewIs64         = Input(Bool())

    val rndIncVec       = Output(UInt(8.W))
  })

  def rndIncGen(v_d: Bool, v_d_1: Bool, tail: UInt, vxrm: UInt): Bool = {
    Mux1H(Seq((vxrm === 0.U) -> v_d_1,
              (vxrm === 1.U) -> (v_d_1 && (tail =/= 0.U || v_d)),
              (vxrm === 2.U) -> false.B,
              (vxrm === 3.U) -> (!v_d && Cat(v_d_1, tail) =/= 0.U) ))
  }

  val sumFinalNonFixP  = io.sumFinalNonFixP
  val vxrm      = io.vxrm
  val sewIs64   = io.sewIs64
  val sewIs32   = io.sewIs32
  val sewIs16   = io.sewIs16
  val sewIs8    = io.sewIs8
  
  val rndIncVec = Wire(UInt(8.W))
  rndIncVec := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(sumFinalNonFixP, 19).reverse.map(x =>         rndIncGen(x(7),  x(6),  x(5, 0), vxrm))),
    sewIs16 -> Cat(UIntSplit(sumFinalNonFixP, 38).reverse.map(x => Fill(2, rndIncGen(x(15), x(14), x(13, 0), vxrm)))),
    sewIs32 -> Cat(UIntSplit(sumFinalNonFixP, 76).reverse.map(x => Fill(4, rndIncGen(x(31), x(30), x(29, 0), vxrm)))),
    sewIs64 -> Fill(8, rndIncGen(sumFinalNonFixP(63), sumFinalNonFixP(62), sumFinalNonFixP(61,0), vxrm))
  ))

  io.rndIncVec := rndIncVec
}

class vdRndGenerator extends Module {
  val io = IO(new Bundle {
    val sumFinalFixP    = Input(UInt(152.W))
    val sewIs8          = Input(Bool())
    val sewIs16         = Input(Bool())
    val sewIs32         = Input(Bool())
    val sewIs64         = Input(Bool())

    val vdRndInc         = Output(UInt(64.W))
  })
  val sumFinalFixP    = io.sumFinalFixP
  val sewIs64         = io.sewIs64
  val sewIs32         = io.sewIs32
  val sewIs16         = io.sewIs16
  val sewIs8          = io.sewIs8

  val vdRndInc    = Wire(UInt(64.W))

  vdRndInc := Mux1H(Seq(
    sewIs8  -> Cat(UIntSplit(sumFinalFixP, 19).reverse.map(x => x(14, 7))),
    sewIs16 -> Cat(UIntSplit(sumFinalFixP, 38).reverse.map(x => x(30, 15))),
    sewIs32 -> Cat(UIntSplit(sumFinalFixP, 76).reverse.map(x => x(62, 31))),
    sewIs64 -> sumFinalFixP(126, 63)
  ))
  
  io.vdRndInc := vdRndInc
}

class vdFixPGenerator extends Module {
  val io = IO(new Bundle {
    val vdRndInc        = Input(UInt(64.W))
    val vdSat           = Input(UInt(64.W))
    val vdCompInc       = Input(UInt(64.W))
    val vdCompNonInc    = Input(UInt(64.W))
    val vxsatNonComp    = Input(UInt(8.W))
    val vxsatCompInc    = Input(UInt(8.W))
    val vxsatCompNonInc = Input(UInt(8.W))
    val rndIncVec       = Input(UInt(8.W))
    val isComp          = Input(Bool())
    val sewIs8          = Input(Bool())
    val sewIs16         = Input(Bool())
    val sewIs32         = Input(Bool())
    val sewIs64         = Input(Bool())

    val vdFixP   = Output(UInt(64.W))
    val vxsat    = Output(UInt(8.W))
  })

  val vdRndInc        = io.vdRndInc
  val vdSat           = io.vdSat
  val vdCompInc       = io.vdCompInc
  val vdCompNonInc    = io.vdCompNonInc
  val vxsatNonComp    = io.vxsatNonComp
  val vxsatCompInc    = io.vxsatCompInc
  val vxsatCompNonInc = io.vxsatCompNonInc
  val rndIncVec       = io.rndIncVec
  val isComp          = io.isComp
  val sewIs64         = io.sewIs64
  val sewIs32         = io.sewIs32
  val sewIs16         = io.sewIs16
  val sewIs8          = io.sewIs8

  val vdFixPNonComp = Wire(UInt(64.W))
  val vdFixPComp    = Wire(UInt(64.W))
  val vdFixP        = Wire(UInt(64.W))
  val vxsatComp     = Wire(UInt(8.W))
  val vxsat         = Wire(UInt(8.W))

  vdFixPNonComp := Cat(UIntSplit(vdRndInc, 8).reverse.lazyZip(UIntSplit(vdSat, 8).reverse).lazyZip(UIntSplit(rndIncVec, 1).map(x => x(0)).reverse).map{ case(rndIncData, satData, rndIncFlag) => 
                  Mux(rndIncFlag, rndIncData, satData)
  })
  
  vdFixPComp := Cat(UIntSplit(vdCompInc, 16).reverse.lazyZip(UIntSplit(vdCompNonInc, 16).reverse).lazyZip(UIntSplit(rndIncVec, 2).map(x => x(0)).reverse).map{ case(compIncData, compNonIncData, rndIncFlag) =>
              Mux(rndIncFlag, compIncData, compNonIncData)
  })

  vxsatComp := Cat(UIntSplit(rndIncVec, 2).map(x => x(0)).reverse.lazyZip(UIntSplit(vxsatCompInc, 2).reverse).lazyZip(UIntSplit(vxsatCompNonInc, 2).reverse).map{ case(rndIncFlag, vxsatCompInc, vxsatCompNonInc) =>
             Mux(rndIncFlag, vxsatCompInc, vxsatCompNonInc)
  })
  
  vdFixP := Mux(isComp, vdFixPComp, vdFixPNonComp)
  vxsat := Mux(isComp, vxsatComp, vxsatNonComp)

  io.vdFixP := vdFixP
  io.vxsat  := vxsat
}

class outputSelect extends Module {
  val io = IO(new Bundle {
    val vdNonFixP = Input(UInt(64.W))
    val vdFixP    = Input(UInt(64.W))
    val vxsat     = Input(UInt(8.W))
    val isFixP    = Input(Bool())

    val vdOut     = Output(UInt(64.W))
    val vxsatOut  = Output(UInt(8.W))
  })

  val vdNonFixP = io.vdNonFixP
  val vdFixP    = io.vdFixP
  val vxsat     = io.vxsat
  val isFixP    = io.isFixP

  io.vdOut    := Mux(io.isFixP, io.vdFixP, io.vdNonFixP)
  io.vxsatOut := Mux(io.isFixP, vxsat, 0.U(8.W))
}