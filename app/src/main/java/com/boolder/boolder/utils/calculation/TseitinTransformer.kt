package com.boolder.boolder.utils.calculation

import com.boolder.boolder.domain.model.BiConditionalClause
import com.boolder.boolder.domain.model.Clause
import android.util.Log
import com.boolder.boolder.domain.model.ClauseType
import java.util.Queue

class TseitinTransformer(clausesD1: Queue<Clause>, clausesD2: Queue<Clause>, clausesD3: Queue<Clause>, n: Int, m: Int, p:Int) {
    private var tseitinDefs = mutableListOf<BiConditionalClause>()
    var cnfClauses = mutableListOf<Clause>()
        private set

    private var startIndex = 1

    init {
        // the first definition is (f1 = the original clause), which is always true
        cnfClauses.add(Clause("f1", null, null, null))

        // encode the clause sets into tseitin definitions
        encodeSet(clausesD1, n)
        encodeSet(clausesD2, m)
        encodeSet(clausesD3, p)

        // translate the definitions into CNF
        tseitinTranslate(tseitinDefs)
    }

    private fun encodeSet(clauses: Queue<Clause>, num: Int) {
        if(clauses.size == num) { // check whether set is same as # of climbs, and if so just append variables to cnfClauses
            for (clause in clauses){
                cnfClauses.add(Clause(clause.literalOne, null, null, ClauseType.DISJUNCTION))
                cnfClauses.add(Clause(clause.literalTwo!!, null, null, ClauseType.DISJUNCTION))
            }
        }
        else { // otherwise encode as normal, starting at 1
            tseitinEncode(clauses, startIndex)
        }
    }

    private fun tseitinEncode(clauses: Queue<Clause>, index: Int) {
        if (clauses.size > 2) { // if more than 2 clauses, recursively build the definitions
            val currentClause = clauses.remove()
            tseitinDefs.add(
                BiConditionalClause(
                    "f${index + 1}",
                    currentClause.literalOne,
                    currentClause.literalTwo!!,
                    ClauseType.CONJUNCTION
                )
            )
            tseitinDefs.add(
                BiConditionalClause(
                    "f${index}",
                    "f${index + 1}",
                    "f${index + 2}",
                    ClauseType.DISJUNCTION
                )
            )
            tseitinEncode(clauses, index+2)
        } else { // when two clauses left, build the last definitions manually
            val currentClause = clauses.remove()
            val nextClause = clauses.remove()
            tseitinDefs.add(
                BiConditionalClause(
                    "f${index + 1}",
                    currentClause.literalOne,
                    currentClause.literalTwo!!,
                    ClauseType.CONJUNCTION
                )
            )
            tseitinDefs.add(
                BiConditionalClause(
                    "f${index + 2}",
                    nextClause.literalOne,
                    nextClause.literalTwo!!,
                    ClauseType.CONJUNCTION
                )
            )
            tseitinDefs.add(
                BiConditionalClause(
                    "f${index}",
                    "f${index + 1}",
                    "f${index + 2}",
                    ClauseType.DISJUNCTION
                )
            )
            // update the starting index for the next clause set
            startIndex = index + 3
        }
    }

    private fun tseitinTranslate(definitions: MutableList<BiConditionalClause>) {
        for (clause in definitions) {
            when (clause.type) {
                ClauseType.DISJUNCTION -> {
                    cnfClauses.add(
                        Clause(
                            "-${clause.literalOne}",
                            clause.literalTwo,
                            clause.literalThree,
                            ClauseType.DISJUNCTION
                        )
                    )
                    cnfClauses.add(
                        Clause(
                            clause.literalOne,
                            "-${clause.literalTwo}",
                            null,
                            ClauseType.DISJUNCTION
                        )
                    )
                    cnfClauses.add(
                        Clause(
                            clause.literalOne,
                            "-${clause.literalThree}",
                            null,
                            ClauseType.DISJUNCTION
                        )
                    )
                }
                ClauseType.CONJUNCTION -> {
                    cnfClauses.add(
                        Clause(
                            clause.literalOne,
                            "-${clause.literalTwo}",
                            "-${clause.literalThree}",
                            ClauseType.DISJUNCTION
                        )
                    )
                    cnfClauses.add(
                        Clause(
                            "-${clause.literalOne}",
                            clause.literalTwo,
                            null,
                            ClauseType.DISJUNCTION
                        )
                    )
                    cnfClauses.add(
                        Clause(
                            "-${clause.literalOne}",
                            clause.literalThree,
                            null,
                            ClauseType.DISJUNCTION
                        )
                    )
                }
                else -> {
                    cnfClauses.add(
                        Clause(
                            clause.literalOne,
                            clause.literalTwo,
                            clause.literalThree,
                            clause.type
                        )
                    )
                }
            }
        }
    }

}