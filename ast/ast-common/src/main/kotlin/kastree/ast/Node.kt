package kastree.ast

sealed class Node {
    data class KotlinFile(
        val preamble: Preamble,
        val topLevelObjects: List<TopLevelObject>
    ) : Node() {
        sealed class TopLevelObject : Node() {
            data class Class(val v: Node.Class) : TopLevelObject()
            data class Object(val v: Node.Object) : TopLevelObject()
            data class Function(val v: Node.Function) : TopLevelObject()
            data class Property(val v: Node.Property) : TopLevelObject()
            data class TypeAlias(val v: Node.TypeAlias) : TopLevelObject()
        }
    }

    data class Script(
        val preamble: Preamble,
        val expressions: List<Expression>
    ) : Node()

    data class Preamble(
        val fileAnnotations: List<Annotation>,
        val packageHeader: PackageHeader?,
        val imports: List<Import>
    ) : Node()

    data class PackageHeader(
        val modifiers: List<Modifier>,
        val name: String
    ) : Node()

    data class Import(
        val names: List<String>,
        val wildcard: Boolean,
        val alias: String?
    ) : Node()

    data class TypeAlias(
        val modifiers: List<Modifier>,
        val name: String,
        val typeParameters: List<TypeParameter>,
        val type: Type
    ) : Node()

    data class Class(
        val modifiers: List<Modifier>,
        val iface: Boolean,
        val name: String,
        val typeParameters: List<TypeParameter>,
        val primaryConstructor: PrimaryConstructor?,
        val delegationAnnotations: List<Annotation>,
        val delegationSpecifier: DelegationSpecifier?,
        val typeConstraints: List<TypeConstraint>,
        val body: Body?
    ) : Node() {
        sealed class Body : Node() {
            data class Class(val body: ClassBody) : Body()
            data class Enum(val entries: List<EnumEntry>) : Body()
        }

        data class EnumEntry(
                val modifiers: List<Modifier>,
                val name: String,
                val args: List<ValueArgument>,
                val classBody: ClassBody?
        ) : Node()
    }

    data class PrimaryConstructor(
        val modifiers: List<Modifier>,
        val parameters: List<FunctionParameter>
    ) : Node()

    // TODO: embed everywhere instead?
    data class ClassBody(
        val members: List<MemberDeclaration>
    ) : Node()

    sealed class DelegationSpecifier : Node() {
        abstract val userType: Node.UserType
        data class ConstructorInvocation(
            override val userType: Node.UserType,
            val callSuffix: Node.CallSuffix
        ) : DelegationSpecifier()
        data class UserType(
            override val userType: Node.UserType
        ) : DelegationSpecifier()
        data class ExplicitDelegation(
            override val userType: Node.UserType,
            val expr: Expression
        ) : DelegationSpecifier()
    }

    data class TypeParameter(
        val modifiers: List<Modifier>,
        val name: String,
        val userType: UserType?
    ) : Node()

    data class TypeConstraint(
        val annotations: List<Annotation>,
        val name: String,
        val type: Type
    ) : Node()

    sealed class MemberDeclaration : Node() {
        data class CompanionObject(
            val modifiers: List<Modifier>,
            val name: String?,
            val delegationSpecifier: DelegationSpecifier?,
            val body: ClassBody?
        ) : MemberDeclaration()
        data class Object(val v: Node.Object) : MemberDeclaration()
        data class Function(val v: Node.Function) : MemberDeclaration()
        data class Property(val v: Node.Property) : MemberDeclaration()
        data class Class(val v: Node.Class) : MemberDeclaration()
        data class TypeAlias(val v: Node.TypeAlias) : MemberDeclaration()
        data class AnonymousInitializer(
            val block: Block
        ) : MemberDeclaration()
        data class SecondaryConstructor(
                val modifiers: List<Modifier>,
                val parameters: List<FunctionParameter>
        ) : MemberDeclaration() {
            data class ConstructorDelegation(
                val parent: Boolean,
                val args: List<ValueArgument>
            ) : Node()
        }
    }

    data class FunctionParameter(
        val modifiers: List<Modifier>,
        val readOnly: Boolean?,
        val parameter: Parameter,
        val expression: Expression?
    ) : Node()

    // TODO: embed everywhere instead?
    data class Block(
        val statements: List<Statement>
    ) : Node()

    data class Function(
        val modifiers: List<Modifier>,
        val typeParameters: List<TypeParameter>,
        val receiverType: Type?,
        val name: String,
        val valueTypeParameters: List<TypeParameter>,
        val valueParameters: List<FunctionParameter>,
        val type: Type?,
        val typeConstraints: List<TypeConstraint>,
        val body: FunctionBody?
    ) : Node()

    sealed class FunctionBody : Node() {
        data class Block(val v: Node.Block) : FunctionBody()
        data class Expression(val v: Node.Expression) : FunctionBody()
    }

    data class VariableDeclarationEntry(
        val name: String,
        val type: Type?
    ) : Node()

    data class Property(
        val modifiers: List<Modifier>,
        val readOnly: Boolean,
        val typeParameters: List<TypeParameter>,
        val receiverType: Type?,
        val varDecls: List<VariableDeclarationEntry>,
        val typeConstraints: List<TypeConstraint>,
        val expression: Expression,
        val accessor: Pair<Accessor, Accessor?>?
    ) : Node() {
        sealed class Accessor : Node() {
            data class Getter(
                val modifiers: List<Modifier>,
                val type: Type?,
                val functionBody: FunctionBody?
            ) : Accessor()
            data class Setter(
                val modifiers: List<Modifier>,
                val paramModifiers: List<Modifier>,
                val name: Name?,
                val functionBody: FunctionBody?
            ) : Accessor() {
                sealed class Name : Node() {
                    data class Simple(val v: String) : Name()
                    data class Parameter(val v: Node.Parameter) : Name()
                }
            }
        }
    }

    data class Object(
        val modifiers: List<Modifier>,
        val name: String,
        val primaryConstructor: PrimaryConstructor?,
        val delegationSpecifier: DelegationSpecifier?,
        val body: ClassBody
    ) : Node()

    data class Parameter(
        val name: String,
        val type: Type
    ) : Node()

    data class Type(
        val suspend: Boolean,
        val annotations: List<Annotation>
    ) : Node()

    sealed class TypeReference : Node() {
        data class Parens(val v: TypeReference) : TypeReference()
        data class FunctionType(
            val receiverType: Type?,
            val parameters: List<Parameter>,
            val type: Type
        ) : TypeReference()
        data class UserType(val v: UserType) : TypeReference()
        data class NullableType(val v: TypeReference) : TypeReference()
        object Dynamic : TypeReference()
    }

    data class UserType(
        val name: String,
        val parameters: List<Parameter>
    ) : Node() {
        sealed class Parameter : Node() {
            data class Named(
                val projection: VarianceAnnotation?,
                val type: Type
            ) : UserType.Parameter()
            object All : UserType.Parameter()
        }
    }

    // TODO: fix placeholders

    class Annotation

    class Modifier

    sealed class Expression

    class CallSuffix
    class Statement
    class ValueArgument
    sealed class VarianceAnnotation : Node() {
        object In : VarianceAnnotation()
        object Out : VarianceAnnotation()
    }
}