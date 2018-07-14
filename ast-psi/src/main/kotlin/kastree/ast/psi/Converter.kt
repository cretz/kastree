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
        else -> TODO()
    }

    fun convertFile(v: KtFile) = Node.File(
        anns = v.fileAnnotationList?.annotations?.map(::convertAnnotationSet) ?: emptyList(),
        pkg = v.packageDirective?.let(::convertPackage),
        imports = v.importDirectives.map(::convertImport),
        decls = v.declarations.map(::convertDecl)
    )

    fun convertImport(v: KtImportDirective) = Node.Import(
        names = v.importedFqName?.pathSegments()?.map { it.asString() } ?: error("Missing import path"),
        wildcard = v.isAllUnder,
        alias = v.aliasName
    )

    fun convertModifiers(v: KtModifierListOwnerStub<*>): List<Node.Modifier> = TODO()

    fun convertPackage(v: KtPackageDirective) = Node.Package(
        mods = convertModifiers(v),
        names = v.packageNames.map { it.getReferencedName() }
    )

    fun convertType(v: KtTypeProjection): Node.Type = TODO()

    fun convertValueArg(v: ValueArgument): Node.ValueArg = TODO()

    companion object : Converter() {
        val KtTypeReference.names get() = (typeElement as? KtUserType)?.names ?: emptyList()
        val KtUserType.names get(): List<String> =
            referencedName?.let { (qualifier?.names ?: emptyList()) + it } ?: emptyList()
    }
}