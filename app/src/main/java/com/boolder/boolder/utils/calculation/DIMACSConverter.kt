package com.boolder.boolder.utils.calculation

import android.util.Log
import com.boolder.boolder.domain.model.BigClause
import com.boolder.boolder.domain.model.Problem
import com.boolder.boolder.domain.model.Clause
import com.boolder.boolder.domain.model.ClauseType

import java.util.LinkedList
import java.util.Queue
import com.boolder.boolder.domain.model.BiConditionalClause

internal class DIMACSConverter(P1: List<Problem>, P2: List<Problem>, P3: List<Problem>, D1: List<Pair<Problem,Problem>>, D2: List<Pair<Problem,Problem>>, D3: List<Pair<Problem,Problem>>, n: Int, m: Int, p:Int) {
    private val P1: List<Problem>
    private val P2: List<Problem>
    private val P3: List<Problem>

    private val D1: List<Pair<Problem,Problem>>
    private val D2: List<Pair<Problem,Problem>>
    private val D3: List<Pair<Problem,Problem>>

    private val n: Int
    private val m: Int
    private val p: Int

    private var tseitinDefs = mutableListOf<BiConditionalClause>()
    var tseitinCNFs = mutableListOf<Clause>()
        private set

    private var tseitinStartIndex = 1

    private var variables = mutableSetOf<String>()
    private var variableMapping = mutableMapOf<String, String>() //mutableSetOf<Pair<String, String>>()

    private var headerString = ""
    private var dimacsString = ""

    var clausesC2 = mutableListOf<Clause>()
        private set
    var clausesC5 = mutableListOf<Clause>()
        private set
    var clausesC3 = mutableListOf<BigClause>()
        private set
    var clausesC6 = mutableListOf<Clause>()
        private set

    init {
        this.P1 = P1
        this.P2 = P2
        this.P3 = P3
        this.D1 = D1
        this.D2 = D2
        this.D3 = D3

        this.n = n
        this.m = m
        this.p = p

        // check shit (i.e. empty sets)

    }

    fun convert(): String {
        // convert each constraint into clauses
        // note: C1=C3, C5 also covers C4
        convertC2()
        convertC3()
        convertC5() // checking...
        convertC6()

        // generate header and clause strings
        makeFileHeader()
        makeFileContents()

        // make + write to file

        // return file

        return headerString + dimacsString
    }

    fun extractSATOutput(output: String, outputFalseAssignments: Boolean){
        val outputLines = output.lines()
        val satisfiable = outputLines[0]

        val trueAssignments = mutableListOf<String>()
        val falseAssignments = mutableListOf<String>()

        // output whether SAT/UNSAT
        Log.i("SAT output: ", satisfiable)

        // if 'SAT', output assignments
        if (satisfiable == "SAT") {
            val assignments = outputLines[1].split("\\s+".toRegex())
            for (assignment in assignments) {
                Log.i("SAT output", assignment)

                if (assignment == "0") { // always skip the 0 from the end
                    break
                } else if (assignment.first() != '-') {
                    val key = variableMapping.entries.find { it.value == assignment }?.key
                    trueAssignments.add(key!!)
                } else {
                    val key = variableMapping.entries.find { it.value == assignment.drop(1) }?.key
                    falseAssignments.add(key!!)
                }

            }
            Log.i("SAT true: ", trueAssignments.toString())
            if (outputFalseAssignments) {
                Log.i("SAT false: ", falseAssignments.toString())
            }
        }

    }

    private fun makeFileHeader() {
        val varCount = variables.size
        val clauseCount = clausesC2.size + clausesC3.size + clausesC5.size + clausesC6.size

        headerString = "p cnf $varCount $clauseCount${System.lineSeparator()}"
    }

    private fun makeFileContents() {
        // add all C2 clauses to file
        for (clause in clausesC2) {
            dimacsString += clause.toDIMACSString()
        }
        // add all C3 clauses to file
        for (clause in clausesC3) {
            dimacsString += clause.toDIMACSString()
        }
        // add all C5 clauses to file
        for (clause in clausesC5) {
            dimacsString += clause.toDIMACSString()

        }
        // add all C6 clauses to file
        for (i in clausesC6.indices) {
            dimacsString += if (i < clausesC6.size-1) {
                clausesC6[i].toDIMACSString()
            } else {
                // last string in the DIMACS file; don't use a CRLF terminator
                clausesC6[i].toTerminalDIMACSString()
            }
        }
    }

    private fun addVarAndGetAlias(variable: String): String{
        // if variable not stored, create an alias using the size of the set (so that variables start from 1)
        if (variable !in variables) {
            variables.add(variable)
            //val mapping = Pair(variable, variables.size.toString())
            variableMapping[variable] = variables.size.toString()
        }
        // return variable's alias
        return variableMapping[variable]!!
    }

