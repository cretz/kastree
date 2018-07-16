package kastree.ast.psi

import com.intellij.openapi.util.text.StringUtilRt
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class CorpusTest(val unit: Corpus.Unit) {

    @Test
    fun testParseAndConvert() {
        try {
            val code = StringUtilRt.convertLineSeparators(unit.fullPath.toFile().readText())
            Parser.parseFile(code)
        } catch (e: Converter.Unsupported) {
            Assume.assumeNoException(e.message, e)
        } catch (e: Parser.ParseError) {
            if (unit.errorMessages.isEmpty()) throw e
            assertEquals(unit.errorMessages.toSet(), e.errors.map { it.errorDescription }.toSet())
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun data() = Corpus.default
    }
}