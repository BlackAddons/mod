package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.node.NodeProperty
import org.blackaddons.api.ui.node.NodeRows
import org.blackaddons.api.ui.node.nodeProperties
import org.blackaddons.api.util.cleanCommand
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.ticksToSeconds
import org.blackaddons.core.actions.ActionDungeonEvent
import org.blackaddons.core.actions.ActionFeedbackProvider
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.ChatAction
import org.blackaddons.core.actions.LocationAction
import org.blackaddons.core.actions.ShortcutAction
import org.blackaddons.core.actions.StepTypes
import org.blackaddons.core.actions.TriggeredBy
import org.blackaddons.core.actions.WaypointShape
import org.blackaddons.core.actions.steps.ControlStep
import org.blackaddons.core.actions.steps.locationFilter
import org.blackaddons.core.actions.steps.prop
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.modals.bindingLabel
import org.blackaddons.core.ui.screen.actions.translatedName
import org.blackaddons.core.ui.screen.actions.typeProperties

internal object ActionGraphProperties {

    fun of(ref: TriggerRef): List<NodeProperty> = nodeProperties {
        when (ref) {
            is TriggerRef.Chat -> chat(ref.action)
            is TriggerRef.Waypoint -> waypoint(ref)
            is TriggerRef.Shortcut -> shortcut(ref.shortcut)
            is TriggerRef.Location -> location(ref.action)
        }
    }

    fun of(step: ActionStep): List<NodeProperty> = nodeProperties {
        flags(
            prop("options", "Options"),
            listOf(NodeRows.Flag("wait_previous", prop("wait_previous", "Wait for previous"), step::waitForPrevious)) +
                StepTypes.flagsFor(step.type).map { flag -> NodeRows.Flag(flag.id, flag.label, { step.has(flag) }) { step.set(flag, it) } },
        )
        triggeredBy(step)
    } + step.typeProperties().filterNot { it is NodeProperty.Note }

    /** For the step editor, whose header already shows the type, "wait for previous" and the on/off switch. */
    fun editorRows(step: ActionStep): List<NodeProperty> = nodeProperties {
        StepTypes.flagsFor(step.type).forEach { flag -> toggle(flag.label, { step.has(flag) }) { step.set(flag, it) } }
        triggeredBy(step)
    } + step.typeProperties()

    // Control steps run for anyone.
    private fun NodeRows.triggeredBy(step: ActionStep) {
        if (step.type !is ControlStep) choice(prop("triggered_by", "Triggered by"), TriggeredBy.entries, { it.translatedName }, step::triggeredBy)
    }

    private fun NodeRows.chat(action: ChatAction) {
        text(prop("pattern", "Pattern"), action::pattern, actionTr("chat.pattern_placeholder", "Pattern / Message"))
        flags(
            prop("options", "Options"),
            listOf(
                NodeRows.Flag("regex", prop("regex", "Regex"), action::isRegex),
                NodeRows.Flag("ignore_own", prop("ignore_own", "Ignore my messages"), action::ignoreOwnMessages),
            ),
        )
        feedback(action) {}
    }

    private fun NodeRows.waypoint(ref: TriggerRef.Waypoint) {
        val wp = ref.waypoint
        val action = ref.action
        val attach = { if (wp.actions.none { it === action }) wp.actions.add(action) }
        if (ref.index == 0) {
            text(prop("name", "Name"), wp::name, actionTr("waypoint.name_placeholder", "Waypoint Name"))
            coordinates(wp::x, wp::y, wp::z)
            number(actionTr("radius", "Radius"), wp::radius, 0.05, 0.01, 10.0)
            number(actionTr("height", "Height"), wp::height, 0.05, 0.01, 2.0)
            number(actionTr("cooldown_label", "Cooldown"), wp::reuseCooldownSeconds, 0.5, 0.0, 60.0, 1)
            choice(prop("shape", "Shape"), WaypointShape.entries, { actionTr("waypoint.shape.${it.name.lowercase()}", it.name) }, wp::shape)
            add(NodeProperty.Swatch(prop("color", "Color"), { wp.colorRgb and RGB }, { wp.colorRgb = it }))
            flags(
                prop("show", "Show"),
                listOf(
                    NodeRows.Flag("outline", prop("outline", "Outline"), wp::showShape),
                    NodeRows.Flag("filled", prop("filled", "Filled"), wp::showFullShape),
                    NodeRows.Flag("text", prop("text", "Text"), wp::showText),
                    NodeRows.Flag("beacon", prop("beacon", "Beacon"), wp::showBeacon),
                ),
            )
        }
        flags(
            prop("triggers", "Triggers"),
            listOf(
                NodeRows.Flag("entry", actionTr("waypoint.trigger.entry", "Entry"), { action.triggerOnEntry }) { attach(); action.triggerOnEntry = it },
                NodeRows.Flag("exit", actionTr("waypoint.trigger.exit", "Exit"), { action.triggerOnExit }) { attach(); action.triggerOnExit = it },
                NodeRows.Flag("gui_exit", actionTr("waypoint.trigger.gui_exit", "GUI Exit"), { action.triggerOnGuiExit }) { attach(); action.triggerOnGuiExit = it },
            ),
        )
        choice(prop("triggered_by", "Triggered by"), TriggeredBy.entries.map { it.translatedName }, { action.triggeredBy.ordinal }) {
            attach()
            action.triggeredBy = TriggeredBy.entries[it]
        }
        feedback(action, attach)
    }

