package kastree.ast

import kastree.ast.psi.Parser
import org.junit.Test
import kotlin.test.assertEquals

class WriterTest {
    @Test
    fun testIdentifierUnderscoreEscape() {
        assertParseAndWriteExact("const val c = FOO_BAR")
        assertParseAndWriteExact("const val c = _FOOBAR")
        assertParseAndWriteExact("const val c = FOOBAR_")
        assertParseAndWriteExact("const val c = `___`")
    }

    fun assertParseAndWriteExact(code: String) {
        assertEquals(code.trim(), Writer.write(Parser.parseFile(code)).trim())
    }
}