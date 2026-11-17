package org.blackaddons.api.event

/**
 * One server tick, derived from Hypixel's per-tick keep-alive; posted on the client thread. Use it to wait
 * for the world to change - [ClientTickEvent] is wall-clock and keeps going while the server lags. Servers
 * that don't send the ping never fire it, and some send several per tick (eu.p3sim.net ~3), so count waits
 * with [ServerClock][org.blackaddons.api.util.ServerClock].
 */
object ServerTickEvent : Event()
