package com.rfandango.haku_x.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.rfandango.haku_x.R
import com.rfandango.haku_x.data.Updates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A newer release: its notes, scrolled, and its download and install (the system installer asks
 * the user then). The download shows its progress and can be cancelled.
 */
@Composable
fun UpdateDialog(release: Updates.Release, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var job by remember { mutableStateOf<Job?>(null) }
  var progress by remember { mutableFloatStateOf(-1f) }   /* 0..1; -1: the size isn't known */
  var error by remember { mutableStateOf<String?>(null) }
  val downloading = job != null
  // the notes take what the dialog has left on a landscape handheld (the progress under them too)
  val notesHeight = (LocalConfiguration.current.screenHeightDp * if (downloading) 0.3f else 0.4f).dp

  AlertDialog(
    onDismissRequest = { if (!downloading) onDismiss() },
    properties = DialogProperties(dismissOnClickOutside = !downloading),
    title = { Text(stringResource(R.string.update_title, release.version)) },
    text = {
      Column {
        Text(
          stringResource(R.string.update_installed, Updates.installedVersion(context)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().heightIn(max = notesHeight).verticalScroll(rememberScrollState())) {
          Text(
            notesText(release.notes.ifBlank { stringResource(R.string.update_no_notes) }),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        if (downloading) {
          if (progress >= 0f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
          } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
          }
          Text(
            if (progress >= 0f) stringResource(R.string.update_downloading_percent, (progress * 100).toInt())
            else stringResource(R.string.update_downloading),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
        error?.let {
          Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
      }
    },
    confirmButton = {
      if (!downloading) {
        TextButton(onClick = {
          error = null
          progress = -1f
          job = scope.launch {
            try {
              val apk = Updates.download(context, release) { done, total ->
                progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
              }
              Updates.install(context, apk)
              onDismiss()
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              error = context.getString(R.string.update_download_failed, e.message ?: e.javaClass.simpleName)
            } finally {
              job = null
            }
          }
        }) { Text(stringResource(if (error != null) R.string.update_retry else R.string.update_download)) }
      }
    },
    dismissButton = {
      TextButton(onClick = {
        if (downloading) {
          job?.cancel()
          job = null
        } else {
          onDismiss()
        }
      }) { Text(stringResource(if (downloading) android.R.string.cancel else R.string.update_later)) }
    },
  )
}

/**
 * Release notes' Markdown, plainly: headings in bold, list items with a bullet (indented by level),
 * **bold** spans bold, links as their text, code marks dropped.
 */
internal fun notesText(md: String): AnnotatedString = buildAnnotatedString {
  val heading = Regex("^#{1,6}\\s+")
  val item = Regex("^(\\s*)[-*+]\\s+")
  val lines = md.replace("\r\n", "\n").trim().lines()
  lines.forEachIndexed { i, raw ->
    val line = raw.trimEnd()
    val h = heading.find(line)
    val li = item.find(line)
    when {
      h != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(line.substring(h.range.last + 1)) }
      li != null -> {
        append("    ".repeat(li.groupValues[1].length / 2) + "•  ")
        inline(line.substring(li.range.last + 1))
      }
      else -> inline(line)
    }
    if (i < lines.lastIndex) append('\n')
  }
}

private fun AnnotatedString.Builder.inline(s: String) {
  val text = s.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1").replace("`", "")
  text.split("**").forEachIndexed { k, part ->
    if (k % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
  }
}
