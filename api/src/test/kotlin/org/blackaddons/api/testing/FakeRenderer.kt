package org.blackaddons.api.testing

import org.blackaddons.api.ui.render.*
import org.joml.Matrix3x2fc
import java.awt.Color

/**
 * Draws nothing and logs calls by name in [calls]. Layers behave like a retained backend: content runs only
 * while stale, counted in [recordings].
 */
class FakeRenderer : Renderer {

    val calls = ArrayList<String>()
    var recordings = 0
        private set
    private val current = HashSet<RenderLayer>()

    fun count(name: String): Int = calls.count { it == name }

    fun counts(): Map<String, Int> = calls.groupingBy { it }.eachCount()

    private fun call(name: String) {
        calls += name
    }

    override fun beginPass() = call("beginPass")
    override fun push() = call("push")
    override fun pop() = call("pop")
    override fun translate(x: Number, y: Number) = call("translate")
    override fun scale(x: Number, y: Number) = call("scale")
    override fun rotate(angle: Number) = call("rotate")
    override fun transform(matrix: Matrix3x2fc) = call("transform")
    override fun globalAlpha(alpha: Number) = call("globalAlpha")
    override fun pushAntiAlias(enabled: Boolean) = call("pushAntiAlias")
    override fun popAntiAlias() = call("popAntiAlias")

    override fun toScreen(x: Number, y: Number): FloatArray {
        call("toScreen")
        return floatArrayOf(x.toFloat(), y.toFloat())
    }

    override fun pushScissor(x: Number, y: Number, w: Number, h: Number) = call("pushScissor")
    override fun popScissor() = call("popScissor")
    override fun line(x1: Number, y1: Number, x2: Number, y2: Number, thickness: Number, color: Color) = call("line")
    override fun rect(x: Number, y: Number, w: Number, h: Number, color: Color, radius: Number) = call("rect")
    override fun rects(rects: List<BatchRect>, color: Color, radius: Number) = call("rects")
    override fun polygon(points: FloatArray, color: Color, corner: Number) = call("polygon")

    override fun createShape(data: PathData): ShapePath {
        call("createShape")
        return ShapePath()
    }

    override fun deleteShape(shape: ShapePath) = call("deleteShape")
    override fun fillShape(shape: ShapePath, color: Color) = call("fillShape")
    override fun strokeShape(shape: ShapePath, thickness: Number, color: Color) = call("strokeShape")

    override fun layer(layer: RenderLayer, x: Number, y: Number, w: Number, h: Number, content: () -> Unit) {
        call("layer")
        if (current.add(layer)) {
            recordings++
            content()
        }
    }

    override fun invalidateLayer(layer: RenderLayer) {
        call("invalidateLayer")
        current.remove(layer)
    }

    override fun deleteLayer(layer: RenderLayer) {
        call("deleteLayer")
        current.remove(layer)
    }

    override fun hollowRect(x: Number, y: Number, w: Number, h: Number, thickness: Number, color: Color, radius: Number) =
        call("hollowRect")

    override fun halfRoundedRect(x: Number, y: Number, w: Number, h: Number, color: Color, radius: Number, roundTop: Boolean) =
        call("halfRoundedRect")

    override fun gradientRect(x: Number, y: Number, w: Number, h: Number, c1: Color, c2: Color, dir: GradientDir, radius: Number) =
        call("gradientRect")

    override fun circle(x: Number, y: Number, radius: Number, color: Color) = call("circle")
    override fun dropShadow(x: Number, y: Number, w: Number, h: Number, blur: Number, spread: Number, radius: Number, color: Color) =
        call("dropShadow")

    override fun shaderRect(x: Number, y: Number, w: Number, h: Number, sksl: String, uniforms: Map<String, FloatArray>, radius: Number) =
        call("shaderRect")

    override fun icon(data: String, x: Number, y: Number, size: Number, color: Color, viewBox: Number, thickness: Number) =
        call("icon")

    override fun text(text: String, x: Number, y: Number, size: Number, color: Color, font: Font) = call("text")
    override fun textShadow(text: String, x: Number, y: Number, size: Number, color: Color, font: Font) = call("textShadow")
    override fun textGradient(text: String, x: Number, y: Number, size: Number, c1: Color, c2: Color, font: Font, dir: GradientDir) =
        call("textGradient")

    override fun textWidth(text: String, size: Number, font: Font): Float {
        call("textWidth")
        return 0f
    }

    override fun textMetrics(size: Number, font: Font): FloatArray {
        call("textMetrics")
        return floatArrayOf(8f, 2f)
    }

    override fun wrappedText(text: String, x: Number, y: Number, w: Number, size: Number, color: Color, font: Font, lineHeight: Number) =
        call("wrappedText")

    override fun wrappedTextBounds(text: String, w: Number, size: Number, font: Font, lineHeight: Number): FloatArray {
        call("wrappedTextBounds")
        return FloatArray(4)
    }

    override fun richText(spans: List<RichSpan>, x: Number, y: Number, w: Number, size: Number, color: Color, font: Font, lineHeight: Number) =
        call("richText")

    override fun richTextBounds(spans: List<RichSpan>, w: Number, size: Number, font: Font, lineHeight: Number): FloatArray {
        call("richTextBounds")
        return FloatArray(4)
    }

    override fun createImage(path: String): Image {
        call("createImage")
        return Image(path)
    }

    override fun deleteImage(image: Image) = call("deleteImage")
    override fun image(image: Image, x: Number, y: Number, w: Number, h: Number, radius: Number) = call("image")
    override fun image(path: String, x: Number, y: Number, w: Number, h: Number, radius: Number) = call("image")

    override fun imageSize(path: String): FloatArray? {
        call("imageSize")
        return null
    }

    override fun image(
        image: Image, x: Number, y: Number, w: Number, h: Number,
        srcX: Number, srcY: Number, srcW: Number, srcH: Number,
        texW: Number, texH: Number, tint: Color?, radius: Number,
    ) = call("image")

    override fun lottie(path: String, x: Number, y: Number, w: Number, h: Number) = call("lottie")
    override fun item(id: String, x: Number, y: Number, w: Number, h: Number, radius: Number, tint: Color?) = call("item")
    override fun playerHead(uuid: String, name: String, textures: String?, x: Number, y: Number, w: Number, h: Number, radius: Number) =
        call("playerHead")

    override fun playerFace(name: String, x: Number, y: Number, w: Number, h: Number, showHat: Boolean, radius: Number): Boolean {
        call("playerFace")
        return false
    }

    override fun itemSpec(json: String, x: Number, y: Number, w: Number, h: Number, radius: Number) = call("itemSpec")
}
