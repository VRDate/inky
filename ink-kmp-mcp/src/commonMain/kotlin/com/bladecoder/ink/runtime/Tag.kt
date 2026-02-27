package com.bladecoder.ink.runtime

class Tag(val text: String) : InkObject() {
    override fun toString(): String = "# $text"
}
