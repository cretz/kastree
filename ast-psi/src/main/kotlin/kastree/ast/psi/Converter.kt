package kastree.ast.psi

import com.intellij.psi.PsiWhiteSpace
import kastree.ast.Node
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.children

open class Converter(
    // If set, will be called after each object is created. Could set node's tag w/ the
    // value, populate an IdentityHashMap w/ node as the key, or anything.
    val nodeMapCallback: ((Node, KtElement) -> Unit)? = null
) {
    fun convertAnnotated(v: KtAnnotatedExpression) = Node.Expr.Annotated(
        anns = v.annotations.map(::convertAnnotationSet),
        expr = convertExpr(v.baseExpression ?: error("No annotated expr for $v"))
    ).map(v)

    fun convertAnnotation(v: KtAnnotationEntry) = Node.Modifier.AnnotationSet.Annotation(
        names = v.typeReference?.names ?: error("Missing annotation name"),
        typeArgs = v.typeArguments.map { convertType(it) ?: error("No ann typ arg for $v") },
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

    fun convertArrayAccess(v: KtArrayAccessExpression) = Node.Expr.ArrayAccess(
        expr = convertExpr(v.arrayExpression ?: error("No array expr for $v")),
        indices = v.indexExpressions.map(::convertExpr)
    ).map(v)

    fun convertBinaryOp(v: KtBinaryExpression) = Node.Expr.BinaryOp(
        lhs = convertExpr(v.left ?: error("No binary lhs for $v")),
        oper = binaryTokensByText[v.operationReference.text].let {
            if (it != null) Node.Expr.BinaryOp.Oper.Token(it).map(v.operationReference)
            else Node.Expr.BinaryOp.Oper.Infix(v.operationReference.text).map(v.operationReference)
        },
        rhs = convertExpr(v.right ?: error("No binary rhs for $v"))
    ).map(v)

    fun convertBinaryOp(v: KtQualifiedExpression) = Node.Expr.BinaryOp(
        lhs = convertExpr(v.receiverExpression),
        oper = Node.Expr.BinaryOp.Oper.Token(
            if (v is KtDotQualifiedExpression) Node.Expr.BinaryOp.Token.DOT else Node.Expr.BinaryOp.Token.DOT_SAFE
        ),
        rhs = convertExpr(v.selectorExpression ?: error("No qualified rhs for $v"))
    ).map(v)

    fun convertBrace(v: KtBlockExpression) = Node.Expr.Brace(
        params = emptyList(),
        stmts = v.block.map(::convertStmt)
    ).map(v)

    fun convertBrace(v: KtFunctionLiteral) = Node.Expr.Brace(
        params = v.valueParameters.map(::convertBraceParam),
        stmts = v.bodyExpression.block.map(::convertStmt)
    ).map(v)

    fun convertBrace(v: KtLambdaExpression) = Node.Expr.Brace(
        params = v.valueParameters.map(::convertBraceParam),
        stmts = v.bodyExpression.block.map(::convertStmt)
    ).map(v)

    fun convertBraceParam(v: KtParameter) = Node.Expr.Brace.Param(
        vars = convertPropertyVars(v),
        destructType = if (v.destructuringDeclaration != null) v.typeReference?.let(::convertType) else null
    ).map(v)

    fun convertBreak(v: KtBreakExpression) = Node.Expr.Break(
        label = v.getLabelName()
    ).map(v)

    fun convertCall(v: KtCallExpression) = Node.Expr.Call(
        expr = convertExpr(v.calleeExpression ?: error("No call expr for $v")),
        typeArgs = v.typeArguments.map(::convertType),
        args = convertValueArgs(v.valueArgumentList),
        lambda = v.lambdaArguments.singleOrNull()?.let(::convertCallTrailLambda)
    ).map(v)

    fun convertCallableRef(v: KtCallableReferenceExpression) = Node.Expr.CallableRef(
        expr = v.receiverExpression?.let(::convertExpr),
        name = v.callableReference.getReferencedName()
    ).map(v)

    fun convertCallTrailLambda(v: KtLambdaArgument): Node.Expr.Call.TrailLambda {
        var label: String? = null
        var anns: List<Node.Modifier.AnnotationSet> = emptyList()
        fun KtExpression.extractLambda(allowParens: Boolean = false): KtLambdaExpression? = when (this) {
            is KtLambdaExpression -> this
            is KtLabeledExpression -> baseExpression?.extractLambda(allowParens).also {
                label = getLabelName()
            }
            is KtAnnotatedExpression -> baseExpression?.extractLambda(allowParens).also {
                anns = annotations.map(::convertAnnotationSet)
            }
            is KtParenthesizedExpression -> if (allowParens) expression?.extractLambda(allowParens) else null
            else -> null
        }
        val expr = v.getArgumentExpression()?.extractLambda() ?: error("No lambda for $v")
        return Node.Expr.Call.TrailLambda(
            anns = anns,
            label = label,
            func = convertBrace(expr)
        ).map(v)
    }

    fun convertClassLit(v: KtClassLiteralExpression) = Node.Expr.ClassLit(
        expr = v.receiverExpression?.let(::convertExpr)
    ).map(v)

    fun convertCollLit(v: KtCollectionLiteralExpression) = Node.Expr.CollLit(
        exprs = v.getInnerExpressions().map(::convertExpr)
    ).map(v)

    fun convertConst(v: KtConstantExpression) = Node.Expr.Const(
        value = v.text,
        form = when (v.node.elementType) {
            KtNodeTypes.BOOLEAN_CONSTANT -> Node.Expr.Const.Form.BOOLEAN
            KtNodeTypes.CHARACTER_CONSTANT -> Node.Expr.Const.Form.CHAR
            KtNodeTypes.INTEGER_CONSTANT -> Node.Expr.Const.Form.INT
            KtNodeTypes.FLOAT_CONSTANT -> Node.Expr.Const.Form.FLOAT
            KtNodeTypes.NULL -> Node.Expr.Const.Form.NULL
            else -> error("Unrecognized const type for $v")
        }
    )

    fun convertConstructor(v: KtSecondaryConstructor) = Node.Decl.Constructor(
        mods = convertModifiers(v),
        params = v.valueParameters.map(::convertFuncParam),
        delegationCall = if (v.hasImplicitDelegationCall()) null else v.getDelegationCall().let {
            Node.Decl.Constructor.DelegationCall(
                target =
                    if (it.isCallToThis) Node.Decl.Constructor.DelegationTarget.THIS
                    else Node.Decl.Constructor.DelegationTarget.SUPER,
                args = convertValueArgs(it.valueArgumentList)
            ).map(it)
        },
        stmts = v.bodyExpression.block.map(::convertStmt)
    ).map(v)

    fun convertContinue(v: KtContinueExpression) = Node.Expr.Continue(
        label = v.getLabelName()
    ).map(v)

    fun convertDecl(v: KtDeclaration): Node.Decl = when (v) {
        is KtEnumEntry -> convertEnumEntry(v)
        is KtClassOrObject -> convertStructured(v)
        is KtAnonymousInitializer -> convertInit(v)
        is KtNamedFunction -> convertFunc(v)
        is KtDestructuringDeclaration -> convertProperty(v)
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
        is KtIfExpression -> convertIf(v)
        is KtTryExpression -> convertTry(v)
        is KtForExpression -> convertFor(v)
        is KtWhileExpressionBase -> convertWhile(v)
        is KtBinaryExpression -> convertBinaryOp(v)
        is KtQualifiedExpression -> convertBinaryOp(v)
        is KtUnaryExpression -> convertUnaryOp(v)
        is KtBinaryExpressionWithTypeRHS -> convertTypeOp(v)
        is KtIsExpression -> convertTypeOp(v)
        is KtCallableReferenceExpression -> convertCallableRef(v)
        is KtClassLiteralExpression -> convertClassLit(v)
        is KtParenthesizedExpression -> convertParen(v)
        is KtStringTemplateExpression -> convertStringTmpl(v)
        is KtConstantExpression -> convertConst(v)
        is KtBlockExpression -> convertBrace(v)
        is KtFunctionLiteral -> convertBrace(v)
        is KtLambdaExpression -> convertBrace(v)
        is KtThisExpression -> convertThis(v)
        is KtSuperExpression -> convertSuper(v)
        is KtWhenExpression -> convertWhen(v)
        is KtObjectLiteralExpression -> convertObject(v)
        is KtThrowExpression -> convertThrow(v)
        is KtReturnExpression -> convertReturn(v)
        is KtContinueExpression -> convertContinue(v)
        is KtBreakExpression -> convertBreak(v)
        is KtCollectionLiteralExpression -> convertCollLit(v)
        is KtSimpleNameExpression -> convertName(v)
        is KtLabeledExpression -> convertLabeled(v)
        is KtAnnotatedExpression -> convertAnnotated(v)
        is KtCallExpression -> convertCall(v)
        is KtArrayAccessExpression -> convertArrayAccess(v)
        // TODO: this is for "function expressions", need to learn about them
        is KtNamedFunction -> throw Unsupported("Function expressions not supported")
        // TODO: this can happen when a labeled expression uses a var decl as the expression
        is KtProperty -> throw Unsupported("Property expressions not supported")
        // TODO: this is present in a recovery test where an interface decl is on rhs of a gt expr
        is KtClass -> throw Unsupported("Class expressions not supported")
        else -> error("Unrecognized expression type from $v")
    }

    fun convertFile(v: KtFile) = Node.File(
        anns = v.annotations.map(::convertAnnotationSet),
        pkg = v.packageDirective?.let(::convertPackage),
        imports = v.importDirectives.map(::convertImport),
        decls = v.declarations.map(::convertDecl)
    ).map(v)

    fun convertFor(v: KtForExpression) = Node.Expr.For(
        anns = v.loopParameter?.annotations?.map(::convertAnnotationSet) ?: emptyList(),
        vars = convertPropertyVars(v.loopParameter ?: error("No param on for $v")),
        inExpr = convertExpr(v.loopRange ?: error("No in range for $v")),
        body = convertExpr(v.body ?: error("No body for $v"))
    ).map(v)

    fun convertFunc(v: KtNamedFunction) = Node.Decl.Func(
        mods = convertModifiers(v),
        typeParams =
            if (v.hasTypeParameterListBeforeFunctionName()) v.typeParameters.map(::convertTypeParam) else emptyList(),
        receiverType = v.receiverTypeReference?.let(::convertType),
        // TODO: this is apparently allowed by the parser
        name = v.name ?: throw Unsupported("Functions without names are not supported"),
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
        type = convertType(v.typeReference ?: throw Unsupported("Function parameters without types not supported")),
        default = v.defaultValue?.let(::convertExpr)
    ).map(v)

    fun convertIf(v: KtIfExpression) = Node.Expr.If(
        expr = convertExpr(v.condition ?: error("No cond on if for $v")),
        body = convertExpr(v.then ?: error("No then on if for $v")),
        elseBody = v.`else`?.let(::convertExpr)
    ).map(v)

    fun convertImport(v: KtImportDirective) = Node.Import(
        names = v.importedFqName?.pathSegments()?.map { it.asString() } ?: error("Missing import path"),
        wildcard = v.isAllUnder,
        alias = v.aliasName
    ).map(v)

    fun convertInit(v: KtAnonymousInitializer) = Node.Decl.Init(
        stmts = v.body.block.map(::convertStmt)
    ).map(v)

    fun convertLabeled(v: KtLabeledExpression) = Node.Expr.Labeled(
        label = v.getLabelName() ?: error("No label name for $v"),
        expr = convertExpr(v.baseExpression ?: error("No label expr for $v"))
    ).map(v)

    fun convertModifiers(v: KtModifierListOwner) = v.modifierList?.node?.children()?.mapNotNull { node ->
        // We go over the node children because we want to preserve order
        node.psi.let {
            when (it) {
                is KtAnnotationEntry ->
                    Node.Modifier.AnnotationSet(target = null, anns = listOf(convertAnnotation(it))).map(it)
                is KtAnnotation -> convertAnnotationSet(it)
                is PsiWhiteSpace -> null
                else -> when (node.text) {
                    // We ignore some modifiers because we handle them elsewhere
                    "enum", "companion" -> null
                    else -> modifiersByText[node.text]?.let {
                        Node.Modifier.Lit(it).let { lit -> (node.psi as? KtElement)?.let { lit.map(it) } ?: lit }
                    } ?: error("Unrecognized modifier: ${node.text}")
                }
            }
        }
    }?.toList() ?: emptyList()

    fun convertName(v: KtSimpleNameExpression) = Node.Expr.Name(
        name = v.getReferencedName()
    ).map(v)

    fun convertObject(v: KtObjectLiteralExpression) = Node.Expr.Object(
        parents = v.objectDeclaration.superTypeListEntries.map(::convertParent),
        members = v.objectDeclaration.declarations.map(::convertDecl)
    ).map(v)

    fun convertPackage(v: KtPackageDirective) = Node.Package(
        mods = convertModifiers(v),
        names = v.packageNames.map { it.getReferencedName() }
    ).map(v)

    fun convertParen(v: KtParenthesizedExpression) = Node.Expr.Paren(
        expr = convertExpr(v.expression ?: error("No paren expr for $v"))
    )

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

    fun convertProperty(v: KtDestructuringDeclaration) = Node.Decl.Property(
        mods = convertModifiers(v),
        readOnly = !v.isVar,
        typeParams = emptyList(),
        receiverType = null,
        vars = v.entries.map(::convertPropertyVar),
        typeConstraints = emptyList(),
        delegated = false,
        expr = v.initializer?.let(::convertExpr),
        accessors = null
    ).map(v)

    fun convertProperty(v: KtProperty) = Node.Decl.Property(
        mods = convertModifiers(v),
        readOnly = !v.isVar,
        typeParams = v.typeParameters.map(::convertTypeParam),
        receiverType = v.receiverTypeReference?.let(::convertType),
        vars = listOf(Node.Decl.Property.Var(
            name = v.name ?: error("No property name on $v"),
            type = v.typeReference?.let(::convertType)
        ).map(v)),
        typeConstraints = v.typeConstraints.map(::convertTypeConstraint),
        delegated = v.hasDelegateExpression(),
        expr = v.delegateExpressionOrInitializer?.let(::convertExpr),
        accessors = v.accessors.map(::convertPropertyAccessor).let {
            if (it.isEmpty()) null else Node.Decl.Property.Accessors(
                first = it.first(),
                second = it.getOrNull(1)
            )
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

    fun convertPropertyVar(v: KtDestructuringDeclarationEntry) =
        if (v.name == "_") null else Node.Decl.Property.Var(
            name = v.name ?: error("No property name on $v"),
            type = v.typeReference?.let(::convertType)
        ).map(v)

    fun convertPropertyVars(v: KtParameter) =
        v.destructuringDeclaration?.entries?.map(::convertPropertyVar) ?: listOf(Node.Decl.Property.Var(
            name = v.name ?: error("No property name on $v"),
            type = v.typeReference?.let(::convertType)
        ).map(v))

    fun convertReturn(v: KtReturnExpression) = Node.Expr.Return(
        label = v.getLabelName(),
        expr = v.returnedExpression?.let(::convertExpr)
    ).map(v)

    fun convertStmt(v: KtExpression) =
        if (v is KtDeclaration) Node.Stmt.Decl(convertDecl(v)).map(v) else Node.Stmt.Expr(convertExpr(v)).map(v)

    fun convertStringTmpl(v: KtStringTemplateExpression) = Node.Expr.StringTmpl(
        elems = v.entries.map(::convertStringTmplElem)
    ).map(v)

    fun convertStringTmplElem(v: KtStringTemplateEntry) = when (v) {
        is KtLiteralStringTemplateEntry ->
            Node.Expr.StringTmpl.Elem.Regular(v.text).map(v)
        is KtSimpleNameStringTemplateEntry ->
            Node.Expr.StringTmpl.Elem.ShortTmpl(v.expression?.text ?: error("No short tmpl text")).map(v)
        is KtBlockStringTemplateEntry ->
            Node.Expr.StringTmpl.Elem.LongTmpl(convertExpr(v.expression ?: error("No expr tmpl"))).map(v)
        is KtEscapeStringTemplateEntry ->
            if (v.text.startsWith("\\u"))
                Node.Expr.StringTmpl.Elem.UnicodeEsc(v.text.substring(2)).map(v)
            else
                Node.Expr.StringTmpl.Elem.RegularEsc(v.unescapedValue.first()).map(v)
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

    fun convertSuper(v: KtSuperExpression) = Node.Expr.Super(
        typeArg = v.superTypeQualifier?.let(::convertType),
        label = v.getLabelName()
    ).map(v)

    fun convertThis(v: KtThisExpression) = Node.Expr.This(
        label = v.getLabelName()
    ).map(v)

    fun convertThrow(v: KtThrowExpression) = Node.Expr.Throw(
        expr = convertExpr(v.thrownExpression ?: error("No throw expr for $v"))
    ).map(v)

    fun convertTry(v: KtTryExpression) = Node.Expr.Try(
        stmts = v.tryBlock.block.map(::convertStmt),
        catches = v.catchClauses.map(::convertTryCatch),
        finallyStmts = v.finallyBlock?.finalExpression?.block?.map(::convertStmt) ?: emptyList()
    ).map(v)

    fun convertTryCatch(v: KtCatchClause) = Node.Expr.Try.Catch(
        anns = v.catchParameter?.annotations?.map(::convertAnnotationSet) ?: emptyList(),
        varName = v.catchParameter?.name ?: error("No catch param name for $v"),
        varType = v.catchParameter?.typeReference?.
            let(::convertTypeRef) as? Node.TypeRef.Simple ?: error("Invalid catch param type for $v"),
        stmts = v.catchBody.block.map(::convertStmt)
    ).map(v)

    fun convertType(v: KtTypeProjection) = v.typeReference?.let { convertType(it) }

    fun convertType(v: KtTypeReference): Node.Type = Node.Type(
        mods = convertModifiers(v),
        ref = convertTypeRef(v)
    ).map(v)

    fun convertTypeAlias(v: KtTypeAlias) = Node.Decl.TypeAlias(
        mods = convertModifiers(v),
        typeParams = v.typeParameters.map(::convertTypeParam),
        type = convertType(v.getTypeReference() ?: error("No type ref for alias $v"))
    ).map(v)

    fun convertTypeConstraint(v: KtTypeConstraint) = Node.TypeConstraint(
        anns = v.children.mapNotNull {
            when (it) {
                is KtAnnotationEntry ->
                    Node.Modifier.AnnotationSet(target = null, anns = listOf(convertAnnotation(it))).map(it)
                is KtAnnotation -> convertAnnotationSet(it)
                else -> null
            }
        },
        name = v.subjectTypeParameterName?.getReferencedName() ?: error("No type constraint name for $v"),
        type = convertType(v.boundTypeReference ?: error("No type constraint type for $v"))
    ).map(v)

    fun convertTypeOp(v: KtBinaryExpressionWithTypeRHS) = Node.Expr.TypeOp(
        lhs = convertExpr(v.left),
        oper = v.operationReference.let {
            Node.Expr.TypeOp.Oper(typeTokensByText[it.text] ?: error("Unable to find op ref $it")).map(it)
        },
        rhs = convertType(v.right ?: error("No type op rhs for $v"))
    ).map(v)

    fun convertTypeOp(v: KtIsExpression) = Node.Expr.TypeOp(
        lhs = convertExpr(v.leftHandSide),
        oper = v.operationReference.let {
            Node.Expr.TypeOp.Oper(typeTokensByText[it.text] ?: error("Unable to find op ref $it")).map(it)
        },
        rhs = convertType(v.typeReference ?: error("No type op rhs for $v"))
    )

    fun convertTypeParam(v: KtTypeParameter) = Node.TypeParam(
        mods = convertModifiers(v),
        name = v.name ?: error("No type param name for $v"),
        type = v.extendsBound?.let(::convertTypeRef) as? Node.TypeRef.Simple
    ).map(v)

    fun convertTypeRef(v: KtTypeReference) = convertTypeRef(v.typeElement ?: error("Missing typ elem")).let {
        if (v.hasParentheses()) Node.TypeRef.Paren(it).map(v) else it
    }

    fun convertTypeRef(v: KtTypeElement): Node.TypeRef = when (v) {
        is KtFunctionType -> Node.TypeRef.Func(
            receiverType = v.receiverTypeReference?.let(::convertType),
            params = v.parameters.map {
                Node.TypeRef.Func.Param(
                    name = it.name,
                    type = convertType(it.typeReference ?: error("No param type"))
                ).map(it)
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

    fun convertUnaryOp(v: KtUnaryExpression) = Node.Expr.UnaryOp(
        expr = convertExpr(v.baseExpression ?: error("No unary expr for $v")),
        oper = v.operationReference.let {
            Node.Expr.UnaryOp.Oper(unaryTokensByText[it.text] ?: error("Unable to find op ref $it")).map(it)
        },
        prefix = v is KtPrefixExpression
    ).map(v)

    fun convertValueArg(v: KtValueArgument) = Node.ValueArg(
        name = v.getArgumentName()?.asName?.asString(),
        asterisk = v.getSpreadElement() != null,
        expr = convertExpr(v.getArgumentExpression() ?: error("No expr for value arg"))
    ).map(v)

    fun convertValueArgs(v: KtValueArgumentList?) = v?.arguments?.map(::convertValueArg) ?: emptyList()

    fun convertWhen(v: KtWhenExpression) = Node.Expr.When(
        expr = v.subjectExpression?.let(::convertExpr),
        entries = v.entries.map(::convertWhenEntry)
    ).map(v)

    fun convertWhenCond(v: KtWhenCondition) = when (v) {
        is KtWhenConditionWithExpression -> Node.Expr.When.Cond.Expr(
            expr = convertExpr(v.expression ?: error("No when cond expr for $v"))
        ).map(v)
        is KtWhenConditionInRange -> Node.Expr.When.Cond.In(
            expr = convertExpr(v.rangeExpression ?: error("No when in expr for $v")),
            not = v.isNegated
        ).map(v)
        is KtWhenConditionIsPattern -> Node.Expr.When.Cond.Is(
            type = convertType(v.typeReference ?: error("No when is type for $v")),
            not = v.isNegated
        ).map(v)
        else -> error("Unrecognized when cond of $v")
    }

    fun convertWhenEntry(v: KtWhenEntry) = Node.Expr.When.Entry(
        conds = v.conditions.map(::convertWhenCond),
        body = convertExpr(v.expression ?: error("No when entry body for $v"))
    ).map(v)

    fun convertWhile(v: KtWhileExpressionBase) = Node.Expr.While(
        expr = convertExpr(v.condition ?: error("No while cond for $v")),
        body = convertExpr(v.body ?: error("No while body for $v")),
        doWhile = v is KtDoWhileExpression
    ).map(v)

    protected fun <T: Node> T.map(v: KtElement) = also { nodeMapCallback?.invoke(it, v) }

    class Unsupported(message: String) : UnsupportedOperationException(message)

    companion object : Converter() {
        internal val modifiersByText = Node.Modifier.Keyword.values().map { it.name.toLowerCase() to it }.toMap()
        internal val binaryTokensByText = Node.Expr.BinaryOp.Token.values().map { it.str to it }.toMap()
        internal val unaryTokensByText = Node.Expr.UnaryOp.Token.values().map { it.str to it }.toMap()
        internal val typeTokensByText = Node.Expr.TypeOp.Token.values().map { it.str to it }.toMap()

        internal val KtTypeReference.names get() = (typeElement as? KtUserType)?.names ?: emptyList()
        internal val KtUserType.names get(): List<String> =
            referencedName?.let { (qualifier?.names ?: emptyList()) + it } ?: emptyList()
        internal val KtExpression?.block get() = (this as? KtBlockExpression)?.statements ?: emptyList()
    }
}