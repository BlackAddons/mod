package org.blackaddons.platform.cosmetics

import java.util.UUID

/** Duck-typed onto `EntityRenderState` by `EntityRenderStateMixin` so `scale`, given only the state, sees the UUID. */
interface BlackaddonsRenderState {
    fun getBlackaddonsUuid(): UUID?
    fun setBlackaddonsUuid(uuid: UUID?)
}
