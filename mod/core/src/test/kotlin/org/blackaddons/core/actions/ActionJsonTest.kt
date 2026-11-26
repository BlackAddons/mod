package org.blackaddons.core.actions

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.blackaddons.api.actions.StepContext
import org.blackaddons.api.actions.StepFlag
import org.blackaddons.api.actions.StepType
import org.blackaddons.core.actions.steps.RepeatStep
import org.blackaddons.core.actions.steps.SendMessageStep
import org.blackaddons.core.actions.steps.TextOverlayStep
import org.blackaddons.core.actions.steps.WaitStep
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActionJsonTest {

    private val gson = ActionJson.gson()

    private fun fieldsOf(value: Any) =
        GsonBuilder().serializeNulls().create().toJsonTree(value).asJsonObject.keySet().filterNot { it.startsWith("legacy") }.toSet()

    private fun json(step: ActionStep) = JsonParser.parseString(gson.toJson(step)).asJsonObject

    private fun read(text: String): ActionStep = gson.fromJson(text, ActionStep::class.java)

    @Test
    fun `every condition field belongs to some condition type`() {
        val claimed = ActionJson.CONDITION_COMMON + ActionJson.CONDITION_FIELDS.values.flatten()
        assertEquals(emptySet<String>(), fieldsOf(ActionCondition()) - claimed)
        assertEquals(ConditionType.entries.toSet(), ActionJson.CONDITION_FIELDS.keys)
    }

    @Test
    fun `a step writes its type and only its non-default fields`() {
        val step = ActionStep.of(SendMessageStep, SendMessageStep.Params("/pc hi"), enabled = false)

        assertEquals(setOf("type", "message", "enabled", "triggeredBy"), json(step).keySet())
        val back = gson.fromJson(json(step), ActionStep::class.java)
        assertEquals("/pc hi", back.paramsOf(SendMessageStep)?.message)
        assertFalse(back.enabled)
        assertTrue(back.waitForPrevious)
        assertEquals(TriggeredBy.ME, back.triggeredBy)
        assertEquals(TriggeredBy.ANYONE, read("""{"type":"SEND_MESSAGE"}""").triggeredBy)
    }

    @Test
    fun `defaults are the params class defaults, not the ones a new step gets`() {
        assertEquals(1.0, json(ActionStep.of(TextOverlayStep)).get("durationSeconds").asDouble)
        assertEquals(0f, read("""{"type":"TEXT_OVERLAY"}""").paramsOf(TextOverlayStep)?.durationSeconds)
    }

    @Test
    fun `what a step uses survives the round trip`() {
        val near = ActionCondition(type = ConditionType.NEAR, negate = true, x = 1.0, y = 2.0, z = 3.0, radius = 5.0, item = "dropped")
        val loop = ActionStep.of(RepeatStep, RepeatStep.Params(repeatWhile = true, condition = near), enabled = false)

        val back = gson.fromJson(gson.toJson(loop), ActionStep::class.java)
        assertFalse(back.enabled)
        assertEquals(RepeatStep.Params(repeatWhile = true, condition = near.copy(item = "")), back.paramsOf(RepeatStep))
    }

    @Test
    fun `old flat steps keep reading, other types' fields ignored`() {
        val step = read("""{"type":"WAIT","waitMode":"CHAT","waitText":"maxor","message":"left over","yaw":90.0}""")

        assertSame(WaitStep, step.type)
        assertEquals(WaitStep.Params(waitMode = WaitMode.CHAT, waitText = "maxor"), step.paramsOf(WaitStep))
        assertEquals(setOf("type", "waitMode", "waitText"), json(step).keySet())
    }

    @Test
    fun `flags and fields this version does not know survive a save`() {
        val text = """{"type":"WAIT","waitText":"x","test:flag":true,"noRotateUntilNextStep":true,"fromTheFuture":[1]}"""
        val step = read(text)

        assertTrue(step.has(TestSteps.Flag))
        assertEquals("x", step.paramsOf(WaitStep)?.waitText)
        assertEquals(JsonParser.parseString(text), json(step))
        step.set(TestSteps.Flag, false)
        assertFalse(json(step).has("test:flag"))
    }

    @Test
    fun `a flag named like a param, or a param named like a flag, is refused`() {
        assertNotNull(StepTypes.register(object : StepFlag("waitText") {
            override val label = ""
        }))
        assertNotNull(StepTypes.register(object : StepFlag("enabled") {
            override val label = ""
        }))
    }

    @Test
    fun `a step without a type is the old default type`() {
        assertEquals("SWITCH_SLOT", read("""{"slotIndex":3}""").typeId)
    }

    @Test
    fun `a step of a type nobody registers keeps all its fields`() {
        val text = """{"enabled":false,"type":"someaddon:gone","speed":3,"nested":{"a":[1,2]}}"""
        val step = read(text)

        assertNull(step.type)
        assertFalse(step.enabled)
        assertEquals(JsonParser.parseString(text), json(step))
    }

    @Test
    fun `values this version cannot read fall back to defaults and mark the step`() {
        val step = read("""{"type":"WAIT","waitMode":"TELEPATHY","waitText":"kept"}""")

        assertEquals(WaitMode.TIME, step.paramsOf(WaitStep)?.waitMode)
        assertEquals("kept", step.paramsOf(WaitStep)?.waitText)
        assertTrue(step.lossy)
        assertFalse(ActionPortable.known(step))
    }

    @Test
    fun `whole profiles round trip`() {
        val profile = ActionProfile(
            chatActions = mutableListOf(ChatAction(pattern = "boom", actions = mutableListOf(ActionStep.of(WaitStep, WaitStep.Params(durationSeconds = 1.5f))))),
            locationActions = mutableListOf(LocationAction(name = "P5", triggerEvent = ActionDungeonEvent.WITHER_KING, phaseFilter = 5)),
        )
        val back = gson.fromJson(gson.toJson(profile), ActionProfile::class.java)
        assertEquals(gson.toJson(profile), gson.toJson(back))
        assertEquals(profile.locationActions, back.locationActions)
    }

    @Test
    fun `a profile leaves out its empty lists and the default name`() {
        val json = JsonParser.parseString(gson.toJson(ActionProfile(id = "default", shortcuts = mutableListOf(ShortcutAction(alias = "wf"))))).asJsonObject

        assertEquals(setOf("id", "shortcuts"), json.keySet())
        val back = gson.fromJson(json, ActionProfile::class.java)
        assertEquals("Default", back.name)
        assertTrue(back.chatActions.isEmpty() && back.locationGroups.isEmpty())
    }

    @Test
    fun `files from before the collapse state was dropped still load`() {
        val old = """{"id":"default","chatActions":[{"pattern":"hi","collapsed":false,"actions":[{"type":"WAIT","collapsed":true}]}]}"""
        val back = gson.fromJson(old, ActionProfile::class.java)

        assertEquals("hi", back.chatActions.single().pattern)
        assertSame(WaitStep, back.chatActions.single().actions.single().type)
    }

    @Test
    fun `an old position wait becomes a position wait mode`() {
        val old = """{"id":"default","chatActions":[{"actions":[{"type":"WAIT","waitPosition":true,"targetX":4.0}]}]}"""
        val wait = gson.fromJson(old, ActionProfile::class.java).adoptLegacy().allSteps().single().paramsOf(WaitStep)

        assertEquals(WaitMode.POSITION, wait?.waitMode)
        assertEquals(4.0, wait?.targetX)
    }

    @Test
    fun `a param that defaults to null is still a param, not an unknown field`() {
        val type = Typed("test:nullable") { NullableParams() }
        assertNull(StepTypes.register(type))

        val step = read("""{"type":"test:nullable","note":"hi","extra":1}""")

        assertEquals("hi", step.paramsOf(type)?.note)
        assertEquals(setOf("extra"), step.extras.keySet())
        assertFalse(step.lossy)
    }

    @Test
    fun `params without a no-arg constructor are refused`() {
        assertNotNull(StepTypes.register(Typed("test:no-defaults") { RequiredParams("x") }))
    }

    private data class NullableParams(var note: String? = null, var count: Int = 1)

    private data class RequiredParams(var note: String)

    private class Typed<P : Any>(id: String, private val params: () -> P) : StepType<P>(id) {
        override val displayName: String get() = id

        override fun newParams() = params()

        override fun execute(params: P, context: StepContext) = Unit
    }
}
