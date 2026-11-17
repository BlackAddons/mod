package org.blackaddons.api.testing

private const val TOP = 30

/** `gradle :api:reuseReport`: what is written more than once, to decide what to extract next. Never fails. */
fun main() {
    val duplicates = ReuseScan.duplicates().sortedByDescending { (it.copies.size - 1) * it.body.length }
    println("== Copied function bodies (${duplicates.size}), most duplicated code first")
    duplicates.take(TOP).forEachIndexed { rank, dup ->
        println("${rank + 1}. ${dup.copies.size}x ${dup.body.length} chars")
        dup.copies.forEach { println("     ${it.path}:${it.line} ${it.name}") }
        println("     ${dup.body.take(160)}")
    }

    val lines = ReuseScan.repeatedLines()
    println("\n== Repeated lines (${lines.size}), most used first")
    lines.take(TOP).forEachIndexed { rank, (line, paths) ->
        println("${rank + 1}. ${paths.size}x  ${line.take(140)}")
        paths.take(4).forEach { println("     $it") }
        if (paths.size > 4) println("     ... ${paths.size - 4} more")
    }
}
