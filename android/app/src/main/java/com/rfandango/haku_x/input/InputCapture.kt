package com.rfandango.haku_x.input

import android.view.InputEvent

/**
 * Controllers' events in the app's own screens (LauncherActivity hands each
 * key and motion event here first): the controller mapping screen watches
 * them for its test panel and takes them while it captures a binding. The
 * listener returns true to take the event.
 */
object InputCapture {
  var listener: ((InputEvent) -> Boolean)? = null

  fun dispatch(e: InputEvent): Boolean = listener?.invoke(e) ?: false
}
