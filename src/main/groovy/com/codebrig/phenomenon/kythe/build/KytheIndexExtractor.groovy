package com.codebrig.phenomenon.kythe.build

import com.codebrig.phenomenon.kythe.KytheIndexObserver
import com.codebrig.phenomenon.kythe.model.KytheIndex
import com.codebrig.phenomenon.kythe.model.QualifiedClassFile
import com.google.common.collect.Sets
import com.google.devtools.kythe.proto.MarkedSource
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
class KytheIndexExtractor {

    private static final Set<String> KYTHE_ENTITY_PARSE_SET = Sets.newHashSet(
            "/kythe/node/kind", "/kythe/subkind",
            "/kythe/loc/start", "/kythe/loc/end")
    private static final Set<String> KYTHE_RELATIONSHIP_PARSE_SET = Sets.newHashSet(
            "/kythe/edge/childof", "/kythe/edge/ref/call")
    private static final String triplesRegexPattern = '\"(.+)\" \"(.+)\" \"(.*)\"'
    private static Set<String> classTypes = Sets.newHashSet("class", "enumClass", "interface")
    private final File kytheDirectory
    private final List<KytheIndexObserver> indexObservers

    KytheIndexExtractor(File kytheDirectory, List<KytheIndexObserver> indexObservers) {
        this.kytheDirectory = Objects.requireNonNull(kytheDirectory)
        this.indexObservers = Objects.requireNonNull(indexObservers)
    }

    KytheIndex processIndexFile(File importFile) {
        def index = new KytheIndex()
        index.kytheDirectory = kytheDirectory
        index.importFile = importFile
        index.classes = new HashMap<>()//s.openMap("classes")
        index.definedFunctions = new HashMap<>()//s.openMap("functions")
        index.extractedNodes = new HashMap<>()//s.openMap("extractedNodes")
        index.bindings = new HashMap<>()//s.openMap("bindings")
        index.paramToTypeMap = new HashMap<>()//s.openMap("paramToTypeMap")
        index.fileLocations = new HashMap<>()//s.openMap("fileLocations")
        index.sourceLocationMap = new HashMap<>()//s.openMap("sourceLocationMap")

        //todo: clean this for max of 2 reads
        preprocessEntities(index)
        processEntities(index)
        processRelationships(index)
        index.importFile.eachLine {
            String[] row = ((it =~ triplesRegexPattern)[0] as String[]).drop(1)
            def subject = row[0]
            def predicate = row[1]
            def object = row[2]

            indexObservers.each {
                it.preprocessKytheTriple(index, subject, predicate, object)
            }
        }

        return index
    }

