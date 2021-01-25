package com.codebrig.phenomenon.kythe

import com.codebrig.phenomena.code.CodeObserver
import com.codebrig.phenomenon.kythe.build.KytheIndexBuilder
import com.codebrig.phenomenon.kythe.model.KytheIndex

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
abstract class KytheIndexObserver extends CodeObserver {

    protected KytheIndexBuilder indexBuilder

    KytheIndexObserver(KytheIndexBuilder indexBuilder) {
        this.indexBuilder = Objects.requireNonNull(indexBuilder)
    }

    abstract void preprocessKytheTriple(KytheIndex index, String subject, String predicate, String object)
}
