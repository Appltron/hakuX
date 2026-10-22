package com.rfandango.haku_x.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.R
import com.rfandango.haku_x.data.AppPrefs
import com.rfandango.haku_x.data.Storage

/**
 * First run: the games folder, the one thing the native core needs (it boots
 * titles with its own kernel and keeps the hard disk in app storage, so no
 * BIOS, flash or disk image is asked for).
 */
@Composable
fun SetupScreen(onDone: () -> Unit) {
  val context = LocalContext.current
  val prefs = remember { AppPrefs(context) }
  var folder by remember { mutableStateOf(prefs.gamesFolderUri) }
  val ready = remember(folder) { Storage.isFolderReady(context, folder) }

  val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
    if (uri != null) {
      Storage.persistPermission(context, uri)
      prefs.gamesFolderUri = uri
      folder = uri
    }
  }

  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(
      Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(
          stringResource(R.string.setup_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
          Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.setup_disc_title), style = MaterialTheme.typography.titleLarge)
            Text(
              stringResource(R.string.setup_disc_body),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              stringResource(
                R.string.setup_disc_value,
                folder?.let { Storage.folderLabel(context, it) } ?: stringResource(R.string.setup_not_set),
              ),
              style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { pickFolder.launch(folder) }) {
              Text(stringResource(R.string.setup_pick_disc))
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        Button(
          onClick = {
            prefs.setupComplete = true
            onDone()
          },
          enabled = ready,
          modifier = Modifier.align(Alignment.End),
        ) {
          Text(stringResource(R.string.setup_finish))
        }
      }
    }
  }
}
