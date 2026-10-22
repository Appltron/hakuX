package com.rfandango.haku_x.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.GpuDriverHelper
import com.rfandango.haku_x.NativeCoreActivity
import com.rfandango.haku_x.R
import com.rfandango.haku_x.data.AppPrefs
import com.rfandango.haku_x.data.Logs
import com.rfandango.haku_x.data.PreviousVersion
import com.rfandango.haku_x.data.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the core reads at game start, plus the games folders the library scans. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onEditControls: () -> Unit,
  onControllers: () -> Unit,
  onHardDisk: () -> Unit,
  /** The first-start screen again: the xemu-based release's data brought over. */
  onMigrate: () -> Unit,
) {
  val context = LocalContext.current
  val prefs = remember { AppPrefs(context) }
  // the xemu-based release left its data here: its migration can run again
  val previous by produceState(false) { value = withContext(Dispatchers.IO) { PreviousVersion(context).find() != null } }
  val scope = rememberCoroutineScope()

  var folders by remember { mutableStateOf(prefs.gamesFolders) }
  var replacing by remember { mutableStateOf<Uri?>(null) }     /* the folder a pick replaces; null: adds */
  var removing by remember { mutableStateOf<Uri?>(null) }
  var uncapped by remember { mutableStateOf(prefs.uncapped) }
  var asyncShaders by remember { mutableStateOf(prefs.asyncShaders) }
  var prebuildShaders by remember { mutableStateOf(prefs.prebuildShaders) }
  var showFps by remember { mutableStateOf(prefs.showFps) }
  var scale by remember { mutableIntStateOf(prefs.renderScale) }
  var aspect by remember { mutableStateOf(prefs.aspectRatio) }
  var fxaa by remember { mutableStateOf(prefs.fxaa) }
  var upscale by remember { mutableStateOf(prefs.upscaleFilter) }
  var upscaleDialog by remember { mutableStateOf(false) }
  var format by remember { mutableStateOf(prefs.screenFormat) }
  var p480 by remember { mutableStateOf(prefs.video480p) }
  var p720 by remember { mutableStateOf(prefs.video720p) }
  var i1080 by remember { mutableStateOf(prefs.video1080i) }
  var pal60 by remember { mutableStateOf(prefs.pal60) }
  var avPack by remember { mutableStateOf(prefs.avPack) }
  var language by remember { mutableStateOf(prefs.language) }
  var languageDialog by remember { mutableStateOf(false) }
  var formatDialog by remember { mutableStateOf(false) }
  var cableDialog by remember { mutableStateOf(false) }
  var aspectDialog by remember { mutableStateOf(false) }
  var driver by remember { mutableStateOf(driverLabel(context)) }
  var scaleDialog by remember { mutableStateOf(false) }
  var driverDialog by remember { mutableStateOf(false) }

  val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
    val old = replacing
    replacing = null
    if (uri != null) {
      Storage.persistPermission(context, uri)
      prefs.gamesFolders = if (old != null) folders.map { if (it == old) uri else it } else folders + uri
      folders = prefs.gamesFolders
      if (old != null && old !in folders) {
        Storage.releasePermission(context, old)
      }
    }
  }
  /* The folder the logs' ZIP goes to (data/Logs.kt). */
  val pickLogsFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
    if (uri != null) {
      Toast.makeText(context, R.string.settings_export_logs_working, Toast.LENGTH_SHORT).show()
      scope.launch {
        val name = withContext(Dispatchers.IO) { Logs.export(context, uri) }
        Toast.makeText(
          context,
          if (name != null) context.getString(R.string.settings_export_logs_done, name)
          else context.getString(R.string.settings_export_logs_failed),
          Toast.LENGTH_LONG,
        ).show()
      }
    }
  }
  /* A driver ZIP picked from storage (Turnip builds: meta.json and the .so). */
  val pickDriverZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) {
      scope.launch {
        val ok = withContext(Dispatchers.IO) { GpuDriverHelper.installDriverFromUri(context, uri) }
        Toast.makeText(
          context,
          if (ok) R.string.settings_gpu_driver_installed else R.string.settings_gpu_driver_install_failed,
          Toast.LENGTH_SHORT,
        ).show()
        driver = driverLabel(context)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
      Section(stringResource(R.string.settings_section_games))
      for (f in folders) {
        ListItem(
          headlineContent = { Text(Storage.folderLabel(context, f)) },
          supportingContent = { Text(Storage.folderPlace(context, f)) },
          trailingContent = if (folders.size > 1) {
            {
              IconButton(onClick = { removing = f }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_games_folder_remove))
              }
            }
          } else {
            null
          },
          modifier = Modifier.clickable {
            replacing = f
            pickFolder.launch(f)
          },
        )
      }
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_games_folder_add)) },
        supportingContent = { Text(stringResource(R.string.settings_games_folder_add_desc)) },
        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
        modifier = Modifier.clickable {
          replacing = null
          pickFolder.launch(folders.lastOrNull())
        },
      )

      Section(stringResource(R.string.settings_section_controls))
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_overlay)) },
        supportingContent = { Text(stringResource(R.string.settings_overlay_desc)) },
        modifier = Modifier.clickable(onClick = onEditControls),
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.controllers_title)) },
        supportingContent = { Text(stringResource(R.string.controllers_settings_desc)) },
        modifier = Modifier.clickable(onClick = onControllers),
      )

      Section(stringResource(R.string.settings_section_display))
      SwitchItem(
        title = stringResource(R.string.settings_uncap),
        summary = stringResource(R.string.settings_uncap_desc) + " " +
          stringResource(R.string.settings_applies_next_start),
        checked = uncapped,
        onChange = { uncapped = it; prefs.uncapped = it },
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_aspect)) },
        supportingContent = {
          Text(stringResource(aspectLabel(aspect)) + ". " + stringResource(R.string.settings_applies_next_start))
        },
        modifier = Modifier.clickable { aspectDialog = true },
      )
      SwitchItem(
        title = stringResource(R.string.settings_show_fps),
        summary = stringResource(R.string.settings_show_fps_desc),
        checked = showFps,
        onChange = { showFps = it; prefs.showFps = it },
      )

      Section(stringResource(R.string.settings_section_console))
      ListItem(
        headlineContent = { Text(stringResource(R.string.hdd_title)) },
        supportingContent = { Text(stringResource(R.string.hdd_settings_desc)) },
        modifier = Modifier.clickable(onClick = onHardDisk),
      )
      if (previous) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_migrate)) },
          supportingContent = { Text(stringResource(R.string.settings_migrate_desc)) },
          modifier = Modifier.clickable(onClick = onMigrate),
        )
      }
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_language)) },
        supportingContent = {
          Text(
            languageLabel(language) + ". " + stringResource(R.string.settings_language_desc)
          )
        },
        modifier = Modifier.clickable { languageDialog = true },
      )

      Section(stringResource(R.string.settings_section_console_video))
      Text(
        stringResource(R.string.settings_console_video_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_screen_format)) },
        supportingContent = { Text(stringResource(formatLabel(format))) },
        modifier = Modifier.clickable { formatDialog = true },
      )
      SwitchItem(
        title = stringResource(R.string.settings_480p),
        summary = stringResource(R.string.settings_480p_desc),
        checked = p480,
        onChange = { p480 = it; prefs.video480p = it },
      )
      SwitchItem(
        title = stringResource(R.string.settings_720p),
        summary = stringResource(R.string.settings_720p_desc),
        checked = p720,
        onChange = { p720 = it; prefs.video720p = it },
      )
      SwitchItem(
        title = stringResource(R.string.settings_1080i),
        summary = stringResource(R.string.settings_1080i_desc),
        checked = i1080,
        onChange = { i1080 = it; prefs.video1080i = it },
      )
      SwitchItem(
        title = stringResource(R.string.settings_pal60),
        summary = stringResource(R.string.settings_pal60_desc),
        checked = pal60,
        onChange = { pal60 = it; prefs.pal60 = it },
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_av_cable)) },
        supportingContent = { Text(stringResource(cableLabel(avPack))) },
        modifier = Modifier.clickable { cableDialog = true },
      )

      Section(stringResource(R.string.settings_section_graphics))
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_scale)) },
        supportingContent = {
          Text(
            stringResource(R.string.settings_scale_value, scale) + ". " +
              stringResource(R.string.settings_applies_next_start)
          )
        },
        modifier = Modifier.clickable { scaleDialog = true },
      )
      SwitchItem(
        title = stringResource(R.string.settings_fxaa),
        summary = stringResource(R.string.settings_fxaa_desc) + " " +
          stringResource(R.string.settings_applies_next_start),
        checked = fxaa,
        onChange = { fxaa = it; prefs.fxaa = it },
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_upscale)) },
        supportingContent = {
          Text(
            stringResource(upscaleLabel(upscale)) + ". " + stringResource(R.string.settings_upscale_desc) + " " +
              stringResource(R.string.settings_applies_next_start)
          )
        },
        modifier = Modifier.clickable { upscaleDialog = true },
      )
      SwitchItem(
        title = stringResource(R.string.settings_async_shaders),
        summary = stringResource(R.string.settings_async_shaders_desc) + " " +
          stringResource(R.string.settings_applies_next_start),
        checked = asyncShaders,
        onChange = { asyncShaders = it; prefs.asyncShaders = it },
      )
      SwitchItem(
        title = stringResource(R.string.settings_prebuild_shaders),
        summary = stringResource(R.string.settings_prebuild_shaders_desc) + " " +
          stringResource(R.string.settings_applies_next_start),
        checked = prebuildShaders,
        onChange = { prebuildShaders = it; prefs.prebuildShaders = it },
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_gpu_driver_title)) },
        supportingContent = { Text(driver) },
        modifier = Modifier.clickable {
          if (GpuDriverHelper.supportsCustomDriverLoading()) {
            driverDialog = true
          } else {
            Toast.makeText(context, R.string.settings_gpu_driver_not_supported, Toast.LENGTH_SHORT).show()
          }
        },
      )

      Section(stringResource(R.string.settings_section_logs))
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_export_logs)) },
        supportingContent = { Text(stringResource(R.string.settings_export_logs_desc)) },
        modifier = Modifier.clickable { pickLogsFolder.launch(null) },
      )
    }
  }

  if (languageDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_language),
      options = LANGUAGES.map { it to languageLabel(it) },
      current = language,
      onPick = { language = it; prefs.language = it; languageDialog = false },
      onDismiss = { languageDialog = false },
    )
  }
  if (formatDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_screen_format),
      options = FORMATS.map { it to stringResource(formatLabel(it)) },
      current = format,
      onPick = { format = it; prefs.screenFormat = it; formatDialog = false },
      onDismiss = { formatDialog = false },
    )
  }
  if (cableDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_av_cable),
      options = CABLES.map { it to stringResource(cableLabel(it)) },
      current = avPack,
      onPick = { avPack = it; prefs.avPack = it; cableDialog = false },
      onDismiss = { cableDialog = false },
    )
  }
  if (aspectDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_aspect),
      options = ASPECTS.map { it to stringResource(aspectLabel(it)) },
      current = aspect,
      onPick = { aspect = it; prefs.aspectRatio = it; aspectDialog = false },
      onDismiss = { aspectDialog = false },
    )
  }
  if (upscaleDialog) {
    ChoiceDialog(
      title = stringResource(R.string.settings_upscale),
      options = UPSCALERS.map { it to stringResource(upscaleLabel(it)) },
      current = upscale,
      onPick = { upscale = it; prefs.upscaleFilter = it; upscaleDialog = false },
      onDismiss = { upscaleDialog = false },
    )
  }
  if (scaleDialog) {
    ScaleDialog(
      current = scale,
      onPick = { scale = it; prefs.renderScale = it; scaleDialog = false },
      onDismiss = { scaleDialog = false },
    )
  }
  if (driverDialog) {
    DriverDialog(
      onDismiss = { driverDialog = false },
      onInstalled = { driver = driverLabel(context) },
      onInstallZip = {
        driverDialog = false
        pickDriverZip.launch(arrayOf("application/zip", "application/octet-stream"))
      },
    )
  }
  removing?.let { f ->
    AlertDialog(
      onDismissRequest = { removing = null },
      title = { Text(stringResource(R.string.settings_games_folder_remove)) },
      text = {
        Text(stringResource(R.string.settings_games_folder_remove_text, Storage.folderPlace(context, f)))
      },
      confirmButton = {
        TextButton(onClick = {
          prefs.gamesFolders = folders - f
          folders = prefs.gamesFolders
          Storage.releasePermission(context, f)
          removing = null
        }) { Text(stringResource(R.string.settings_games_folder_remove_confirm)) }
      },
      dismissButton = {
        TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) }
      },
    )
  }
}

