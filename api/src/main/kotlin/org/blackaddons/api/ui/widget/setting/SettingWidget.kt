package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.ui.widget.Widget

/**
 * Draws and handles one [Setting] at the right of its row; the controller draws the label. Created by
 * [widgetFor] and cached per feature in [org.blackaddons.core.ui.tab.FeatureCategoryTabController].
 */
abstract class SettingWidget(val setting: Setting<*>) : Widget {

    override val height: Float = Theme.SETTING_ROW_HEIGHT
    override val width: Float get() = widgetWidth

    /** Size to the widest text it can ever show, or it resizes as the user clicks. */
    open val naturalWidth: Float get() = DEFAULT_WIDTH

    /** False where the size is the meaning (a stretched toggle looks broken). */
    open val stretches: Boolean get() = true

    /** Extra tooltip line when the input isn't obvious (e.g. a slider needs Ctrl+wheel). */
    open val inputHint: String? get() = null

    /** Set by a container to align a column; defaults to [naturalWidth]. */
    var widgetWidth: Float = UNSET
        get() = if (field == UNSET) naturalWidth else field

    /** Stamped in [render]; input hit-tests against it. */
    protected val bounds = Rect()

    /** Widget only, not the label. */
    abstract fun render(x: Float, y: Float, mouseX: Float, mouseY: Float)

    /**
     * Drawn after every row and outside their clips, for example an open list. While [isOverlaying] is true, it gets
     * presses first.
     */
    open fun renderOverlay(mouseX: Float, mouseY: Float) {}

    final override fun draw(x: Float, y: Float) = render(x, y, UiMouse.x, UiMouse.y)

    companion object {
        private const val UNSET = -1f

        const val DEFAULT_WIDTH = 80f

        /** Per side. */
        const val CONTENT_PAD = 10f

        /** Longer text ellipsizes with a tooltip. */
        const val MAX_WIDTH = 260f
    }
}
