package org.blackaddons.core.ui.screen.pv.tabs
import org.blackaddons.api.skyblock.profile.InventoryPage
import org.blackaddons.api.skyblock.profile.ProfileMember
import org.blackaddons.api.skyblock.profile.ProfilePet
import org.blackaddons.api.ui.layout.Cards
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.sectionHeader
import org.blackaddons.api.ui.layout.valueRow
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.ui.widget.Widget
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.core.profile.pets.PetLeveling
import org.blackaddons.core.skyblock.SkyblockItems
import org.blackaddons.core.ui.screen.pv.PvFormat
import org.blackaddons.core.ui.screen.pv.parts.PvIcons
import org.blackaddons.core.ui.screen.pv.parts.PvProgressCard
import java.awt.Color
import kotlin.math.roundToInt

private val tr = I18n.scope("blackaddons.pv")

internal class PvPetsTab {

    internal data class DisplayPet(val key: String, val pet: ProfilePet, val progress: PetLeveling.Progress)

    val widgets = WidgetHost()
    private val grid = widgets.add(PetGrid { selectedKey = it })
    private var builtFrom: List<ProfilePet>? = null
    private var displayedPets = emptyList<DisplayPet>()
    private var selectedKey: String? = null

    fun reset() {
        builtFrom = null
        displayedPets = emptyList()
        selectedKey = null
        grid.reset()
    }

    fun hide() = grid.clear()

    fun render(x: Float, y: Float, width: Float, height: Float, member: ProfileMember) {
        if (InventoryPage.PETS !in member.inventoryPages) {
            hide()
            renderEmpty(x, y, width, height, "api_off_title", "api_off_detail")
            return
        }
        val pets = displayPetsFor(member.pets)
        if (pets.isEmpty()) {
            hide()
            renderEmpty(x, y, width, height, "empty_title", "empty_detail")
            return
        }

        val selected = pets.firstOrNull { it.key == selectedKey }
            ?: pets.firstOrNull { it.pet.active }
            ?: pets.first().also { selectedKey = it.key }
        selectedKey = selected.key

        val summary = tr("pets_detail.summary", "%d pets · %d maxed").format(pets.size, pets.count { it.progress.maxed })
        val top = column(width = width) { sectionHeader(width, tr("pets_detail.title", "Pets"), summary) }
        val bodyH = (height - top.height - Cards.GAP).coerceAtLeast(0f)
        val listW = (width * LIST_PORTION).coerceAtLeast(0f)
        val detailsW = (width - listW - Cards.GAP).coerceAtLeast(0f)
        column(spacing = Cards.GAP, width = width) {
            add(top)
            row(spacing = Cards.GAP, width = width) {
                cell(listW, bodyH) { gx, gy -> grid.render(gx, gy, listW, bodyH, pets, selectedKey) }
                add(detailsCard(detailsW, bodyH, selected, pets.any { !it.progress.available }))
            }
        }.draw(x, y)
    }

    /** Pet XP doesn't change while open, so resolve and sort once per profile. */
    private fun displayPetsFor(pets: List<ProfilePet>): List<DisplayPet> {
        if (builtFrom === pets) return displayedPets
        hide()
        builtFrom = pets
        displayedPets = displayPets(pets)
        selectedKey = null
        grid.reset()
        return displayedPets
    }