    private fun convertC2() {
        clausesC2.addAll(convertC2Helper(P1, 1, n))
        clausesC2.addAll(convertC2Helper(P2, n+1, n+m))
        clausesC2.addAll(convertC2Helper(P3, n+m+1, n+m+p))
    }

    private fun convertC2Helper(gradeRange: List<Problem>, lower: Int, upper: Int): List<Clause> {
        var clauses = mutableListOf<Clause>()
        for (i in lower..upper) {
            for (j in gradeRange) {
                for (k in gradeRange) {
                    if (k != j){
                        // DIMACS doesn't count negated variables, so store non-negated
                        val alias1 = addVarAndGetAlias("$i${j.id}")
                        val alias2 = addVarAndGetAlias("$i${k.id}")

                        clauses.add(Clause("-$alias1", "-$alias2", null, ClauseType.DISJUNCTION))
/*                        clauses.add(Clause("-$i${j.id}", "-$i${k.id}", null, ClauseType.DISJUNCTION))

                        variables.add("$i${j.id}")
                        variables.add("$i${k.id}")*/
                    }
                }
            }
        }

        return clauses
    }

    private fun convertC3() {
        clausesC3.addAll(convertC3Helper(P1, 1, n))
        clausesC3.addAll(convertC3Helper(P2, n+1, n+m))
        clausesC3.addAll(convertC3Helper(P3, n+m+1, n+m+p))
    }

    private fun convertC3Helper(gradeRange: List<Problem>, lower: Int, upper: Int): MutableList<BigClause> {
        var clauses = mutableListOf<BigClause>()
        var clause = mutableListOf<String>()

        for (i in lower..upper){
            for (j in gradeRange) {
                val alias = addVarAndGetAlias("$i${j.id}")
                clause.add(alias)
/*                clause.add("$i${j.id}")
                variables.add("$i${j.id}")*/
            }
            clauses.add(BigClause(clause.toList(), ClauseType.DISJUNCTION))
            clause.clear()
        }
        return clauses
    }

    private fun convertC5(){
        val clausesD1 = convertC5Helper(D1, 1, n)
        val clausesD2 = convertC5Helper(D2, n+1, n+m)
        val clausesD3 = convertC5Helper(D3, n+m+1, n+m+p)

        tseitin(clausesD1, clausesD2, clausesD3)
        clausesC5.addAll(tseitinCNFs)
        /*val tseitin = TseitinTransformer(clausesD1, clausesD2, clausesD3, n, m, p).cnfClauses
        clausesC5.addAll(tseitin)*/
    }

    private fun convertC5Helper(set: List<Pair<Problem,Problem>>, lower: Int, upper: Int): Queue<Clause> {
        val clauses: Queue<Clause> = LinkedList()
        for (i in lower..upper-1) {
            for (pair in set) {
/*                clauses.add(Clause("$i${pair.first.id}", "${i+1}${pair.second.id}", null, ClauseType.CONJUNCTION))
                variables.add("$i${pair.first.id}")
                variables.add("${i+1}${pair.second.id}")*/
                val alias1 = addVarAndGetAlias("$i${pair.first.id}")
                val alias2 = addVarAndGetAlias("${i+1}${pair.second.id}")
                clauses.add(Clause(alias1, alias2, null, ClauseType.CONJUNCTION))
            }
        }
        return clauses
    }

    private fun tseitin(clausesD1: Queue<Clause>, clausesD2: Queue<Clause>, clausesD3: Queue<Clause>){
        // encode the clause sets into tseitin definitions
        tseitinEncode(clausesD1, tseitinStartIndex)
        tseitinEncode(clausesD2, tseitinStartIndex)
        tseitinEncode(clausesD3, tseitinStartIndex)

        // translate the definitions into CNF
        tseitinTranslate(tseitinDefs)
    }

/*    private fun tseitinEncodeSet(clauses: Queue<Clause>, num: Int) {
        if(clauses.size == num) { // check whether set is same as # of climbs, and if so just append variables to cnfClauses
            for (clause in clauses){
                tseitinCNFs.add(Clause(clause.literalOne, null, null, ClauseType.DISJUNCTION))
                tseitinCNFs.add(Clause(clause.literalTwo!!, null, null, ClauseType.DISJUNCTION))
            }
        }
        else { // otherwise encode as normal, starting at 1
            tseitinEncode(clauses, tseitinStartIndex)
        }
    }*/

