package com.rfandango.haku_x.data.hdd

/**
 * The keys a console signs saves with, read from the user's own dumps when a console's or xemu's data
 * comes in (re-signed to the native kernel's: SaveResigner). Never kept: read again each time.
 *
 * - The certificate key, from which each title's save key comes (SaveResigner.authKey): the BIOS's 2BL
 *   holds the EEPROM, certificate and kernel keys in a row; the MCPX 1.0 boot ROM decrypts it (RC4, the
 *   key at 0x1A5; the 2BL is the 24 KiB below the flash's last 512 bytes). The EEPROM key is told by its
 *   HMAC-SHA1 midstates, the certificate key is the 16 bytes after it.
 * - The HD key, for saves locked to the console and its downloaded content: the EEPROM's encrypted
 *   section (hash, confounder, HD key, region) is RC4 with HMAC(EEPROM key, hash) and checks by its hash.
 *   The midstates are enough: no BIOS needed.
 */
object ConsoleKeys {
  /** An EEPROM key's HMAC-SHA1 midstates (inner, outer). */
  class Midstates(val name: String, val inner: IntArray, val outer: IntArray)

  /** The retail and debug kernels' EEPROM keys, as xemu's hw/xbox/eeprom_generation.c gives them. */
  val EEPROM_KEYS = listOf(
    Midstates("debug",
      intArrayOf(0x85F9E51A.toInt(), 0xE04613D2.toInt(), 0x6D86A50C, 0x77C32E3C, 0x4BD717A4),
      intArrayOf(0x5D7A9C6B, 0xE1922BEB.toInt(), 0xB82CCDBC.toInt(), 0x3137AB34, 0x486B52B3)),
    Midstates("1.0",
      intArrayOf(0x72127625, 0x336472B9, 0xBE609BEA.toInt(), 0xF55E226B.toInt(), 0x99958DAC.toInt()),
      intArrayOf(0x76441D41, 0x4DE82659, 0x2E8EF85E, 0xB256FACA.toInt(), 0xC4FE2DE8.toInt())),
    Midstates("1.1-1.4",
      intArrayOf(0x39B06E79, 0xC9BD25E8.toInt(), 0xDBC6B498.toInt(), 0x40B4389D, 0x86BBD7ED.toInt()),
      intArrayOf(0x9B49BED3.toInt(), 0x84B430FC.toInt(), 0x6B8749CD, 0xEBFE5FE5.toInt(), 0xD96E7393.toInt())),
    Midstates("1.6",
      intArrayOf(0x8058763A.toInt(), 0xF97D4E0E.toInt(), 0x865A9762.toInt(), 0x8A3D920D.toInt(), 0x08995B2C),
      intArrayOf(0x01075307, 0xA2F1E037.toInt(), 0x1186EEEA, 0x88DA9992.toInt(), 0x168A5609)),
  )

  const val MCPX_SIZE = 512
  private const val MCPX_RC4 = 0x1A5
  private const val BL_SIZE = 0x6000
  private const val BL_END = 0x200

  /** The certificate key in a BIOS for the MCPX 1.0 boot ROM, or null (another ROM, not a BIOS). */
  fun certKey(mcpx: ByteArray, flash: ByteArray, eepromKeys: List<Midstates> = EEPROM_KEYS): ByteArray? {
    if (mcpx.size != MCPX_SIZE || flash.size < 0x40000) return null
    val bl = XCrypto.rc4(mcpx.copyOfRange(MCPX_RC4, MCPX_RC4 + 16), flash, flash.size - BL_END - BL_SIZE, BL_SIZE)
    for (off in 0..bl.size - 48) {
      val mid = XCrypto.innerMid(bl, off, 16)
      if (eepromKeys.any { it.inner.contentEquals(mid) }) return bl.copyOfRange(off + 16, off + 32)
    }
    return null
  }

  /** The HD key in an EEPROM dump, or null when its encrypted section checks with none of the keys. */
  fun hdKey(eeprom: ByteArray, eepromKeys: List<Midstates> = EEPROM_KEYS): ByteArray? {
    if (eeprom.size < 0x30) return null
    val hash = eeprom.copyOf(20)
    for (k in eepromKeys) {
      val plain = XCrypto.rc4(XCrypto.hmacMid(k.inner, k.outer, hash), eeprom, 20, 0x1C)
      if (XCrypto.hmacMid(k.inner, k.outer, plain).contentEquals(hash)) return plain.copyOfRange(8, 24)
    }
    return null
  }
}
