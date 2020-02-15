package com.codebrig.phenomenon.kythe.model

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
    String buildDirectory

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
}
