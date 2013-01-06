package rocket

import Chisel._

object Util
{
  implicit def intToUFix(x: Int): UFix = UFix(x)
  implicit def intToBoolean(x: Int): Boolean = if (x != 0) true else false
  implicit def booleanToInt(x: Boolean): Int = if (x) 1 else 0
  implicit def booleanToBool(x: Boolean): Bits = Bool(x)

  implicit def wcToUFix(c: WideCounter): UFix = c.value
}

object AVec
{
  def apply[T <: Data](elts: Seq[T]): Vec[T] = Vec(elts) { elts.head.clone }
  def apply[T <: Data](elts: Vec[T]): Vec[T] = apply(elts.toSeq)
  def apply[T <: Data](elt0: T, elts: T*): Vec[T] = apply(elt0 :: elts.toList)

  def tabulate[T <: Data](n: Int)(f: Int => T): Vec[T] =
    apply((0 until n).map(i => f(i)))
  def tabulate[T <: Data](n1: Int, n2: Int)(f: (Int, Int) => T): Vec[Vec[T]] =
    tabulate(n1)(i1 => tabulate(n2)(f(i1, _)))
}

object Split
{
  // is there a better way to do do this?
  def apply(x: Bits, n0: Int) = {
    val w = checkWidth(x, n0)
    (x(w-1,n0), x(n0-1,0))
  }
  def apply(x: Bits, n1: Int, n0: Int) = {
    val w = checkWidth(x, n1, n0)
    (x(w-1,n1), x(n1-1,n0), x(n0-1,0))
  }
  def apply(x: Bits, n2: Int, n1: Int, n0: Int) = {
    val w = checkWidth(x, n2, n1, n0)
    (x(w-1,n2), x(n2-1,n1), x(n1-1,n0), x(n0-1,0))
  }

  private def checkWidth(x: Bits, n: Int*) = {
    val w = x.getWidth
    def decreasing(x: Seq[Int]): Boolean =
      if (x.tail.isEmpty) true
      else x.head > x.tail.head && decreasing(x.tail)
    require(decreasing(w :: n.toList))
    w
  }
}

// a counter that clock gates most of its MSBs using the LSB carry-out
case class WideCounter(width: Int, inc: Bool = Bool(true))
{
  private val isWide = width >= 4
  private val smallWidth = if (isWide) log2Up(width) else width
  private val small = Reg(resetVal = UFix(0, smallWidth))
  private val nextSmall = small + UFix(1, smallWidth+1)
  when (inc) { small := nextSmall(smallWidth-1,0) }

  private val large = if (isWide) {
    val r = Reg(resetVal = UFix(0, width - smallWidth))
    when (inc && nextSmall(smallWidth)) { r := r + UFix(1) }
    r
  } else null

  val value = Cat(large, small)

  def := (x: UFix) = {
    val w = x.getWidth
    small := x(w.min(smallWidth)-1,0)
    if (isWide) large := (if (w < smallWidth) UFix(0) else x(w.min(width)-1,smallWidth))
  }
}
