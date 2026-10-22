package com.rfandango.haku_x.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.security.MessageDigest
import java.util.Properties

/**
 * What a game's disc says about it: its certificate's title, ID, version and
 * regions, its icon, and where its game partition starts (past the video
 * partition in a full dump, which XisoConverter can turn into an XISO).
 */
data class DiscInfo(
  val title: String?,
  val titleId: Long,
  val version: Long,
  val regions: Int,
  val partition: Long,
  val icon: File?,
) {
  /** The title ID as the Xbox shows it, e.g. 5345000A. */
  val titleIdText: String get() = "%08X".format(titleId)

  /** The disc's release: one title's discs and revisions differ in the version. */
  val release: Pair<Long, Long> get() = titleId to version

  val isFullDump: Boolean get() = partition != 0L

  companion object {
    const val REGION_NA = 1
    const val REGION_JAPAN = 2
    const val REGION_REST_OF_WORLD = 4
  }
}

/**
 * The games' disc info, kept in the app's files so each disc is read once:
 * <key>.info (title, ID, version, regions, partition) and <key>.png when the disc has
 * an icon. Entries for images no longer in the library go (retainOnly).
 */
class DiscInfoCache(context: Context) {
  private val app = context.applicationContext
  private val dir = File(app.filesDir, "disc_info")

  /** The info read before, or null when this disc hasn't been read yet. */
  fun cached(game: Game): DiscInfo? {
    val key = key(game)
    val file = File(dir, "$key.info")
    if (!file.isFile) return null
    val p = Properties()
    try {
      file.reader().use(p::load)
    } catch (_: Exception) {
      return null
    }
    // An entry from before the partition or the version was kept is read again.
    val partition = p.getProperty("partition")?.toLongOrNull(16) ?: return null
    val version = p.getProperty("version")?.toLongOrNull(16) ?: return null
    return DiscInfo(
      title = p.getProperty("title")?.ifBlank { null },
      titleId = p.getProperty("id")?.toLongOrNull(16) ?: 0L,
      version = version,
      regions = p.getProperty("regions")?.toIntOrNull() ?: 0,
      partition = partition,
      icon = File(dir, "$key.png").takeIf { it.isFile },
    )
  }

  /** Reads the disc and keeps what it says. Null when it can't be read now (it's tried again next time). Blocking. */
  fun read(game: Game): DiscInfo? {
    val read = XboxDisc.open(app, game.uri) {
      DiscInfo(it.titleName, it.titleId, it.version, it.regions, it.partitionOffset, null) to it.titleImage()
    } ?: return null
    val (info, bitmap) = read
    val key = key(game)
    dir.mkdirs()
    var icon: File? = null
    if (bitmap != null) {
      val tmp = File(dir, "$key.tmp")
      tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      icon = File(dir, "$key.png").takeIf { tmp.renameTo(it) }
    }
    store(key, info)
    return info.copy(icon = icon)
  }

  /**
   * A converted dump's info, kept for its XISO (the same disc: title, ID,
   * version, regions and icon; the partition now at 0); the dump's entry goes.
   */
  fun converted(from: Game, info: DiscInfo, to: Game) {
    val src = key(from)
    val dst = key(to)
    dir.mkdirs()
    File(dir, "$src.png").takeIf { it.isFile }?.copyTo(File(dir, "$dst.png"), overwrite = true)
    store(dst, info.copy(partition = 0L))
    forget(src)
  }

  /** Drops the entries of images that aren't in games (moved, converted, deleted). */
  fun retainOnly(games: List<Game>) {
    val keep = games.mapTo(HashSet()) { key(it) }
    dir.listFiles()?.forEach { f ->
      if (f.name.substringBefore('.') !in keep) f.delete()
    }
  }

  private fun store(key: String, info: DiscInfo) {
    val p = Properties()
    p.setProperty("title", info.title.orEmpty())
    p.setProperty("id", info.titleIdText)
    p.setProperty("version", info.version.toString(16))
    p.setProperty("regions", info.regions.toString())
    p.setProperty("partition", info.partition.toString(16))
    File(dir, "$key.info").writer().use { p.store(it, null) } // last: it marks the disc as read
  }

  private fun forget(key: String) {
    for (ext in listOf("info", "png", "tmp")) File(dir, "$key.$ext").delete()
  }

  /** The image file and its size name the cache entry: a replaced image is read again. */
  private fun key(game: Game): String {
    val digest = MessageDigest.getInstance("SHA-1").digest("${game.uri}|${game.sizeBytes}".toByteArray())
    return digest.take(10).joinToString("") { "%02x".format(it) }
  }
}
