package com.boolder.boolder.domain.model

import android.util.Log

class BigClause (
    val literals: List<String>,
    val type: ClauseType?,
) {
    fun toDIMACSString(): String {
        var output = ""
        for (i in literals.indices) {
            output += if (i == literals.size-1) {
                "${literals[i]} 0${System.lineSeparator()}"
            } else {
                "${literals[i]} "
            }
        }
        return output
    }
}
