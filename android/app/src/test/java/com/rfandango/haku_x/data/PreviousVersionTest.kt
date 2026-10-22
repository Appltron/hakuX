package com.rfandango.haku_x.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviousVersionTest {
  /** The xemu-based release's xemu.toml, as its core wrote it (paths shortened). */
  private val toml = """
    [general]
    show_welcome = false

    [display.quality]
    surface_scale = 1

    [sys.files]
    bootrom_path = '/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box/mcpx.bin'
    dvd_path = '/dev/fdset/0'
    eeprom_path = '/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box/eeprom.bin'
    flashrom_path = "/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box/flash.bin"
    hdd_path = '/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box/hdd.img'

    [sys]
    mem_limit = '64'
  """.trimIndent()

  @Test
  fun sysFiles() {
    val f = PreviousVersion.sysFiles(toml)
    assertEquals("/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box/mcpx.bin", f["bootrom_path"])
    assertEquals("/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box/flash.bin", f["flashrom_path"])
    assertEquals("/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box/eeprom.bin", f["eeprom_path"])
    assertEquals("/storage/emulated/0/Android/data/com.rfandango.haku_x/files/x1box/hdd.img", f["hdd_path"])
    assertEquals(null, f["mem_limit"])   // another section's
  }
}
