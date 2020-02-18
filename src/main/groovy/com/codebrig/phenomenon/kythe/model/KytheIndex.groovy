package com.codebrig.phenomenon.kythe.model

import com.codebrig.phenomenon.kythe.build.KytheIndexBuilder
import com.google.devtools.kythe.util.KytheURI
import groovy.transform.Canonical
import org.apache.commons.io.FilenameUtils

/**
 * todo: description
 * todo: index handles MVSTore thing
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
@Canonical
class KytheIndex {

    Map<KytheURI, QualifiedClassFile> classes
    Set<KytheURI> definedFunctions
    Map<String, KytheNode> extractedNodes
    Map<String, String> bindings
    Map<String, String> paramToTypeMap
    Map<String, String> fileLocations
    Map<String, int[]> sourceLocationMap
    final Set<String> functionNameSet = new HashSet<>()
    final Set<String> definedFiles = new HashSet<>()
    File importFile
    File kytheDirectory

    KytheNode getBindedNode(KytheURI uri) {
        def bindingStr = bindings.get(uri.signature, uri.toString())
        return getExtractedNode(KytheURI.parse(bindingStr))
    }

    void addBinding(KytheURI subjectUri, KytheURI objectUri) {
        if (subjectUri.signature == "" || objectUri.signature == "") {
            throw new IllegalStateException("Didn't expect this")
        }
        bindings.put(subjectUri.signature, objectUri.toString())
    }

    KytheNode getParentNode(KytheURI uri) {
        def node = extractedNodes.get(uri.toString())
        if (node == null) {
            node = extractedNodes.get(uri.signature)
        }
        if (node != null) {
            if (node.isFile || node.isFunction || node.parentNode == null) {
                return node
            } else if (node.parentNode.uri != null) {
                return getParentNode(node.parentNode.uri)
            } else {
                return node.parentNode
            }
        }
        return null
    }

    KytheNode getExtractedNode(KytheURI uri) {
        if (uri.signature == null || uri.signature.isEmpty()) {
            //file
            def file = new KytheNode()
            extractedNodes.putIfAbsent(uri.toString(), file)
            return extractedNodes.get(uri.toString())
        }

        //function
        def function = new KytheNode()
        function.signatureSalt = uri.signature
        extractedNodes.putIfAbsent(uri.signature, function)
        return extractedNodes.get(uri.signature)
    }

    String getQualifiedName(KytheURI uri) {
        if (uri.signature == null || uri.signature.isEmpty()) {
            return getQualifiedName(uri, true)
        } else {
            return getQualifiedName(uri, false)
        }
    }

    String getQualifiedName(KytheURI uri, boolean isClass) {
        if (isClass) {
            def qualifiedName = FilenameUtils.removeExtension(uri.path.replace("src/main/" + uri.path.substring(
                    uri.path.lastIndexOf(".") + 1) + "/", ""))
                    .replace("/", ".")
            if (qualifiedName.contains("#")) {
                return qualifiedName.substring(0, qualifiedName.indexOf("#"))
            } else {
                return qualifiedName
            }
        } else {
            return extractedNodes.get(uri.signature).getQualifiedName(this)
        }
    }

    KytheURI toUniversalUri(KytheURI uri) {
        if (uri.corpus == "jdk") return uri
        def indexerPath = new File(kytheDirectory, KytheIndexBuilder.javacExtractorLocation).absolutePath
        if (uri.path.contains(indexerPath)) {
            uri = new KytheURI(uri.signature, uri.corpus, uri.root,
                    uri.path
                            .replaceAll(indexerPath + "!/", "")
                            .replaceAll(indexerPath + "%21/", ""),
                    uri.language)
        }
        if (uri.path.contains("src/main/") && !uri.language?.isEmpty()) {
            def srcPath = "src/main/" + uri.language + "/"
            uri = new KytheURI(uri.signature, uri.corpus, uri.root,
                    uri.path.substring(uri.path.indexOf(srcPath) + srcPath.length()), uri.language)
        } else if ((uri.path =~ '(src/main/[^/]+/)').find()) {
            String langPath = (uri.path =~ '(src/main/[^/]+/)')[0][0]
            uri = new KytheURI(uri.signature, uri.corpus, uri.root,
                    uri.path.substring(uri.path.indexOf(langPath) + langPath.length()), uri.language)
        }
        if (uri.path.contains("target/classes/")) {
            uri = new KytheURI(uri.signature, uri.corpus, uri.root,
                    uri.path.substring(uri.path.indexOf("target/classes/") + "target/classes/".length()), uri.language)
        }
        if (uri.path.contains(".jar!")) {
            uri = new KytheURI(uri.signature, uri.corpus, uri.root,
                    uri.path.substring(uri.path.indexOf(".jar!") + 6), uri.language)
        }
        if (uri.path.endsWith(".class") && !uri.language?.isEmpty()) {
            uri = new KytheURI(uri.signature, uri.corpus, uri.root,
                    uri.path.substring(0, uri.path.indexOf(".class")) + "." + uri.language, uri.language)
        }
        return uri
    }

    boolean isJDK(String uri) {
        return isJDK(toUniversalUri(KytheURI.parse(uri)))
    }

    static boolean isJDK(KytheURI uri) {
        return uri.corpus == "jdk" ||
                uri.path.startsWith("java/") ||
                uri.path.startsWith("javax/") ||
                uri.path.startsWith("sun/") ||
                uri.path.startsWith("com/sun/")
    }

    static String getQualifiedClassName(String qualifiedName) {
        if (!qualifiedName.contains('(')) {
            return qualifiedName
        }
        def withoutArgs = qualifiedName.substring(0, qualifiedName.indexOf("("))
        if (withoutArgs.contains("<")) {
            withoutArgs = withoutArgs.substring(0, withoutArgs.indexOf("<"))
            return withoutArgs.substring(withoutArgs.lastIndexOf("?") + 1, withoutArgs.lastIndexOf("."))
        } else {
            return withoutArgs.substring(withoutArgs.lastIndexOf("?") + 1, withoutArgs.lastIndexOf("."))
        }
    }
}
