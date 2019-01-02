
fun test() {
    val temp = fun(s: String): Int { return s.toInt() }
    val temp2: (String) -> Int = fun(s) = s.toInt()
    val temp3: ((String) -> Int)? = fun(s) = s.toInt()
    val temp4: ((((String) -> Int)?) -> Int)? = { 5 }
    println("A: " + temp("12"))
    println("B: " + temp2("13"))
}