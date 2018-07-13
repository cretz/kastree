package kastree.ast

sealed class Node {
    interface WithAnnotations {
        val anns: List<Modifier.Annotation>
    }

    interface WithModifiers : WithAnnotations {
        val mods: List<Modifier>
        override val anns: List<Modifier.Annotation> get() = mods.mapNotNull { it as? Modifier.Annotation }
    }

    interface Entry : WithAnnotations {
        val pkg: Package?
        val imports: List<Import>
    }

    data class File(
        override val anns: List<Modifier.Annotation>,
        override val pkg: Package?,
        override val imports: List<Import>,
        val decls: List<Decl>
    ) : Node(), Entry

    data class Script(
        override val anns: List<Modifier.Annotation>,
        override val pkg: Package?,
        override val imports: List<Import>,
        val exprs: List<Expr>
    ) : Node(), Entry

    data class Package(
        override val mods: List<Modifier>,
        val name: String
    ) : Node(), WithModifiers

    data class Import(
        val names: List<String>,
        val wildcard: Boolean,
        val alias: String?
    ) : Node()

    sealed class Decl {
        data class Structured(
            override val mods: List<Modifier>,
            val form: Form,
            val name: String,
            val typeParams: List<TypeParam>,
            val primaryConstructor: PrimaryConstructor?,
            val parentAnns: List<Modifier.Annotation>,
            val parents: List<Parent>,
            val typeConstraints: List<TypeConstraint>,
            // TODO: Can include primary constructor
            val members: List<Decl>
        ) : Decl(), WithModifiers {
            enum class Form {
                CLASS, ENUM_CLASS, INTERFACE, OBJECT, COMPANION_OBJECT
            }
            sealed class Parent : Node() {
                data class CallConstructor(
                    val type: TypeRef.Simple,
                    val typeArgs: List<Type>,
                    val args: List<ValueArg>,
                    val call: AnnotatedLambda
                ) : Parent()
                data class Type(
                    val ref: TypeRef.Simple,
                    val by: Expr?
                ) : Parent()
            }
            data class PrimaryConstructor(
                override val mods: List<Modifier>,
                val params: List<Func.Param>
            ) : Node(), WithModifiers
        }
        data class Init(val stmts: List<Statement>) : Decl()
        data class Func(
            override val mods: List<Modifier>,
            val typeParams: List<TypeParam>,
            val receiverType: Type?,
            val name: String,
            val paramTypeParams: List<TypeParam>,
            val valueParams: List<Func.Param>,
            val type: Type?,
            val typeConstraints: List<TypeConstraint>,
            val body: Body?
        ) : Decl(), WithModifiers {
            data class Param(
                override val mods: List<Modifier>,
                val readOnly: Boolean?,
                val name: String,
                val type: Type,
                val default: Expr?
            ) : Node(), WithModifiers
            sealed class Body : Node() {
                data class Block(val stmts: List<Statement>) : Body()
                data class Expr(val expr: Node.Expr) : Body()
            }
        }
        data class Property(
            override val mods: List<Modifier>,
            val typeParams: List<TypeParam>,
            val receiverType: Type?,
            val vars: List<Pair<String, Type?>>,
            val typeConstraints: List<TypeConstraint>,
            val delegated: Boolean,
            val expr: Expr?,
            val accessors: Pair<Accessor, Accessor?>?
        ) : Decl(), WithModifiers {
            sealed class Accessor : Node(), WithModifiers {
                data class Get(
                    override val mods: List<Modifier>,
                    val type: Type?,
                    val body: Func.Body?
                ) : Accessor()
                data class Set(
                    override val mods: List<Modifier>,
                    val paramMods: List<Modifier>,
                    val paramName: String?,
                    val paramType: Type?,
                    val body: Func.Body?
                ) : Accessor()
            }
        }
        data class TypeAlias(
            override val mods: List<Modifier>,
            val typeParams: List<TypeParam>,
            val type: Type
        ) : Decl(), WithModifiers
        data class Constructor(
            override val mods: List<Modifier>,
            val args: List<Func.Param>,
            val delegationCall: Pair<DelegationTarget, List<ValueArg>>?
        ) : Decl(), WithModifiers {
            enum class DelegationTarget { THIS, SUPER }
        }
        data class EnumEntry(
            override val mods: List<Modifier>,
            val name: String,
            val args: List<ValueArg>,
            val members: List<Decl>
        ) : Decl(), WithModifiers
    }

    data class TypeParam(
        override val mods: List<Modifier>,
        val name: String,
        val type: TypeRef.Simple?
    ) : Node(), WithModifiers

    data class TypeConstraint(
        override val anns: List<Modifier.Annotation>,
        val name: String,
        val type: Type
    ) : Node(), WithAnnotations

    sealed class TypeRef : Node() {
        data class Paren(val v: TypeRef) : TypeRef()
        data class Func(
            val receiverType: Type?,
            val params: List<Pair<String, Type>>,
            val type: Type
        ) : TypeRef()
        data class Simple(
            val name: String,
            // Null means any
            val typeParams: List<Type?>
        ) : TypeRef()
        data class Nullable(val v: TypeRef) : TypeRef()
        object Dynamic : TypeRef()
    }

    data class Type(
        override val mods: List<Modifier>,
        val ref: TypeRef
    ) : Node(), WithModifiers

    data class ValueArg(
        val name: String?,
        val asterisk: Boolean,
        val expr: Expr
    ) : Node()

    class Expr : Node()

    // TODO: can't have enum
    // TODO: include in/out
    sealed class Modifier : Node() {
        class Annotation : Modifier()
    }

    class Statement
    class AnnotatedLambda
}