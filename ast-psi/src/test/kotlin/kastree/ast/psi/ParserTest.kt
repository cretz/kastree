package kastree.ast.psi

import kotlin.test.Test

class ParserTest {
    @Test
    fun testParser() {
        Parser.parse("package whatevs\n\nclass Foo\n")
    }
}