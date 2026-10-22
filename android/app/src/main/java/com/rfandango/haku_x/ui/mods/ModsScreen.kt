package com.rfandango.haku_x.ui.mods

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rfandango.haku_x.R
import com.rfandango.haku_x.data.ModCatalog
import com.rfandango.haku_x.data.ModChoices
import com.rfandango.haku_x.ui.settings.Section

/**
 * One disc's mods and cheats (the repository's mods/ folder): each on or
 * off. Mods take the next start; cheats also turn on and off in the game's
 * menu while it runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen(gameKey: String, title: String, onBack: () -> Unit) {
  val context = LocalContext.current
  val game = remember(gameKey) { ModCatalog.load(context, gameKey) }
  val choices = remember(gameKey) { ModChoices(context, gameKey) }
  val mods = remember(gameKey) { mutableStateMapOf<String, Boolean>().apply { game.mods.forEach { put(it.id, choices.mod(it.id)) } } }
  val cheats = remember(gameKey) {
    mutableStateMapOf<String, Boolean>().apply { game.cheats.forEach { put(it.name, choices.cheat(it.name)) } }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(stringResource(R.string.mods_title))
            Text(
              title,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
      if (game.isEmpty) {
        Hint(stringResource(R.string.mods_none))
      }
      if (game.mods.isNotEmpty()) {
        Section(stringResource(R.string.mods_section_mods))
        Hint(stringResource(R.string.mods_mods_desc))
        for (mod in game.mods) {
          val on = mods[mod.id] == true
          val toggle = { v: Boolean ->
            choices.setMod(mod.id, v)
            mods[mod.id] = v
          }
          ListItem(
            headlineContent = { Text(mod.name) },
            supportingContent = {
              val by = if (mod.author.isNotEmpty()) stringResource(R.string.mods_by, mod.author) else ""
              val text = listOf(mod.description, by).filter { it.isNotEmpty() }.joinToString("\n")
              if (text.isNotEmpty()) Text(text)
            },
            trailingContent = { Switch(checked = on, onCheckedChange = toggle) },
          )
        }
      }
      if (game.cheats.isNotEmpty()) {
        Section(stringResource(R.string.mods_section_cheats))
        Hint(stringResource(R.string.mods_cheats_desc))
        for (cheat in game.cheats) {
          val on = cheats[cheat.name] == true
          ListItem(
            headlineContent = { Text(cheat.name) },
            supportingContent = if (cheat.note.isNotEmpty()) ({ Text(cheat.note) }) else null,
            trailingContent = {
              Switch(checked = on, onCheckedChange = { v ->
                choices.setCheat(cheat.name, v)
                cheats[cheat.name] = v
              })
            },
          )
        }
      }
    }
  }
}

@Composable
private fun Hint(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  )
}
