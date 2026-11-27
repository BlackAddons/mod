package org.blackaddons.platform.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import org.blackaddons.api.world.BlockStateProperty

fun blockById(id: String): Block? = Identifier.tryParse(id)?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }

/** Non-default properties as `name=value`. */
fun BlockState.changedProperties(): Map<String, String> {
    val default = block.defaultBlockState()
    return properties.filter { getValue(it) != default.getValue(it) }.associate { it.name to valueName(it) }
}

/** Null when the block has no such property. */
fun BlockState.propertyValue(name: String): String? = block.stateDefinition.getProperty(name)?.let { valueName(it) }

/** Unknown names and values get skipped. */
fun BlockState.withProperties(values: Map<String, String>): BlockState = values.entries.fold(this) { state, (name, value) ->
    state.block.stateDefinition.getProperty(name)?.let { state.withParsed(it, value) } ?: state
}

private fun <T : Comparable<T>> BlockState.valueName(property: Property<T>): String = property.getName(getValue(property))

private fun <T : Comparable<T>> BlockState.withParsed(property: Property<T>, value: String): BlockState =
    property.getValue(value).map { setValue(property, it) }.orElse(this)

fun <T : Comparable<T>> Property<T>.describe(state: BlockState): BlockStateProperty =
    BlockStateProperty(name, possibleValues.map { getName(it) }, getName(state.getValue(this)))
