package org.blackaddons.api.config

/** Settings save automatically (ClickGUI close, HUD editor exit, shutdown); this is for values changed from code. */
interface ConfigHost {

    /** Writes settings now. Cheap and thread-safe. */
    fun save()
}
