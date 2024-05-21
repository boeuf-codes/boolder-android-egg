package com.boolder.boolder.domain.model

data class Clause (
    val literalOne: String,
    val literalTwo: String?,
    val literalThree: String?,
    val type: ClauseType?,
) {
    fun toDIMACSString(): String {
        if (literalTwo != null && literalThree != null) {
            return "$literalOne $literalTwo $literalThree 0${System.lineSeparator()}"
        } else if (literalTwo != null && literalThree == null) {
            return "$literalOne $literalTwo 0${System.lineSeparator()}"
        } else {
            return "$literalOne 0${System.lineSeparator()}"
        }
    }

    fun toTerminalDIMACSString(): String {
        if (literalTwo != null && literalThree != null) {
            return "$literalOne $literalTwo $literalThree 0"
        } else if (literalTwo != null && literalThree == null) {
            return "$literalOne $literalTwo 0"
        } else {
            return "$literalOne 0"
        }
    }
}
