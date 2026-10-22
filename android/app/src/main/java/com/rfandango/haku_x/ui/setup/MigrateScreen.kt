package com.rfandango.haku_x.ui.setup

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfandango.haku_x.R

/**
 * The first start after the update from the xemu-based release: what of its data comes along (the
 * games folder and GPU driver as they are, the BIOS kept for imports), and its Xbox hard disk brought
 * into the native core's, its saves re-signed. Once, then the setup (no games folder) or the library.
 */
@Composable
fun MigrateScreen(onDone: () -> Unit, vm: MigrateViewModel = viewModel()) {
  val context = LocalContext.current
  val phase by vm.phase.collectAsStateWithLifecycle()
  var bringDisk by rememberSaveable { mutableStateOf(true) }
  val size = { b: Long -> Formatter.formatShortFileSize(context, b) }

  // nothing of the previous release after all: on to the app
  LaunchedEffect(phase) {
    val p = phase
    if (p is MigrateViewModel.Phase.Done && p.plan == null) {
      vm.finish()
      onDone()
    }
  }

  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(
      Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.migrate_title), style = MaterialTheme.typography.headlineMedium)
        when (val p = phase) {
          MigrateViewModel.Phase.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

          is MigrateViewModel.Phase.Ready -> {
            val plan = p.plan
            Text(
              stringResource(R.string.migrate_subtitle),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Item(stringResource(R.string.migrate_hdd_title)) {
              Text(
                if (plan.saves != null) {
                  pluralStringResource(R.plurals.migrate_hdd_saves, plan.saves, plan.saves, plan.titles ?: 0, size(plan.image.size))
                } else {
                  stringResource(R.string.migrate_hdd_image, size(plan.image.size))
                }
              )
              Note(stringResource(if (plan.keys) R.string.migrate_hdd_resign else R.string.migrate_hdd_nokeys))
              if (plan.nativeSaves > 0) {
                Note(pluralStringResource(R.plurals.migrate_hdd_replaces, plan.nativeSaves, plan.nativeSaves))
              }
              Row(
                Modifier.fillMaxWidth().clickable { bringDisk = !bringDisk },
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(checked = bringDisk, onCheckedChange = { bringDisk = it })
                Text(stringResource(R.string.migrate_hdd_bring))
              }
            }
            Item(stringResource(R.string.migrate_folder_title)) {
              Text(plan.folders.joinToString("\n").ifEmpty { stringResource(R.string.migrate_folder_lost) })
            }
            Item(stringResource(R.string.migrate_driver_title)) {
              Text(plan.driver?.let { stringResource(R.string.migrate_driver_kept, it) }
                     ?: stringResource(R.string.migrate_driver_system))
            }
            Item(stringResource(R.string.migrate_bios_title)) {
              Text(stringResource(R.string.migrate_bios_body))
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              // nothing to bring over: straight on
              val leave = {
                vm.finish()
                onDone()
              }
              TextButton(onClick = leave) { Text(stringResource(R.string.migrate_skip)) }
              Button(onClick = { if (bringDisk) vm.start() else leave() }) { Text(stringResource(R.string.migrate_go)) }
            }
          }

          is MigrateViewModel.Phase.Running -> {
            Text(stringResource(R.string.migrate_running), style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(
              progress = { if (p.total > 0) (p.done.toFloat() / p.total).coerceIn(0f, 1f) else 0f },
              modifier = Modifier.fillMaxWidth(),
            )
            Text(p.what, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }

          is MigrateViewModel.Phase.Done -> if (p.plan != null) {
            if (p.error != null) {
              Text(stringResource(R.string.migrate_failed, p.error))
              Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                  vm.finish()
                  onDone()
                }) { Text(stringResource(R.string.migrate_skip)) }
                Button(onClick = { vm.start() }) { Text(stringResource(R.string.migrate_retry)) }
              }
            } else {
              Text(stringResource(R.string.migrate_done), style = MaterialTheme.typography.bodyLarge)
              p.outcome?.let { o ->
                Text(pluralStringResource(R.plurals.hdd_job_summary, o.result.files, o.result.files, size(o.result.bytes)))
                o.resign?.let { rep ->
                  Text(pluralStringResource(R.plurals.hdd_resigned, rep.resigned, rep.resigned))
                  if (rep.unsigned.isNotEmpty()) Note(pluralStringResource(R.plurals.hdd_resign_unsigned, rep.unsigned.size, rep.unsigned.size))
                  if (rep.noKey.isNotEmpty()) Note(stringResource(R.string.hdd_resign_nokey, rep.noKey.joinToString(", ") { it.uppercase() }))
                }
              }
              Button(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.migrate_finish)) }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Item(title: String, content: @Composable () -> Unit) {
  OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}

@Composable
private fun Note(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
