package com.rfandango.haku_x.data.hdd

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/** A disk image read by guest offset: a raw image or a qcow2 one (xemu's). */
interface DiskImage {
  val size: Long
  val format: String

  /** Reads len bytes at offset into buf at 0 (zeros where the image holds none). */
  fun read(offset: Long, buf: ByteArray, len: Int = buf.size)

  companion object {
    /** The image behind ch, by its magic. */
    fun open(ch: FileChannel): DiskImage {
      val head = ByteBuffer.allocate(4)
      ch.read(head, 0)
      return if (head.getInt(0) == Qcow2.MAGIC) Qcow2.Reader(ch) else RawImage(ch)
    }
  }
}

class RawImage(private val ch: FileChannel) : DiskImage {
  override val size: Long = ch.size()
  override val format = "raw"

  override fun read(offset: Long, buf: ByteArray, len: Int) {
    val b = ByteBuffer.wrap(buf, 0, len)
    var o = offset
    while (b.hasRemaining()) {
      val n = ch.read(b, o)
      if (n <= 0) {
        while (b.hasRemaining()) b.put(0)     // past the end: zeros
        break
      }
      o += n
    }
  }

  companion object {
    /**
     * Writes a raw image of `size` bytes: the data source's bytes, the ranges
     * nextData skips left as holes (a sparse file where the file system has
     * them, zeros to the reader either way).
     */
    fun write(
      ch: FileChannel, size: Long, source: (Long, ByteArray, Int) -> Unit, nextData: (Long) -> Long,
      progress: (Long) -> Unit,
    ) {
      val chunk = 1 shl 20
      val buf = ByteArray(chunk)
      var o = 0L
      while (o < size) {
        val d = nextData(o)
        if (d < 0 || d >= size) break
        o = maxOf(o, d)                                 // over a hole
        val n = minOf(chunk.toLong(), size - o).toInt()
        source(o, buf, n)
        if (!zero(buf, n)) {
          val b = ByteBuffer.wrap(buf, 0, n)
          var p = o
          while (b.hasRemaining()) p += ch.write(b, p)
        }
        o += n
        progress(o)
      }
      if (ch.size() < size) {
        ch.write(ByteBuffer.wrap(ByteArray(1)), size - 1)   // the full length
      }
      progress(size)
    }
  }
}

internal fun zero(b: ByteArray, n: Int): Boolean {
  for (i in 0 until n) if (b[i].toInt() != 0) return false
  return true
}

/**
 * qcow2 (QEMU's, the disk image xemu keeps the hard disk in): a big-endian
 * header (version 2 or 3), an L1 table of L2 tables of cluster entries
 * (bit 63 copied, 62 compressed, 0 all zeros in version 3; else the host
 * offset in bits 9-55), and refcounts. Backing files, encryption and
 * external data files are refused.
 */
object Qcow2 {
  const val MAGIC = 0x514649FB  // "QFI\xfb"
  private const val OFFSET_MASK = 0x00FFFFFFFFFFFE00L
  private const val COMPRESSED = 1L shl 62
  private const val COPIED = 1L shl 63

  class Reader(private val ch: FileChannel) : DiskImage {
    override val format = "qcow2"
    override val size: Long
    private val clusterBits: Int
    private val clusterSize: Int
    private val l2Bits: Int
    private val l1: LongArray
    private val l2Cache = HashMap<Long, LongArray>()

    init {
      val h = ByteBuffer.allocate(104).order(ByteOrder.BIG_ENDIAN)
      ch.read(h, 0)
      require(h.getInt(0) == MAGIC) { "not a qcow2 image" }
      val version = h.getInt(4)
      if (version !in 2..3) throw IOException("qcow2 version $version isn't supported")
      if (h.getLong(8) != 0L) throw IOException("this qcow2 image depends on a backing file; convert it to a standalone image first")
      clusterBits = h.getInt(20)
      if (clusterBits !in 9..21) throw IOException("bad qcow2 cluster size")
      clusterSize = 1 shl clusterBits
      size = h.getLong(24)
      if (h.getInt(32) != 0) throw IOException("encrypted qcow2 images aren't supported")
      val l1Size = h.getInt(36)
      val l1Off = h.getLong(40)
      if (version == 3) {
        val incompat = h.getLong(72)
        if (incompat and 0x6L != 0L) throw IOException("this qcow2 image uses an external data file or zstd compression")
      }
      l2Bits = clusterBits - 3
      val t = ByteBuffer.allocate(l1Size * 8).order(ByteOrder.BIG_ENDIAN)
      readFully(t, l1Off)
      l1 = LongArray(l1Size) { t.getLong(it * 8) }
    }

