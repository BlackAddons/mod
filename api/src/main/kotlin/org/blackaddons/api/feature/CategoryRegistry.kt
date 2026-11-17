package org.blackaddons.api.feature

/** Host and addon-declared categories. None are implicit - an unknown id is a bug in whoever wrote the package. */
object CategoryRegistry {

    private val byId = LinkedHashMap<String, FeatureCategory>()
    private val addonOwners = HashMap<String, String>()

    init {
        Categories.builtIns.forEach { byId[it.id] = it }
    }

    /** Host categories first, then addon ones in registration order. */
    val all: List<FeatureCategory> get() = byId.values.toList()

    /** Case-insensitive. */
    fun of(id: String): FeatureCategory? = byId[id.lowercase()]

    /** Null when the host owns it. */
    fun addonIdOf(category: FeatureCategory): String? = addonOwners[category.id]

    /** Registers [addonId]'s categories; returns the ones refused because the id was taken. */
    fun register(addonId: String, categories: List<FeatureCategory>): List<FeatureCategory> =
        categories.filterNot { category ->
            (byId.putIfAbsent(category.id, category) == null).also { fresh ->
                if (fresh) addonOwners[category.id] = addonId
            }
        }

    internal fun infer(cls: Class<*>): FeatureCategory {
        val pkg = cls.`package`?.name ?: error("Feature ${cls.simpleName} has no package")
        val parts = pkg.split(".")
        val idx = parts.indexOf("impl")
        require(idx >= 0 && idx + 1 < parts.size) {
            "Feature ${cls.simpleName} must be in package .features.impl.<category>.*"
        }
        val segment = parts[idx + 1]
        return of(segment) ?: error(
            "Feature ${cls.simpleName} is in package segment '$segment', which is not a declared " +
                "category. Declare it in Categories (host) or in your Addon.categories (addon), " +
                "or move the feature under an existing one.",
        )
    }
}
