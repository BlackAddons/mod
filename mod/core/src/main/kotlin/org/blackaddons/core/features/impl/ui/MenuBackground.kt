package org.blackaddons.core.features.impl.ui

import org.blackaddons.api.Platform
import org.blackaddons.api.event.render.MenuBackgroundEvent
import org.blackaddons.api.event.render.MenuFramerateEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.ui.render.GradientDir
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.FrameClock
import org.blackaddons.api.util.expSmoothing
import org.blackaddons.core.theme.NebulaPalette
import org.blackaddons.core.theme.ThemedPalette
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A dark menu background instead of the rotating panorama: nebula streaks drifting over near-black, with twinkling
 * stars. It only replaces the panorama where vanilla draws it, so menus in a world still show the world.
 */
object MenuBackground : Feature(
    description = "Replaces the main menu panorama with a dark BlackAddons background.",
) {
    override val section = Categories.Client.Menu

    private const val COLORS = "Colors"
    private const val VANILLA_FPS = "60 FPS (Vanilla)"
    private const val UNLIMITED = "Unlimited"
    private const val VSYNC = "VSync"
    private const val BRIGHTNESS = "Brightness"
    private const val MOTION = "Motion"
    // Stars look like shit on other patterns.
    private val STARRY_PATTERNS = setOf("Nebula", "Flow", "Grid")

    private val pattern by mode("Pattern", default = "Nebula", modes = listOf("Nebula", "Flow", "Grid", "Ridges", "Landscape"))
        .asDropdown()
        .withDescription("Nebula = drifting streaks.\nFlow = the slow clouds from the BlackAddons website.\nGrid = a grid lit by drifting points.\nRidges = stacked wave lines.\nLandscape = a flight over hills toward a setting sun.\nRidges and Landscape are heavy on weaker graphics cards.")
    private val menuFps by mode("Menu FPS", default = UNLIMITED, modes = listOf(VANILLA_FPS, UNLIMITED, VSYNC))
        .withDescription("Frame rate of menus without a world.\n60 FPS = vanilla's cap.\nVSync = your monitor's refresh rate.\nAnimated backgrounds use more GPU the faster they run.")
    private val palette by mode("Palette", default = "Default", modes = listOf("Default", "Theme", "Custom"))
        .withDescription("Default = the BlackAddons colors.\nTheme = follows your UI theme's accent and background.\nCustom = your own colors below.")
        .group(COLORS)
    private val customTop by color("Background Top", NebulaPalette.NEBULA.top, allowAlpha = false).group(COLORS).showIf(::isCustom)
    private val customBottom by color("Background Bottom", NebulaPalette.NEBULA.bottom, allowAlpha = false).group(COLORS).showIf(::isCustom)
    private val customDeep by color("Color 1", NebulaPalette.NEBULA.deep, allowAlpha = false).group(COLORS).showIf(::isCustom)
    private val customMain by color("Color 2", NebulaPalette.NEBULA.main, allowAlpha = false).group(COLORS).showIf(::isCustom)
    private val customHot by color("Color 3", NebulaPalette.NEBULA.hot, allowAlpha = false).group(COLORS).showIf(::isCustom)
    private val customPlum by color("Color 4", NebulaPalette.NEBULA.plum, allowAlpha = false).group(COLORS).showIf(::isCustom)

    private val brightness by number("Brightness", 90.0, 0.0, 150.0, 5.0, unit = "%")
        .withDescription("Background brightness.")
        .group(BRIGHTNESS)
    private val stars by boolean("Stars", default = true)
        .withDescription("Faint twinkling stars over the nebula.")
        .group(BRIGHTNESS)
        .showIf(::hasStars)
    private val starBrightness by number("Star Brightness", 100.0, 10.0, 100.0, 5.0, unit = "%")
        .group(BRIGHTNESS)
        .showIf { hasStars() && stars.value }
    private val darken by number("Darken", 0.0, 0.0, 90.0, 5.0, unit = "%")
        .withDescription("A black veil over the whole picture, for calmer menus.")
        .group(BRIGHTNESS)

    private val animate by boolean("Animate", default = true)
        .withDescription("Let the nebula drift and the stars twinkle. Off = a still picture.")
        .group(MOTION)
    private val speed by number("Speed", 100.0, 10.0, 300.0, 10.0, unit = "%")
        .group(MOTION)
        .showIf { animate.value }
    private val parallax by boolean("Parallax", default = true)
        .withDescription("The picture follows the mouse a little, the stars least.")
        .group(MOTION)
        .showIf { animate.value }

    private fun isCustom() = palette.value == "Custom"

    private fun hasStars() = pattern.value in STARRY_PATTERNS

    /** A streak names a role in the palette, not a color, so every palette paints the same nebula. */
    private enum class Tone { DEEP, MAIN, HOT, PLUM }

    private fun NebulaPalette.of(tone: Tone): Color = when (tone) {
        Tone.DEEP -> deep
        Tone.MAIN -> main
        Tone.HOT -> hot
        Tone.PLUM -> plum
    }

    /**
     * One blurred streak. Sizes are fractions of the window's longer side, and positions are fractions of the window.
     * [speed] is how many turns per minute it drifts, and [depth] is how much parallax it gets.
     */
    private class Streak(
        val tone: Tone,
        val alpha: Int,
        val length: Float,
        val thickness: Float,
        val x: Float,
        val y: Float,
        val angle: Float,
        val sway: Float,
        val speed: Float,
        val phase: Float,
        val depth: Float,
    )

    private val streaks = listOf(
        Streak(Tone.DEEP, 170, 0.95f, 0.16f, 0.26f, 0.28f, -0.42f, 0.05f, 0.9f, 0.0f, 0.5f),
        Streak(Tone.HOT, 150, 0.75f, 0.11f, 0.70f, 0.70f, -0.28f, 0.06f, 1.2f, 2.0f, 0.9f),
        Streak(Tone.MAIN, 120, 0.55f, 0.07f, 0.60f, 0.20f, 0.30f, 0.07f, 1.5f, 4.1f, 0.7f),
        Streak(Tone.PLUM, 140, 0.80f, 0.13f, 0.20f, 0.80f, 0.18f, 0.05f, 1.0f, 1.2f, 0.6f),
        Streak(Tone.HOT, 110, 0.45f, 0.06f, 0.90f, 0.22f, -0.70f, 0.08f, 1.8f, 3.3f, 1.0f),
        Streak(Tone.DEEP, 130, 0.60f, 0.09f, 0.48f, 0.52f, 0.55f, 0.06f, 1.1f, 5.0f, 0.8f),
        Streak(Tone.MAIN, 90, 0.40f, 0.05f, 0.10f, 0.50f, -0.20f, 0.07f, 1.6f, 0.7f, 0.4f),
    )

    // Blur relative to thickness. A lower value keeps dark gaps between the streaks instead of one even wash.
    private const val BLUR = 0.9f

    private class Star(val x: Float, val y: Float, val size: Float, val alpha: Float, val twinkle: Float, val phase: Float)

    private const val STAR_COUNT = 140
    private const val STAR_DEPTH = 0.25f
    private val STAR = Color(232, 222, 255)

    // Fixed seed: the same sky every launch.
    private val sky = Random(0xB1AC).let { random ->
        List(STAR_COUNT) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                size = 0.25f + random.nextFloat() * random.nextFloat() * 0.75f,
                alpha = 0.25f + random.nextFloat() * 0.6f,
                twinkle = 0.3f + random.nextFloat() * 1.2f,
                phase = random.nextFloat() * 2 * PI.toFloat(),
            )
        }
    }

    private const val PARALLAX_PX = 16f
    private const val PARALLAX_RATE = 4.0

    // The scene's own clock. Pausing it keeps a still picture where it was instead of snapping to a fixed pose.
    private var seconds = 0.0
    private val frameClock = FrameClock()
    private var lookX = 0f
    private var lookY = 0f

    private val themedNebula = ThemedPalette(NebulaPalette::nebula)
    private val themedFlow = ThemedPalette(NebulaPalette::flow)

    override fun init() {
        register<MenuBackgroundEvent> {
            draw()
            event.replaced = true
        }
        register<MenuFramerateEvent> {
            event.limit = when (menuFps.value) {
                UNLIMITED -> MenuFramerateEvent.Limit.UNLIMITED
                VSYNC -> MenuFramerateEvent.Limit.DISPLAY
                else -> MenuFramerateEvent.Limit.VANILLA
            }
        }
    }

    private fun draw() {
        val w = Platform.window.scaledWidth
        val h = Platform.window.scaledHeight
        // A minimized window is 0 x 0. Nothing shows, and dividing by it below would break the parallax.
        if (w <= 0f || h <= 0f) return
        advance(w, h)
        /* All shader patterns share Flow's palette. */
        val flow = pattern.value != "Nebula"
        val colors = currentPalette(flow)
        val strength = brightness.value.toFloat() / 100

        if (flow) {
            drawShader(shader(pattern.value), colors, strength, w, h)
        } else {
            Render.gradientRect(0, 0, w, h, colors.top, colors.bottom, GradientDir.TOP_BOTTOM)
            val span = max(w, h)
            streaks.forEach { drawStreak(it, colors, strength, w, h, span) }
        }
        if (hasStars() && stars.value) drawStars(w, h, starBrightness.value.toFloat() / 100)
        if (darken.value > 0) Render.rect(0, 0, w, h, Color.BLACK.withAlpha((darken.value / 100).toFloat()))
    }

    private fun currentPalette(flow: Boolean): NebulaPalette = when (palette.value) {
        "Theme" -> if (flow) themedFlow.current() else themedNebula.current()
        "Custom" -> NebulaPalette(
            customTop.value, customBottom.value, customDeep.value, customMain.value, customHot.value, customPlum.value,
        )
        else -> if (flow) NebulaPalette.FLOW else NebulaPalette.NEBULA
    }

    private val shaders = HashMap<String, String>()

    private fun shader(pattern: String): String = shaders.getOrPut(pattern) {
        val path = "/assets/blackaddons/shaders/${pattern.lowercase()}.sksl"
        checkNotNull(MenuBackground::class.java.getResourceAsStream(path)) { "Missing $path" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private const val FLOW_AMOUNT = 1 / 3f
    private const val FLOW_PARALLAX = 0.6f
    private const val FLOW_CLOCK_WRAP = 1000.0

    private fun drawShader(source: String, colors: NebulaPalette, strength: Float, w: Float, h: Float) {
        Render.shaderRect(
            0, 0, w, h, source,
            mapOf(
                "res" to floatArrayOf(w, h),
                "mouse" to floatArrayOf(Platform.window.mouseX, Platform.window.mouseY),
                "t" to floatArrayOf((seconds % FLOW_CLOCK_WRAP).toFloat()),
                "amt" to floatArrayOf(FLOW_AMOUNT * strength),
                "look" to floatArrayOf(-lookX * FLOW_PARALLAX, -lookY * FLOW_PARALLAX),
                "density" to floatArrayOf(Platform.window.guiScale),
                "top" to rgb(colors.top),
                "bottom" to rgb(colors.bottom),
                "navy" to rgb(colors.deep),
                "violet" to rgb(colors.main),
                "plum" to rgb(colors.plum),
                "rose" to rgb(colors.hot),
            ),
        )
    }

    private fun rgb(color: Color) = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f)

    private fun drawStreak(streak: Streak, colors: NebulaPalette, strength: Float, w: Float, h: Float, span: Float) {
        val alpha = (streak.alpha * strength).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        val t = seconds / 60 * streak.speed * 2 * PI + streak.phase
        val cx = w * (streak.x + streak.sway * sin(t).toFloat()) + lookX * streak.depth
        val cy = h * (streak.y + streak.sway * cos(t * 0.7).toFloat()) + lookY * streak.depth
        val length = span * streak.length
        val thickness = span * streak.thickness
        Render.push()
        Render.translate(cx, cy)
        Render.rotate(streak.angle + 0.08f * sin(t * 0.5).toFloat())
        Render.dropShadow(
            -length / 2, -thickness / 2, length, thickness,
            blur = thickness * BLUR, spread = 0, radius = thickness / 2, color = colors.of(streak.tone).withAlpha(alpha),
        )
        Render.pop()
    }

    private fun drawStars(w: Float, h: Float, brightness: Float) {
        val dx = lookX * STAR_DEPTH
        val dy = lookY * STAR_DEPTH
        sky.forEach { star ->
            val shimmer = 0.6f + 0.4f * sin(seconds * star.twinkle + star.phase).toFloat()
            Render.circle(star.x * w + dx, star.y * h + dy, star.size, STAR.withAlpha(star.alpha * shimmer * brightness))
        }
    }

    private fun advance(w: Float, h: Float) {
        val dt = frameClock.tick()
        if (!animate.value) return
        seconds += dt * speed.value / 100

        val (targetX, targetY) = if (parallax.value) {
            (Platform.window.mouseX / w - 0.5f) * -PARALLAX_PX to (Platform.window.mouseY / h - 0.5f) * -PARALLAX_PX
        } else {
            0f to 0f
        }
        // Frame-rate independent ease toward the target.
        val ease = expSmoothing(dt, PARALLAX_RATE).toFloat()
        lookX += (targetX - lookX) * ease
        lookY += (targetY - lookY) * ease
        /*
         * The ease feeds on itself, so a single NaN (from reading the cursor mid-resize) would hide every streak and
         * star for good.
         */
        if (!lookX.isFinite() || !lookY.isFinite()) {
            lookX = 0f
            lookY = 0f
        }
    }
}
