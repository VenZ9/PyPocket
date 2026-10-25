package dev.pypocket

import android.content.Context
import java.io.File

class ProjectStore(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }

    fun list(): List<String> = root.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.equals("py", ignoreCase = true) }
        .map { it.name }
        .sortedBy(String::lowercase)

    fun read(name: String): String = file(name).readText()
    fun size(name: String): Long = file(name).length()
    fun modified(name: String): Long = file(name).lastModified()

    fun save(name: String, contents: String) {
        val target = file(name)
        val temporary = File(root, ".${target.name}.tmp")
        temporary.writeText(contents)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("Could not save ${target.name}.")
        }
    }

    fun create(name: String, contents: String = "") {
        val target = file(name)
        check(target.createNewFile()) { "${target.name} already exists." }
        target.writeText(contents)
    }

    fun rename(oldName: String, newName: String) {
        val old = file(oldName)
        val new = file(newName)
        require(old == new || !new.exists()) { "${new.name} already exists." }
        check(old.renameTo(new)) { "Could not rename ${old.name}." }
    }

    fun delete(name: String) {
        check(file(name).delete()) { "Could not delete $name." }
    }

    private fun file(name: String): File {
        val clean = name.trim()
        require(clean.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}\\.py"))) {
            "Use a filename with letters, numbers, dots, dashes, or underscores and a .py extension."
        }
        return File(root, clean)
    }
}