    private fun detailsCard(width: Float, height: Float, selected: DisplayPet, hasIssue: Boolean): LayoutNode {
        val pet = selected.pet
        val progress = selected.progress
        val footer: (LayoutBuilder.(Float) -> Unit)? = if (!hasIssue) null else { innerW ->
            cell(innerW, NOTICE_H) { nx, ny ->
                Render.rect(nx, ny, innerW, NOTICE_H, Theme.ACCENT_SOFT, Theme.RADIUS_SMALL)
                Render.text(
                    tr("pets_detail.notice", "Some pets have unavailable level data."), nx + Theme.PADDING,
                    Render.centerY(ny, NOTICE_H, TextStyle.SMALL_SECONDARY), TextStyle.SMALL_SECONDARY, maxWidth = innerW - Theme.PADDING * 2f,
                )
            }
        }
        return Cards.card(width, tr("pets_detail.info", "Info"), minHeight = height, footer = footer) { innerW ->
            identity(innerW, pet)
            if (!progress.available) {
                text(tr("pets_detail.level_unavailable", "Level data unavailable"), Theme.FONT_BODY, Theme.DANGER, Fonts.medium)
                val detail = tr("pets_detail.level_unavailable_detail", "This pet's tier or XP is not valid profile data.")
                cell(innerW, Render.wrappedTextHeight(detail, innerW, Theme.FONT_SMALL, Fonts.regular)) { dx, dy ->
                    Render.wrappedText(detail, dx, dy, innerW, TextStyle.SMALL_MUTED)
                }
                valueRow(innerW, tr("pets_detail.xp", "Pet XP"), PvFormat.exact(pet.experience))
                return@card
            }
            text(tr("level", "Level") + " ${progress.level} / ${progress.cap}", Theme.FONT_BODY, Theme.ACCENT, Fonts.medium)
            valueRow(innerW, tr("pets_detail.xp", "Pet XP"), PvFormat.exact(pet.experience))
            cell(innerW, Cards.ROW_H) { bx, by -> Cards.drawBar(bx, by, innerW, Cards.ROW_H, progress.toNext ?: 0.0, rarityColor(pet.tier)) }
            val next = if (progress.maxed) {
                tr("pets_detail.max_level", "Maximum level reached")
            } else {
                tr("pets_detail.to_next", "%d%% to next level").format(((progress.toNext ?: 0.0) * 100).roundToInt())
            }
            text(next, TextStyle.SMALL_SECONDARY)
            if (!progress.maxed) text(tr("pets_detail.total_progress", "%d%% to maximum").format(((progress.toMax ?: 0.0) * 100).roundToInt()), TextStyle.SMALL_MUTED)
            if (pet.candyUsed > 0) valueRow(innerW, tr("pets_detail.candy_used", "Candy Used"), "${pet.candyUsed} / 10")
            pet.heldItem?.let { heldItem(innerW, it) }
        }
    }

