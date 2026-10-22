package com.rfandango.haku_x.data.hdd

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * An Xbox disk image (xemu's qcow2 or a raw dump) brought into the native core's disk, its saves
 * re-signed for hakuX with the console files' keys (SaveResigner). The hard disk screen's import and
 * the first start after the update from the xemu-based release (PreviousVersion) both run it.
 */
object DiskImport {
  class Outcome(val result: XboxHdd.Result, val resign: SaveResigner.Report?)

  /** Opens an image (an app file, or a document) and runs use on it. */
  fun <T> open(context: Context, file: File?, uri: Uri?, use: (DiskImage) -> T): T = when {
    file != null -> RandomAccessFile(file, "r").use { raf -> use(DiskImage.open(raf.channel)) }
    else -> context.contentResolver.openFileDescriptor(uri!!, "r")!!.use { pfd ->
      FileInputStream(pfd.fileDescriptor).channel.use { ch -> use(DiskImage.open(ch)) }
    }
  }

  /**
   * The image over the native core's disk; its saves re-signed with resigner's keys, or left as they are
   * without one. resigning: called as the re-signing starts (it reads the library's discs: a while).
   */
  fun run(context: Context, image: DiskImage, resigner: SaveResigner?, progress: XboxHdd.Progress,
          resigning: () -> Unit = {}): Outcome {
    val root = HddStore(context).root
    val result = XboxHdd.import(image, root, progress)
    val report = resigner?.let { rs ->
      resigning()
      val e = XboxHdd.folder(root, 1)
      val ids = listOf("UDATA", "TDATA").flatMap { area ->
        e.listFiles().orEmpty().filter { it.name.equals(area, ignoreCase = true) }.flatMap { it.list().orEmpty().toList() }
      }.mapNotNull { it.toLongOrNull(16) }.toSet()
      rs.resignAll(e, LibraryKeys.load(context, ids)::get)
    }
    return Outcome(result, report)
  }
}
