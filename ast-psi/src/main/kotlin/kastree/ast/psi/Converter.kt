package kastree.ast.psi

import kastree.ast.Node
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.children

open class Converter {
    fun convertAnnotation(v: KtAnnotationEntry) = Node.Modifier.AnnotationSet.Annotation(
        names = v.typeReference?.names ?: error("Missing annotation name"),
        typeArgs = v.typeArguments.map(::convertType),
        args = v.valueArguments.map(::convertValueArg)
    )

    fun convertAnnotationSet(v: KtAnnotation) = Node.Modifier.AnnotationSet(
        target = when (v.useSiteTarget?.getAnnotationUseSiteTarget()) {
            null -> null
            AnnotationUseSiteTarget.FIELD -> Node.Modifier.AnnotationSet.Target.FIELD
            AnnotationUseSiteTarget.FILE -> Node.Modifier.AnnotationSet.Target.FILE
            AnnotationUseSiteTarget.PROPERTY -> Node.Modifier.AnnotationSet.Target.PROPERTY
            AnnotationUseSiteTarget.PROPERTY_GETTER -> Node.Modifier.AnnotationSet.Target.GET
            AnnotationUseSiteTarget.PROPERTY_SETTER -> Node.Modifier.AnnotationSet.Target.SET
            AnnotationUseSiteTarget.RECEIVER -> Node.Modifier.AnnotationSet.Target.RECEIVER
            AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER -> Node.Modifier.AnnotationSet.Target.PARAM
            AnnotationUseSiteTarget.SETTER_PARAMETER -> Node.Modifier.AnnotationSet.Target.SETPARAM
            AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD -> Node.Modifier.AnnotationSet.Target.DELEGATE
        },
        anns = v.entries.map(::convertAnnotation)
    )

    fun convertConstructor(v: KtSecondaryConstructor) = Node.Decl.Constructor(
        mods = convertModifiers(v),
        params = v.valueParameters.map(::convertFuncParam),
        delegationCall = if (v.hasImplicitDelegationCall()) null else v.getDelegationCall().let {
            val target =
                if (it.isCallToThis) Node.Decl.Constructor.DelegationTarget.THIS
                else Node.Decl.Constructor.DelegationTarget.SUPER
            target to it.valueArguments.map(::convertValueArg)
        },
        stmts = v.bodyExpression.block.map(::convertStmt)
    )

    fun convertDecl(v: KtDeclaration): Node.Decl = when (v) {
        is KtEnumEntry -> convertEnumEntry(v)
        is KtClassOrObject -> convertStructured(v)
        is KtAnonymousInitializer -> convertInit(v)
        is KtFunction -> convertFunc(v)
        is KtProperty -> convertProperty(v)
        is KtTypeAlias -> convertTypeAlias(v)
        is KtSecondaryConstructor -> convertConstructor(v)
        else -> error("Unrecognized declaration type for $v")
    }

    fun convertEnumEntry(v: KtEnumEntry) = Node.Decl.EnumEntry(
        mods = convertModifiers(v),
        name = v.name ?: error("Unnamed enum"),
        args = (v.superTypeListEntries.firstOrNull() as? KtSuperTypeCallEntry)?.
            valueArguments?.map(::convertValueArg) ?: emptyList(),
        members = v.declarations.map(::convertDecl)
    )

    fun convertExpr(v: KtExpression): Node.Expr = when (v) {
        is KtStringTemplateExpression -> convertStringTmpl(v)
        else -> error("Unrecognized expression type from $v")
    }

    fun convertFile(v: KtFile) = Node.File(
        anns = v.fileAnnotationList?.annotations?.map(::convertAnnotationSet) ?: emptyList(),
        pkg = v.packageDirective?.let(::convertPackage),
        imports = v.importDirectives.map(::convertImport),
        decls = v.declarations.map(::convertDecl)
    )

    fun convertFunc(v: KtFunction): Node.Decl.Func = TODO()

    fun convertFuncParam(v: KtParameter) = Node.Decl.Func.Param(
        mods = convertModifiers(v),
        readOnly = if (v.hasValOrVar()) !v.isMutable else null,
        name = v.name ?: error("No param name"),
        type = convertType(v.typeReference ?: error("No param type")),
        default = v.defaultValue?.let(::convertExpr)
    )

    fun convertImport(v: KtImportDirective) = Node.Import(
        names = v.importedFqName?.pathSegments()?.map { it.asString() } ?: error("Missing import path"),
        wildcard = v.isAllUnder,
        alias = v.aliasName
    )

    fun convertInit(v: KtAnonymousInitializer) = Node.Decl.Init(
        stmts = v.body.block.map(::convertStmt)
    )

    fun convertModifiers(v: KtModifierListOwnerStub<*>) = v.modifierList?.node?.children()?.mapNotNull { node ->
        // We go over the node children because we want to preserve order
        node.psi.let {
            when (it) {
                is KtAnnotationEntry -> Node.Modifier.AnnotationSet(target = null, anns = listOf(convertAnnotation(it)))
                is KtAnnotation -> convertAnnotationSet(it)
                else -> modifiersByText[node.text]?.let { Node.Modifier.Lit(it) }
            }
        }
    }?.toList() ?: emptyList()

