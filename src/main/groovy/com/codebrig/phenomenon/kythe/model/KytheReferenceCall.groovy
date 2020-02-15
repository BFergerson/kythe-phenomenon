package com.codebrig.phenomenon.kythe.model

import com.google.devtools.kythe.util.KytheURI
import groovy.transform.Canonical
import groovy.transform.TupleConstructor

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
@Canonical
@TupleConstructor
class KytheReferenceCall implements Serializable {
    KytheURI callerUri
    String callerQualifiedName
    KytheURI calledUri
    String calledQualifiedName
    String callFileLocation
    int[] callSourceLocation
}