    private static void preprocessEntities(KytheIndex index) {
        index.importFile.eachLine {
            String[] row = ((it =~ triplesRegexPattern)[0] as String[]).drop(1)
            def subjectUri = index.toUniversalUri(KytheURI.parse(row[0]))
            index.getExtractedNode(subjectUri).uri = subjectUri

            def predicate = row[1]
            def object = row[2]
            if (predicate == "/kythe/node/kind" && object == "file") {
                def definedFile = index.getQualifiedName(subjectUri, true)
                index.definedFiles.add(definedFile)
                log.info("Defined file: " + definedFile)
            }
            if ((predicate == "/kythe/node/kind" || predicate == "/kythe/subkind") && classTypes.contains(object)) {
                def fileLocation = KytheURI.parse(row[0]).path
                if (!fileLocation.isEmpty()) {
                    index.fileLocations.put(subjectUri.toString(), fileLocation)
                }
            } else if (predicate == "/kythe/edge/defines/binding") {
                def objectUri = index.toUniversalUri(KytheURI.parse(object))
                index.getExtractedNode(objectUri).uri = objectUri
                index.addBinding(subjectUri, objectUri)
            } else if (predicate == "/kythe/edge/childof") {
                def objectUri = index.toUniversalUri(KytheURI.parse(object))
                if (!objectUri.path.isEmpty()) {
                    index.getExtractedNode(objectUri).uri = objectUri
                    def parentNode = index.getExtractedNode(objectUri)
                    index.getExtractedNode(subjectUri).setParentNode(parentNode)
                }
            } else if (predicate.startsWith("/kythe/edge/param.")) {
                def objectUri = index.toUniversalUri(KytheURI.parse(object))
                def extractedFunction = index.getExtractedNode(subjectUri)
                extractedFunction.addParam(predicate.replace("/kythe/edge/param.", "") as int, objectUri)
            } else if (predicate == "/kythe/edge/named") {
                def namedNode = index.getExtractedNode(subjectUri)
                def className = object.substring(object.indexOf("#") + 1)
                namedNode.context = className.substring(0, className.lastIndexOf(".") + 1)
                namedNode.identifier = URLDecoder.decode(className.substring(className.lastIndexOf(".") + 1), "UTF-8")
            } else if (predicate == "/kythe/code") {
                def markedSource = MarkedSource.parseFrom(object.decodeBase64())
                if (markedSource.childCount == 0) {
                    return //nothing to do
                }

                def type = ""
                def context = ""
                def identifier = ""
                def isParam = false
                def hasInitializer = false
                def isFunction = false
                for (int i = 0; i < markedSource.childCount; i++) {
                    def child = markedSource.getChild(i)
                    if (child.kind == MarkedSource.Kind.TYPE) {
                        type = getType(child)
                        isParam = true
                    } else if (child.kind == MarkedSource.Kind.CONTEXT) {
                        context = getContext(child)
                    } else if (child.kind == MarkedSource.Kind.IDENTIFIER) {
                        identifier = child.preText
                    } else if (child.kind == MarkedSource.Kind.INITIALIZER) {
                        hasInitializer = true
                    } else if (child.kind == MarkedSource.Kind.PARAMETER_LOOKUP_BY_PARAM) {
                        isFunction = true
                    }
                }
                if (hasInitializer) {
                    //do nothing; need function definitions not function calls
                } else if (!isFunction && isParam) {
                    index.paramToTypeMap.put(subjectUri.toString(), type)
                } else {
                    index.getExtractedNode(subjectUri).uri = subjectUri
                    index.getExtractedNode(subjectUri).context = context
                    index.getExtractedNode(subjectUri).identifier = identifier
                    index.getExtractedNode(subjectUri).isFunction = isFunction
                }
            }
        }
    }

    private static void processEntities(KytheIndex index) {
        index.importFile.eachLine {
            String[] row = ((it =~ triplesRegexPattern)[0] as String[]).drop(1)
            String subject = row[0]
            String predicate = row[1]
            if (KYTHE_ENTITY_PARSE_SET.contains(predicate)) {
                def object = row[2]
                processRecordEntity(subject, predicate, object, index)
            }
        }
    }

    private static void processRelationships(KytheIndex index) {
        index.importFile.eachLine {
            String[] row = ((it =~ triplesRegexPattern)[0] as String[]).drop(1)
            def subject = row[0]
            String predicate = row[1]
            if (KYTHE_RELATIONSHIP_PARSE_SET.contains(predicate)) {
                def object = row[2]
                processRecordRelationship(subject, predicate, object, index)
            }
        }
    }

    private static void processRecordEntity(String subject, String predicate, String object, KytheIndex index) {
        if (predicate == "/kythe/node/kind" || predicate == "/kythe/subkind") {
            if (classTypes.contains(object) || object == "function") {
                if (!index.isJDK(subject)) {
                    def subjectUri = index.toUniversalUri(KytheURI.parse(subject))
                    if (classTypes.contains(object)) {
                        index.getExtractedNode(subjectUri).isFile = true
                        def fileLocation = subject.substring(subject.indexOf("path=") + 5)
                        if (fileLocation.contains("#")) {
                            fileLocation = fileLocation.substring(0, fileLocation.indexOf("#"))
                        }
                        def classQualifiedName = index.getQualifiedName(subjectUri)
                        if (index.definedFiles.contains(classQualifiedName)) {
                            index.classes.put(subjectUri, new QualifiedClassFile(classQualifiedName, fileLocation))
                        }
                    }
                    if (object == "function") {
                        index.getExtractedNode(subjectUri).isFunction = true
                        index.functionNameSet.add(subjectUri.signature)
                    }
                    index.getExtractedNode(subjectUri).uri = subjectUri
                }
            }
        } else if (predicate == "/kythe/loc/start") {
            def subjectUri = index.toUniversalUri(KytheURI.parse(subject))
            subjectUri = index.getBindedNode(subjectUri).uri

            if (index.sourceLocationMap.containsKey(subjectUri.signature)) {
                index.sourceLocationMap.put(subjectUri.signature,
                        [Integer.parseInt(object), index.sourceLocationMap.get(subjectUri.signature)[1]] as int[])
            } else {
                index.sourceLocationMap.put(subjectUri.signature, [Integer.parseInt(object), -1] as int[])
            }
        } else if (predicate == "/kythe/loc/end") {
            def subjectUri = index.toUniversalUri(KytheURI.parse(subject))
            subjectUri = index.getBindedNode(subjectUri).uri

            if (index.sourceLocationMap.containsKey(subjectUri.signature)) {
                index.sourceLocationMap.put(subjectUri.signature,
                        [index.sourceLocationMap.get(subjectUri.signature)[0], Integer.parseInt(object)] as int[])
            } else {
                index.sourceLocationMap.put(subjectUri.signature, [-1, Integer.parseInt(object)] as int[])
            }
        }
    }

