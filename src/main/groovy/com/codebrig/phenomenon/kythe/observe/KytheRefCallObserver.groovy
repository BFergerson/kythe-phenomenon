package com.codebrig.phenomenon.kythe.observe

import com.codebrig.arthur.observe.structure.StructureFilter
import com.codebrig.arthur.observe.structure.filter.FunctionFilter
import com.codebrig.arthur.observe.structure.filter.TypeFilter
import com.codebrig.phenomena.code.ContextualNode
import com.codebrig.phenomenon.kythe.KytheIndexObserver
import com.codebrig.phenomenon.kythe.build.KytheIndexExtractor
import com.codebrig.phenomenon.kythe.model.KytheIndex
import com.codebrig.phenomenon.kythe.model.KytheReferenceCall
import com.google.devtools.kythe.util.KytheURI

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
class KytheRefCallObserver extends KytheIndexObserver {

    private static final FunctionFilter FUNCTION_FILTER = new FunctionFilter()
    private static final TypeFilter FUNCTION_CALL_FILTER = new TypeFilter("MethodInvocation") //todo: language agnostic
    private Map<String, Map<String, KytheReferenceCall>> referenceCalls = new HashMap<>()
    private Map<String, List<ContextualNode>> functionCalls = new HashMap<>()
    private Set<String> internalMethodSignatures = new HashSet<>()

    @Override
    void applyObservation(ContextualNode node, ContextualNode parentNode) {
        internalMethodSignatures.add(node.name)
        if (functionCalls.get(node.name)) {
            functionCalls.remove(node.name)
        }

        def functionRefCalls = referenceCalls.get(node.name)
        FUNCTION_CALL_FILTER.getFilteredNodes(node).each {
            def callPosition = it.underlyingNode.startPosition.offset() + "," + it.underlyingNode.endPosition.offset()
            def refCall = functionRefCalls.get(callPosition)
            if (refCall != null && !internalMethodSignatures.contains(refCall.calledQualifiedName)) {
                functionCalls.putIfAbsent(refCall.calledQualifiedName, new ArrayList<>())
                functionCalls.get(refCall.calledQualifiedName).add(codeObserverVisitor.getOrCreateContextualNode(it, node.sourceFile))
            }
        }
    }

    @Override
    void preprocessKytheTriple(KytheIndex index, String subject, String predicate, String object) {
        if (predicate == "/kythe/edge/ref/call") {
            def subjectUriOriginal = KytheIndexExtractor.toUniversalUri(KytheURI.parse(subject))
            def objectUriOriginal = KytheIndexExtractor.toUniversalUri(KytheURI.parse(object))
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

//            if (KytheIndexExtractor.isJDK(subjectNode.uri) || KytheIndexExtractor.isJDK(objectNode.uri)) {
//                return //no jdk
//            } else
            if ((!(subjectNode.isFile || subjectNode.isFunction)) || !objectNode.isFunction) {
                return //todo: what are these?
            }

            def subjectUri = subjectNode.uri
            def objectUri = objectNode.uri
            def subjectQualifiedName = subjectNode.getQualifiedName(index)
            def objectQualifiedName = objectNode.getQualifiedName(index)
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

    Map<String, List<ContextualNode>> getFunctionCalls() {
        return functionCalls
    }

    @Override
    StructureFilter getFilter() {
        return FUNCTION_FILTER
    }
}
