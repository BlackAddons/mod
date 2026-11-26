package org.blackaddons.platform.ui.skin

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.AnimationMap
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.mixin.gui.AbstractContainerScreenAccessor
import org.spongepowered.asm.mixin.injection.invoke.arg.Args
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Container screens in [Glass]: a card panel and a cell per slot under the texture's own marks ([Decor]),
 * theme-colored labels and eased slot hover. The mixins call in; the feature sets [enabled] and [hideFillers].
 * Render thread only.
 */
object ContainerGlass {

    @JvmField var enabled = false
    @JvmField var hideFillers = true

    private var painted: AbstractContainerScreen<*>? = null
    private val hovers = AnimationMap<Int>()
    private val fadeIn = Animation(durationMs = Theme.ANIM_FAST.toLong())
    private var seen: AbstractContainerScreen<*>? = null
    private var seenTitle = ""
    private var seenClass: Class<*>? = null
    private var seenAt = TimeSource.Monotonic.markNow()

    private var fillerStacks = arrayOfNulls<ItemStack>(0)
    private var fillerResults = BooleanArray(0)

    /** Before the first background blit; [background] then swaps each blit's texture for its marks. */
    fun paint(screen: AbstractContainerScreen<*>, g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!enabled) {
            painted = null
            return
        }
        painted = screen
        val acc = screen as AbstractContainerScreenAccessor
        val left = acc.`blackaddons$getLeftPos`()
        val top = acc.`blackaddons$getTopPos`()
        val alpha = openAlpha(screen)
        val w = acc.`blackaddons$getImageWidth`()
        val h = acc.`blackaddons$getImageHeight`()
        SkinGlass.draw(g, left, top, w, h, Glass.CARD, alpha = alpha)
        for ((i, slot) in screen.menu.slots.withIndex()) {
            if (!slot.isActive || hides(screen, slot)) continue
            val x = left + slot.x
            val y = top + slot.y
            val over = slot.isHighlightable && mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17
            SkinGlass.draw(g, x, y, ITEM, ITEM, Glass.CELL, hovers[i, over], alpha)
        }
    }

    /** A background blit's arguments: the texture becomes its marks, minus the frames under this screen's slots. */
    fun background(screen: AbstractContainerScreen<*>, args: Args) {
        if (!skinned(screen) || args.size() != PLAIN_BLIT_ARGS) return
        val acc = screen as AbstractContainerScreenAccessor
        val dx = acc.`blackaddons$getLeftPos`() - args.get<Int>(2) + ITEM / 2
        val dy = acc.`blackaddons$getTopPos`() - args.get<Int>(3) + ITEM / 2
        val slots = screen.menu.slots
        val holes = IntArray(slots.size * 2)
        for ((i, slot) in slots.withIndex()) {
            holes[i * 2] = slot.x + dx
            holes[i * 2 + 1] = slot.y + dy
        }
        swap(args, holes)
    }

    /** The recipe book's panel blit, beside container screens. */
    fun recipeBook(g: GuiGraphicsExtractor, args: Args) {
        if (!enabled || args.size() != PLAIN_BLIT_ARGS) return
        SkinGlass.draw(g, args.get(2), args.get(3), args.get(6), args.get(7), Glass.CARD)
        swap(args, NO_HOLES)
    }

    /* Args of blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight). */
    private fun swap(args: Args, holes: IntArray) {
        val w = args.get<Int>(6)
        val h = args.get<Int>(7)
        val u = args.get<Float>(4).toInt()
        val v = args.get<Float>(5).toInt()
        val marks = Decor.region(args.get(1), u, v, w, h, args.get(8), args.get(9), holes) ?: return
        if (marks === Decor.EMPTY) {
            args.set(6, 0)
            return
        }
        args.set(1, marks)
        args.set(4, 0f)
        args.set(5, 0f)
        args.set(8, w)
        args.set(9, h)
    }

    fun skinned(screen: AbstractContainerScreen<*>): Boolean = enabled && screen === painted

    /** True while the open screen is a container this skin paints. */
    fun paintsCurrent(): Boolean = enabled && painted != null && painted === Minecraft.getInstance().currentScreen

    /** Vanilla's dark gray labels vanish on the card; other label colors (costs, warnings) mean something. */
    fun labelColor(screen: AbstractContainerScreen<*>, color: Int): Int =
        if (color == VANILLA_LABEL && skinned(screen)) Theme.TEXT_SECONDARY.rgb else color

    /** Skyblock's gray and black filler panes: blank custom name and no lore. Other colors carry meaning (terminals). */
    fun hides(screen: AbstractContainerScreen<*>, slot: Slot): Boolean {
        if (!hideFillers || !skinned(screen)) return false
        val stack = slot.item
        val item = stack.item
        if (item !== Items.STAINED_GLASS_PANE.gray() && item !== Items.STAINED_GLASS_PANE.black()) return false
        val i = slot.index
        if (i >= fillerStacks.size) {
            fillerStacks = fillerStacks.copyOf(i + 1)
            fillerResults = fillerResults.copyOf(i + 1)
        }
        if (fillerStacks[i] !== stack) {
            fillerStacks[i] = stack
            fillerResults[i] = isFiller(stack)
        }
        return fillerResults[i]
    }

    private fun isFiller(stack: ItemStack): Boolean {
        val name = stack.get(DataComponents.CUSTOM_NAME) ?: return false
        val lore = stack.get(DataComponents.LORE)
        return (lore == null || lore.lines().isEmpty()) && ChatFormatting.stripFormatting(name.string).isNullOrBlank()
    }

    private fun openAlpha(screen: AbstractContainerScreen<*>): Float {
        if (screen !== seen) {
            val title = screen.title.string
            val reopen = title == seenTitle && screen.javaClass == seenClass && seenAt.elapsedNow() < REOPEN
            if (!reopen) {
                fadeIn.set(0f)
                fadeIn.animateTo(1f)
            }
            seen = screen
            seenTitle = title
            seenClass = screen.javaClass
        }
        seenAt = TimeSource.Monotonic.markNow()
        return fadeIn.get()
    }

    private const val ITEM = 16
    private val REOPEN = 1.seconds
    private const val PLAIN_BLIT_ARGS = 10
    private const val VANILLA_LABEL = -12566464
    private val NO_HOLES = IntArray(0)
}