    private fun readFully(b: ByteBuffer, at: Long) {
      var o = at
      while (b.hasRemaining()) {
        val n = ch.read(b, o)
        if (n <= 0) throw IOException("qcow2 image cut short")
        o += n
      }
      b.flip()
    }

    private fun l2(i: Int): LongArray? {
      if (i >= l1.size) return null
      val off = l1[i] and OFFSET_MASK
      if (off == 0L) return null
      return l2Cache.getOrPut(off) {
        val b = ByteBuffer.allocate(clusterSize).order(ByteOrder.BIG_ENDIAN)
        readFully(b, off)
        LongArray(clusterSize / 8) { b.getLong(it * 8) }.also {
          if (l2Cache.size > 64) l2Cache.clear()
        }
      }
    }

    /** One guest cluster's bytes into dst (zeros if unallocated). */
    private fun cluster(gc: Long, dst: ByteArray) {
      val e = l2((gc ushr l2Bits).toInt())?.get((gc and ((1L shl l2Bits) - 1)).toInt()) ?: 0L
      if (e and COMPRESSED != 0L) {
        val x = 62 - (clusterBits - 8)
        val host = e and ((1L shl x) - 1)
        val sectors = ((e ushr x) and ((1L shl (clusterBits - 8)) - 1)) + 1
        val len = (sectors * 512 - (host and 511)).toInt()
        val c = ByteBuffer.allocate(len)
        var o = host
        while (c.hasRemaining()) {
          val n = ch.read(c, o)
          if (n <= 0) break
          o += n
        }
        val inf = Inflater(true)
        inf.setInput(c.array(), 0, c.position())
        val n = inf.inflate(dst, 0, clusterSize)
        inf.end()
        java.util.Arrays.fill(dst, n, clusterSize, 0)
        return
      }
      val off = e and OFFSET_MASK
      if (off == 0L || e and 1L != 0L) {
        java.util.Arrays.fill(dst, 0)
        return
      }
      val b = ByteBuffer.wrap(dst)
      readFully(b, off)
    }

    private val one = ByteArray(1 shl 16)

    override fun read(offset: Long, buf: ByteArray, len: Int) {
      val cl = if (one.size == clusterSize) one else ByteArray(clusterSize)
      var o = offset
      var i = 0
      while (i < len) {
        val gc = o ushr clusterBits
        val inC = (o and (clusterSize - 1L)).toInt()
        val n = minOf(clusterSize - inC, len - i)
        cluster(gc, cl)
        System.arraycopy(cl, inC, buf, i, n)
        o += n
        i += n
      }
    }
  }

