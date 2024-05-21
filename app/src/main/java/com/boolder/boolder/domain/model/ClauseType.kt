package com.boolder.boolder.domain.model

enum class ClauseType(val type: String?) {
    CONJUNCTION("conjunction"),
    DISJUNCTION("disjunction"),
    OTHER(null);

    companion object {
        fun fromTextValue(value: String): ClauseType = when (value.lowercase()) {
            "conjunction" -> CONJUNCTION
            "disjunction" -> DISJUNCTION
            else -> OTHER
        }
    }
}