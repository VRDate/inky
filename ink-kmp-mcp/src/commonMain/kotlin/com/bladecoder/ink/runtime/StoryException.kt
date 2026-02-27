package com.bladecoder.ink.runtime

/**
 * Exception that represents an error when running a Story at runtime.
 * An exception being thrown of this type is typically when there's a bug
 * in your ink, rather than in the ink engine itself!
 */
class StoryException(message: String? = null) : Exception(message) {
    var useEndLineNumber: Boolean = false
}
