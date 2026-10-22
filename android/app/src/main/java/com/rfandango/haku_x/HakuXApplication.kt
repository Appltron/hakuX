package com.rfandango.haku_x

import android.app.Application
import com.rfandango.haku_x.data.Logs

class HakuXApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    /* The main process's start is an app session's (data/Logs.kt); a game
     * process (":native") starts its own log in NativeCoreActivity. */
    if (getProcessName() == packageName) {
      Logs.startApp(this)
    }
  }
}
