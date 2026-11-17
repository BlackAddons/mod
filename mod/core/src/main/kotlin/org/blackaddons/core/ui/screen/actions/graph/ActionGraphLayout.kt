package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.core.actions.ActionJson
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.GuardedFile
import org.blackaddons.core.ui.screen.actions.ActionsPage
import java.nio.file.Path

internal object ActionGraphLayout {

    class FileData(
        var version: Int = 1,
        var cameraX: Double? = null,
        var cameraY: Double? = null,
        var zoom: Double? = null,
        var positions: MutableMap<String, FloatArray> = mutableMapOf(),
        var loose: MutableList<LooseFile> = mutableListOf(),
    ) {
        fun looseChains(): List<LooseChain> = loose.map { LooseChain(it.steps, it.positions) }
    }

    class LooseFile(
        var steps: MutableList<ActionStep> = mutableListOf(),
        var positions: MutableList<FloatArray> = mutableListOf(),
    )

    private val gson = ActionJson.gson()
    private val guards = HashMap<Path, GuardedFile>()

    fun load(profileId: String, page: ActionsPage): FileData {
        val guard = guard(profileId, page) ?: return FileData()
        val text = guard.read() ?: return FileData()
        return runCatching { gson.fromJson(text, FileData::class.java) }
            .onFailure {
                Log.main.error("Failed to read action graph layout {}", guard.path, it)
                guard.refuseWrites("it could not be parsed")
            }
            .getOrNull()
            ?.sanitized()
            ?: FileData()
    }

    fun save(profileId: String, page: ActionsPage, data: FileData) {
        guard(profileId, page)?.write(gson.toJson(data))
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun FileData.sanitized(): FileData {
        if (positions == null) positions = mutableMapOf()
        if (loose == null) loose = mutableListOf()
        positions.entries.removeIf { (_, p) -> p == null || p.size < 2 || !p[0].isFinite() || !p[1].isFinite() }
        loose.removeIf { it == null || it.steps == null || it.positions == null }
        loose.forEach { chain -> chain.steps.removeIf { it == null } }
        if (zoom?.isFinite() != true || zoom!! <= 0.0) zoom = null
        return this
    }

    private fun guard(profileId: String, page: ActionsPage): GuardedFile? {
        if (!ActionStorage.isValidProfileId(profileId)) return null
        val path = ActionStorage.graphDir.resolve(page.id).resolve("$profileId.json")
        return guards.getOrPut(path) { GuardedFile(path) }
    }
}
