package kastree.ast

open class Writer(val app: Appendable = StringBuilder()) : Visitor() {

    protected var indent = ""

    protected fun line() = append('\n')
    protected fun line(str: String) = append(indent).append(str).append('\n')
    protected fun lineBegin(str: String = "") = append(indent).append(str)
    protected fun lineEnd(str: String = "") = append(str).append('\n')
    protected fun append(ch: Char) = also { app.append(ch) }
    protected fun append(str: String) = also { app.append(str) }
    protected fun <T> noNewlines(fn: () -> T): T = TODO()
    protected fun <T> indented(fn: () -> T): T = run {
        indent += "    "
        fn().also { indent = indent.dropLast(4) }
    }

    override fun <T : Node?> visit(v: T, parent: Node) {
        v?.apply {
            when (this) {
                is Node.File -> {
                    if (anns.isNotEmpty()) children(anns).line()
                    if (pkg != null) children(pkg).line()
                    if (imports.isNotEmpty()) children(imports).line()
                    children(decls)
                }
                is Node.Package ->
                    children(mods).line("package ${names.joinToString(".")}").line()
                is Node.Import -> names.joinToString(".").let {
                    line("import " + if (wildcard) "$it.*" else if (alias != null) "$it as $alias" else it)
                }
                is Node.Decl.Structured -> lineBegin().also { children(mods) }.also {
                    lineBegin(when (form) {
                        Node.Decl.Structured.Form.CLASS -> "class "
                        Node.Decl.Structured.Form.ENUM_CLASS -> "enum class "
                        Node.Decl.Structured.Form.INTERFACE -> "interface "
                        Node.Decl.Structured.Form.OBJECT -> "object "
                        Node.Decl.Structured.Form.COMPANION_OBJECT -> "companion object "
                    })
                    bracketedChildren(typeParams)
                    primaryConstructor?.also { children(it) }
                    if (parents.isNotEmpty()) noNewlines {
                        append(" : ")
                        children(parentAnns)
                        children(parents, ", ")
                    }
                    childTypeConstraints(typeConstraints)
                    if (members.isNotEmpty()) lineEnd(" {").indented { children(members, "\n") }.line("}")
                }
                is Node.Decl.Structured.Parent.CallConstructor -> {
                    children(type)
                    bracketedChildren(typeArgs)
                    parenChildren(args)
                }
                is Node.Decl.Structured.Parent.Type -> {
                    children(type)
                    if (by != null) append(" by ").also { children(by) }
                }
                is Node.Decl.Structured.PrimaryConstructor -> {
                    if (mods.isNotEmpty()) append(" ").also { children(mods).append("constructor") }
                    parenChildren(params)
                }
                is Node.Decl.Init ->
                    line("init {").indented { children(stmts) }.line("}")
                is Node.Decl.Func -> {
                    lineBegin().also { children(mods) }.append("fun ")
                    bracketedChildren(typeParams, " ")
                    if (receiverType != null) children(receiverType).append(".")
                    append(name)
                    bracketedChildren(paramTypeParams)
                    parenChildren(params)
                    if (type != null) append(": ").also { children(type) }
                    childTypeConstraints(typeConstraints)
                    children(body)
                }
                is Node.Decl.Func.Body.Block ->
                    lineEnd(" {").indented { children(stmts) }.line("}")
                is Node.Decl.Func.Body.Expr ->
                    append(" = ").noNewlines { children(expr) }.lineEnd()
                is Node.Decl.Property -> {
                    if (mods.isNotEmpty()) lineBegin().also { children(mods) }.lineEnd()
                    lineBegin(if (readOnly) "val " else "var ")
                    bracketedChildren(typeParams, " ")
                    if (receiverType != null) children(receiverType).append('.')
                    childVars(vars)
                    childTypeConstraints(typeConstraints)
                    if (expr != null) {
                        if (delegated) append(" by ") else append(" = ")
                        children(expr)
                    }
                    lineEnd()
                    if (accessors != null) indented { children(accessors.first, accessors.second) }
                }
                else -> super.visit(v, parent)
            }
        }
    }

    protected fun Node.childTypeConstraints(v: List<Node.TypeConstraint>) = this@Writer.also {
        if (v.isNotEmpty()) noNewlines { append(" where ").also { children(v, ", ") } }
    }

    protected fun Node.childVars(vars: List<Node.Decl.Property.Var?>) =
        if (vars.size == 1) children(vars) else {
            append('(')
            vars.forEachIndexed { index, v ->
                if (v == null) append('_') else children(v)
                if (index < vars.size - 1) append(", ")
            }
            append(')')
        }

    protected inline fun Node.children(vararg v: Node?) = this@Writer.also { v.forEach { visitChildren(it) } }

    protected fun Node.bracketedChildren(v: List<Node?>, appendIfNotEmpty: String = "") = this@Writer.also {
        if (v.isNotEmpty()) children(v, ", ", "<", ">").append(appendIfNotEmpty)
    }

    protected fun Node.parenChildren(v: List<Node?>) = children(v, ", ", "(", ")")

    protected fun Node.children(v: List<Node?>, sep: String = "", prefix: String = "", postfix: String = "") =
        this@Writer.also {
            append(prefix)
            v.forEachIndexed { index, t ->
                visit(t, this)
                if (index < v.size - 1) append(sep)
            }
            append(postfix)
        }
}