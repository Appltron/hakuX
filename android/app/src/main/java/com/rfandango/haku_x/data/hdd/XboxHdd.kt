package com.rfandango.haku_x.data.hdd

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * The Xbox hard disk between its two forms: an image (raw, or qcow2 as xemu
 * keeps it; the retail 8 GB layout, FATX partitions) and the native core's
 * folders (native/kernel/io.c: Partition0-5, raw-partition<n>.bin holding a
 * partition's first 512 KiB where the kernel reads it raw: the config area's
 * cache table, a cache drive's superblock).
 */
object XboxHdd {
  /** The retail disk's size and partitions (io.c k_partitions). */
  const val SIZE = 0x1DD156000L
  const val RAW_KEEP = 0x80000

  class Part(val index: Int, val offset: Long, val size: Long, val drive: String) {
    val end get() = offset + size
  }

  val PARTS = listOf(
    Part(0, 0x00000000L, 0x00080000L, ""),
    Part(3, 0x00080000L, 0x2EE00000L, "X"),
    Part(4, 0x2EE80000L, 0x2EE00000L, "Y"),
    Part(5, 0x5DC80000L, 0x2EE00000L, "Z"),
    Part(2, 0x8CA80000L, 0x1F400000L, "C"),
    Part(1, 0xABE80000L, 0x1312D6000L, "E"),
  )

  /** The default cluster: 32 sectors (16 KiB), as the Xbox formats its drives. */
  private const val SECTORS_PER_CLUSTER = 32

  fun folder(root: File, part: Int) = File(root, "Partition$part")
  fun rawHead(root: File, part: Int) = File(root, "raw-partition$part.bin")

  /** What an import or export did. */
  class Result(val files: Int, val bytes: Long, val skipped: List<String>)

  /** Step progress: bytes done of total, and what is being done. */
  fun interface Progress {
    fun update(done: Long, total: Long, what: String)
  }

  // ------------------------------------------------------------------ import

  /** An image's game saves (E:\UDATA): how many titles have saves there, and how many saves; null: no E:. */
  fun saves(image: DiskImage): Pair<Int, Int>? {
    val e = PARTS.first { it.index == 1 }
    val r = runCatching { Fatx.Reader({ o, b -> image.read(e.offset + o, b) }, e.size) }.getOrNull() ?: return null
    val udata = r.list().firstOrNull { it.isDirectory && it.name.equals("UDATA", ignoreCase = true) } ?: return 0 to 0
    val titles = r.list(udata.firstCluster).filter { it.isDirectory }
    val saves = titles.map { t -> r.list(t.firstCluster).count { it.isDirectory } }
    return saves.count { it > 0 } to saves.sum()
  }

  /**
   * The image's partitions into root's folders, replacing what they held:
   * every FATX partition's files (times kept), the config area and the cache
   * drives' heads. eeprom.bin and the rest of root stay.
   */
  fun import(image: DiskImage, root: File, progress: Progress): Result {
    if (image.size < SIZE) {
      throw IOException("not an Xbox hard disk image: ${image.size shr 20} MiB, an Xbox disk has ${SIZE shr 20} MiB")
    }
    val readers = HashMap<Int, Fatx.Reader>()
    for (p in PARTS) {
      if (p.index == 0) continue
      val r = runCatching { Fatx.Reader({ o, b -> image.read(p.offset + o, b) }, p.size) }.getOrNull()
      if (r != null) readers[p.index] = r
    }
    if (readers[1] == null) {
      throw IOException("no FATX data partition (E:) in this image: not an Xbox hard disk, or a locked one")
    }
    // what there is to copy, for the progress
    var total = 0L
    fun sizeOf(r: Fatx.Reader, first: Long?): Long = r.list(first).sumOf {
      if (it.isDirectory) sizeOf(r, it.firstCluster) else it.size
    }
    for ((_, r) in readers) total += sizeOf(r, null)
    // the folders start over
    for (p in PARTS) {
      if (p.index == 0) continue
      val f = folder(root, p.index)
      f.deleteRecursively()
      f.mkdirs()
      rawHead(root, p.index).delete()
    }
    val head = ByteArray(RAW_KEEP)
    image.read(0, head)
    rawHead(root, 0).writeBytes(head)
    var done = 0L
    var files = 0
    val skipped = ArrayList<String>()
    for (p in PARTS) {
      val r = readers[p.index] ?: continue
      if (p.index >= 3) {                       // a cache drive: its superblock where the kernel reads it raw
        image.read(p.offset, head)
        rawHead(root, p.index).writeBytes(head)
      }
      fun copy(first: Long?, dir: File, path: String) {
        for (e in r.list(first)) {
          val name = e.name
          if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\u0000')) {
            skipped += "${p.drive}:$path\\$name"
            continue
          }
          val dst = File(dir, name)
          if (e.isDirectory) {
            dst.mkdirs()
            copy(e.firstCluster, dst, "$path\\$name")
          } else {
            progress.update(done, total, "${p.drive}:$path\\$name")
            dst.outputStream().buffered(1 shl 16).use { out ->
              r.readFile(e) { b, n ->
                out.write(b, 0, n)
                done += n
              }
            }
            files++
          }
          if (e.written > 0) dst.setLastModified(e.written)
        }
      }
      copy(null, folder(root, p.index), "")
    }
    progress.update(total, total, "")
    return Result(files, total, skipped)
  }

  // ------------------------------------------------------------------ export

  /** A partition's FATX writer, or null for an unformatted cache drive. */
  private fun writerFor(root: File, p: Part, skipped: MutableList<String>): Fatx.Writer? {
    val dir = folder(root, p.index)
    val head = rawHead(root, p.index)
    var spc = SECTORS_PER_CLUSTER
    var id = 0x48414B55 xor p.index           // "HAKU": any id will do
    if (head.isFile && head.length() >= 16) {
      val b = ByteBuffer.wrap(head.readBytes().copyOf(16)).order(ByteOrder.LITTLE_ENDIAN)
      if (b.getInt(0) == 0x58544146) {
        id = b.getInt(4)
        b.getInt(8).takeIf { it in 1..1024 && it and (it - 1) == 0 }?.let { spc = it }
      } else if (p.index >= 3) {
        return null                             // a cache drive left unformatted
      }
    } else if (p.index >= 3 && (dir.list()?.isEmpty() != false)) {
      return null
    }
    fun node(f: File, path: String): Fatx.Writer.Node? {
      val name = f.name
      val ok = name.length <= Fatx.NAME_MAX && name.all { it.code in 0x20..0xFF }
      if (!ok) {
        skipped += "${p.drive}:$path\\$name"
        return null
      }
      return if (f.isDirectory) {
        val kids = (f.listFiles() ?: emptyArray()).sortedBy { it.name.lowercase() }
          .mapNotNull { node(it, "$path\\$name") }
        Fatx.Writer.Node(name, true, written = f.lastModified(), children = kids)
      } else {
        Fatx.Writer.Node(name, false, size = f.length(), written = f.lastModified(), source = FileSource(f)::read)
      }
    }
    val kids = (dir.listFiles() ?: emptyArray()).sortedBy { it.name.lowercase() }.mapNotNull { node(it, "") }
    val layout = Fatx.Layout(p.size, spc)
    return Fatx.Writer(layout, id, Fatx.Writer.Node("", true, children = kids))
  }

  /** A host file read in place (one open file at a time, kept between reads). */
  private class FileSource(private val f: File) {
    fun read(offset: Long, buf: ByteArray, len: Int) {
      val raf = open(f)
      raf.seek(offset)
      raf.readFully(buf, 0, len)
    }

    companion object {
      private var cur: File? = null
      private var raf: RandomAccessFile? = null

      fun open(f: File): RandomAccessFile {
        if (cur != f) {
          raf?.close()
          raf = RandomAccessFile(f, "r")
          cur = f
        }
        return raf!!
      }

      fun close() {
        raf?.close()
        raf = null
        cur = null
      }
    }
  }

  /** The image of root's folders, by offset: bytes and the next offset with data. */
  private class Composite(root: File, skipped: MutableList<String>) {
    val writers = HashMap<Int, Fatx.Writer?>()
    val config: ByteArray? = rawHead(root, 0).takeIf { it.isFile }?.readBytes()?.copyOf(RAW_KEEP)

    init {
      for (p in PARTS) if (p.index != 0) writers[p.index] = writerFor(root, p, skipped)
    }

    fun part(off: Long) = PARTS.firstOrNull { off >= it.offset && off < it.end }

    fun read(off: Long, buf: ByteArray, len: Int) {
      java.util.Arrays.fill(buf, 0, len, 0)
      var o = off
      var i = 0
      val tmp = ByteArray(1 shl 16)
      while (i < len) {
        val p = part(o)
        val n = if (p == null) len - i else minOf((p.end - o).toLong(), (len - i).toLong()).toInt()
        if (p != null) {
          if (p.index == 0) {
            config?.let { System.arraycopy(it, (o - p.offset).toInt(), buf, i, n) }
          } else {
            val w = writers[p.index]
            if (w != null) {
              var k = 0
              while (k < n) {
                val m = minOf(tmp.size, n - k)
                w.readAt(o - p.offset + k, tmp, m)
                System.arraycopy(tmp, 0, buf, i + k, m)
                k += m
              }
            }
          }
        }
        o += n
        i += n
      }
    }

    fun nextData(off: Long): Long {
      var o = off
      for (p in PARTS) {
        if (o >= p.end) continue
        if (o < p.offset) o = p.offset
        if (p.index == 0) {
          if (config != null) return o
          continue
        }
        val w = writers[p.index] ?: continue
        val d = w.nextData(o - p.offset)
        if (d >= 0) return p.offset + d
      }
      return -1
    }
  }

  /** root's folders as an image into ch: qcow2 (xemu's) or raw. */
  fun export(root: File, ch: FileChannel, qcow2: Boolean, progress: Progress): Result {
    val skipped = ArrayList<String>()
    val img = Composite(root, skipped)
    var files = 0
    var bytes = 0L
    for (p in PARTS) {
      if (p.index == 0) continue
      folder(root, p.index).walkTopDown().filter { it.isFile }.forEach {
        files++
        bytes += it.length()
      }
    }
    try {
      val report = { at: Long -> progress.update(at, SIZE, "") }
      if (qcow2) {
        Qcow2.write(ch, SIZE, img::read, img::nextData, report)
      } else {
        RawImage.write(ch, SIZE, img::read, img::nextData, report)
      }
    } finally {
      FileSource.close()
    }
    return Result(files, bytes, skipped)
  }
}
