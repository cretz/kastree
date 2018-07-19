package kastree.ast

interface ExtrasMap {
    fun extrasBefore(v: Node): List<Node.Extra>
    fun extrasAfter(v: Node): List<Node.Extra>
    fun docComment(v: Node): Node.Extra.Comment?
}