@Composable
internal fun Section(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
  )
}

@Composable
internal fun SwitchItem(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(summary) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    modifier = Modifier.selectable(selected = checked, role = Role.Switch) { onChange(!checked) },
  )
}

internal val ASPECTS = listOf("auto", "4:3", "16:9", "stretch")

internal val UPSCALERS = listOf(NativeCoreActivity.UPSCALE_BILINEAR, NativeCoreActivity.UPSCALE_SGSR)
internal val FORMATS = listOf(AppPrefs.FORMAT_NORMAL, AppPrefs.FORMAT_LETTERBOX, AppPrefs.FORMAT_WIDESCREEN)
internal val CABLES = listOf(AppPrefs.AV_HDTV, AppPrefs.AV_STANDARD, AppPrefs.AV_SVIDEO)
internal val LANGUAGES = listOf(AppPrefs.LANG_AUTO) + AppPrefs.XBOX_LANGUAGES

/* Each language in its own words, as the dashboard lists them. */
private val LANGUAGE_NAMES = mapOf(
  "en" to "English",
  "ja" to "日本語",
  "de" to "Deutsch",
  "fr" to "Français",
  "es" to "Español",
  "it" to "Italiano",
  "ko" to "한국어",
  "zh" to "中文（繁體）",
  "pt" to "Português",
)

