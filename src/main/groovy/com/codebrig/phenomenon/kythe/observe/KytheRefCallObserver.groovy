package com.codebrig.phenomenon.kythe.observe

import com.codebrig.arthur.observe.structure.StructureFilter
import com.codebrig.arthur.observe.structure.filter.FunctionFilter
import com.codebrig.arthur.observe.structure.filter.TypeFilter
import com.codebrig.phenomena.code.ContextualNode
import com.codebrig.phenomenon.kythe.KytheIndexObserver
import com.codebrig.phenomenon.kythe.model.KytheIndex
import com.codebrig.phenomenon.kythe.model.KytheReferenceCall
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.devtools.kythe.util.KytheURI
import groovy.util.logging.Slf4j

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
@Slf4j
class KytheRefCallObserver extends KytheIndexObserver {

    private static final FunctionFilter FUNCTION_FILTER = new FunctionFilter()
    private static final TypeFilter FUNCTION_CALL_FILTER = new TypeFilter("MethodInvocation") //todo: language agnostic
    private Map<String, Map<String, KytheReferenceCall>> referenceCalls = new HashMap<>()

    @Override
    void applyObservation(ContextualNode node, ContextualNode parentNode) {
        def functionRefCalls = referenceCalls.get(node.name)
        if (functionRefCalls != null) {
            FUNCTION_CALL_FILTER.getFilteredNodes(node).each {
                def callPosition = it.underlyingNode.startPosition.offset() + "," + it.underlyingNode.endPosition.offset()
                def refCall = functionRefCalls.get(callPosition)
                if (refCall != null) {
                    def methodCallNode = codeObserverVisitor.getOrCreateContextualNode(it, node.sourceFile)
                    methodCallNode.hasAttribute("calledQualifiedName", refCall.calledQualifiedName)
                    methodCallNode.hasAttribute("calledUri", refCall.calledUri.toString())
                }
            }
        }
    }

    @Override
    void preprocessKytheTriple(KytheIndex index, String subject, String predicate, String object) {
        if (predicate == "/kythe/edge/ref/call") {
            def subjectUriOriginal = index.toUniversalUri(KytheURI.parse(subject))
            def objectUriOriginal = index.toUniversalUri(KytheURI.parse(object))
            def subjectNode = index.getParentNode(subjectUriOriginal)
            def objectNode = index.getParentNode(objectUriOriginal)

            int[] location
            if (subjectNode?.uri == null || objectNode?.uri == null) {
                return
            } else if (index.sourceLocationMap.containsKey(subjectUriOriginal.toString())) {
                location = index.sourceLocationMap.get(subjectUriOriginal.toString()) //file
            } else if (index.sourceLocationMap.containsKey(subjectUriOriginal.signature)) {
                location = index.sourceLocationMap.get(subjectUriOriginal.signature) //function
            } else {
                location = [-1, -1] //no code location
            }

            if (index.isJDK(subjectNode.uri) || index.isJDK(objectNode.uri)) {
                return //no jdk
            } else if ((!(subjectNode.isFile || subjectNode.isFunction)) || !objectNode.isFunction) {
                return //todo: what are these?
            } else if (index.definedFunctions.contains(objectNode.uri)) {
                return // internal function call
            }

            def subjectUri = subjectNode.uri
            def objectUri = objectNode.uri
            def subjectQualifiedName = subjectNode.getQualifiedName(index)
            def objectQualifiedName = objectNode.getQualifiedName(index)
            log.info subjectQualifiedName + " calls " + objectQualifiedName

            def fileLocation = index.fileLocations.get(subjectUri.toString())
            if (fileLocation == null) {
                fileLocation = Objects.requireNonNull(index.fileLocations.get(subjectNode.parentNode.uri.toString()))
            }

            def refCall = new KytheReferenceCall(subjectUri, subjectQualifiedName,
                    objectUri, objectQualifiedName, fileLocation, location)
            referenceCalls.putIfAbsent(subjectQualifiedName, new HashMap<>())
            referenceCalls.get(subjectQualifiedName).put(refCall.callSourceLocation[0] + "," + refCall.callSourceLocation[1], refCall)
        }
    }

    @Override
    StructureFilter getFilter() {
        return FUNCTION_FILTER
    }

    @Override
    String getSchema() {
        return Resources.toString(Resources.getResource("schema/external-reference-schema.gql"), Charsets.UTF_8)
    }
}
