package kastree.ast.psi

import kotlin.test.Test

class ParserTest {
    @Test
    fun testParser() {
        val file = Parser.parseFile("""
            package whatevs

//            @[ann0 ann1] internal @ann2 class Foo

//            enum class Foo(val temp: String) {
//                BAR("foo ${'$'}bar \u0001 \n");
//            }

            class Foo : Bar by Baz
        """.trimIndent())
        println("FILE: $file")
    }
}