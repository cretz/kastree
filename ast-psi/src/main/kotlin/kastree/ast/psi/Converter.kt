package kastree.ast.psi

import kastree.ast.Node
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*

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

    fun convertDecl(v: KtDeclaration): Node.Decl = when (v) {
        is KtAnonymousInitializer -> convertInit(v)
        is KtClassOrObject -> convertStructured(v)
        is KtFunction -> convertFunc(v)
        else -> TODO()
    }

    fun convertFile(v: KtFile) = Node.File(
        anns = v.fileAnnotationList?.annotations?.map(::convertAnnotationSet) ?: emptyList(),
        pkg = v.packageDirective?.let(::convertPackage),
        imports = v.importDirectives.map(::convertImport),
        decls = v.declarations.map(::convertDecl)
    )

    fun convertFunc(v: KtFunction): Node.Decl.Func = TODO()

    fun convertImport(v: KtImportDirective) = Node.Import(
        names = v.importedFqName?.pathSegments()?.map { it.asString() } ?: error("Missing import path"),
        wildcard = v.isAllUnder,
        alias = v.aliasName
    )

    fun convertInit(v: KtAnonymousInitializer) = Node.Decl.Init(
        stmts = (v.body as? KtBlockExpression)?.statements?.map(::convertStmt) ?: emptyList()
    )

    fun convertModifiers(v: KtModifierListOwnerStub<*>): List<Node.Modifier> {
        // We want to preserve the order
        return v.children.mapNotNull {
            TODO("Work with $it")
        }
    }

    fun convertPackage(v: KtPackageDirective) = Node.Package(
        mods = convertModifiers(v),
        names = v.packageNames.map { it.getReferencedName() }
    )

    fun convertParent(v: KtSuperTypeListEntry): Node.Decl.Structured.Parent = TODO()

    fun convertPrimaryConstructor(v: KtPrimaryConstructor): Node.Decl.Structured.PrimaryConstructor = TODO()

    fun convertStmt(v: KtExpression): Node.Stmt = TODO()

    fun convertStructured(v: KtClassOrObject) = Node.Decl.Structured(
        mods = convertModifiers(v),
        form = when (v) {
            is KtClass ->
                if (v.isEnum()) Node.Decl.Structured.Form.ENUM_CLASS
                else if (v.isInterface()) Node.Decl.Structured.Form.INTERFACE
                else Node.Decl.Structured.Form.CLASS
            is KtObjectDeclaration ->
                if (v.isCompanion()) Node.Decl.Structured.Form.COMPANION_OBJECT
                else Node.Decl.Structured.Form.OBJECT
            else -> error("Unknown type of $v")
        },
        name = v.name ?: error("Missing name"),
        typeParams = v.typeParameters.map(::convertTypeParam),
        primaryConstructor = v.primaryConstructor?.let(::convertPrimaryConstructor),
        parentAnns = emptyList<Node.Modifier.AnnotationSet>().also { TODO() },
        parents = v.superTypeListEntries.map(::convertParent),
        typeConstraints = v.typeConstraints.map(::convertTypeConstraint),
        members = v.declarations.map(::convertDecl)
    )

    fun convertType(v: KtTypeProjection): Node.Type = TODO()

    fun convertTypeConstraint(v: KtTypeConstraint): Node.TypeConstraint = TODO()

    fun convertTypeParam(v: KtTypeParameter): Node.TypeParam = TODO()

    fun convertValueArg(v: ValueArgument): Node.ValueArg = TODO()

    companion object : Converter() {
        // TODO: Use KtTokens.MODIFIER_KEYWORDS_ARRAY
//        val modifierTokens = Node.Modifier.Keyword.values().map {
//            KtModifierKeywordToken.keywordModifier(it.name.toLowerCase())
//        }

        val KtTypeReference.names get() = (typeElement as? KtUserType)?.names ?: emptyList()
        val KtUserType.names get(): List<String> =
            referencedName?.let { (qualifier?.names ?: emptyList()) + it } ?: emptyList()
    }
}