package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.BooleanSetting
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.MouseButtonInput

class BooleanWidget(setting: BooleanSetting) : SettingWidget(setting) {

    private val bool = setting
    private val trackW = 26f
    private val trackH = 14f
    override val naturalWidth: Float get() = trackW
    override val stretches: Boolean get() = false

    private val anim = Animation(
        initial = if (bool.value) 1f else 0f,
        durationMs = Theme.ANIM_FAST.toLong(),
        easing = Easing.easeOutCubic,
    )

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        anim.animateTo(if (bool.value) 1f else 0f)
        val t = anim.get()
        val cy = centered(y, Theme.SETTING_ROW_HEIGHT, trackH)
        bounds.set(x, cy, trackW, trackH)
        Render.toggleSwitch(x, cy, trackW, trackH, t)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft || !bounds.has(mouse.x, mouse.y)) return false
        bool.value = !bool.value
        return true
    }
}
