package com.rfandango.haku_x.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque
import java.util.Locale

/**
 * A game image; title is the file's name until its disc's own title is read
 * (DiscInfoCache). relativePath is within folder, the games folder it was
 * found in.
 */
data class Game(
  val title: String,
  val uri: Uri,
  val relativePath: String,
  val sizeBytes: Long,
  val folder: Uri,
)

/** The games in a folder (and its subfolders): a listing, no image is opened. */
object GameScanner {
  private val gameExts = setOf("iso", "xiso", "cso", "cci")

  /**
   * Every folder's games, by title. A file two folders reach (one inside the
   * other) is listed once: its document is the same, its URIs (one per
   * folder) are not. Blocking.
   */
  fun scan(context: Context, folders: List<Uri>): List<Game> {
    val seen = HashSet<String>()
    return folders.flatMap { scan(context, it) }
      .filter { seen.add(documentId(it.uri)) }
      .sortedBy { it.title.lowercase(Locale.ROOT) }
  }

  private fun documentId(uri: Uri): String =
    try {
      DocumentsContract.getDocumentId(uri)
    } catch (_: IllegalArgumentException) {
      uri.toString()
    }

  /** Blocking (SAF listing): call off the main thread. */
  fun scan(context: Context, folder: Uri): List<Game> {
    val root = DocumentFile.fromTreeUri(context, folder) ?: return emptyList()
    val stack = ArrayDeque<Pair<DocumentFile, String>>()
    stack.add(root to "")

    val games = ArrayList<Game>()
    while (stack.isNotEmpty()) {
      val (node, prefix) = stack.removeLast()
      val files = try {
        node.listFiles()
      } catch (_: Exception) {
        emptyArray()
      }
      for (child in files) {
        val name = child.name ?: continue
        if (child.isDirectory) {
          // Converted dumps wait in their own folder, out of the library.
          if (!name.equals(XisoConverter.ORIGINALS_FOLDER, ignoreCase = true)) {
            stack.add(child to "$prefix$name/")
          }
          continue
        }
        if (!child.isFile || !isSupported(name)) {
          continue
        }
        games += Game(
          title = toTitle(name),
          uri = child.uri,
          relativePath = prefix + name,
          sizeBytes = child.length(),
          folder = folder,
        )
      }
    }

    // A raw .iso with a converted .xiso.iso beside it is shown once.
    val paths = games.mapTo(HashSet()) { it.relativePath.lowercase(Locale.ROOT) }
    games.removeAll { game ->
      val lower = game.relativePath.lowercase(Locale.ROOT)
      lower.endsWith(".iso") && !lower.endsWith(".xiso.iso") &&
        paths.contains(lower.substringBeforeLast(".iso") + ".xiso.iso")
    }
    games.sortBy { it.title.lowercase(Locale.ROOT) }
    return games
  }

  private fun isSupported(name: String): Boolean {
    val lower = name.lowercase(Locale.ROOT)
    if (lower.endsWith(".xiso.iso")) {
      return true
    }
    val ext = lower.substringAfterLast('.', "")
    return ext.isNotEmpty() && ext in gameExts
  }

  /** A game's title from its file's name (toTitle); brackets kept when asked. */
  fun fileTitle(relativePath: String, brackets: Boolean = false): String =
    toTitle(relativePath.substringAfterLast('/'), brackets)

  /**
   * A file's name as a title: the extension dropped, and anything in
   * brackets too ("(USA, Japan) (En,Ja)", "[!]"), unless that leaves nothing.
   */
  private fun toTitle(fileName: String, brackets: Boolean = false): String {
    val lower = fileName.lowercase(Locale.ROOT)
    val name = when {
      lower.endsWith(".xiso.iso") -> fileName.dropLast(".xiso.iso".length)
      fileName.contains('.') -> fileName.substringBeforeLast('.')
      else -> fileName
    }.trim()
    if (brackets) return name
    return name.replace(BRACKETED, " ").replace(SPACES, " ").trim().ifEmpty { name }
  }

  // Every bracket escaped: Android's ICU regex rejects a bare '}' (and ']').
  private val BRACKETED = Regex("""\([^\)]*\)|\[[^\]]*\]|\{[^\}]*\}""")
  private val SPACES = Regex("""\s+""")
}
