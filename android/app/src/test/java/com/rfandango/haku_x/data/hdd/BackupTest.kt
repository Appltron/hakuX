package com.rfandango.haku_x.data.hdd

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
  private fun file(f: File, text: String, mtime: Long = 1_700_000_000_000L) {
    f.parentFile!!.mkdirs()
    f.writeText(text)
    f.setLastModified(mtime)
  }

  private fun tree(dir: File) = dir.walkTopDown().filter { it.isFile }
    .associate { it.relativeTo(dir).path.replace('\\', '/') to it.readText() }

  /** A console's files/native: saves, game data, C:, a cache drive, the EEPROM, a savestate, a leftover. */
  private fun console(n: File) {
    file(File(n, "hdd/Partition1/UDATA/4d530004/s1/blam.sav"), "save", 1_600_000_000_000L)
    File(n, "hdd/Partition1/TDATA/4d530004").mkdirs()
    file(File(n, "hdd/Partition2/xboxdash.xbe"), "dash")
    file(File(n, "hdd/Partition4/cache.bin"), "a's cache")
    file(File(n, "hdd/raw-partition4.bin"), "a's Y head")
    file(File(n, "hdd/eeprom.bin"), "eeprom")
    file(File(n, "states/4D530004.9/slot1.hxs"), "state")
    file(File(n, "hdd-pgr1first/x"), "leftover")
  }

  @Test
  fun backupRestore() {
    val tmp = Files.createTempDirectory("backup").toFile()
    try {
      val a = File(tmp, "a").apply { console(this) }
      val out = ByteArrayOutputStream()
      val r = Backup.write(out, a, states = true, caches = false) { _, _, _ -> }
      assertEquals(4, r.files)                                   // no cache drive, no leftover
      val zip = out.toByteArray()
      val info = Backup.info(ByteArrayInputStream(zip))!!
      assertEquals("backup", info.kind)
      assertTrue(info.states)
      assertFalse(info.caches)

      // another console: its saves and savestates go, its cache drive stays (the backup has none)
      val b = File(tmp, "b")
      file(File(b, "hdd/Partition1/UDATA/4d530004/old/x.sav"), "b's save")
      file(File(b, "hdd/Partition4/cache.bin"), "b's cache")
      file(File(b, "hdd/raw-partition4.bin"), "b's Y head")
      file(File(b, "states/X/slot1.hxs"), "b's state")
      val rr = Backup.restore(ByteArrayInputStream(zip), zip.size.toLong(), b) { _, _, _ -> }
      assertEquals(4, rr.files)
      assertEquals(
        mapOf("hdd/Partition1/UDATA/4d530004/s1/blam.sav" to "save", "hdd/Partition2/xboxdash.xbe" to "dash",
              "hdd/eeprom.bin" to "eeprom", "hdd/Partition4/cache.bin" to "b's cache",
              "hdd/raw-partition4.bin" to "b's Y head", "states/4D530004.9/slot1.hxs" to "state"),
        tree(b))
      assertTrue(File(b, "hdd/Partition1/TDATA/4d530004").isDirectory)   // empty folders come back too
      assertEquals(1_600_000_000_000L / 2000,
                   File(b, "hdd/Partition1/UDATA/4d530004/s1/blam.sav").lastModified() / 2000)
      assertEquals(listOf("hdd", "states"), b.list()!!.sorted())         // nothing left aside

      // with the cache drives, a restore brings the backup's
      val withCaches = ByteArrayOutputStream()
      Backup.write(withCaches, a, states = false, caches = true) { _, _, _ -> }
      Backup.restore(ByteArrayInputStream(withCaches.toByteArray()), 0, b) { _, _, _ -> }
      assertEquals("a's cache", File(b, "hdd/Partition4/cache.bin").readText())
      assertEquals("state", File(b, "states/4D530004.9/slot1.hxs").readText())   // states: not in it, kept
    } finally {
      tmp.deleteRecursively()
    }
  }

  @Test
  fun onlyBackups() {
    val tmp = Files.createTempDirectory("backup").toFile()
    try {
      val a = File(tmp, "a").apply { console(this) }
      val saves = ByteArrayOutputStream()
      SaveArchive.write(saves, listOf(SaveArchive.Pick(File(a, "hdd/Partition1/UDATA/4d530004"))))
      val b = File(tmp, "b")
      file(File(b, "hdd/eeprom.bin"), "b's")
      try {
        Backup.restore(ByteArrayInputStream(saves.toByteArray()), 0, b) { _, _, _ -> }
        fail("a zip of saves restored as a backup")
      } catch (_: IOException) {
      }
      assertEquals(mapOf("hdd/eeprom.bin" to "b's"), tree(b))

      // saves import from a backup: E:'s saves only, nothing of the caches or savestates
      val backup = ByteArrayOutputStream()
      Backup.write(backup, a, states = true, caches = true) { _, _, _ -> }
      val plan = SaveArchive.plan(ByteArrayInputStream(backup.toByteArray()), File(b, "hdd/Partition1"))
      assertEquals("backup", plan.kind)
      assertEquals(mapOf("4d530004" to setOf("s1")), plan.saves)
      assertEquals(1, plan.files)
      assertTrue(plan.skipped.isEmpty())
      assertEquals("saves", SaveArchive.plan(ByteArrayInputStream(saves.toByteArray()), File(b, "hdd/Partition1")).kind)
    } finally {
      tmp.deleteRecursively()
    }
  }
}