    private static void processRecordRelationship(String subject, String predicate, String object, KytheIndex index) {
        def subjectUriOriginal = index.toUniversalUri(KytheURI.parse(subject))
        def objectUriOriginal = index.toUniversalUri(KytheURI.parse(object))
        def subjectNode = index.getParentNode(subjectUriOriginal)
        def objectNode = index.getParentNode(objectUriOriginal)
        if (subjectNode?.uri == null || objectNode?.uri == null) {
            return
        }

        if (predicate == "/kythe/edge/childof") {
            if (index.isJDK(subjectNode.uri) || index.isJDK(objectNode.uri)) {
                return //no jdk
            } else if (!objectNode.isFile || !subjectNode.isFunction) {
                return //todo: what are these?
            }

            def subjectUri = subjectNode.uri
            def qualifiedName = subjectNode.getQualifiedName(index)
            def classQualifiedName = index.getQualifiedClassName(qualifiedName)
            if (classQualifiedName.contains('$')) {
                classQualifiedName = classQualifiedName.substring(0, classQualifiedName.indexOf('$'))
                while (objectNode.parentNode?.isFile && objectNode.parentNode.uri != null
                        && objectNode != objectNode.parentNode) { //todo: how does node become its own parent?
                    objectNode = objectNode.parentNode
                }
            }
            if (index.definedFiles.contains(classQualifiedName)) {
                log.info "Defined function: " + qualifiedName
                index.definedFunctions.put(qualifiedName, subjectUri)
            } else {
                log.warn "Undefined function: $qualifiedName - Could not find file definition: $classQualifiedName"
            }
        }
    }

    private static String getType(MarkedSource markedSource) {
        if (markedSource.kind != MarkedSource.Kind.TYPE) {
            throw new IllegalArgumentException("Marked source missing context")
        }

        def postText = ""
        def type = ""
        for (int i = 0; i < markedSource.childCount; i++) {
            def child = markedSource.getChild(i)
            if (child.postText == "[]") {
                //todo: this, better
                postText += child.postText
            }

            if (child.kind == MarkedSource.Kind.IDENTIFIER) {
                type += child.preText
                if ((i + 1) < markedSource.childCount || markedSource.addFinalListToken) {
                    type += markedSource.postChildText
                }
            } else if (child.kind == MarkedSource.Kind.CONTEXT) {
                type += getContext(child)
            }
        }

        def typeChild = markedSource.getChild(0)
        if (typeChild.kind == MarkedSource.Kind.BOX && typeChild.childCount == 1) {
            typeChild = typeChild.getChild(0)
        }
        for (int i = 0; i < typeChild.childCount; i++) {
            def child = typeChild.getChild(i)
            if (child.kind == MarkedSource.Kind.IDENTIFIER) {
                type += child.preText
                if ((i + 1) < typeChild.childCount || typeChild.addFinalListToken) {
                    type += typeChild.postChildText
                }
            } else if (child.kind == MarkedSource.Kind.CONTEXT) {
                type += getContext(child)
            }
        }
        return type + postText
    }

    private static String getContext(MarkedSource markedSource) {
        if (markedSource.kind != MarkedSource.Kind.CONTEXT) {
            throw new IllegalArgumentException("Marked source missing context")
        }

        def context = ""
        for (int i = 0; i < markedSource.childCount; i++) {
            def child = markedSource.getChild(i)
            if (child.kind == MarkedSource.Kind.IDENTIFIER) {
                context += child.preText
                if ((i + 1) < markedSource.childCount || markedSource.addFinalListToken) {
                    context += markedSource.postChildText
                }
            }

            for (int z = 0; z < child.childCount; z++) {
                def grandChild = child.getChild(z)
                context += grandChild.preText
                if ((z + 1) < child.childCount || child.addFinalListToken) {
                    context += child.postChildText
                }
            }
        }
        return context
    }
}
