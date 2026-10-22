package com.rfandango.haku_x.data

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * The XPR0 texture in an XBE's title image section, decoded: DXT1/3/5 and
 * the 16- and 32-bit RGB formats, swizzled or linear. XPR0: magic, total
 * size, header size (where the pixels start), then the texture's resource
 * header (Common, Data, Lock, Format, Size).
 */
internal object XprImage {
  private const val XPR0 = 0x30525058 // "XPR0"

  fun decode(b: ByteBuffer): Bitmap? {
    if (b.capacity() < 0x20 || b.getInt(0) != XPR0) return null
    val at = b.getInt(8) + b.getInt(0x10)
    val format = b.getInt(0x18)
    val size = b.getInt(0x1C)
    val w: Int
    val h: Int
    var pitch = 0
    if (size != 0) { // linear: width, height and pitch in Size
      w = (size and 0xFFF) + 1
      h = ((size ushr 12) and 0xFFF) + 1
      pitch = (((size ushr 24) and 0xFF) + 1) * 64
    } else { // swizzled and compressed: log2 sizes in Format
      w = 1 shl ((format ushr 20) and 0xF)
      h = 1 shl ((format ushr 24) and 0xF)
    }
    if (w !in 1..1024 || h !in 1..1024 || at < 0 || at >= b.capacity()) return null
    val pixels = when ((format ushr 8) and 0xFF) {
      0x0C -> dxt(b, at, w, h, 1)
      0x0E -> dxt(b, at, w, h, 3)
      0x0F -> dxt(b, at, w, h, 5)
      0x02 -> plain(b, at, w, h, 2, true, pitch, ::argb1555)
      0x03 -> plain(b, at, w, h, 2, true, pitch) { argb1555(it or 0x8000) }
      0x04 -> plain(b, at, w, h, 2, true, pitch, ::argb4444)
      0x05 -> plain(b, at, w, h, 2, true, pitch, ::rgb565)
      0x06 -> plain(b, at, w, h, 4, true, pitch) { it }
      0x07 -> plain(b, at, w, h, 4, true, pitch) { it or 0xFF000000.toInt() }
      0x10 -> plain(b, at, w, h, 2, false, pitch, ::argb1555)
      0x11 -> plain(b, at, w, h, 2, false, pitch, ::rgb565)
      0x12 -> plain(b, at, w, h, 4, false, pitch) { it }
      0x1C -> plain(b, at, w, h, 2, false, pitch) { argb1555(it or 0x8000) }
      0x1D -> plain(b, at, w, h, 2, false, pitch, ::argb4444)
      0x1E -> plain(b, at, w, h, 4, false, pitch) { it or 0xFF000000.toInt() }
      else -> null
    } ?: return null
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
  }

  /** 4x4 blocks in rows: DXT1 (8 bytes), DXT3/DXT5 (8 bytes of alpha first). */
  private fun dxt(b: ByteBuffer, at: Int, w: Int, h: Int, kind: Int): IntArray? {
    val blockBytes = if (kind == 1) 8 else 16
    val bw = (w + 3) / 4
    val bh = (h + 3) / 4
    if (at.toLong() + bw.toLong() * bh * blockBytes > b.capacity()) return null
    val out = IntArray(w * h)
    val alpha = IntArray(16)
    val rgb = IntArray(4)
    var p = at
    for (by in 0 until bh) {
      for (bx in 0 until bw) {
        when (kind) {
          3 -> { // 4 bits per pixel
            val bits = b.getLong(p)
            for (i in 0 until 16) alpha[i] = ((bits ushr (4 * i)) and 0xF).toInt() * 17
            p += 8
          }
          5 -> { // two endpoints and 3-bit indices
            val a0 = b.get(p).toInt() and 0xFF
            val a1 = b.get(p + 1).toInt() and 0xFF
            var bits = 0L
            for (i in 0 until 6) bits = bits or ((b.get(p + 2 + i).toLong() and 0xFF) shl (8 * i))
            for (i in 0 until 16) {
              val c = ((bits ushr (3 * i)) and 7).toInt()
              alpha[i] = when {
                c == 0 -> a0
                c == 1 -> a1
                a0 > a1 -> ((8 - c) * a0 + (c - 1) * a1) / 7
                c == 6 -> 0
                c == 7 -> 255
                else -> ((6 - c) * a0 + (c - 1) * a1) / 5
              }
            }
            p += 8
          }
          else -> alpha.fill(255)
        }
        val c0 = b.getShort(p).toInt() and 0xFFFF
        val c1 = b.getShort(p + 2).toInt() and 0xFFFF
        val idx = b.getInt(p + 4)
        p += 8
        rgb[0] = rgb565(c0)
        rgb[1] = rgb565(c1)
        val fourColors = kind != 1 || c0 > c1
        rgb[2] = if (fourColors) mix(rgb[0], rgb[1], 2, 1, 3) else mix(rgb[0], rgb[1], 1, 1, 2)
        rgb[3] = if (fourColors) mix(rgb[0], rgb[1], 1, 2, 3) else 0
        for (i in 0 until 16) {
          val x = bx * 4 + (i and 3)
          val y = by * 4 + (i shr 2)
          if (x >= w || y >= h) continue
          val code = (idx ushr (2 * i)) and 3
          val a = if (!fourColors && code == 3) 0 else alpha[i]
          out[y * w + x] = (a shl 24) or (rgb[code] and 0xFFFFFF)
        }
      }
    }
    return out
  }

