package kastree.ast.psi

import kotlin.test.Test

class ParserTest {

    @Test(expected = Parser.ParseError::class)
    fun testParserError() {
        // Fails because a destructuring can't be at the property level
        Parser.parseFile("package whatevs\nval (foo, bar) = qux")
    }

    @Test
    fun testParser() {
        // This is basically my test bed while developing
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
            val (temp7, temp8) = temp9

            fun foo() {
                val (temp7, _, temp8) = temp9
                for ((a, _, b) in bar()) baz()

                a1.filter { (x, y) -> }
                a2.filter { (x) -> }
                a3.filter { z, (x, y) -> }
                a4.filter { (x, y), z -> }
                a5.filter { q, (x, y), z -> }
                a6.filter { (x, y), (z, w) -> }

                a7.filter { (x, y): Type, (z: Type), (w, u: T) : V -> foo7() }
            }
            fun simple() {
                this@x::foo
            }
        """.trimIndent())
//        println("FILE: $file")
    }
}