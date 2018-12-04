package localTestData

fun singleLambdaUnderscoreParam() {
    arrayOf(1, 2).forEachIndexed { k, _ -> }
}