@Composable
internal fun languageLabel(value: String) =
  if (value == AppPrefs.LANG_AUTO) {
    stringResource(R.string.settings_language_auto, LANGUAGE_NAMES.getValue(AppPrefs.autoLanguage()))
  } else {
    LANGUAGE_NAMES[value] ?: value
  }

internal fun formatLabel(value: String) = when (value) {
  AppPrefs.FORMAT_LETTERBOX -> R.string.settings_format_letterbox
  AppPrefs.FORMAT_WIDESCREEN -> R.string.settings_format_widescreen
  else -> R.string.settings_format_normal
}

internal fun cableLabel(value: String) = when (value) {
  AppPrefs.AV_STANDARD -> R.string.settings_av_standard
  AppPrefs.AV_SVIDEO -> R.string.settings_av_svideo
  else -> R.string.settings_av_hdtv
}

internal fun upscaleLabel(value: String) = when (value) {
  NativeCoreActivity.UPSCALE_SGSR -> R.string.settings_upscale_sgsr
  else -> R.string.settings_upscale_bilinear
}

internal fun aspectLabel(value: String) = when (value) {
  "4:3" -> R.string.settings_aspect_4_3
  "16:9" -> R.string.settings_aspect_16_9
  "stretch" -> R.string.settings_aspect_stretch
  else -> R.string.settings_aspect_auto
}

