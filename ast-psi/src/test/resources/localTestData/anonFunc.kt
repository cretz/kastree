
fun test() {
    val temp = fun(s: String): Int { return s.toInt() }
    val temp2: (String) -> Int = fun(s) = s.toInt()
    println("A: " + temp("12"))
    println("B: " + temp2("13"))
}