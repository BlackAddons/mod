package org.blackaddons.api.gui.task

import org.blackaddons.api.feature.settings.SettingProvider
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.I18n
import kotlin.time.Duration.Companion.milliseconds

/** The menu settings of a feature that runs [ContainerTask]s, in one [groupLabel] group. */
class ContainerTaskSettings(provider: SettingProvider, groupLabel: String = "Menus") {

    private val silent = provider.boolean("Silent Menus", default = true)
    private val stopMovement = provider.boolean("Stop Movement", default = true)
    private val cancelOnInput = provider.boolean("Cancel On Input")
    private val minClickDelay = provider.number("Min Click Delay", 50.0, 0.0, 500.0, 10.0, "ms")
    private val maxClickDelay = provider.number("Max Click Delay", 100.0, 0.0, 500.0, 10.0, "ms")
    private val showProgress = provider.boolean("Show Progress", default = true)

    init {
        with(provider) {
            silent.withDescription("Open the menus without showing them on screen.")
            stopMovement.withDescription("Stand still while a hidden menu is open, as you would with it on screen.")
                .showIf { silent.value }
            cancelOnInput.withDescription("Stop the menu task when you press a key or mouse button while a hidden menu is open.")
                .showIf { silent.value }
            minClickDelay.withDescription("Shortest wait before each menu click.")
            maxClickDelay.withDescription("Longest wait before each menu click.")
            showProgress.withDescription("Show the running menu task in a HUD element you can move in the HUD editor.")
            listOf(silent, stopMovement, cancelOnInput, minClickDelay, maxClickDelay, showProgress).forEach {
                registerSetting(it.group(groupLabel))
            }
        }
    }

    val progressShown: Boolean get() = showProgress.value

    fun options(): ContainerTaskOptions = ContainerTaskOptions(
        silent = silent.value,
        stopMovement = stopMovement.value,
        cancelOnInput = cancelOnInput.value,
        clickDelay = minClickDelay.value.milliseconds..maxClickDelay.value.milliseconds,
    )

    companion object {
        private val tr = I18n.scope("blackaddons.container_task")

        private const val PADDING_X = 8f
        private const val NAME_Y = 6f
        private const val STEP_Y = 19f
        private const val BAR_Y = 33f
        private const val BAR_HEIGHT = 3f
        private const val HEIGHT = 42f
        private const val MIN_WIDTH = 120f
        private const val BACKGROUND_ALPHA = 205

        /** An FME-style card for [task], with a sample in the HUD editor ([example]). */
        fun progressCard(task: ContainerTask?, example: Boolean): LayoutNode {
            val name = task?.name ?: if (example) tr("example", "Equip Spirit Mask") else ""
            val done = task?.completedSteps ?: if (example) 1 else 0
            val total = task?.totalSteps ?: if (example) 3 else 1
            val step = tr("step", "Step %d of %d", minOf(done + 1, total), total)
            val width = maxOf(
                Render.textWidth(name, Theme.FONT_SMALL, Fonts.medium),
                Render.textWidth(step, Theme.FONT_SMALL, Fonts.regular),
                MIN_WIDTH - 2 * PADDING_X,
            ) + 2 * PADDING_X
            return column {
                cell(width, HEIGHT) { x, y ->
                    Render.rect(x, y, width, HEIGHT, Theme.BACKGROUND.withAlpha(BACKGROUND_ALPHA), Theme.RADIUS_SMALL)
                    Render.text(name, x + PADDING_X, y + NAME_Y, TextStyle.SMALL_PRIMARY_MEDIUM)
                    Render.text(step, x + PADDING_X, y + STEP_Y, TextStyle.SMALL_MUTED)
                    val barWidth = width - 2 * PADDING_X
                    Render.rect(x + PADDING_X, y + BAR_Y, barWidth, BAR_HEIGHT, Theme.TRACK, BAR_HEIGHT / 2)
                    val filled = barWidth * done.coerceIn(0, total) / total.coerceAtLeast(1)
                    if (filled > 0f) Render.rect(x + PADDING_X, y + BAR_Y, filled, BAR_HEIGHT, Theme.ACCENT, BAR_HEIGHT / 2)
                }
            }
        }
    }
}
