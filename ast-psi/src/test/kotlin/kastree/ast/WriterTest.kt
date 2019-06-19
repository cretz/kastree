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

    @Test
    fun testTypeParameterModifiers() {
        assertParseAndWriteExact("fun delete(p: Array<out String>?) {}")
    }

    @Test
    fun testSimpleCharacterEscaping() {
        assertParseAndWriteExact("""val x = "input\b\n\t\r\'\"\\\${'$'}"""")
    }

    @Test
    fun testSuperclassPrimaryConstructor() {
        assertParseAndWriteExact("private object SubnetSorter : DefaultSorter<Subnet>()")
    }

    @Test
    fun testOpType() {
        assertParseAndWriteExact("""val x = "" as String""")
    }

    fun assertParseAndWriteExact(code: String) {

        val node = Parser.parseFile(code)
        val identityNode = MutableVisitor.preVisit(node) { v, _ -> v }

        assertEquals(
            code.trim(),
            Writer.write(node).trim(),
            "Parse -> Write for $code, not equal")

        assertEquals(
            code.trim(),
            Writer.write(identityNode).trim(),
            "Parse -> Identity Transform -> Write for $code, failed")


    }

}