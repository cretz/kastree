package kastree.ast

open class Writer(val app: Appendable = StringBuilder()) : Visitor() {

    protected var indent = ""

    protected fun line() = append('\n')
    protected fun line(str: String) = append(indent).append(str).append('\n')
    protected fun lineBegin(str: String = "") = append(indent).append(str)
    protected fun lineEnd(str: String = "") = append(str).append('\n')
    protected fun append(ch: Char) = also { app.append(ch) }
    protected fun append(str: String) = also { app.append(str) }
    protected fun <T> indented(fn: () -> T): T = run {
        indent += "    "
        fn().also { indent = indent.dropLast(4) }
    }

    override fun <T : Node?> visit(v: T, parent: Node) {
        v?.apply {
            when (this) {
                is Node.File -> {
                    if (anns.isNotEmpty()) childAnns().line()
                    childrenLines(pkg, lastSepLineCount = 2)
                    childrenLines(imports, lastSepLineCount = 2)
                    children(decls)
                }
                is Node.Package ->
                    childMods().append("package ${names.joinToString(".")}")
                is Node.Import -> names.joinToString(".").let {
                    append("import " + if (wildcard) "$it.*" else if (alias != null) "$it as $alias" else it)
                }
                is Node.Decl.Structured -> childMods().also {
                    append(when (form) {
                        Node.Decl.Structured.Form.CLASS -> "class "
                        Node.Decl.Structured.Form.ENUM_CLASS -> "enum class "
                        Node.Decl.Structured.Form.INTERFACE -> "interface "
                        Node.Decl.Structured.Form.OBJECT -> "object "
                        Node.Decl.Structured.Form.COMPANION_OBJECT -> "companion object "
                    })
                    bracketedChildren(typeParams)
                    children(primaryConstructor)
                    if (parents.isNotEmpty()) {
                        append(" : ")
                        children(parentAnns)
                        children(parents, ", ")
                    }
                    childTypeConstraints(typeConstraints)
                    if (members.isNotEmpty()) lineEnd(" {").indented {
                        childrenLines(members, sepLineCount = 2, lastSepLineCount = 1)
                    }.lineBegin("}")
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
                    if (mods.isNotEmpty()) append(" ").also { childMods(newlines = false).append("constructor") }
                    parenChildren(params)
                }
                is Node.Decl.Init ->
                    append("init").also { childBlock(stmts) }
                is Node.Decl.Func -> {
                    childMods().append("fun ")
                    bracketedChildren(typeParams, " ")
                    if (receiverType != null) children(receiverType).append(".")
                    append(name)
                    bracketedChildren(paramTypeParams)
                    parenChildren(params)
                    if (type != null) append(": ").also { children(type) }
                    childTypeConstraints(typeConstraints)
                    if (body != null) children(body)
                }
                is Node.Decl.Func.Body.Block ->
                    childBlock(stmts)
                is Node.Decl.Func.Body.Expr ->
                    append(" = ").also { children(expr) }
                is Node.Decl.Property -> {
                    childMods().append(if (readOnly) "val " else "var ")
                    bracketedChildren(typeParams, " ")
                    if (receiverType != null) children(receiverType).append('.')
                    childVars(vars)
                    childTypeConstraints(typeConstraints)
                    if (expr != null) {
                        if (delegated) append(" by ") else append(" = ")
                        children(expr)
                    }
                    if (accessors != null) lineEnd().indented { children(accessors) }
                }
                is Node.Decl.Property.Var -> {
                    append(name)
                    if (type != null) append(": ").also { children(type) }
                }
                is Node.Decl.Property.Accessors -> {
                    childrenLines(first)
                    if (second != null) childrenLines(second)
                }
                is Node.Decl.Property.Accessor.Get -> {
                    childMods().append("get")
                    if (body != null) {
                        append("()")
                        if (type != null) append(": ").also { children(type) }
                        children(body)
                    }
                }
                is Node.Decl.Property.Accessor.Set -> {
                    childMods().append("set")
                    if (body != null) {
                        append('(')
                        childMods(newlines = false)
                        append(paramName ?: error("Missing setter param name when body present"))
                        if (paramType != null) append(": ").also { children(paramType) }
                        append(')')
                        children(body)
                    }
                }
                is Node.Decl.TypeAlias -> {
                    childMods().append("typealias ").append(name)
                    bracketedChildren(typeParams).append(" = ")
                    children(type)
                }
                is Node.Decl.Constructor -> {
                    childMods().append("constructor")
                    parenChildren(params)
                    if (delegationCall != null) append(": ").also { children(delegationCall) }
                    childBlock(stmts)
                }
                is Node.Decl.EnumEntry -> {
                    childMods().append(name)
                    if (args.isNotEmpty()) parenChildren(args)
                    if (members.isNotEmpty()) lineEnd(" {").indented {
                        childrenLines(members, sepLineCount = 2, lastSepLineCount = 1)
                    }.lineBegin("}")
                    // We see if we're the last one in the enum decl and if so put semicolon, otherwise comma
                    append(if ((parent as Node.Decl.Structured).members.last() == this) ";" else ",")
                }
                is Node.TypeParam -> {
                    childMods(newlines = false).append(name)
                    if (type != null) append(": ").also { children(type) }
                }
                is Node.TypeConstraint ->
                    childAnns(sameLine = true).append(name).append(": ").also { children(type) }
                is Node.TypeRef.Paren ->
                    append('(').also { children(type) }.append(')')
                is Node.TypeRef.Func -> {
                    if (receiverType != null) children(receiverType).append('.')
                    parenChildren(params).append(" -> ").also { children(type) }
                }
                is Node.TypeRef.Simple ->
                    append(name).also { bracketedChildren(typeParams) }
                is Node.TypeRef.Nullable ->
                    children(type).append('?')
                is Node.TypeRef.Dynamic ->
                    append("dynamic")
                is Node.Type ->
                    childMods(newlines = false).also { children(ref) }
                is Node.ValueArg -> {
                    if (name != null) append(name).append(" = ")
                    if (asterisk) append('*')
                    children(expr)
                }
                is Node.Expr.If -> {
                    append("if (").also { children(expr) }.append(") ")
                    children(body)
                    if (elseBody != null) append(" else ").also { children(elseBody) }
                }
                is Node.Expr.Try -> {
                    append("try ")
                    childBlock(stmts)
                    if (catches.isNotEmpty()) children(catches, " ", prefix = " ")
                    if (finallyStmts.isNotEmpty()) append(" finally").also { childBlock(finallyStmts) }
                }
                is Node.Expr.Try.Catch -> {
                    append("catch (")
                    childAnns(sameLine = true)
                    append(varName).append(": ").also { children(varType) }.append(")")
                    childBlock(stmts)
                }
                else -> super.visit(v, parent)
            }
        }
    }

