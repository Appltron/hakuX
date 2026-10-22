package com.rfandango.haku_x.data.hdd

import java.security.MessageDigest

/** The Xbox's crypto as saves and dumps need it: HMAC-SHA1 (also from SHA-1 midstates), RC4. */
internal object XCrypto {
  val SHA1_IV = intArrayOf(0x67452301, 0xEFCDAB89.toInt(), 0x98BADCFE.toInt(), 0x10325476, 0xC3D2E1F0.toInt())

  fun hmac(key: ByteArray, data: ByteArray, off: Int = 0, len: Int = data.size - off): ByteArray =
    Hmac(key).apply { update(data, off, len) }.finish()

  /** HMAC-SHA1, incremental: copy() forks it, for messages that share a start. */
  class Hmac private constructor(private val inner: MessageDigest, private val outerKey: ByteArray) {
    constructor(key: ByteArray) : this(MessageDigest.getInstance("SHA-1"), pad(key, 0x5C)) {
      inner.update(pad(key, 0x36))
    }

    fun update(b: ByteArray, off: Int, len: Int) {
      if (len > 0) inner.update(b, off, len)
    }

    fun copy() = Hmac(inner.clone() as MessageDigest, outerKey)

    fun finish(): ByteArray {
      val i = inner.digest()
      return MessageDigest.getInstance("SHA-1").run {
        update(outerKey)
        update(i)
        digest()
      }
    }

    private companion object {
      fun pad(key: ByteArray, x: Int): ByteArray {
        val k = if (key.size > 64) MessageDigest.getInstance("SHA-1").digest(key) else key
        return ByteArray(64) { ((if (it < k.size) k[it].toInt() else 0) xor x).toByte() }
      }
    }
  }

  fun rc4(key: ByteArray, data: ByteArray, off: Int = 0, len: Int = data.size - off): ByteArray {
    val s = IntArray(256) { it }
    var j = 0
    for (i in 0 until 256) {
      j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
      val t = s[i]; s[i] = s[j]; s[j] = t
    }
    val out = ByteArray(len)
    var i = 0
    j = 0
    for (n in 0 until len) {
      i = (i + 1) and 0xFF
      j = (j + s[i]) and 0xFF
      val t = s[i]; s[i] = s[j]; s[j] = t
      out[n] = (data[off + n].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
    }
    return out
  }

  /** SHA-1's compression of the 64-byte block at off, from state h. */
  fun compress(h: IntArray, block: ByteArray, off: Int = 0): IntArray {
    val w = IntArray(80)
    for (t in 0 until 16) {
      val p = off + t * 4
      w[t] = ((block[p].toInt() and 0xFF) shl 24) or ((block[p + 1].toInt() and 0xFF) shl 16) or
        ((block[p + 2].toInt() and 0xFF) shl 8) or (block[p + 3].toInt() and 0xFF)
    }
    for (t in 16 until 80) w[t] = Integer.rotateLeft(w[t - 3] xor w[t - 8] xor w[t - 14] xor w[t - 16], 1)
    var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]
    for (t in 0 until 80) {
      val f: Int
      val k: Int
      when {
        t < 20 -> { f = (b and c) or (b.inv() and d); k = 0x5A827999 }
        t < 40 -> { f = b xor c xor d; k = 0x6ED9EBA1 }
        t < 60 -> { f = (b and c) or (b and d) or (c and d); k = 0x8F1BBCDC.toInt() }
        else -> { f = b xor c xor d; k = 0xCA62C1D6.toInt() }
      }
      val tmp = Integer.rotateLeft(a, 5) + f + e + k + w[t]
      e = d; d = c; c = Integer.rotateLeft(b, 30); b = a; a = tmp
    }
    return intArrayOf(h[0] + a, h[1] + b, h[2] + c, h[3] + d, h[4] + e)
  }

  /** SHA-1 of msg after one block already hashed into mid (an HMAC key's pad). */
  fun sha1From(mid: IntArray, msg: ByteArray): ByteArray {
    val total = 64L + msg.size
    val padded = ByteArray(((msg.size + 9 + 63) / 64) * 64)
    msg.copyInto(padded)
    padded[msg.size] = 0x80.toByte()
    val bits = total * 8
    for (i in 0 until 8) padded[padded.size - 1 - i] = (bits ushr (8 * i)).toByte()
    var h = mid
    for (o in padded.indices step 64) h = compress(h, padded, o)
    return ByteArray(20) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
  }

  /** HMAC-SHA1 from the key's inner and outer midstates. */
  fun hmacMid(inner: IntArray, outer: IntArray, msg: ByteArray) = sha1From(outer, sha1From(inner, msg))

  /** A key's HMAC inner midstate: SHA-1's compression of its ipad block. */
  fun innerMid(key: ByteArray, off: Int = 0, len: Int = key.size): IntArray =
    compress(SHA1_IV, ByteArray(64) { ((if (it < len) key[off + it].toInt() else 0) xor 0x36).toByte() })

  fun outerMid(key: ByteArray): IntArray =
    compress(SHA1_IV, ByteArray(64) { ((if (it < key.size) key[it].toInt() else 0) xor 0x5C).toByte() })
}
