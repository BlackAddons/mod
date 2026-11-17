package org.blackaddons.core.backend.gateway

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Where a backend with new opcodes meets an older mod: unknown frames have to arrive intact and never throw. */
class GatewayProtocolTest {

    @Test
    fun `a frame is its opcode and payload`() {
        val frame = Frame.decode("""{ "op": "READY", "d": { "cosmeticsVersion": "1a2b" } }""")
        assertNotNull(frame)
        assertEquals(Op.READY, frame!!.op)
        assertEquals("1a2b", frame.d.stringOrNull("cosmeticsVersion"))
    }

    @Test
    fun `an unknown opcode decodes like any other`() {
        val frame = Frame.decode("""{ "op": "SOMETHING_NEW", "d": { "x": 1 } }""")
        assertEquals("SOMETHING_NEW", frame?.op)
    }

    @Test
    fun `a payload that is missing or not an object reads as an empty one`() {
        assertEquals(0, Frame.decode("""{ "op": "HEARTBEAT_ACK" }""")?.d?.size())
        assertEquals(0, Frame.decode("""{ "op": "HEARTBEAT_ACK", "d": 7 }""")?.d?.size())
    }

    @Test
    fun `garbage and a frame without an opcode are not frames`() {
        assertNull(Frame.decode("not json at all"))
        assertNull(Frame.decode("""{ "d": { } }"""))
        assertNull(Frame.decode("""{ "op": "", "d": { } }"""))
        assertNull(Frame.decode("[]"))
    }

    @Test
    fun `encoding round-trips`() {
        val payload = JsonParser.parseString("""{ "nonce": "8f3a1c" }""").asJsonObject
        val decoded = Frame.decode(Frame(Op.IDENTIFY, payload).encode())
        assertEquals(Op.IDENTIFY, decoded?.op)
        assertEquals("8f3a1c", decoded?.d?.stringOrNull("nonce"))
    }

    @Test
    fun `readers refuse a value of the wrong type rather than coercing one`() {
        val d = JsonParser.parseString("""{ "n": 30, "s": "30", "obj": { }, "blank": "  " }""").asJsonObject
        assertEquals(30, d.intOrNull("n"))
        assertNull(d.intOrNull("s")) // a string-encoded number isn't a number
        assertNull(d.stringOrNull("n")) // asString would have made it "30"
        assertNull(d.stringOrNull("blank"))
        assertNull(d.intOrNull("missing"))
        assertNull(d.stringOrNull("obj"))
    }
}
