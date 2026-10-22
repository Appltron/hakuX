package com.rfandango.haku_x.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rfandango.haku_x.GpuDriverHelper
import com.rfandango.haku_x.R
import com.rfandango.haku_x.data.AppPrefs
import com.rfandango.haku_x.data.PreviousVersion
import com.rfandango.haku_x.data.Storage
import com.rfandango.haku_x.data.hdd.ConsoleFiles
import com.rfandango.haku_x.data.hdd.DiskImport
import com.rfandango.haku_x.data.hdd.HddStore
import com.rfandango.haku_x.data.hdd.XboxHdd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The first start after the update from the xemu-based release: what it left (PreviousVersion), and
 * its hard disk brought over (DiskImport, its saves re-signed with its BIOS's and EEPROM's keys).
 */
class MigrateViewModel(app: Application) : AndroidViewModel(app) {
  /** What the previous release left, as the screen shows it. */
  class Plan(
    val image: PreviousVersion.Image,
    /** Its disk's titles with saves and saves (null: unread). */
    val titles: Int?,
    val saves: Int?,
    /** The games folders it can still reach, where each is ("Internal storage › ROMs/xbox"). */
    val folders: List<String>,
    /** The custom GPU driver installed, or null: the system's. */
    val driver: String?,
    /** The BIOS and EEPROM it ran give the keys to re-sign its saves. */
    val keys: Boolean,
    /** Saves on the native core's disk already, which the disk replaces. */
    val nativeSaves: Int,
  )

  sealed interface Phase {
    object Loading : Phase
    class Ready(val plan: Plan) : Phase
    class Running(val plan: Plan, val done: Long = 0, val total: Long = 1, val what: String = "") : Phase
    /** Finished: the disk's outcome (null: not brought over), or why it failed. */
    class Done(val outcome: DiskImport.Outcome?, val error: String? = null, val plan: Plan? = null) : Phase
  }

  private val _phase = MutableStateFlow<Phase>(Phase.Loading)
  val phase: StateFlow<Phase> = _phase

  init {
    viewModelScope.launch {
      _phase.value = withContext(Dispatchers.IO) { load() }
    }
  }

  private fun load(): Phase {
    val app = getApplication<Application>()
    val found = PreviousVersion(app).find() ?: return Phase.Done(null)
    // its dumps, kept as the console files (the keys its saves are re-signed with)
    val console = ConsoleFiles(app)
    found.mcpx?.let { console.adopt(ConsoleFiles.Kind.MCPX, it) }
    found.flash?.let { console.adopt(ConsoleFiles.Kind.BIOS, it) }
    found.eeprom?.let { console.adopt(ConsoleFiles.Kind.EEPROM, it) }
    val counts = runCatching { DiskImport.open(app, found.hdd.file, found.hdd.uri) { XboxHdd.saves(it) } }.getOrNull()
    val folders = AppPrefs(app).gamesFolders.filter { Storage.hasReadPermission(app, it) }.map { Storage.folderPlace(app, it) }
    GpuDriverHelper.init(app)
    return Phase.Ready(Plan(
      found.hdd, counts?.first, counts?.second, folders, GpuDriverHelper.getInstalledDriverName(),
      console.status().ready, HddStore(app).saves().sumOf { it.saves.size },
    ))
  }

  /** The disk brought over; the screen shows the outcome. */
  fun start() {
    val plan = when (val p = _phase.value) {
      is Phase.Ready -> p.plan
      is Phase.Done -> p.plan ?: return
      else -> return
    }
    _phase.value = Phase.Running(plan)
    val app = getApplication<Application>()
    viewModelScope.launch {
      val r = withContext(Dispatchers.IO) {
        runCatching {
          DiskImport.open(app, plan.image.file, plan.image.uri) { image ->
            DiskImport.run(app, image, ConsoleFiles(app).resigner(), { d, t, w ->
              _phase.update { (it as? Phase.Running)?.let { r -> Phase.Running(r.plan, d, t, w) } ?: it }
            }) {
              _phase.update { (it as? Phase.Running)?.let { r -> Phase.Running(r.plan, r.total, r.total, app.getString(R.string.hdd_resigning)) } ?: it }
            }
          }
        }
      }
      r.onSuccess { finish() }
      _phase.value = Phase.Done(r.getOrNull(), r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }, plan)
    }
  }

  /** The screen won't show again. */
  fun finish() {
    AppPrefs(getApplication()).previousMigrated = true
  }
}
