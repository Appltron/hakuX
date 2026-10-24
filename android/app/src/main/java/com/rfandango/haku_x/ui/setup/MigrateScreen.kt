package com.rfandango.haku_x.ui.setup

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfandango.haku_x.R
import com.rfandango.haku_x.ui.common.CheckItem
import com.rfandango.haku_x.ui.common.OnboardingCard
import com.rfandango.haku_x.ui.common.OnboardingLead
import com.rfandango.haku_x.ui.common.OnboardingNote
import com.rfandango.haku_x.ui.common.OnboardingPage
import com.rfandango.haku_x.ui.common.ProgressBlock
import com.rfandango.haku_x.ui.common.fractionOf

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

  OnboardingPage(stringResource(R.string.migrate_title)) {
    when (val p = phase) {
      MigrateViewModel.Phase.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

      is MigrateViewModel.Phase.Ready -> {
        val plan = p.plan
        OnboardingLead(stringResource(R.string.migrate_subtitle))
        OnboardingCard(stringResource(R.string.migrate_hdd_title)) {
          Text(
            if (plan.saves != null) {
              pluralStringResource(R.plurals.migrate_hdd_saves, plan.saves, plan.saves, plan.titles ?: 0, size(plan.image.size))
            } else {
              stringResource(R.string.migrate_hdd_image, size(plan.image.size))
            }
          )
          OnboardingNote(stringResource(if (plan.keys) R.string.migrate_hdd_resign else R.string.migrate_hdd_nokeys))
          if (plan.nativeSaves > 0) {
            OnboardingNote(pluralStringResource(R.plurals.migrate_hdd_replaces, plan.nativeSaves, plan.nativeSaves))
          }
          CheckItem(stringResource(R.string.migrate_hdd_bring), null, bringDisk) { bringDisk = it }
        }
        OnboardingCard(stringResource(R.string.migrate_folder_title)) {
          Text(plan.folders.joinToString("\n").ifEmpty { stringResource(R.string.migrate_folder_lost) })
        }
        OnboardingCard(stringResource(R.string.migrate_driver_title)) {
          Text(plan.driver?.let { stringResource(R.string.migrate_driver_kept, it) }
                 ?: stringResource(R.string.migrate_driver_system))
        }
        OnboardingCard(stringResource(R.string.migrate_bios_title)) {
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
        ProgressBlock(fractionOf(p.done, p.total), p.what)
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
              if (rep.unsigned.isNotEmpty()) {
                OnboardingNote(pluralStringResource(R.plurals.hdd_resign_unsigned, rep.unsigned.size, rep.unsigned.size))
              }
              if (rep.noKey.isNotEmpty()) {
                OnboardingNote(stringResource(R.string.hdd_resign_nokey, rep.noKey.joinToString(", ") { it.uppercase() }))
              }
            }
          }
          Button(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.migrate_finish)) }
        }
      }
    }
  }
}
