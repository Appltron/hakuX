package com.rfandango.haku_x.ui.driver

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rfandango.haku_x.GpuDriverHelper
import com.rfandango.haku_x.GpuDriverHelper.AddError
import com.rfandango.haku_x.GpuDriverHelper.DriverMetadata
import com.rfandango.haku_x.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The GPU driver screen's state: the stored packages, the one in use, a change being made, a notice. */
class GpuDriverViewModel(app: Application) : AndroidViewModel(app) {
  data class State(
    val drivers: List<DriverMetadata> = emptyList(),
    /** The stored package in use; null: the system driver, or one without its package (orphan). */
    val inUse: String? = null,
    /** The driver in use when its package isn't stored (brought over from the previous release). */
    val orphan: DriverMetadata? = null,
    val loading: Boolean = true,
    /** A package being unpacked, added or deleted: the list waits. */
    val busy: Boolean = false,
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state
  private val _notice = MutableStateFlow<String?>(null)
  val notice: StateFlow<String?> = _notice

  init {
    GpuDriverHelper.init(app)
    viewModelScope.launch { refresh() }
  }

  fun noticeShown() {
    _notice.value = null
  }

  /** The list read again (each package's meta.json: off the main thread). */
  private suspend fun refresh() {
    val s = withContext(Dispatchers.IO) {
      val drivers = GpuDriverHelper.getAvailableDrivers()
      val inUse = drivers.firstOrNull { d -> d.path?.let { GpuDriverHelper.isInUse(File(it)) } == true }?.path
      val active = GpuDriverHelper.getActiveDriver()
      State(drivers, inUse, if (inUse == null) active else null, loading = false)
    }
    _state.value = s
  }

  private fun change(work: suspend () -> String?) {
    if (_state.value.busy) return
    _state.update { it.copy(busy = true) }
    viewModelScope.launch {
      val notice = work()
      refresh()
      _notice.value = notice
    }
  }

  private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

  fun useSystem() = change {
    withContext(Dispatchers.IO) { GpuDriverHelper.useSystemDriver() }
    str(R.string.gpu_driver_system_selected)
  }

  fun use(d: DriverMetadata) = change {
    val ok = withContext(Dispatchers.IO) { d.path?.let { GpuDriverHelper.useDriver(File(it)) } ?: false }
    if (ok) str(R.string.gpu_driver_selected, label(d)) else str(R.string.gpu_driver_err_unpack, label(d))
  }

  fun add(uri: Uri) = change {
    val r = withContext(Dispatchers.IO) { GpuDriverHelper.addDriver(getApplication(), uri) }
    val name = r.driver?.let(::label) ?: ""
    when (r.error) {
      null -> str(R.string.gpu_driver_added, name)
      AddError.UNREADABLE -> str(R.string.gpu_driver_err_unreadable)
      AddError.NOT_A_DRIVER -> str(R.string.gpu_driver_err_not_driver)
      AddError.NO_LIBRARY -> str(R.string.gpu_driver_err_no_library)
      AddError.API -> str(R.string.gpu_driver_err_api, r.driver?.minApi ?: 0, android.os.Build.VERSION.SDK_INT)
      AddError.UNPACK -> str(R.string.gpu_driver_err_unpack, name)
    }
  }

  fun delete(d: DriverMetadata) = change {
    val wasInUse = withContext(Dispatchers.IO) { d.path?.let { GpuDriverHelper.deleteDriver(File(it)) } ?: false }
    str(if (wasInUse) R.string.gpu_driver_deleted_in_use else R.string.gpu_driver_deleted, label(d))
  }

  /** The driver in use without its package: back to the system driver, the unpacked copy gone. */
  fun deleteOrphan() = change {
    val name = _state.value.orphan?.let(::label) ?: ""
    withContext(Dispatchers.IO) { GpuDriverHelper.useSystemDriver() }
    str(R.string.gpu_driver_deleted_in_use, name)
  }

  companion object {
    /** A driver's name, else its package's file name. */
    fun label(d: DriverMetadata) = d.name ?: File(d.path ?: "").nameWithoutExtension
  }
}
