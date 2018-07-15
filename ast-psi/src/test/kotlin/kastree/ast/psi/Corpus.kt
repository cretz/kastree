package kastree.ast.psi

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

object Corpus {
    val default by lazy {
        // Recursive from $KOTLIN_REPO/compiler/testData/psi/**/*.kt
        val root = Paths.get(
            System.getenv("KOTLIN_REPO") ?: error("No KOTLIN_REPO env var"),
            "compiler/testData/psi"
        )
        require(Files.isDirectory(root)) { "Dir not found at $root" }
        Files.walk(root).filter { it.toString().endsWith(".kt") }.toList().map {
            Unit(
                relativePath = root.relativize(it),
                fullPath = it,
                // See if it ends in error or if there is a .txt file of the same name that contains a "PsiErrorElement"
                error = it.toString().endsWith("_ERR.kt") || Paths.get(it.toString().replace(".kt", ".txt")).let {
                    Files.isRegularFile(it) && it.toFile().readText().contains("PsiErrorElement")
                }
            )
        }
    }

    data class Unit(
        val relativePath: Path,
        val fullPath: Path,
        val error: Boolean
    ) {
        override fun toString() = relativePath.toString()
    }
}