package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.Platform
import org.blackaddons.api.event.render.RenderHudEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.ui.render.Image
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.FrameClock
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.io.FileFormat
import java.awt.Color
import java.nio.file.Path
import kotlin.random.Random

object Adhd : Feature(
    description = "Bounces an image around your screen like the DVD logo.",
    name = "ADHD",
) {
    private val imageFile by string("Image File", "", maxLength = 512, id = "image_file")
        .hidden()

    @Suppress("unused")
    private val chooseImage by button(
        name = "Image",
        label = "Choose...",
        id = "choose_image",
        action = ::openImagePicker,
    ).withDescription("Choose the PNG, JPEG, WebP, or GIF to bounce around.")

    private val count by number("Count", 1.0, 1.0, 50.0, 1.0)
        .withDescription("How many copies bounce at once, each on its own path.")

    private val size by number("Size", 15.0, 1.0, 100.0, 1.0, unit = "%")
        .withDescription("Image height as a share of the screen height. The width keeps the image's shape.")

    private val transparency by number("Transparency", 0.0, 0.0, 100.0, 1.0, unit = "%")
        .withDescription("Make the image see-through. 100% hides it completely.")

    private val speed by number("Speed", 15.0, 1.0, 100.0, 1.0, unit = "%/s")
        .withDescription("How fast the image moves, in screen heights per second.")

    private val colorChange by boolean("Color Change", true)
        .withDescription("Tint the image a new color every time it hits a wall. Brightest on light images.")

    private val cornerHit by mode(
        "Corner Hit",
        CORNER_BOTH,
        listOf(CORNER_OFF, CORNER_SOUND, CORNER_TOAST, CORNER_BOTH)
    )
        .withDescription("Celebrate when an image hits a corner exactly.")

    private class Bouncer(var x: Float, var y: Float, var dirX: Float, var dirY: Float, var tint: Color? = null)

    private val bouncers = mutableListOf<Bouncer>()
    private val frameClock = FrameClock()

    override fun init() {
        register<RenderHudEvent> { draw() }
    }

    override fun onDisable() {
        super.onDisable()
        bouncers.clear()
        frameClock.reset()
    }

    private fun draw() {
        val path = imageFile.value.trim().ifEmpty { null }?.let { Render.FILE_SCHEME + it } ?: DEFAULT_IMAGE
        val (imageW, imageH) = Render.imageSize(path) ?: return
        val seconds = frameClock.tick().toFloat()
        val screenH = Platform.window.scaledHeight
        val h = screenH * size.value.toFloat() / 100f
        val w = h * imageW / imageH
        val spanX = Platform.window.scaledWidth - w
        val spanY = screenH - h
        val step = screenH * speed.value.toFloat() / 100f * seconds
        val image = Image(path)
        resize(count.value.toInt(), spanX, spanY)
        Render.push()
        Render.globalAlpha(1f - transparency.value.toFloat() / 100f)
        for (b in bouncers) {
            val (x, dirX) = bounce(b.x, b.dirX, step, spanX)
            val (y, dirY) = bounce(b.y, b.dirY, step, spanY)
            val hitX = dirX != b.dirX
            val hitY = dirY != b.dirY
            b.x = x; b.y = y; b.dirX = dirX; b.dirY = dirY
            if (hitX || hitY) b.tint = if (colorChange.value) randomTint() else null
            if (hitX && hitY) celebrateCorner()
            Render.image(image, b.x, b.y, w, h, 0f, 0f, imageW, imageH, imageW, imageH, b.tint)
        }
        Render.pop()
    }

    private fun resize(target: Int, spanX: Float, spanY: Float) {
        while (bouncers.size > target) bouncers.removeAt(bouncers.lastIndex)
        while (bouncers.size < target) bouncers += Bouncer(
            x = Random.nextFloat() * spanX.coerceAtLeast(0f),
            y = Random.nextFloat() * spanY.coerceAtLeast(0f),
            dirX = if (Random.nextBoolean()) 1f else -1f,
            dirY = if (Random.nextBoolean()) 1f else -1f,
        )
    }

    internal fun bounce(pos: Float, dir: Float, step: Float, span: Float): Pair<Float, Float> {
        if (span <= 0f) return 0f to dir
        val next = pos + dir * step
        return when {
            next <= 0f -> 0f to 1f
            next >= span -> span to -1f
            else -> next to dir
        }
    }

    private fun randomTint(): Color = Color.getHSBColor(Random.nextFloat(), TINT_SATURATION, 1f)

    private fun celebrateCorner() {
        val mode = cornerHit.value
        if (mode == CORNER_SOUND || mode == CORNER_BOTH) Platform.feedback.playSound(CORNER_SOUND_ID)
        if (mode == CORNER_TOAST || mode == CORNER_BOTH) Toasts.show(
            I18n.trOr(
                "blackaddons.adhd.corner_hit",
                "Corner hit!"
            )
        )
    }

    private fun openImagePicker() {
        Platform.desktop.chooseFile("Choose ADHD Image", FileFormat.IMAGE_EXTENSIONS) { path ->
            OffThread.run(
                name = "adhd-image-check",
                work = { FileFormat.isImageFile(path) },
                onFailure = { false },
                onResult = { valid -> Platform.scheduler.schedule(0) { finishImageSelection(path, valid) } },
            )
        }
    }

    private fun finishImageSelection(path: Path, valid: Boolean) {
        if (!valid) {
            Toasts.show(
                I18n.trOr("blackaddons.adhd.image_invalid", "Unsupported image"),
                I18n.trOr("blackaddons.dungeon_map.background_invalid_detail", "Use PNG/JPEG/WebP/GIF."),
                ToastKind.ERROR,
            )
            return
        }
        imageFile.value = path.toString()
        Platform.config.save()
    }

    private const val DEFAULT_IMAGE = "assets/blackaddons/icon.png"
    private const val TINT_SATURATION = 0.6f
    private const val CORNER_SOUND_ID = "minecraft:entity.player.levelup"
    private const val CORNER_OFF = "Off"
    private const val CORNER_SOUND = "Sound"
    private const val CORNER_TOAST = "Toast"
    private const val CORNER_BOTH = "Both"
}
