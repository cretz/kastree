package kastree.ast.psi

import kotlin.test.Test

class ParserTest {
    @Test
    fun testParser() {
        val file = Parser.parseFile("""
            package whatevs

            @[ann0 ann1] internal @ann2 class Foo

            enum class Foo(val temp: String) {
                BAR("foo ${'$'}bar \u0001 \n");
            }

            class Foo : Bar by Baz
            val temp1 = true
            val temp2 = '5'
            val temp3 = 123
            val temp4 = 1.23
            val temp5 = null

            val temp6 = 1 and 2
        """.trimIndent())
//        println("FILE: $file")
    }
}