  /**
   * Writes a qcow2 version 3 image of `size` guest bytes (64 KiB clusters,
   * 16-bit refcounts): the clusters the source has data in (nextData skips
   * the rest, all-zero clusters stay unallocated) one after another from
   * host cluster 1, then the L2 tables, the L1 table, the refcount blocks
   * and table, and the header last.
   */
  fun write(
    ch: FileChannel, size: Long, source: (Long, ByteArray, Int) -> Unit, nextData: (Long) -> Long,
    progress: (Long) -> Unit,
  ) {
    val bits = 16
    val cs = 1 shl bits
    val l2Entries = cs / 8
    val guestClusters = (size + cs - 1) / cs
    val l1Size = ((guestClusters + l2Entries - 1) / l2Entries).toInt()
    val map = LongArray(guestClusters.toInt())          // guest cluster -> host offset (0: none)
    var host = 1L                                       // cluster 0: the header
    val buf = ByteArray(cs)
    var g = 0L
    while (g < guestClusters) {
      val off = g * cs
      val d = nextData(off)
      if (d < 0) break
      if (d > off + cs - 1) {
        g = d / cs
        continue
      }
      val n = minOf(cs.toLong(), size - off).toInt()
      source(off, buf, n)
      if (n < cs) java.util.Arrays.fill(buf, n, cs, 0)
      if (!zero(buf, cs)) {
        writeAll(ch, ByteBuffer.wrap(buf), host * cs)
        map[g.toInt()] = host * cs
        host++
      }
      g++
      progress(minOf(g * cs, size))
    }
    // L2 tables, for the regions with data
    val l1 = LongArray(l1Size)
    for (t in 0 until l1Size) {
      val b = ByteBuffer.allocate(cs).order(ByteOrder.BIG_ENDIAN)
      var any = false
      for (k in 0 until l2Entries) {
        val gi = t.toLong() * l2Entries + k
        if (gi < guestClusters && map[gi.toInt()] != 0L) {
          b.putLong(k * 8, map[gi.toInt()] or COPIED)
          any = true
        }
      }
      if (any) {
        writeAll(ch, b, host * cs)
        l1[t] = host * cs or COPIED
        host++
      }
    }
    val l1Off = host * cs
    val l1Clusters = (l1Size * 8L + cs - 1) / cs
    val l1b = ByteBuffer.allocate((l1Clusters * cs).toInt()).order(ByteOrder.BIG_ENDIAN)
    for (t in 0 until l1Size) l1b.putLong(t * 8, l1[t])
    writeAll(ch, l1b, l1Off)
    host += l1Clusters
    // refcounts: every cluster so far, and the refcount clusters themselves
    val perBlock = cs / 2L
    var blocks = 0L
    var tableClusters = 0L
    while (true) {
      val total = host + blocks + tableClusters
      val nb = (total + perBlock - 1) / perBlock
      val nt = (nb * 8 + cs - 1) / cs
      if (nb == blocks && nt == tableClusters) break
      blocks = nb
      tableClusters = nt
    }
    val tableOff = host * cs
    val firstBlock = host + tableClusters
    val total = host + tableClusters + blocks
    val table = ByteBuffer.allocate((tableClusters * cs).toInt()).order(ByteOrder.BIG_ENDIAN)
    for (bi in 0 until blocks) {
      table.putLong((bi * 8).toInt(), (firstBlock + bi) * cs)
      val rb = ByteBuffer.allocate(cs).order(ByteOrder.BIG_ENDIAN)
      for (k in 0 until perBlock) {
        val c = bi * perBlock + k
        if (c >= total) break
        rb.putShort((k * 2).toInt(), 1)
      }
      writeAll(ch, rb, (firstBlock + bi) * cs)
    }
    writeAll(ch, table, tableOff)
    // the header
    val h = ByteBuffer.allocate(cs).order(ByteOrder.BIG_ENDIAN)
    h.putInt(0, MAGIC)
    h.putInt(4, 3)
    h.putLong(8, 0)                  // no backing file
    h.putInt(16, 0)
    h.putInt(20, bits)
    h.putLong(24, size)
    h.putInt(32, 0)                  // no encryption
    h.putInt(36, l1Size)
    h.putLong(40, l1Off)
    h.putLong(48, tableOff)
    h.putInt(56, tableClusters.toInt())
    h.putInt(60, 0)                  // snapshots
    h.putLong(64, 0)
    h.putLong(72, 0)                 // incompatible features
    h.putLong(80, 0)                 // compatible
    h.putLong(88, 0)                 // autoclear
    h.putInt(96, 4)                  // refcount_order: 16-bit
    h.putInt(100, 104)               // header length; extensions end at 104 (type 0)
    writeAll(ch, h, 0)
    progress(size)
  }

  private fun writeAll(ch: FileChannel, b: ByteBuffer, at: Long) {
    b.position(0)
    var o = at
    while (b.hasRemaining()) o += ch.write(b, o)
  }
}
