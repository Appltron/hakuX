package com.rfandango.haku_x

import android.app.Application
import com.rfandango.haku_x.data.Logs
import java.io.File

class HakuXApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    /* The main process's start is an app session's (data/Logs.kt); a game
     * process (":native") starts its own log in NativeCoreActivity. */
    if (getProcessName() == packageName) {
      Logs.startApp(this)
      dropPerfMaps()
    }
  }

  /* Earlier cores wrote a JIT symbol map (perf-<pid>.map, tens of MB) into
   * the files folder at every launch and never removed it: 3.3 GB on one
   * device. Gone at the app's start, but for a process still running. */
  private fun dropPerfMaps() = Thread {
    filesDir.listFiles { _, name -> name.startsWith("perf-") && name.endsWith(".map") }?.forEach { f ->
      val pid = f.name.removePrefix("perf-").removeSuffix(".map")
      if (!File("/proc/$pid").exists()) f.delete()
    }
  }.start()
}