    private fun NodeRows.shortcut(shortcut: ShortcutAction) {
        val fallback = { shortcut.alias.ifEmpty { actionTr("shortcut.fallback", "Shortcut") } }
        text(prop("name", "Name"), { shortcut.name }, { shortcut.name = it.trim().ifEmpty(fallback) }, actionTr("shortcut.name_placeholder", "Shortcut Name (e.g. Warp Forge)"))
        text(prop("alias", "Alias"), { shortcut.alias }, { shortcut.alias = it.cleanCommand() }, actionTr("shortcut.alias_placeholder", "Command Alias without / (e.g. wf)"))
        add(
            NodeProperty.Binding(
                prop("keybind", "Keybind"), { shortcut.key }, { shortcut.key = it },
                ::bindingLabel, actionTr("shortcut.press_key", "Press a key / click..."),
            ),
        )
    }

    private fun NodeRows.location(action: LocationAction) {
        text(
            prop("name", "Name"), { action.name },
            { action.name = it.trim().ifEmpty { actionTr("location.fallback", "Location Trigger") } },
            actionTr("location.name_placeholder", "Trigger Name (e.g. Enter F7)"),
        )
        locationFilter({ action }, { action })
        val triggers = listOf(actionTr("location.on_enter", "On entering the location")) +
            ActionDungeonEvent.entries.map { actionTr("location.on_event", "On %s", it.translatedName) }
        choice(prop("trigger", "Trigger"), triggers, { action.triggerEvent?.let { it.ordinal + 1 } ?: 0 }) { action.triggerEvent = ActionDungeonEvent.entries.getOrNull(it - 1) }
        number(prop("delay", "Delay"), action::delaySeconds, 0.1, 0.0, 60.0, 1)
        add(
            NodeProperty.Number(
                prop("tick_delay", "Tick delay"), { action.delayServerTicks.toDouble() }, { action.delayServerTicks = it.toInt() }, 1.0, 0.0, decimals = 0,
            ) { " (${it.toInt().ticksToSeconds.formatDecimals()}s)" },
        )
        number(actionTr("cooldown_label", "Cooldown"), action::cooldownSeconds, 0.5, 0.5, 60.0, 1)
    }

    private fun NodeRows.feedback(f: ActionFeedbackProvider, attach: () -> Unit) {
        text(
            prop("sound", "Sound"), { f.soundId }, { attach(); f.soundId = it.trim() }, DEFAULT_SOUND,
            suggestions = { query ->
                val ids = Platform.feedback.soundIds()
                val lower = query.lowercase()
                (if (lower.isEmpty()) ids else ids.filter { it.contains(lower) }).take(SOUND_SUGGESTIONS)
            },
            suggestionDisplay = { it.removePrefix("minecraft:") },
        )
        buttons(actionTr("feedback.test", "Test") to { Platform.feedback.playSound(f.soundId.ifEmpty { DEFAULT_SOUND }, f.volume, f.pitch) })
        number(actionTr("feedback.volume", "Volume"), { f.volume.toDouble() }, { attach(); f.volume = it.toFloat() }, 0.05, 0.0, 1.0)
        number(actionTr("feedback.pitch", "Pitch"), { f.pitch.toDouble() }, { attach(); f.pitch = it.toFloat() }, 0.05, 0.5, 2.0)
        text(prop("title", "Title"), { f.title }, { attach(); f.title = it.trim() }, actionTr("feedback.title_placeholder", "Screen Title"))
        text(prop("subtitle", "Subtitle"), { f.subtitle }, { attach(); f.subtitle = it.trim() }, actionTr("feedback.subtitle_placeholder", "Screen Subtitle"))
        number(prop("title_time", "Title time"), { f.durationSeconds.toDouble() }, { attach(); f.durationSeconds = it.toFloat() }, 0.5, 0.5, 10.0, 1)
        toggle(prop("notification", "Notification"), { f.showNotification }) { attach(); f.showNotification = it }
        if (!f.showNotification) return
        text(prop("toast_title", "Toast title"), { f.notificationTitle }, { attach(); f.notificationTitle = it.trim() }, actionTr("feedback.toast_title_placeholder", "Toast Notification Title"))
        text(prop("toast_message", "Toast message"), { f.notificationMessage }, { attach(); f.notificationMessage = it.trim() }, actionTr("feedback.toast_message_placeholder", "Toast Notification Message"))
    }

    private const val RGB = 0xFFFFFF
    private const val SOUND_SUGGESTIONS = 50
    private const val DEFAULT_SOUND = "minecraft:entity.experience_orb.pickup"
}