    fun convertPackage(v: KtPackageDirective) = Node.Package(
        mods = convertModifiers(v),
        names = v.packageNames.map { it.getReferencedName() }
    )

    fun convertParent(v: KtSuperTypeListEntry): Node.Decl.Structured.Parent {
        TODO()
    }

    fun convertPrimaryConstructor(v: KtPrimaryConstructor) = Node.Decl.Structured.PrimaryConstructor(
        mods = convertModifiers(v),
        params = v.valueParameters.map(::convertFuncParam)
    )

    fun convertProperty(v: KtProperty): Node.Decl.Property = TODO()

    fun convertStmt(v: KtExpression): Node.Stmt = TODO()

    fun convertStringTmpl(v: KtStringTemplateExpression) = Node.Expr.Lit.StringTmpl(
        elems = v.entries.map(::convertStringTmplElem)
    )

    fun convertStringTmplElem(v: KtStringTemplateEntry): Node.Expr.Lit.StringTmpl.Elem = when (v) {
        is KtLiteralStringTemplateEntry -> Node.Expr.Lit.StringTmpl.Elem.Regular(v.text)
        is KtSimpleNameStringTemplateEntry ->
            Node.Expr.Lit.StringTmpl.Elem.ShortTmpl(v.expression?.text ?: error("No short tmpl text"))
        is KtEscapeStringTemplateEntry ->
            if (v.text.startsWith("\\u"))
                Node.Expr.Lit.StringTmpl.Elem.UnicodeEsc(v.text.substring(2).toCharArray().toList())
            else
                Node.Expr.Lit.StringTmpl.Elem.RegularEsc(v.unescapedValue.first())
        else -> error("Unrecognized string template type for $v")
    }

    fun convertStructured(v: KtClassOrObject) = Node.Decl.Structured(
        mods = convertModifiers(v),
        form = when (v) {
            is KtClass -> when {
                v.isEnum() -> Node.Decl.Structured.Form.ENUM_CLASS
                v.isInterface() -> Node.Decl.Structured.Form.INTERFACE
                else -> Node.Decl.Structured.Form.CLASS
            }
            is KtObjectDeclaration ->
                if (v.isCompanion()) Node.Decl.Structured.Form.COMPANION_OBJECT
                else Node.Decl.Structured.Form.OBJECT
            else -> error("Unknown type of $v")
        },
        name = v.name ?: error("Missing name"),
        typeParams = v.typeParameters.map(::convertTypeParam),
        primaryConstructor = v.primaryConstructor?.let(::convertPrimaryConstructor),
        // TODO: this
        parentAnns = emptyList(),
        parents = v.superTypeListEntries.map(::convertParent),
        typeConstraints = v.typeConstraints.map(::convertTypeConstraint),
        members = v.declarations.map(::convertDecl)
    )

    fun convertType(v: KtTypeProjection) = convertType(v.typeReference ?: error("Project has no reference"))

    fun convertType(v: KtTypeReference): Node.Type = Node.Type(
        mods = convertModifiers(v),
        ref = convertTypeRef(v)
    )

    fun convertTypeAlias(v: KtTypeAlias): Node.Decl.TypeAlias = TODO()

    fun convertTypeConstraint(v: KtTypeConstraint): Node.TypeConstraint = TODO()

    fun convertTypeParam(v: KtTypeParameter): Node.TypeParam = TODO()

    fun convertTypeRef(v: KtTypeReference) = convertTypeRef(v.typeElement ?: error("Missing typ elem")).let {
        if (v.hasParentheses()) Node.TypeRef.Paren(it) else it
    }

    fun convertTypeRef(v: KtTypeElement): Node.TypeRef = when (v) {
        is KtFunctionType -> Node.TypeRef.Func(
            receiverType = v.receiverTypeReference?.let(::convertType),
            params = v.parameters.map {
                (it.name ?: error("No param name")) to convertType(it.typeReference ?: error("No param type"))
            },
            type = convertType(v.returnTypeReference ?: error("No return type"))
        )
        is KtUserType -> Node.TypeRef.Simple(
            name = v.referencedName ?: error("No type name"),
            typeParams = v.typeArguments.map {
                if (it.projectionKind == KtProjectionKind.STAR) null
                else convertType(it)
            }
        )
        is KtNullableType -> Node.TypeRef.Nullable(
            type = convertTypeRef(v.innerType ?: error("No inner type for nullable"))
        )
        is KtDynamicType -> Node.TypeRef.Dynamic
        else -> error("Unrecognized type of $v")
    }

    fun convertValueArg(v: ValueArgument) = Node.ValueArg(
        name = v.getArgumentName()?.asName?.asString(),
        asterisk = v.getSpreadElement() != null,
        expr = convertExpr(v.getArgumentExpression() ?: error("No expr for value arg"))
    )

    companion object : Converter() {
        internal val modifiersByText = Node.Modifier.Keyword.values().map { it.name.toLowerCase() to it }.toMap()

        val KtTypeReference.names get() = (typeElement as? KtUserType)?.names ?: emptyList()
        val KtUserType.names get(): List<String> =
            referencedName?.let { (qualifier?.names ?: emptyList()) + it } ?: emptyList()
        val KtExpression?.block get() = (this as? KtBlockExpression)?.statements ?: emptyList()
    }
}