  /** Uncompressed texels: swizzled (Morton order, as the GPU samples them) or in rows of pitch bytes. */
  private fun plain(
    b: ByteBuffer, at: Int, w: Int, h: Int, bpp: Int, swizzled: Boolean, pitch: Int, conv: (Int) -> Int,
  ): IntArray? {
    val stride = if (!swizzled && pitch > 0) pitch else w * bpp
    if (at.toLong() + stride.toLong() * (h - 1) + w * bpp > b.capacity()) return null
    val order = if (swizzled) swizzleOrder(w, h) else null
    return IntArray(w * h) { i ->
      val off = if (order != null) at + order[i] * bpp else at + (i / w) * stride + (i % w) * bpp
      conv(if (bpp == 4) b.getInt(off) else b.getShort(off).toInt() and 0xFFFF)
    }
  }

  /** Each texel's place in a swizzled texture: x and y bits interleaved, x first, while both last. */
  private fun swizzleOrder(w: Int, h: Int): IntArray {
    var xMask = 0
    var yMask = 0
    var bit = 1
    var i = 1
    while (i < w || i < h) {
      if (i < w) { xMask = xMask or bit; bit = bit shl 1 }
      if (i < h) { yMask = yMask or bit; bit = bit shl 1 }
      i = i shl 1
    }
    val xs = IntArray(w) { deposit(it, xMask) }
    val ys = IntArray(h) { deposit(it, yMask) }
    return IntArray(w * h) { ys[it / w] or xs[it % w] }
  }

  /** The low bits of v spread over the set bits of mask. */
  private fun deposit(v: Int, mask: Int): Int {
    var r = 0
    var bits = v
    var m = mask
    while (m != 0) {
      val low = m and -m
      if (bits and 1 != 0) r = r or low
      bits = bits ushr 1
      m = m and (m - 1)
    }
    return r
  }

  private fun rgb565(v: Int): Int {
    val r = (v ushr 11) and 31
    val g = (v ushr 5) and 63
    val b = v and 31
    return (0xFF shl 24) or (((r shl 3) or (r shr 2)) shl 16) or (((g shl 2) or (g shr 4)) shl 8) or
      ((b shl 3) or (b shr 2))
  }

  private fun argb1555(v: Int): Int {
    val r = (v ushr 10) and 31
    val g = (v ushr 5) and 31
    val b = v and 31
    val a = if (v and 0x8000 != 0) 0xFF else 0
    return (a shl 24) or (((r shl 3) or (r shr 2)) shl 16) or (((g shl 3) or (g shr 2)) shl 8) or
      ((b shl 3) or (b shr 2))
  }

  private fun argb4444(v: Int): Int =
    (((v ushr 12) and 15) * 17 shl 24) or (((v ushr 8) and 15) * 17 shl 16) or
      (((v ushr 4) and 15) * 17 shl 8) or ((v and 15) * 17)

  /** (wa * a + wb * b) / div per RGB channel. */
  private fun mix(a: Int, b: Int, wa: Int, wb: Int, div: Int): Int {
    var out = 0xFF shl 24
    for (shift in intArrayOf(16, 8, 0)) {
      val c = (wa * ((a ushr shift) and 0xFF) + wb * ((b ushr shift) and 0xFF)) / div
      out = out or (c shl shift)
    }
    return out
  }
}
