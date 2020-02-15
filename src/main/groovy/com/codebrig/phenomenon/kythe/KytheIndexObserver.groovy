package com.codebrig.phenomenon.kythe

import com.codebrig.phenomena.code.CodeObserver
import com.codebrig.phenomenon.kythe.model.KytheIndex

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon.fergerson@codebrig.com">Brandon Fergerson</a>
 */
abstract class KytheIndexObserver extends CodeObserver {
    abstract void preprocessKytheTriple(KytheIndex index, String subject, String predicate, String object)
}