/** A choice of one, as radio buttons. */
@Composable
internal fun <T> ChoiceDialog(
  title: String,
  options: List<Pair<T, String>>,
  current: T,
  onPick: (T) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        for ((value, label) in options) {
          Row(
            Modifier
              .fillMaxWidth()
              .selectable(selected = value == current, role = Role.RadioButton) { onPick(value) }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = value == current, onClick = null)
            Text(label, modifier = Modifier.padding(start = 12.dp))
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
  )
}

@Composable
private fun ScaleDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_scale)) },
    text = {
      Column {
        for (s in 1..4) {
          Row(
            Modifier
              .fillMaxWidth()
              .selectable(selected = s == current, role = Role.RadioButton) { onPick(s) }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = s == current, onClick = null)
            Text(
              if (s == 1) stringResource(R.string.settings_scale_native) else "${s}x",
              modifier = Modifier.padding(start = 12.dp),
            )
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
  )
}

/** One choice among the system's driver and the stored ones (the active one
 * marked), or installing another from a ZIP. The stored ZIPs stay, so going
 * back from the system driver is one tap. */
@Composable
private fun DriverDialog(onDismiss: () -> Unit, onInstalled: () -> Unit, onInstallZip: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val drivers = remember { GpuDriverHelper.getAvailableDrivers() }
  val active = remember { GpuDriverHelper.getInstalledDriverName() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_gpu_driver_select_title)) },
    text = {
      Column {
        DriverChoice(
          label = stringResource(R.string.settings_gpu_driver_system_choice),
          description = null,
          selected = active.isNullOrEmpty(),
        ) {
          GpuDriverHelper.installDefaultDriver()
          onInstalled()
          onDismiss()
        }
        for (d in drivers) {
          DriverChoice(
            label = d.name ?: File(d.path ?: "").nameWithoutExtension,
            description = d.description,
            selected = !active.isNullOrEmpty() && d.name == active,
          ) {
            onDismiss()
            scope.launch {
              val ok = withContext(Dispatchers.IO) { d.path?.let { GpuDriverHelper.installDriver(File(it)) } ?: false }
              if (!ok) {
                Toast.makeText(context, R.string.settings_gpu_driver_install_failed, Toast.LENGTH_SHORT).show()
              }
              onInstalled()
            }
          }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_gpu_driver_install)) },
          modifier = Modifier.clickable(onClick = onInstallZip),
        )
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
  )
}

@Composable
private fun DriverChoice(label: String, description: String?, selected: Boolean, onPick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Column(Modifier.padding(start = 12.dp)) {
      Text(label)
      if (!description.isNullOrEmpty()) {
        Text(description, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

private fun driverLabel(context: android.content.Context): String {
  GpuDriverHelper.init(context)
  val name = GpuDriverHelper.getInstalledDriverName()
  return when {
    !GpuDriverHelper.supportsCustomDriverLoading() -> context.getString(R.string.settings_gpu_driver_not_supported)
    name.isNullOrEmpty() -> context.getString(R.string.settings_gpu_driver_system)
    else -> context.getString(R.string.settings_gpu_driver_active, name) + ". " +
      context.getString(R.string.settings_applies_next_start)
  }
}
