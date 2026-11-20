package org.blackaddons.platform.impl.gui
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.gui.ContainerOverlay
import org.blackaddons.api.gui.ContainerOverlayRegistry
import org.blackaddons.api.gui.GuiBox
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.blackskija.backend.common.SkijaCompositor
import org.blackaddons.platform.Installable
import org.blackaddons.platform.event.impl.RenderScreenBodyEvent
import org.blackaddons.platform.impl.input.MinecraftKeyNames
import org.blackaddons.platform.mixin.gui.AbstractContainerScreenAccessor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [ContainerOverlayRegistry] using fabric-screen-api for input: on each container init, applicable overlays hook
 * `allowMouseClick`/`allowMouseScroll`/`allowKeyPress` (false blocks vanilla). The real container stays open
 * underneath. Fabric drops listeners on every init including resize, so hooks get reattached each time.
 *
 * Drawing runs on [RenderScreenBodyEvent], inside the screen's extraction (Fabric's `afterExtract` landed under
 * `UiManager` popups). [ContainerOverlay.renderUi] gets its own GUI stratum, then [ContainerOverlay.render] a fresh
 * one, so vanilla items can't sort under the toolkit layer.
 */
abstract class AbstractContainerOverlays : ContainerOverlayRegistry, Installable {

    // Registered from any thread, iterated per init and frame.
    private val overlays = CopyOnWriteArrayList<ContainerOverlay>()

    override fun register(overlay: ContainerOverlay) {
        overlays.add(overlay)
    }

