package org.blackaddons.platform.util

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.blackaddons.core.log.Log

/**
 * Rebuilds Fabric's internal `register`/`unregister` payloads with a filtered channel list by probing the runtime
 * class, instead of depending on Fabric internals.
 */
object PayloadHelper {

    private const val ERROR_PAYLOAD_CREATION = "Failed to create payload via reflection constructor"
    private const val ERROR_CHANNEL_READ = "Failed to read announced channels off a registration payload"

    /** Channels in [payload], or null. The only honest basis for a shortened announcement. */
    fun readChannels(payload: CustomPacketPayload): List<String>? {
        return try {
            payload.javaClass.declaredFields
                .firstNotNullOfOrNull { field ->
                    if (!Collection::class.java.isAssignableFrom(field.type)) return@firstNotNullOfOrNull null
                    field.isAccessible = true
                    (field.get(payload) as? Collection<*>)?.map(Any?::toString)
                }
        } catch (e: Exception) {
            Log.main.error(ERROR_CHANNEL_READ, e)
            null
        }
    }

    /**
     * Same class as [original], but with only [channels]. Null if no constructor matched. Unparseable channels get
     * dropped.
     */
    fun createRegisterPayload(original: CustomPacketPayload, channels: Set<String>): CustomPacketPayload? {
        return try {
            val clazz = original.javaClass
            val ids: List<Identifier> = channels.mapNotNull { Identifier.tryParse(it) }

            for (constructor in clazz.declaredConstructors) {
                constructor.isAccessible = true
                val params = constructor.parameterTypes
                when (params.size) {
                    1 -> {
                        val p = params[0]
                        if (List::class.java.isAssignableFrom(p)) {
                            return constructor.newInstance(ids) as CustomPacketPayload
                        }
                        if (Set::class.java.isAssignableFrom(p)) {
                            return constructor.newInstance(HashSet(ids)) as CustomPacketPayload
                        }
                        if (Collection::class.java.isAssignableFrom(p)) {
                            return constructor.newInstance(ids) as CustomPacketPayload
                        }
                    }
                    2 -> {
                        if (CustomPacketPayload.Type::class.java.isAssignableFrom(params[0]) &&
                            Collection::class.java.isAssignableFrom(params[1])
                        ) {
                            return constructor.newInstance(original.type(), ids) as CustomPacketPayload
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.main.error(ERROR_PAYLOAD_CREATION, e)
            null
        }
    }
}
