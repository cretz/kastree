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
                // Text files (same name w/ ext changed from kt to txt) have <whitespace>PsiElement:<error>
                errorMessages = Paths.get(it.toString().replace(".kt", ".txt")).let {
                    if (!Files.isRegularFile(it)) emptyList() else it.toFile().readLines().mapNotNull { line ->
                        line.substringAfterLast("PsiErrorElement:", "").takeIf { it.isNotEmpty() }
                    }
                }
            )
        }
    }

    data class Unit(
        val relativePath: Path,
        val fullPath: Path,
        val errorMessages: List<String>
    ) {
        override fun toString() = relativePath.toString()
    }
}