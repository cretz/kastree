
expect class Foo {
    fun bar(): String
}

actual class Foo {
    actual fun bar(): String = "baz"
}