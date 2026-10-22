package com.rfandango.haku_x.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rfandango.haku_x.OnScreenController
import com.rfandango.haku_x.R
import kotlin.math.roundToInt

/**
 * The layout editor's bar over the controller: the selected control's size
 * and visibility, the controls' opacity, and reset / cancel / done. It folds
 * away into a small tab at the top edge, so controls can go up there too.
 * Plain views, so it works over the game (the core's SDL activity) as in the
 * app. Place it at the top centre of the screen.
 */
class OverlayEditorBar(
  context: Context,
  private val controller: OnScreenController,
  private val onFinished: () -> Unit,
) : FrameLayout(context) {
  private val density = resources.displayMetrics.density
  private val bar = LinearLayout(context)
  private val tab = TextView(context)
  private val title = label(bold = true)
  private val smaller = action(R.string.overlay_editor_smaller) { controller.resize(1f / STEP) }
  private val size = label()
  private val larger = action(R.string.overlay_editor_larger) { controller.resize(STEP) }
  private val visibility = action(R.string.overlay_editor_hide) { controller.toggleSelectedVisible() }
  private val opacity = label()

  init {
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    val pad = dp(6)
    bar.setPadding(pad * 2, pad, pad, pad)
    bar.background = GradientDrawable().apply {
      cornerRadius = dp(22).toFloat()
      setColor(Color.argb(225, 24, 24, 24))
      setStroke(dp(1), Color.argb(70, 255, 255, 255))
    }
    bar.elevation = dp(6).toFloat()

    bar.addView(title, LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT))
    bar.addView(smaller)
    bar.addView(size, LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT))
    bar.addView(larger)
    bar.addView(visibility)
    bar.addView(divider())
    bar.addView(action(R.string.overlay_editor_smaller) { controller.changeOpacity(-0.1f) })
    bar.addView(opacity, LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT))
    bar.addView(action(R.string.overlay_editor_larger) { controller.changeOpacity(0.1f) })
    bar.addView(divider())
    bar.addView(action(R.string.overlay_editor_reset) { controller.resetLayout() })
    bar.addView(action(R.string.overlay_editor_cancel) {
      controller.reloadLayout()
      onFinished()
    })
    bar.addView(action(R.string.overlay_editor_done, accent = true) {
      controller.saveLayout()
      onFinished()
    })
    bar.addView(divider())
    bar.addView(action(R.string.overlay_editor_fold) { setFolded(true) }.apply {
      contentDescription = context.getString(R.string.overlay_editor_fold_desc)
    })
    addView(bar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
      .apply { topMargin = dp(12) })

    // Folded: a tab hanging from the top edge brings the bar back.
    tab.setText(R.string.overlay_editor_tools)
    tab.setTextColor(Color.WHITE)
    tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    tab.typeface = Typeface.DEFAULT_BOLD
    tab.setPadding(dp(18), dp(4), dp(18), dp(8))
    tab.background = GradientDrawable().apply {
      val r = dp(14).toFloat()
      cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
      setColor(Color.argb(200, 24, 24, 24))
      setStroke(dp(1), Color.argb(70, 255, 255, 255))
    }
    tab.isClickable = true
    tab.setOnClickListener { setFolded(false) }
    tab.visibility = GONE
    addView(tab, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

    controller.onSelectionChanged = { refresh() }
    controller.onLayoutChanged = { refresh() }
    refresh()
  }

  private fun refresh() {
    val sel = controller.selectedControl()
    title.text = sel?.title ?: context.getString(R.string.overlay_editor_pick)
    // Size and opacity: the selected control's, or with none selected all of them.
    val scale = controller.scalePercent()
    size.text = when {
      sel != null -> "${scale ?: 100}%"
      scale != null -> context.getString(R.string.overlay_editor_size_all, scale)
      else -> context.getString(R.string.overlay_editor_size_mixed)
    }
    visibility.isEnabled = sel != null && sel != ControlId.MENU
    visibility.alpha = if (visibility.isEnabled) 1f else 0.35f
    visibility.setText(if (controller.isSelectedVisible()) R.string.overlay_editor_hide else R.string.overlay_editor_show)
    val percent = controller.opacityPercent()
    opacity.text = when {
      sel != null -> context.getString(R.string.overlay_editor_opacity, percent ?: 0)
      percent != null -> context.getString(R.string.overlay_editor_opacity_all, percent)
      else -> context.getString(R.string.overlay_editor_opacity_mixed)
    }
  }

  private fun label(bold: Boolean = false) = TextView(context).apply {
    setTextColor(Color.WHITE)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    maxLines = 2
  }

  private fun action(text: Int, accent: Boolean = false, onClick: () -> Unit) = TextView(context).apply {
    setText(text)
    setTextColor(if (accent) ACCENT else Color.WHITE)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    minWidth = dp(40)
    setPadding(dp(12), dp(10), dp(12), dp(10))
    val mask = GradientDrawable().apply {
      cornerRadius = dp(16).toFloat()
      setColor(Color.WHITE)
    }
    background = RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), null, mask)
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
  }

  /** Folds the bar into the tab at the top edge (or opens it again). */
  fun setFolded(folded: Boolean) {
    bar.visibility = if (folded) GONE else VISIBLE
    tab.visibility = if (folded) VISIBLE else GONE
  }

  private fun divider() = View(context).apply {
    setBackgroundColor(Color.argb(60, 255, 255, 255))
    layoutParams = LinearLayout.LayoutParams(dp(1), dp(28)).apply {
      marginStart = dp(6)
      marginEnd = dp(6)
    }
  }

  private fun dp(v: Int) = (v * density).roundToInt()

  private companion object {
    const val STEP = 1.1f
    val ACCENT = 0xFF8BC34A.toInt()
  }
}