    private fun tseitinEncode(clauses: Queue<Clause>, index: Int) {
        if (clauses.size > 2) { // if more than 2 clauses, recursively build the definitions
            val currentClause = clauses.remove()

            val alias1 = addVarAndGetAlias("f${index}")
            val alias2 = addVarAndGetAlias("f${index + 1}")
            val alias3 = addVarAndGetAlias("f${index + 2}")

/*            tseitinDefs.add(
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
            )*/
            tseitinDefs.add(
                BiConditionalClause(
                    alias2,
                    currentClause.literalOne,
                    currentClause.literalTwo!!,
                    ClauseType.CONJUNCTION
                )
            )
            tseitinDefs.add(
                BiConditionalClause(
                    alias1,
                    alias2,
                    alias3,
                    ClauseType.DISJUNCTION
                )
            )
            tseitinEncode(clauses, index+2)
        } else { // when two clauses left, build the last definitions manually
            val currentClause = clauses.remove()
            val nextClause = clauses.remove()

            val alias1 = addVarAndGetAlias("f${index}")
            val alias2 = addVarAndGetAlias("f${index + 1}")
            val alias3 = addVarAndGetAlias("f${index + 2}")
            val startAlias = addVarAndGetAlias("f${tseitinStartIndex}")

            tseitinDefs.add(
                BiConditionalClause(
                    alias2,
                    currentClause.literalOne,
                    currentClause.literalTwo!!,
                    ClauseType.CONJUNCTION
                )
            )
            tseitinDefs.add(
                BiConditionalClause(
                    alias3,
                    nextClause.literalOne,
                    nextClause.literalTwo!!,
                    ClauseType.CONJUNCTION
                )
            )
            tseitinDefs.add(
                BiConditionalClause(
                    alias1,
                    alias2,
                    alias3,
                    ClauseType.DISJUNCTION
                )
            )

/*            tseitinDefs.add(
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
            )*/

            // the first definition is the original clause, which is always true
            tseitinCNFs.add(Clause(startAlias, null, null, null))

            // update the starting index for the next clause set
            Log.i("tseitin | idx: ", index.toString())
            tseitinStartIndex = index + 3
        }
    }

    private fun tseitinTranslate(definitions: MutableList<BiConditionalClause>) {
        for (clause in definitions) {
            when (clause.type) {
                ClauseType.DISJUNCTION -> {
                    tseitinCNFs.add(
                        Clause(
                            "-${clause.literalOne}",
                            clause.literalTwo,
                            clause.literalThree,
                            ClauseType.DISJUNCTION
                        )
                    )
                    tseitinCNFs.add(
                        Clause(
                            clause.literalOne,
                            "-${clause.literalTwo}",
                            null,
                            ClauseType.DISJUNCTION
                        )
                    )
                    tseitinCNFs.add(
                        Clause(
                            clause.literalOne,
                            "-${clause.literalThree}",
                            null,
                            ClauseType.DISJUNCTION
                        )
                    )
                }
                ClauseType.CONJUNCTION -> {
                    tseitinCNFs.add(
                        Clause(
                            clause.literalOne,
                            "-${clause.literalTwo}",
                            "-${clause.literalThree}",
                            ClauseType.DISJUNCTION
                        )
                    )
                    tseitinCNFs.add(
                        Clause(
                            "-${clause.literalOne}",
                            clause.literalTwo,
                            null,
                            ClauseType.DISJUNCTION
                        )
                    )
                    tseitinCNFs.add(
                        Clause(
                            "-${clause.literalOne}",
                            clause.literalThree,
                            null,
                            ClauseType.DISJUNCTION
                        )
                    )
                }
                else -> {
                    tseitinCNFs.add(
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

    private fun convertC6(){
/*        // combine the sets
        var P = mutableListOf<Problem>()
        P.addAll(P1)
        P.addAll(P2)
        P.addAll(P3)

        clausesC6.addAll(convertC6Helper(P, 1, n+m+p))*/
        clausesC6.addAll(convertC6Helper(P1, 1, n))
        clausesC6.addAll(convertC6Helper(P2, n+1, n+m))
        clausesC6.addAll(convertC6Helper(P3, n+m+1, n+m+p))
    }

    private fun convertC6Helper(gradeRange: List<Problem>, lower: Int, upper: Int): MutableList<Clause> {
        var clauses = mutableListOf<Clause>()
        for (i in gradeRange) {
            for (j in lower..upper) {
                for (k in lower..upper) {
                    if (k != j){
                        // DIMACS doesn't count negated variables, so store non-negated
                        val alias1 = addVarAndGetAlias("$j${i.id}")
                        val alias2 = addVarAndGetAlias("$k${i.id}")
                        clauses.add(Clause("-$alias1", "-$alias2", null, ClauseType.DISJUNCTION))
/*                        clauses.add(Clause("-$j${i.id}", "-$k${i.id}", null, ClauseType.DISJUNCTION))
                        variables.add("$j${i.id}")
                        variables.add("$k${i.id}")*/
                    }
                }
            }
        }

        return clauses
    }
}