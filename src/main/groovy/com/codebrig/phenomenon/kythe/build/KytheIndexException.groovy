package com.codebrig.phenomenon.kythe.build

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
class KytheIndexException extends Exception {

    KytheIndexException() {
    }

    KytheIndexException(String message) {
        super(message)
    }

    KytheIndexException(String message, Throwable cause) {
        super(message, cause)
    }

    KytheIndexException(Throwable cause) {
        super(cause)
    }
}
