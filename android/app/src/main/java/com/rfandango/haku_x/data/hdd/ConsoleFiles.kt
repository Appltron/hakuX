package com.rfandango.haku_x.data.hdd

import android.content.Context
import com.rfandango.haku_x.data.AppPrefs
import com.rfandango.haku_x.data.DiscInfoCache
import com.rfandango.haku_x.data.GameScanner
import com.rfandango.haku_x.data.XboxDisc
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * The console dumps a console's or xemu's data comes in with (SaveResigner): the MCPX boot ROM and the
 * BIOS (the certificate key) and that console's EEPROM (its HD key; xemu's eeprom.bin). Kept in the
 * app's storage (files/console) as the user picks them; the earlier core's copies (x1box, in the app's
 * external storage) are taken for those not set. The keys themselves are read when needed, never kept.
 */
class ConsoleFiles(private val context: Context) {
  enum class Kind(val file: String, val legacy: String, val sizes: LongRange) {
    MCPX("mcpx.bin", "mcpx.bin", ConsoleKeys.MCPX_SIZE.toLong()..ConsoleKeys.MCPX_SIZE.toLong()),
    BIOS("bios.bin", "flash.bin", 0x40000L..0x400000L),
    EEPROM("eeprom.bin", "eeprom.bin", 0x100L..0x100L),
  }

  /** What's set, and whether the keys read from it. */
  class Status(val set: Set<Kind>, val certKey: Boolean, val hdKey: Boolean) {
    val ready get() = certKey && hdKey
  }

  private val dir = File(context.filesDir, "console")

  private fun file(k: Kind) = File(dir, k.file)

  private fun read(k: Kind): ByteArray? = file(k).takeIf { it.isFile && it.length() in k.sizes }?.readBytes()

  fun status(): Status {
    adoptLegacy()
    val m = read(Kind.MCPX)
    val b = read(Kind.BIOS)
    val e = read(Kind.EEPROM)
    return Status(
      Kind.entries.filter { file(it).isFile }.toSet(),
      m != null && b != null && ConsoleKeys.certKey(m, b) != null,
      e != null && ConsoleKeys.hdKey(e) != null,
    )
  }

  /** The re-signer for that console's data to the native core's, or null when the dumps don't give both keys. */
  fun resigner(): SaveResigner? {
    val cert = ConsoleKeys.certKey(read(Kind.MCPX) ?: return null, read(Kind.BIOS) ?: return null) ?: return null
    val hd = ConsoleKeys.hdKey(read(Kind.EEPROM) ?: return null) ?: return null
    return SaveResigner(cert, hd, SaveResigner.nativeHdKey(HddStore(context).root))
  }

  /** A dump picked by the user, kept in place of the one before. */
  fun set(k: Kind, input: InputStream) {
    dir.mkdirs()
    val tmp = File(dir, "${k.file}.tmp")
    try {
      val n = input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
      if (n !in k.sizes) throw IOException("not a ${k.name} dump ($n bytes)")
      if (!tmp.renameTo(file(k))) throw IOException("can't keep ${k.file}")
    } finally {
      tmp.delete()
    }
  }

  /** A dump of the previous release's (where its xemu.toml says), taken when none is set. */
  fun adopt(k: Kind, src: File) {
    if (file(k).exists() || !src.isFile || src.length() !in k.sizes) return
    dir.mkdirs()
    runCatching { src.copyTo(file(k)) }
  }

  /** The earlier core's dumps (x1box), for the ones not set. */
  private fun adoptLegacy() {
    val x1box = File(context.getExternalFilesDir(null) ?: return, "x1box")
    for (k in Kind.entries) {
      val src = File(x1box, k.legacy)
      if (!file(k).exists() && src.isFile && src.length() in k.sizes) {
        dir.mkdirs()
        runCatching { src.copyTo(file(k)) }
      }
    }
  }
}

/**
 * Each title's save key from the library's games (XboxDisc.saveKeys): a game's own first, then the
 * keys games hold for other titles. Reads the discs' certificates: blocking.
 */
object LibraryKeys {
  fun load(context: Context, wanted: Set<Long>): Map<Long, ByteArray> {
    if (wanted.isEmpty()) return emptyMap()
    val games = GameScanner.scan(context, AppPrefs(context).gamesFolders)
    val cache = DiscInfoCache(context)
    val out = HashMap<Long, ByteArray>()
    val alternates = HashMap<Long, ByteArray>()
    // the games whose own ID is wanted first; the rest only for IDs still missing
    val (own, others) = games.partition { g -> cache.cached(g)?.titleId?.let { it in wanted } == true }
    for (g in own + others) {
      if (wanted.all { it in out }) break
      val keys = XboxDisc.open(context, g.uri) { it.saveKeys } ?: continue
      keys.entries.firstOrNull()?.let { (id, key) -> if (id in wanted) out.putIfAbsent(id, key) }
      for ((id, key) in keys.entries.drop(1)) if (id in wanted) alternates.putIfAbsent(id, key)
    }
    for ((id, key) in alternates) out.putIfAbsent(id, key)
    return out
  }
}
