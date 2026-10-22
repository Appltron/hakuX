package com.rfandango.haku_x.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.rfandango.haku_x.R

/** Storage Access Framework helpers: the games folders and the games in them. */
object Storage {
  fun persistPermission(context: Context, uri: Uri, write: Boolean = true) {
    var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    if (write) {
      flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
    try {
      context.contentResolver.takePersistableUriPermission(uri, flags)
    } catch (_: SecurityException) {
    }
  }

  fun hasReadPermission(context: Context, uri: Uri): Boolean =
    context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

  /** A folder the app may still read, and that still exists. */
  fun isFolderReady(context: Context, uri: Uri?): Boolean {
    if (uri == null || !hasReadPermission(context, uri)) {
      return false
    }
    val root = DocumentFile.fromTreeUri(context, uri) ?: return false
    return root.exists() && root.isDirectory
  }

  fun folderLabel(context: Context, uri: Uri): String {
    val name = DocumentFile.fromTreeUri(context, uri)?.name
    return if (!name.isNullOrBlank()) name else uri.toString()
  }

  /**
   * Where a picked folder is, for telling two of the same name apart:
   * "Internal storage › ROMs/xbox" (the external storage provider's tree
   * IDs are volume:path; another provider's folder shows its own label).
   */
  fun folderPlace(context: Context, uri: Uri): String {
    val id = try {
      DocumentsContract.getTreeDocumentId(uri)
    } catch (_: IllegalArgumentException) {
      return folderLabel(context, uri)
    }
    if (uri.authority != EXTERNAL_STORAGE_AUTHORITY || ':' !in id) {
      return folderLabel(context, uri)
    }
    val volume = id.substringBefore(':')
    val where = context.getString(
      if (volume == "primary") R.string.storage_internal else R.string.storage_removable,
    )
    return "$where › ${id.substringAfter(':')}"
  }

  /** The app's access to a folder it no longer lists. */
  fun releasePermission(context: Context, uri: Uri) {
    try {
      context.contentResolver.releasePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    } catch (_: SecurityException) {
    }
  }

  private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
}
