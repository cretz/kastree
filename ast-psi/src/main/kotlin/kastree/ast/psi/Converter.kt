package kastree.ast.psi

import kastree.ast.Node
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.children

open class Converter(
    // If set, will be called after each object is created. Could set node's tag w/ the
    // value, populate an IdentityHashMap w/ node as the key, or anything.
    val nodeMapCallback: ((Node, KtElement) -> Unit)? = null
) {
    fun convertAnnotation(v: KtAnnotationEntry) = Node.Modifier.AnnotationSet.Annotation(
        names = v.typeReference?.names ?: error("Missing annotation name"),
        typeArgs = v.typeArguments.map(::convertType),
        args = convertValueArgs(v.valueArgumentList)
    ).map(v)

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
    ).map(v)

    fun convertConstructor(v: KtSecondaryConstructor) = Node.Decl.Constructor(
        mods = convertModifiers(v),
        params = v.valueParameters.map(::convertFuncParam),
        delegationCall = if (v.hasImplicitDelegationCall()) null else v.getDelegationCall().let {
            val target =
                if (it.isCallToThis) Node.Decl.Constructor.DelegationTarget.THIS
                else Node.Decl.Constructor.DelegationTarget.SUPER
            target to convertValueArgs(it.valueArgumentList)
        },
        stmts = v.bodyExpression.block.map(::convertStmt)
    ).map(v)

    fun convertDecl(v: KtDeclaration): Node.Decl = when (v) {
        is KtEnumEntry -> convertEnumEntry(v)
        is KtClassOrObject -> convertStructured(v)
        is KtAnonymousInitializer -> convertInit(v)
        is KtNamedFunction -> convertFunc(v)
        is KtProperty -> convertProperty(v)
        is KtTypeAlias -> convertTypeAlias(v)
        is KtSecondaryConstructor -> convertConstructor(v)
        else -> error("Unrecognized declaration type for $v")
    }

    fun convertEnumEntry(v: KtEnumEntry) = Node.Decl.EnumEntry(
        mods = convertModifiers(v),
        name = v.name ?: error("Unnamed enum"),
        args = convertValueArgs((v.superTypeListEntries.firstOrNull() as? KtSuperTypeCallEntry)?.valueArgumentList),
        members = v.declarations.map(::convertDecl)
    ).map(v)

    fun convertExpr(v: KtExpression): Node.Expr = when (v) {
        // TODO: a bunch more
        is KtStringTemplateExpression -> convertStringTmpl(v)
        else -> error("Unrecognized expression type from $v")
    }

    fun convertFile(v: KtFile) = Node.File(
        anns = v.fileAnnotationList?.annotations?.map(::convertAnnotationSet) ?: emptyList(),
        pkg = v.packageDirective?.let(::convertPackage),
        imports = v.importDirectives.map(::convertImport),
        decls = v.declarations.map(::convertDecl)
    ).map(v)

    fun convertFunc(v: KtNamedFunction) = Node.Decl.Func(
        mods = convertModifiers(v),
        typeParams =
            if (v.hasTypeParameterListBeforeFunctionName()) v.typeParameters.map(::convertTypeParam) else emptyList(),
        receiverType = v.receiverTypeReference?.let(::convertType),
        name = v.name ?: error("No func name"),
        // TODO: validate
        paramTypeParams =
            if (!v.hasTypeParameterListBeforeFunctionName()) v.typeParameters.map(::convertTypeParam) else emptyList(),
        params = v.valueParameters.map(::convertFuncParam),
        type = v.typeReference?.let(::convertType),
        typeConstraints = v.typeConstraints.map(::convertTypeConstraint),
        body = v.bodyExpression?.let(::convertFuncBody)
    ).map(v)

    fun convertFuncBody(v: KtExpression) =
        if (v is KtBlockExpression) Node.Decl.Func.Body.Block(v.block.map(::convertStmt)).map(v)
        else Node.Decl.Func.Body.Expr(convertExpr(v)).map(v)

    fun convertFuncParam(v: KtParameter) = Node.Decl.Func.Param(
        mods = convertModifiers(v),
        readOnly = if (v.hasValOrVar()) !v.isMutable else null,
        name = v.name ?: error("No param name"),
        type = convertType(v.typeReference ?: error("No param type")),
        default = v.defaultValue?.let(::convertExpr)
    ).map(v)

    fun convertImport(v: KtImportDirective) = Node.Import(
        names = v.importedFqName?.pathSegments()?.map { it.asString() } ?: error("Missing import path"),
        wildcard = v.isAllUnder,
        alias = v.aliasName
    ).map(v)

    fun convertInit(v: KtAnonymousInitializer) = Node.Decl.Init(
        stmts = v.body.block.map(::convertStmt)
    ).map(v)

    fun convertModifiers(v: KtModifierListOwnerStub<*>) = v.modifierList?.node?.children()?.mapNotNull { node ->
        // We go over the node children because we want to preserve order
        node.psi.let {
            when (it) {
                is KtAnnotationEntry ->
                    Node.Modifier.AnnotationSet(target = null, anns = listOf(convertAnnotation(it))).map(it)
                is KtAnnotation -> convertAnnotationSet(it)
                else -> modifiersByText[node.text]?.let {
                    Node.Modifier.Lit(it).let { lit -> (node.psi as? KtElement)?.let { lit.map(it) } ?: lit }
                }
            }
        }
    }?.toList() ?: emptyList()

    fun convertPackage(v: KtPackageDirective) = Node.Package(
        mods = convertModifiers(v),
        names = v.packageNames.map { it.getReferencedName() }
    ).map(v)

    fun convertParent(v: KtSuperTypeListEntry) = when (v) {
        is KtSuperTypeCallEntry -> Node.Decl.Structured.Parent.CallConstructor(
            type = v.typeReference?.let(::convertTypeRef) as? Node.TypeRef.Simple ?: error("Bad type on super call $v"),
            typeArgs = v.typeArguments.map(::convertType),
            args = convertValueArgs(v.valueArgumentList),
            // TODO
            lambda = null
        ).map(v)
        else -> Node.Decl.Structured.Parent.Type(
            type = v.typeReference?.let(::convertTypeRef) as? Node.TypeRef.Simple ?: error("Bad type on super call $v"),
            by = (v as? KtDelegatedSuperTypeEntry)?.delegateExpression?.let(::convertExpr)
        ).map(v)
    }

    fun convertPrimaryConstructor(v: KtPrimaryConstructor) = Node.Decl.Structured.PrimaryConstructor(
        mods = convertModifiers(v),
        params = v.valueParameters.map(::convertFuncParam)
    ).map(v)

    fun convertProperty(v: KtProperty) = Node.Decl.Property(
        mods = convertModifiers(v),
        typeParams = v.typeParameters.map(::convertTypeParam),
        receiverType = v.receiverTypeReference?.let(::convertType),
        // TODO: how to handle multiple
        vars = listOf((v.name ?: error("No property name")) to v.typeReference?.let(::convertType)),
        typeConstraints = v.typeConstraints.map(::convertTypeConstraint),
        delegated = v.hasDelegateExpression(),
        expr = v.delegateExpressionOrInitializer?.let(::convertExpr),
        accessors = v.accessors.map(::convertPropertyAccessor).let {
            when {
                it.isEmpty() -> null
                it.size == 1 -> it.first() to null
                else -> it[0] to it[1]
            }
        }
    ).map(v)

    fun convertPropertyAccessor(v: KtPropertyAccessor) =
        if (v.isGetter) Node.Decl.Property.Accessor.Get(
            mods = convertModifiers(v),
            type = v.returnTypeReference?.let(::convertType),
            body = v.bodyExpression?.let(::convertFuncBody)
        ).map(v) else Node.Decl.Property.Accessor.Set(
            mods = convertModifiers(v),
            paramMods = v.parameter?.let(::convertModifiers) ?: emptyList(),
            paramName = v.parameter?.name,
            paramType = v.parameter?.typeReference?.let(::convertType),
            body = v.bodyExpression?.let(::convertFuncBody)
        ).map(v)

    fun convertStmt(v: KtExpression) =
        if (v is KtDeclaration) Node.Stmt.Decl(convertDecl(v)).map(v) else Node.Stmt.Expr(convertExpr(v)).map(v)

    fun convertStringTmpl(v: KtStringTemplateExpression) = Node.Expr.Lit.StringTmpl(
        elems = v.entries.map(::convertStringTmplElem)
    ).map(v)

    fun convertStringTmplElem(v: KtStringTemplateEntry) = when (v) {
        is KtLiteralStringTemplateEntry ->
            Node.Expr.Lit.StringTmpl.Elem.Regular(v.text).map(v)
        is KtSimpleNameStringTemplateEntry ->
            Node.Expr.Lit.StringTmpl.Elem.ShortTmpl(v.expression?.text ?: error("No short tmpl text")).map(v)
        is KtEscapeStringTemplateEntry ->
            if (v.text.startsWith("\\u"))
                Node.Expr.Lit.StringTmpl.Elem.UnicodeEsc(v.text.substring(2).toCharArray().toList()).map(v)
            else
                Node.Expr.Lit.StringTmpl.Elem.RegularEsc(v.unescapedValue.first()).map(v)
        else ->
            error("Unrecognized string template type for $v")
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
    ).map(v)

    fun convertType(v: KtTypeProjection) = convertType(v.typeReference ?: error("No reference for projection $v"))

    fun convertType(v: KtTypeReference): Node.Type = Node.Type(
        mods = convertModifiers(v),
        ref = convertTypeRef(v)
    ).map(v)

    fun convertTypeAlias(v: KtTypeAlias) = Node.Decl.TypeAlias(
        mods = convertModifiers(v),
        typeParams = v.typeParameters.map(::convertTypeParam),
        type = convertType(v.getTypeReference() ?: error("No type ref for alias $v"))
    ).map(v)

    fun convertTypeConstraint(v: KtTypeConstraint): Node.TypeConstraint = TODO()

    fun convertTypeParam(v: KtTypeParameter): Node.TypeParam = TODO()

    fun convertTypeRef(v: KtTypeReference) = convertTypeRef(v.typeElement ?: error("Missing typ elem")).let {
        if (v.hasParentheses()) Node.TypeRef.Paren(it).map(v) else it
    }

    fun convertTypeRef(v: KtTypeElement): Node.TypeRef = when (v) {
        is KtFunctionType -> Node.TypeRef.Func(
            receiverType = v.receiverTypeReference?.let(::convertType),
            params = v.parameters.map {
                (it.name ?: error("No param name")) to convertType(it.typeReference ?: error("No param type"))
            },
            type = convertType(v.returnTypeReference ?: error("No return type"))
        ).map(v)
        is KtUserType -> Node.TypeRef.Simple(
            name = v.referencedName ?: error("No type name"),
            typeParams = v.typeArguments.map {
                if (it.projectionKind == KtProjectionKind.STAR) null
                else convertType(it)
            }
        ).map(v)
        is KtNullableType -> Node.TypeRef.Nullable(
            type = convertTypeRef(v.innerType ?: error("No inner type for nullable"))
        ).map(v)
        is KtDynamicType -> Node.TypeRef.Dynamic().map(v)
        else -> error("Unrecognized type of $v")
    }

    fun convertValueArg(v: KtValueArgument) = Node.ValueArg(
        name = v.getArgumentName()?.asName?.asString(),
        asterisk = v.getSpreadElement() != null,
        expr = convertExpr(v.getArgumentExpression() ?: error("No expr for value arg"))
    ).map(v)

    fun convertValueArgs(v: KtValueArgumentList?) = v?.arguments?.map(::convertValueArg) ?: emptyList()

    protected fun <T: Node> T.map(v: KtElement) = also { nodeMapCallback?.invoke(it, v) }

    companion object : Converter() {
        internal val modifiersByText = Node.Modifier.Keyword.values().map { it.name.toLowerCase() to it }.toMap()

        internal val KtTypeReference.names get() = (typeElement as? KtUserType)?.names ?: emptyList()
        internal val KtUserType.names get(): List<String> =
            referencedName?.let { (qualifier?.names ?: emptyList()) + it } ?: emptyList()
        internal val KtExpression?.block get() = (this as? KtBlockExpression)?.statements ?: emptyList()
    }
}