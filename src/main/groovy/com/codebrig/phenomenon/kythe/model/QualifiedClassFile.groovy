package com.codebrig.phenomenon.kythe.model

import groovy.transform.Canonical
import groovy.transform.TupleConstructor

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
@Canonical
@TupleConstructor
class QualifiedClassFile implements Serializable {
    String qualifiedName
    String fileLocation
}
