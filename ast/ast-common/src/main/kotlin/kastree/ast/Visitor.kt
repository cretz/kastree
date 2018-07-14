package kastree.ast

interface Visitor {
    fun visit(v: Node) {
        when (v) {
            is Node.File -> visit(v)
        }
    }

    fun visit(v: Node.File) {

    }

    // TODO: the rest
}