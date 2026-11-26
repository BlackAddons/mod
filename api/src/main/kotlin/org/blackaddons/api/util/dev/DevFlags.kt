package org.blackaddons.api.util.dev

import org.blackaddons.api.HostApi


/**
 * Developer flags, owned by the `DevMode` feature (all forced false while it's off). `@Volatile` for hot-path
 * reads. [isDevelopment] must be seeded by the platform at startup, since :api has no Fabric.
 */
object DevFlags {
    @Volatile
    var isDevelopment: Boolean = false
        @HostApi set

    @Volatile
    var verboseLogging: Boolean = false
        @HostApi set
    @Volatile
    var rethrowListenerExceptions: Boolean = false
        @HostApi set
    @Volatile
    var logEventDispatch: Boolean = false
        @HostApi set
}