    override fun install() {
        EventListener(RenderScreenBodyEvent::class.java, EventPriority.NORMAL) {
            (event.screen as? AbstractContainerScreen<*>)?.let { draw(it, event.graphics, event.mouseX, event.mouseY) }
        }.register()
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            val title = screen.title.string
            if (overlays.none { it.appliesTo(title) }) return@register

            if (overlays.any { it.appliesTo(title) && it.repositionInventory(title) }) anchorInventoryToBottom(screen)

            ScreenMouseEvents.allowMouseClick(screen).register { s, click ->
                val m = metrics(s as AbstractContainerScreen<*>)
                overlays.none { it.appliesTo(title) && it.mouseClicked(m, MouseButtonInput(click.x().toFloat(), click.y().toFloat(), MinecraftKeyNames.button(click.button()))) }
            }
            ScreenMouseEvents.allowMouseScroll(screen).register { s, mouseX, mouseY, _, vertical ->
                val m = metrics(s as AbstractContainerScreen<*>)
                overlays.none { it.appliesTo(title) && it.mouseScrolled(m, MouseScrollInput(mouseX.toFloat(), mouseY.toFloat(), vertical.toFloat())) }
            }
            ScreenMouseEvents.allowMouseDrag(screen).register { s, event, deltaX, deltaY ->
                val m = metrics(s as AbstractContainerScreen<*>)
                overlays.none { it.appliesTo(title) && it.mouseDragged(m, MouseDragInput(event.x().toFloat(), event.y().toFloat(), MinecraftKeyNames.button(event.button()), deltaX.toFloat(), deltaY.toFloat())) }
            }
            ScreenMouseEvents.allowMouseRelease(screen).register { _, click ->
                // Lets overlays end drags, never blocks the release.
                val mouse = MouseButtonInput(click.x().toFloat(), click.y().toFloat(), MinecraftKeyNames.button(click.button()))
                for (o in overlays) if (o.appliesTo(title)) o.mouseReleased(mouse)
                true
            }
            ScreenKeyboardEvents.allowKeyPress(screen).register { s, key ->
                val m = metrics(s as AbstractContainerScreen<*>)
                overlays.none { it.appliesTo(title) && it.keyPressed(m, KeyInput(key.key(), MinecraftKeyNames.modifiers(key.modifiers()))) }
            }
        }
    }

    private fun draw(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val title = screen.title.string
        val active = overlays.filter { it.appliesTo(title) }
        if (active.isEmpty()) return
        if (active.any { it.repositionInventory(title) }) anchorInventoryToBottom(screen)
        val m = metrics(screen)
        Render.beginPass()
        UiMouse.set(mouseX.toFloat(), mouseY.toFloat())
        val scale = skijaScale()
        for (o in active) Render.isolated {
            Render.push()
            Render.scale(scale, scale)
            o.renderUi(m, mouseX, mouseY)
            Render.pop()
        }
        if (!UiManager.isOpen) Render.isolated {
            Render.push()
            Render.scale(scale, scale)
            Tooltip.flush(mouseX.toFloat(), mouseY.toFloat())
            Render.pop()
        }
        SkijaCompositor.markScreenLayer(graphics)
        graphics.nextStratum()
        for (o in active) o.render(graphics, m, mouseX, mouseY)
    }

    /** From the charTyped mixin. */
    fun charTyped(screen: AbstractContainerScreen<*>, codepoint: Int): Boolean {
        val title = screen.title.string
        return overlays.any { it.appliesTo(title) && it.charTyped(codepoint) }
    }

    /** What the open container's overlays draw outside it, for REI. */
    fun exclusionZones(screen: AbstractContainerScreen<*>): List<GuiBox> {
        val title = screen.title.string
        val active = overlays.filter { it.appliesTo(title) }
        if (active.isEmpty()) return emptyList()
        val m = metrics(screen)
        return active.flatMap { it.exclusionZones(m) }
    }

    /** Skip vanilla's draw so the overlay sits over the world. It redraws what it needs. */
    fun suppressesVanilla(screen: AbstractContainerScreen<*>): Boolean {
        val title = screen.title.string
        return overlays.any { it.appliesTo(title) && it.suppressesVanilla(title) }
    }

    fun keepsCursor(screen: AbstractContainerScreen<*>): Boolean {
        val title = screen.title.string
        return overlays.any { it.appliesTo(title) && it.keepsCursor(title) }
    }

    /** Moves the inventory to the bottom. Moving `topPos` moves slots and their hit-testing together. */
    private fun anchorInventoryToBottom(screen: AbstractContainerScreen<*>) {
        val acc = screen as AbstractContainerScreenAccessor
        val inv = playerStorageSlots(screen)
        acc.`blackaddons$setTopPos`(screen.height - BOTTOM_MARGIN - (inv.maxOf { it.y } + 16))
    }

    /*
     * Container units to Skija's: BetterScreens extracts containers at its own GUI scale, Skija composites at the
     * game's.
     */
    private fun skijaScale(): Float {
        val mc = Minecraft.getInstance()
        val window = mc.window
        return window.guiScale.toFloat() / window.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode)
    }

    private fun metrics(screen: AbstractContainerScreen<*>): ContainerMetrics {
        val acc = screen as AbstractContainerScreenAccessor
        val top = acc.`blackaddons$getTopPos`()
        val left = acc.`blackaddons$getLeftPos`()
        val inv = playerStorageSlots(screen)
        val inventoryTop = top + inv.minOf { it.y } - 2
        val inventoryBottom = top + inv.maxOf { it.y } + 16 + 2
        val inventoryLeft = left + inv.minOf { it.x } - 2
        val inventoryRight = left + inv.maxOf { it.x } + 16 + 2
        val gui = GuiBox(left, top, acc.`blackaddons$getImageWidth`(), acc.`blackaddons$getImageHeight`())
        return ContainerMetrics(
            screen.width, screen.height, inventoryTop, inventoryLeft, inventoryRight, inventoryBottom, gui, suppressesVanilla(screen),
        )
    }

    private fun playerStorageSlots(screen: AbstractContainerScreen<*>): List<Slot> {
        val inventory = Minecraft.getInstance().player?.inventory
        val slots = screen.menu.slots.filter { it.container === inventory && it.containerSlot in 0 until 36 }
        return slots.takeIf { it.size == 36 } ?: screen.menu.slots.takeLast(36)
    }

    private companion object {
        const val BOTTOM_MARGIN = 6
    }
}