    // Does not do a newline, leaves dangling ending brace
    protected fun Node.childBlock(v: List<Node.Stmt>) =
        lineEnd(" {").indented { children(v) }.also { lineBegin("}") }

    protected fun Node.childTypeConstraints(v: List<Node.TypeConstraint>) = this@Writer.also {
        if (v.isNotEmpty()) append(" where ").also { children(v, ", ") }
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

    // Ends with newline (or space if sameLine) if there are any
    protected fun Node.WithAnnotations.childAnns(sameLine: Boolean = false) = this@Writer.also {
        if (anns.isNotEmpty()) (this@childAnns as Node).apply {
            if (sameLine) children(anns, " ", "", " ")
            else anns.forEach { ann -> lineBegin().also { children(ann) }.lineEnd() }
        }
    }

    // Ends with newline if last is ann or space is last is mod or nothing if empty
    protected fun Node.WithModifiers.childMods(newlines: Boolean = true) =
        this@Writer.also {
            if (mods.isNotEmpty()) (this@childMods as Node).apply {
                mods.forEachIndexed { index, mod ->
                    children(mod)
                    if (newlines && (mod is Node.Modifier.AnnotationSet ||
                            mods.getOrNull(index + 1) is Node.Modifier.AnnotationSet))
                        lineEnd().lineBegin()
                    else append(' ')
                }
            }
        }

    protected inline fun Node.children(vararg v: Node?) = this@Writer.also { v.forEach { visitChildren(it) } }

    // Null list values are asterisks
    protected fun Node.bracketedChildren(v: List<Node?>, appendIfNotEmpty: String = "") = this@Writer.also {
        if (v.isNotEmpty()) children(v.map { it ?: Node.Expr.Name("*") }, ", ", "<", ">").append(appendIfNotEmpty)
    }

    protected fun Node.parenChildren(v: List<Node?>) = children(v, ", ", "(", ")")

    protected fun Node.childrenLines(v: Node?, sepLineCount: Int = 1, lastSepLineCount: Int = sepLineCount) =
        childrenLines(listOf(v), sepLineCount, lastSepLineCount)

    protected fun Node.childrenLines(v: List<Node?>, sepLineCount: Int = 1, lastSepLineCount: Int = sepLineCount) =
        this@Writer.also {
            v.forEachIndexed { index, node ->
                lineBegin().also { children(node) }
                val moreLines = if (index == v.size - 1) lastSepLineCount else sepLineCount
                if (moreLines > 0) {
                    lineEnd()
                    (1 until moreLines).forEach { line() }
                }
            }
        }

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