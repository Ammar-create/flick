package me.ammar.flick.model

import kotlinx.serialization.Serializable

@Serializable
data class PluginEntry(
    val id: String,
    val name: String,
    val version: String,
    val api: Int,
    val author: String? = null,
    val description: String? = null,
    val file: String,
    val hash: String
)

@Serializable
data class RepoManifest(
    val name: String,
    val description: String? = null,
    val manifestVersion: Int = 1,
    val plugins: List<PluginEntry> = emptyList()
)
