package org.blackaddons.cheat.features.impl.settings

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.player.BlackumAim
import org.blackaddons.api.player.ReactionMode
import org.blackaddons.api.ui.layout.Cell
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.cheat.aim.*
import java.awt.Color
import java.util.Random
import kotlin.time.Duration.Companion.milliseconds

@AlwaysActive
internal object Rotations : Feature(
    description = "Skill presets for the Blackum Algorithm and the simulated hand behind its turns.",
    name = "Rotations",
) {
    override val section = Categories.Settings.General

    @Suppress("unused")
    private val presetsButton by button("Presets", action = { UiManager.push(PresetScreen()) }, label = "Edit")
        .withDescription("Built-in skill levels and your own. AutoSS, Actions and Secret Utils pick one in their Rotation Curve.")

    private val silentView by boolean("NoRotate Cam", default = true)
        .withDescription("While a silent rotation is active, show what the server sees in a HUD element you can move in the HUD editor. Hidden while a shader pack is in use.")
    private val silentViewFps by number("NoRotate Cam FPS", default = 30.0, min = 5.0, max = 240.0, step = 5.0, unit = "fps")
        .withDescription("How often the view is redrawn. Every redraw renders the world once more.")
        .showIf { silentView.value }

    @Suppress("unused")
    private val silentViewHud = hudElement(
        "NoRotate Cam",
        enabled = { silentView.value },
        shouldDraw = { Platform.movement.silentYaw != null },
        hasText = false,
        defaultAnchorX = AnchorX.CENTER,
        defaultAnchorY = AnchorY.TOP,
        defaultOffsetX = -150f,
        defaultOffsetY = 35f,
        defaultScale = 1.25f,
    ) { example ->
        val height = VIEW_WIDTH * Platform.window.scaledHeight / Platform.window.scaledWidth
        Cell(VIEW_WIDTH, height) { x, y ->
            val drawn = drawSilentView(x, y, height)
            if (!drawn && example) Render.rect(x, y, VIEW_WIDTH, height, VIEW_PLACEHOLDER)
            if (drawn || example) Render.hollowRect(x, y, VIEW_WIDTH, height, VIEW_FRAME, Theme.ACCENT)
        }
    }

    /* 0 means not drawn yet. */
    private val handSeed by number("Hand Seed", default = 0.0, min = 0.0, max = Int.MAX_VALUE.toDouble(), step = 1.0).hidden()
    private val custom by list("Custom Presets", itemMaxLength = LINE_MAX).hidden()

    private var signature = HandSignature(0)

    private val pickers = ArrayList<AimCurve.Picker>()

    /* Parsed on every read. It's a few lines, read only when a turn starts or the editor draws. */
    val presets: List<AimPreset>
        get() {
            val reserved = AimPreset.BUILT_IN.map { it.name.lowercase() }
            val saved = custom.value.mapNotNull(AimPreset::fromLine).filter { it.name.lowercase() !in reserved }
            return AimPreset.BUILT_IN + saved.distinctBy { it.name.lowercase() }
        }

    private val customPresets: List<AimPreset> get() = presets.filterNot(::isBuiltIn)

    fun preset(name: String): AimPreset? = presets.firstOrNull { it.name == name }

    fun isBuiltIn(preset: AimPreset): Boolean = AimPreset.BUILT_IN.any { it.name == preset.name }

    fun aim(preset: AimPreset, reaction: ReactionMode, targetRadius: Float, speed: Float = 1f) = BlackumAim(
        speed = preset.speed * speed,
        reaction = reaction,
        reactionTime = preset.reaction.toDouble().milliseconds,
        targetRadius = targetRadius,
        hand = aimHand(preset, signature()),
    )

    /* Read every time it's used, so a config loaded after enabling still picks the saved hand. */
    private fun signature(): HandSignature {
        val seed = handSeed.value.toInt()
        if (signature.seed != seed) signature = HandSignature(seed)
        return signature
    }

    private fun drawSilentView(x: Float, y: Float, height: Float): Boolean {
        val player = Platform.player.uuid ?: return false
        val yaw = Platform.movement.silentYaw ?: return false
        val pitch = Platform.movement.silentPitch ?: return false
        return Platform.camera.drawEntityView(player, x, y, VIEW_WIDTH, height, silentViewFps.value.toInt(), yaw, pitch)
    }

    fun track(picker: AimCurve.Picker) {
        pickers += picker
    }

    fun isFreeName(name: String, except: String? = null): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > NAME_MAX) return false
        val taken = presets.map { it.name }
        return taken.none { it != except && it.equals(trimmed, ignoreCase = true) }
    }

    fun freeName(base: String): String =
        generateSequence(2) { it + 1 }.map { "$base $it".take(NAME_MAX) }.first(::isFreeName)

    fun add(preset: AimPreset) {
        custom.value = customPresets.map { it.toLine() } + preset.toLine()
    }

    fun update(name: String, preset: AimPreset) {
        custom.value = customPresets.map { (if (it.name == name) preset else it).toLine() }
        if (preset.name != name) pickers.forEach { if (it.value == name) it.value = preset.name }
    }

    fun delete(name: String) {
        custom.value = customPresets.filterNot { it.name == name }.map { it.toLine() }
        pickers.forEach { if (it.value == name) it.value = AimPreset.DEFAULT.name }
    }

    override fun onEnable() {
        if (handSeed.value == 0.0) roll()
    }

    private fun roll() {
        handSeed.value = 1.0 + Random().nextInt(Int.MAX_VALUE - 1)
        Platform.config.save()
    }

    private const val VIEW_WIDTH = 160f
    private const val VIEW_FRAME = 1f
    private val VIEW_PLACEHOLDER = Color(0, 0, 0, 128)
    private const val LINE_MAX = 256
    const val NAME_MAX = 24
}
