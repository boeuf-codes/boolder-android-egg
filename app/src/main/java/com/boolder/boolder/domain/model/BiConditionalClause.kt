package com.boolder.boolder.domain.model

import com.boolder.boolder.view.detail.PointD
import kotlinx.parcelize.Parcelize
import android.os.Parcelable
import com.boolder.boolder.view.search.BaseObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Parcelize
data class BiConditionalClause (
    val literalOne: String,
    val literalTwo: String,
    val literalThree: String,
    val type: ClauseType,
) : BaseObject, Parcelable