    /** "Held Item" on the left, the item's name in its rarity color and its icon on the right. */
    private fun LayoutBuilder.heldItem(w: Float, id: String) {
        cell(w, HELD_ITEM_SIZE) { hx, hy ->
            val label = tr("pets_detail.held_item", "Held Item")
            Render.text(label, hx, Render.centerY(hy, HELD_ITEM_SIZE, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED)
            val iconX = hx + w - HELD_ITEM_SIZE
            SkyblockItems.spec(id)?.let { Render.itemSpec(it, iconX, hy, HELD_ITEM_SIZE, HELD_ITEM_SIZE) }
            val nameRoom = iconX - Theme.PADDING_SMALL - (hx + Render.textWidth(label, TextStyle.SMALL_MUTED) + Theme.PADDING)
            val color = SkyblockItems.tier(id)?.let(::rarityColor) ?: Theme.TEXT_PRIMARY
            Render.textRight(
                SkyblockItems.name(id) ?: prettyName(id), iconX - Theme.PADDING_SMALL,
                Render.centerY(hy, HELD_ITEM_SIZE, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, color, Fonts.medium,
                maxWidth = nameRoom,
            )
        }
    }

    /** Icon, name and rarity, with "Active" on the right for the summoned pet. */
    private fun LayoutBuilder.identity(w: Float, pet: ProfilePet) {
        val active = tr("active", "Active")
        val activeW = if (pet.active) Render.textWidth(active, Theme.FONT_SMALL, Fonts.medium) + Theme.PADDING else 0f
        cell(w, ICON_SIZE) { ix, iy ->
            PvProgressCard.drawIcon(PvIcons.pet(pet), ix, iy, ICON_SIZE)
            val textX = ix + ICON_SIZE + Theme.PADDING_SMALL
            val nameH = Render.textHeight(Theme.FONT_TITLE, Fonts.medium)
            val tierH = Render.textHeight(Theme.FONT_SMALL, Fonts.medium)
            val textY = iy + (ICON_SIZE - nameH - Theme.PADDING_SMALL - tierH) / 2f
            Render.text(prettyName(pet.type), textX, textY, TextStyle.TITLE_PRIMARY_MEDIUM, maxWidth = (w - (textX - ix) - activeW).coerceAtLeast(0f))
            Render.text(prettyName(pet.tier), textX, textY + nameH + Theme.PADDING_SMALL, Theme.FONT_SMALL, rarityColor(pet.tier), Fonts.medium)
            if (pet.active) Render.text(active, ix + w - (activeW - Theme.PADDING), Render.centerY(iy, ICON_SIZE, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, Theme.SUCCESS, Fonts.medium)
        }
    }

    private fun renderEmpty(x: Float, y: Float, width: Float, height: Float, titleKey: String, detailKey: String) {
        val apiOff = titleKey == "api_off_title"
        Cards.empty(
            width, height,
            tr("pets_detail.$titleKey", if (apiOff) "Pet API unavailable" else "No pets recorded"),
            tr("pets_detail.$detailKey", if (apiOff) "This profile does not expose its pet collection." else "This profile has no saved pets."),
        ).draw(x, y)
    }

    private class PetGrid(private val onSelect: (String) -> Unit) : Widget {
        private data class Cell(val key: String, val bounds: Rect)

        private val scroll = Scroll().apply { showTrack = false }
        private var cells = emptyList<Cell>()
        private var pets = emptyList<DisplayPet>()
        private var selectedKey: String? = null
        private var boxWidth = 0f
        private var boxHeight = 0f

        fun render(x: Float, y: Float, width: Float, height: Float, pets: List<DisplayPet>, selectedKey: String?) {
            boxWidth = width
            boxHeight = height
            this.pets = pets
            this.selectedKey = selectedKey
            draw(x, y)
        }

        override val width: Float get() = boxWidth
        override val height: Float get() = boxHeight

        override fun draw(x: Float, y: Float) {
            val width = boxWidth
            val height = boxHeight
            Cards.drawCard(x, y, width, height)
            val innerW = (width - Theme.PADDING * 2f - SCROLL_GUTTER).coerceAtLeast(SLOT_SIZE)
            val slot = slotSize(innerW, height - Theme.PADDING * 2f, pets.size)
            val scale = slot / SLOT_SIZE
            val columns = columns(innerW, slot)
            val gridW = columns * slot + (columns - 1) * SLOT_GAP
            val left = centered(x, width, gridW)
            val rows = (pets.size + columns - 1) / columns
            val contentH = Theme.PADDING * 2f + rows * slot + (rows - 1).coerceAtLeast(0) * SLOT_GAP
            val offset = scroll.update(x, y, width, height, contentH)
            val visible = ArrayList<Cell>(pets.size)
            val levelFont = LEVEL_FONT * scale

            Render.pushScissor(x, y, width, height)
            try {
                pets.forEachIndexed { index, display ->
                    val slotX = left + index % columns * (slot + SLOT_GAP)
                    val slotY = y + Theme.PADDING + index / columns * (slot + SLOT_GAP) - offset
                    if (slotY + slot < y || slotY > y + height) return@forEachIndexed
                    val hitTop = maxOf(slotY, y)
                    val hitBottom = minOf(slotY + slot, y + height)
                    val hitBounds = Rect(slotX, hitTop, slot, hitBottom - hitTop)
                    visible += Cell(display.key, hitBounds)
                    val selected = display.key == selectedKey
                    Render.rect(slotX, slotY, slot, slot, if (selected) Theme.SURFACE_ACTIVE else Theme.BACKGROUND, Theme.RADIUS_SMALL)
                    if (selected) Render.hollowRect(slotX, slotY, slot, slot, 1f, rarityColor(display.pet.tier), Theme.RADIUS_SMALL)
                    val inset = SLOT_INSET * scale
                    PvProgressCard.drawIcon(PvIcons.pet(display.pet), slotX + inset, slotY + inset, slot - inset * 2f)
                    val level = display.progress.level.takeIf { display.progress.available }?.toString() ?: "?"
                    val levelW = Render.textWidth(level, levelFont, Fonts.bold)
                    Render.rect(slotX + slot - levelW - 5f * scale, slotY + slot - levelFont - 4f * scale, levelW + 4f * scale, levelFont + 3f * scale, Theme.BACKGROUND, Theme.RADIUS_SMALL)
                    Render.text(level, slotX + slot - levelW - 3f * scale, slotY + slot - levelFont - 3f * scale, levelFont, rarityColor(display.pet.tier), Fonts.bold)
                    if (display.pet.active) Render.circle(slotX + 4f * scale, slotY + 4f * scale, 2f * scale, Theme.SUCCESS)
                    if (hitBounds.has(UiMouse.x, UiMouse.y)) Tooltip.show(display.tooltip())
                }
            } finally {
                Render.popScissor()
            }
            cells = visible
            scroll.draw()
        }

        private fun columns(width: Float, slot: Float): Int = ((width + SLOT_GAP) / (slot + SLOT_GAP)).toInt().coerceAtLeast(1)

        // A short pet list grows its slots until it would need to scroll, so a few pets don't sit in a corner.
        private fun slotSize(width: Float, height: Float, count: Int): Float =
            generateSequence(MAX_SLOT_SIZE) { it - SLOT_STEP }.takeWhile { it > SLOT_SIZE }.firstOrNull { slot ->
                val rows = (count + columns(width, slot) - 1) / columns(width, slot)
                rows * slot + (rows - 1) * SLOT_GAP <= height
            } ?: SLOT_SIZE

        override fun mouseClicked(mouse: MouseButtonInput): Boolean {
            if (scroll.mouseClicked(mouse)) return true
            if (!mouse.isLeft) return false
            val cell = cells.firstOrNull { it.bounds.has(mouse.x, mouse.y) } ?: return false
            onSelect(cell.key)
            return true
        }

        override fun mouseReleased(mouse: MouseButtonInput): Boolean = scroll.mouseReleased(mouse)
        override fun mouseDragged(drag: MouseDragInput): Boolean =
            scroll.mouseDragged(drag)
        override fun mouseScrolled(wheel: MouseScrollInput): Boolean = scroll.mouseScrolled(wheel)
        fun reset() {
            scroll.reset()
            clear()
        }
        fun clear() {
            cells = emptyList()
            pets = emptyList()
        }

        private fun DisplayPet.tooltip() = buildString {
            append(prettyName(pet.type)).append('\n').append(prettyName(pet.tier))
            if (progress.available) append('\n').append(tr("level", "Level")).append(": ").append(progress.level).append(" / ").append(progress.cap)
            append('\n').append(tr("pets_detail.xp", "Pet XP")).append(": ").append(PvFormat.exact(pet.experience))
            if (pet.active) append('\n').append(tr("active", "Active"))
        }
    }

    companion object {
        internal fun displayPets(pets: List<ProfilePet>): List<DisplayPet> = pets.mapIndexed { index, pet ->
            DisplayPet("$index:${pet.type}:${pet.tier}:${pet.experience}:${pet.active}", pet, PetLeveling.resolve(pet))
        }.sortedWith(compareByDescending<DisplayPet> { it.pet.active }.thenByDescending { rarityRank(it.pet.tier) }.thenByDescending { it.pet.experience })

        private fun prettyName(type: String): String = type.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        private fun rarityRank(tier: String): Int = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "VERY_SPECIAL").indexOf(tier.uppercase())
        private fun rarityColor(tier: String): Color = when (tier.uppercase()) {
            "UNCOMMON" -> Color(85, 255, 85)
            "RARE" -> Color(85, 85, 255)
            "EPIC" -> Color(170, 85, 255)
            "LEGENDARY" -> Color(255, 170, 0)
            "MYTHIC" -> Color(255, 85, 255)
            "SPECIAL", "VERY_SPECIAL" -> Color(255, 85, 85)
            else -> Theme.TEXT_SECONDARY
        }

        private const val LIST_PORTION = 0.64f
        private const val ICON_SIZE = 38f
        private const val SLOT_SIZE = 32f
        private const val MAX_SLOT_SIZE = 56f
        private const val SLOT_STEP = 2f
        private const val SLOT_GAP = 5f
        private const val SLOT_INSET = 3f
        private const val LEVEL_FONT = 8f
        private const val SCROLL_GUTTER = 8f
        private const val HELD_ITEM_SIZE = 18f
        private const val NOTICE_H = 34f
    }
}
