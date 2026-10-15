package it.fabriziodavi.vivovideo.model

import android.content.Context
import java.io.File

/** Salva i progetti come file JSON nella memoria privata dell'app. */
class ProjectStore(context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }
    val lutDir = File(context.filesDir, "luts").apply { mkdirs() }

    fun list(): List<Project> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { Project.fromJson(it.readText()) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    fun load(id: String): Project? = File(dir, "$id.json").takeIf { it.exists() }
        ?.let { runCatching { Project.fromJson(it.readText()) }.getOrNull() }

    fun save(project: Project) {
        val tmp = File(dir, "${project.id}.tmp")
        tmp.writeText(project.toJson())
        tmp.renameTo(File(dir, "${project.id}.json"))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
