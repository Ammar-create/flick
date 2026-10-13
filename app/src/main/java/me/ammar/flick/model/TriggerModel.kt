package me.ammar.flick.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TriggerModel(
    val id: String,
    val edge: String,
    val gesture: String,
    val fromPct: Int = 0,
    val toPct: Int = 100,
    val thicknessPx: Int = 24,
    val script: String,
    val args: JsonObject? = null,
    val comment: String? = null
)
