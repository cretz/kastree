package kastree.ast.psi

import com.intellij.openapi.util.text.StringUtilRt
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CorpusTest(val unit: Corpus.Unit) {

    @Test
    fun testParseAndConvert() {
        val code = StringUtilRt.convertLineSeparators(unit.fullPath.toFile().readText())
        Parser.parseFile(code)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun data() = Corpus.default.filter { !it.error }.take(50) // TODO: all, not just 50
    }
}