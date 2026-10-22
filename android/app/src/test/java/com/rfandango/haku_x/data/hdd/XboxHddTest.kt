package com.rfandango.haku_x.data.hdd

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XboxHddTest {
  private val rnd = Random(42)

  private fun file(f: File, size: Int, mtime: Long = 1_700_000_000_000L) {
    f.parentFile!!.mkdirs()
    f.writeBytes(ByteArray(size) { rnd.nextInt().toByte() })
    f.setLastModified(mtime)
  }

  /** A native-core disk with saves, title data, a formatted and an unformatted cache drive. */
  private fun disk(root: File) {
    val e = File(root, "Partition1")
    file(File(e, "UDATA/4d530004/TitleMeta.xbx"), 64)
    file(File(e, "UDATA/4d530004/ABCDEF012345/SaveMeta.xbx"), 100)
    file(File(e, "UDATA/4d530004/ABCDEF012345/savegame.bin"), 3 * 16384 + 17)   // over clusters
    file(File(e, "UDATA/4d530004/ABCDEF012345/empty.dat"), 0)
    file(File(e, "TDATA/4d530004/settings.bin"), 16384)                         // exactly a cluster
    for (i in 0 until 300) {                                                    // a directory over clusters
      file(File(e, "TDATA/many/f$i.txt"), i % 7)
    }
    File(root, "Partition2/xboxdash.xbe").let { file(it, 5000) }
    file(File(root, "Partition4/cache000.map"), 300_000)
    File(root, "Partition3").mkdirs()                                            // unformatted (no head)
    File(root, "Partition5").mkdirs()
    // Y's superblock where the kernel keeps it raw (XMountUtilityDrive's format)
    val head = ByteArray(XboxHdd.RAW_KEEP) { 0xFF.toByte() }
    ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).apply {
      putInt(0, 0x58544146); putInt(4, 0x12345678); putInt(8, 32); putInt(12, 1)
    }
    File(root, "raw-partition4.bin").writeBytes(head)
    File(root, "raw-partition0.bin").writeBytes(ByteArray(XboxHdd.RAW_KEEP) { (it * 7).toByte() })
  }

  private fun tree(root: File, part: Int): Map<String, Pair<ByteArray?, Long>> {
    val dir = File(root, "Partition$part")
    return dir.walkTopDown().filter { it != dir }.associate {
      it.relativeTo(dir).path.replace('\\', '/').lowercase() to
        ((if (it.isFile) it.readBytes() else null) to (if (it.isFile) it.lastModified() / 2000 else 0L))
    }
  }

  private fun assertSame(a: File, b: File, part: Int) {
    val ta = tree(a, part)
    val tb = tree(b, part)
    assertEquals("partition $part's entries", ta.keys.sorted(), tb.keys.sorted())
    for ((k, v) in ta) {
      val w = tb[k]!!
      if (v.first != null) {
        assertArrayEquals("$k's bytes", v.first, w.first)
        assertEquals("$k's time", v.second, w.second)
      }
    }
  }

  @Test
  fun qcow2RoundTrip() {
    val tmp = Files.createTempDirectory("hdd").toFile()
    try {
      val a = File(tmp, "a").apply { mkdirs() }
      disk(a)
      val img = File(tmp, "x.qcow2")
      val res = RandomAccessFile(img, "rw").use { raf ->
        XboxHdd.export(a, raf.channel, qcow2 = true) { _, _, _ -> }
      }
      assertTrue("skipped ${res.skipped}", res.skipped.isEmpty())
      assertTrue("an image of the used space only (${img.length()})", img.length() < 32L shl 20)
      val b = File(tmp, "b").apply { mkdirs() }
      RandomAccessFile(img, "r").use { raf ->
        val di = DiskImage.open(raf.channel)
        assertEquals("qcow2", di.format)
        assertEquals(XboxHdd.SIZE, di.size)
        XboxHdd.import(di, b) { _, _, _ -> }
      }
      for (p in listOf(1, 2, 4)) assertSame(a, b, p)
      assertTrue(tree(b, 3).isEmpty())
      assertFalse("X stays unformatted", File(b, "raw-partition3.bin").exists())
      assertArrayEquals(File(a, "raw-partition0.bin").readBytes(), File(b, "raw-partition0.bin").readBytes())
      // Y's superblock keeps its volume id and cluster size
      val y = ByteBuffer.wrap(File(b, "raw-partition4.bin").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
      assertEquals(0x58544146, y.getInt(0))
      assertEquals(0x12345678, y.getInt(4))
      assertEquals(32, y.getInt(8))
    } finally {
      tmp.deleteRecursively()
    }
  }

  @Test
  fun fatxLayout() {
    // the retail partitions: 16-bit FATs for C and the cache drives, 32-bit for E
    val e = Fatx.Layout(0x1312D6000L, 32)
    assertTrue(e.wide)
    assertEquals(1253376L, e.fatSize)
    val x = Fatx.Layout(0x2EE00000L, 32)
    assertFalse(x.wide)
    assertEquals(98304L, x.fatSize)
    assertEquals(0x1000L + 98304L, x.dataStart)
  }

  @Test
  fun fatTimes() {
    val t = 1_700_000_000_000L          // 2023-11-14 22:13:20 UTC
    assertEquals(t / 2000, Fatx.fromFat(Fatx.toFat(t)) / 2000)
  }

  @Test
  fun rawWriterHoles() {
    val tmp = Files.createTempFile("raw", ".img").toFile()
    try {
      val size = 3L shl 20
      RandomAccessFile(tmp, "rw").use { raf ->
        RawImage.write(raf.channel, size, { o, b, n -> for (i in 0 until n) b[i] = ((o + i) % 251).toByte() },
                       { o -> if (o < (1L shl 20)) o else if (o < (2L shl 20)) (2L shl 20) else o }) { }
      }
      assertEquals(size, tmp.length())
      val d = tmp.readBytes()
      assertEquals(((5L) % 251).toByte(), d[5])
      assertEquals(0.toByte(), d[(1 shl 20) + 5])                     // the hole
      assertEquals((((2L shl 20) + 9) % 251).toByte(), d[(2 shl 20) + 9])
    } finally {
      tmp.delete()
    }
  }
}
