package org.blackaddons.api.util.io
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.blackaddons.api.util.io.Gsons.compact
import org.blackaddons.api.util.io.Gsons.pretty

/** Shared thread-safe Gson: [pretty] for files on disk, [compact] for request bodies. */
object Gsons {
    val pretty: Gson = GsonBuilder().setPrettyPrinting().create()
    val compact: Gson = Gson()
}
