package com.rfandango.haku_x.ui.driver

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfandango.haku_x.GpuDriverHelper.DriverMetadata
import com.rfandango.haku_x.R
import com.rfandango.haku_x.ui.settings.Section

/**
 * The Vulkan driver the Xbox's GPU runs on: the phone's own, or one of the stored packages (Turnip's
 * releases, say), each removable, and packages added from a ZIP. The choice is read at game start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuDriverScreen(onBack: () -> Unit, vm: GpuDriverViewModel = viewModel()) {
  val s by vm.state.collectAsStateWithLifecycle()
  val notice by vm.notice.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var deleting by remember { mutableStateOf<DriverMetadata?>(null) }
  var deletingOrphan by remember { mutableStateOf(false) }
  LaunchedEffect(notice) {
    // cleared once shown: clearing it first changes the key, which cancels the snackbar
    snackbar.showSnackbar(notice ?: return@LaunchedEffect)
    vm.noticeShown()
  }
  val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) vm.add(uri)
  }
  val enabled = !s.loading && !s.busy

  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_gpu_driver_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        },
      )
    },
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      // the list waits while a package is unpacked, added or deleted
      Box(Modifier.fillMaxWidth().height(4.dp)) {
        if (s.loading || s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      }
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
          stringResource(R.string.gpu_driver_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Section(stringResource(R.string.gpu_driver_section))
        DriverRow(
          title = stringResource(R.string.gpu_driver_system_choice),
          detail = stringResource(R.string.gpu_driver_system_desc),
          selected = !s.loading && s.inUse == null && s.orphan == null,
          enabled = enabled,
          onSelect = vm::useSystem,
          onDelete = null,
        )
        s.orphan?.let { o ->
          DriverRow(
            title = GpuDriverViewModel.label(o),
            detail = (listOfNotNull(details(o)) + stringResource(R.string.gpu_driver_no_package)).joinToString("\n"),
            selected = true,
            enabled = enabled,
            onSelect = {},
            onDelete = { deletingOrphan = true },
          )
        }
        for (d in s.drivers) {
          DriverRow(
            title = GpuDriverViewModel.label(d),
            detail = details(d),
            selected = d.path == s.inUse,
            enabled = enabled,
            onSelect = { if (d.path != s.inUse) vm.use(d) },
            onDelete = { deleting = d },
          )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        ListItem(
          headlineContent = { Text(stringResource(R.string.gpu_driver_add)) },
          supportingContent = { Text(stringResource(R.string.gpu_driver_add_desc)) },
          leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
          modifier = Modifier.clickable(enabled = enabled) {
            pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
          },
        )
      }
    }
  }

  deleting?.let { d ->
    val name = GpuDriverViewModel.label(d)
    DeleteDialog(
      text = stringResource(
        if (d.path == s.inUse) R.string.gpu_driver_delete_text_in_use else R.string.gpu_driver_delete_text, name,
      ),
      onConfirm = { deleting = null; vm.delete(d) },
      onDismiss = { deleting = null },
    )
  }
  if (deletingOrphan) {
    DeleteDialog(
      text = stringResource(R.string.gpu_driver_delete_text_in_use, s.orphan?.let(GpuDriverViewModel::label) ?: ""),
      onConfirm = { deletingOrphan = false; vm.deleteOrphan() },
      onDismiss = { deletingOrphan = false },
    )
  }
}

/** A driver's version, vendor and author, as its package names them, on one line; null: none named. */
@Composable
private fun details(d: DriverMetadata): String? {
  val parts = listOfNotNull(
    d.driverVersion?.let { stringResource(R.string.gpu_driver_version, it) },
    d.vendor,
    d.author?.let { stringResource(R.string.gpu_driver_by, it) },
  )
  val line = parts.joinToString(" · ").ifEmpty { null }
  return listOfNotNull(d.description, line).joinToString("\n").ifEmpty { null }
}

/** One driver: picked with its radio button (the one in use marked), deleted with its button. */
@Composable
private fun DriverRow(
  title: String,
  detail: String?,
  selected: Boolean,
  enabled: Boolean,
  onSelect: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = detail?.let { { Text(it) } },
    leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
    trailingContent = onDelete?.let {
      {
        IconButton(onClick = it, enabled = enabled) {
          Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.gpu_driver_delete))
        }
      }
    },
    modifier = Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
  )
}

@Composable
private fun DeleteDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.gpu_driver_delete)) },
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.gpu_driver_delete_confirm)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
  )
}
