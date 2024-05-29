package com.boolder.boolder.view.map

import android.content.res.Resources
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boolder.boolder.R
import com.boolder.boolder.data.database.repository.AreaRepository
import com.boolder.boolder.data.database.repository.ProblemRepository
import com.boolder.boolder.domain.convert
import com.boolder.boolder.domain.model.ALL_GRADES
import com.boolder.boolder.domain.model.GradeRange
import com.boolder.boolder.domain.model.Topo
import com.boolder.boolder.domain.model.TopoOrigin
import com.boolder.boolder.domain.model.gradeRangeLevelDisplay
import com.boolder.boolder.domain.model.Problem
import com.boolder.boolder.utils.calculation.GPSDistanceAlgorithm
import com.boolder.boolder.utils.calculation.DIMACSConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.NumberFormatException
import java.text.Normalizer

class MapViewModel(
    private val areaRepository: AreaRepository,
    private val problemRepository: ProblemRepository,
    private val topoDataAggregator: TopoDataAggregator,
    private val resources: Resources
) : ViewModel() {

    private val _topoStateFlow = MutableStateFlow<Topo?>(null)
    val topoStateFlow = _topoStateFlow.asStateFlow()

    //do these want to be MutableLiveData like in search VM?
    private val gradeSearchResult = mutableListOf<Problem>()
    private val areaSearchResult  = mutableListOf<Problem>()

    private var currentGradeRange = GradeRange(
        min = ALL_GRADES.first(),
        max = ALL_GRADES.last(),
        isCustom = false
    )

    private val _gradeStateFlow = MutableStateFlow(
        GradeState(
            gradeRangeButtonTitle = resources.getString(R.string.grades),
            grades = ALL_GRADES
        )
    )
    val gradeStateFlow = _gradeStateFlow.asStateFlow()

    private val _areaStateFlow = MutableStateFlow<AreaState>(AreaState.Undefined)
    val areaStateFlow = _areaStateFlow.asStateFlow()

    fun fetchTopo(problemId: Int, origin: TopoOrigin) {
        viewModelScope.launch {
            _topoStateFlow.value = topoDataAggregator.aggregate(
                problemId = problemId,
                origin = origin
            )
        }
    }

    fun updateCircuitControlsForProblem(problemId: String) {
        val currentTopoState = _topoStateFlow.value ?: return

        val intProblemId = try {
            problemId.toInt()
        } catch (e: NumberFormatException) {
            return
        }

        viewModelScope.launch {
            val selectedProblem = currentTopoState.otherCompleteProblems
                .find { it.problemWithLine.problem.id == intProblemId }
                ?: return@launch

            val otherProblems = buildList {
                currentTopoState.selectedCompleteProblem?.let(::add)
                currentTopoState.otherCompleteProblems.forEach { completeProblem ->
                    if (completeProblem != selectedProblem) add(completeProblem)
                }
            }

            val circuitInfo = topoDataAggregator.updateCircuitControlsForProblem(intProblemId)

            _topoStateFlow.update {
                currentTopoState.copy(
                    selectedCompleteProblem = selectedProblem,
                    otherCompleteProblems = otherProblems,
                    circuitInfo = circuitInfo,
                    origin = TopoOrigin.TOPO
                )
            }
        }
    }

    fun withCurrentGradeRange(action: (GradeRange) -> Unit) {
        action(currentGradeRange)
    }

    fun onGradeRangeSelected(gradeRange: GradeRange) {
        currentGradeRange = gradeRange

        val grades = with(ALL_GRADES) {
            subList(
                fromIndex = indexOf(gradeRange.min),
                toIndex = indexOf(gradeRange.max) + 1
            )
        }

        val gradeRangeButtonTitle = if (gradeRange == GradeRange.LARGEST) {
            resources.getString(R.string.grades)
        } else {
            resources.gradeRangeLevelDisplay(gradeRange)
        }

        _gradeStateFlow.update {
            GradeState(
                gradeRangeButtonTitle = gradeRangeButtonTitle,
                grades = grades
            )
        }
    }

    fun onAreaVisited(areaId: Int) {
        viewModelScope.launch {
            val currentState = _areaStateFlow.value

            if (currentState is AreaState.Area && currentState.id == areaId) return@launch

            val area = areaRepository.getAreaById(areaId)

            _areaStateFlow.update { AreaState.Area(id = areaId, name = area.name) }
        }
    }

    fun onAreaLeft() {
        if (_areaStateFlow.value is AreaState.Undefined) return

        _areaStateFlow.update { AreaState.Undefined }
    }

    private fun convertCNF(P1: List<Problem>, P2: List<Problem>, P3: List<Problem>,
                                         D1: List<Pair<Problem,Problem>>, D2: List<Pair<Problem,Problem>>, D3: List<Pair<Problem,Problem>>,
                                         n: Int, m: Int, p:Int) {
        val converter = DIMACSConverter(P1, P2, P3, D1, D2, D3, n , m, p)
        val dimacsString = converter.convert()
        Log.i("convertCNF, DIMACS", dimacsString)

        val outputStr = "SAT\n" +
                "-1 -2 -3 4 -5 -6 -7 8 -9 -10 -11 -12 -13 -14 15 -16 -17 -18 19 -20 " +
                "-21 -22 23 -24 -25 -26 -27 -28 -29 -30 -31 32 -33 -34 -35 36 -37 -38 " +
                "-39 -40 41 -42 -43 -44 -45 -46 47 -48 -49 -50 51 -52 53 -54 55 -56 57 " +
                "-58 59 -60 61 -62 63 -64 65 -66 67 -68 69 -70 71 -72 73 74 -75 76 -77 78 " +
                "-79 80 -81 82 -83 84 -85 86 -87 88 -89 90 -91 92 -93 94 -95 96 -97 98 -99 " +
                "100 -101 102 -103 104 105 106 -107 108 -109 110 -111 112 -113 114 -115 116 -117" +
                " 118 -119 120 121 -122 -123 -124 -125 -126 -127 -128 -129 -130 -131 -132 -133 " +
                "-134 -135 -136 -137 -138 -139 -140 -141 -142 -143 -144 -145 -146 -147 -148 -149 " +
                "-150 -151 -152 -153 -154 -155 -156 -157 -158 -159 -160 -161 -162 -163 -164 -165 " +
                "-166 -167 -168 -169 -170 -171 -172 -173 -174 -175 -176 -177 -178 -179 -180 -181 " +
                "-182 -183 -184 -185 -186 -187 -188 -189 -190 -191 -192 -193 -194 -195 -196 -197 " +
                "-198 -199 -200 -201 -202 -203 -204 -205 -206 -207 -208 -209 -210 -211 -212 -213 " +
                "-214 -215 -216 -217 -218 -219 -220 -221 -222 -223 -224 -225 -226 -227 -228 -229 " +
                "-230 -231 -232 233 234 -235 -236 -237 -238 -239 -240 -241 -242 -243 -244 -245 -246" +
                " -247 -248 -249 -250 -251 -252 -253 -254 -255 -256 -257 -258 -259 -260 -261 -262" +
                " -263 -264 -265 -266 -267 -268 -269 -270 -271 -272 -273 -274 -275 -276 -277 -278 " +
                "-279 -280 -281 -282 -283 -284 -285 -286 -287 -288 -289 -290 -291 -292 -293 -294 -295" +
                " -296 -297 -298 -299 -300 -301 -302 -303 -304 0\n"
        converter.extractSATOutput(outputStr, outputFalseAssignments = false)
    }
    fun collectDataAndConvertCNF(areaName: String, wuGrades: List<String>,
                                 pGrades: List<String>, cdGrades: List<String>,
                                 distance: Float, n: Int, m: Int, p:Int) {
        viewModelScope.launch {
            // collect sets P1, P2 and P3
            val P1 = getProblemsByAreaAndGrade(areaName, wuGrades)
            val P2 = getProblemsByAreaAndGrade(areaName, pGrades)
            val P3 = getProblemsByAreaAndGrade(areaName, cdGrades)

            // calculate sets D1, D2 and D3
            val D1 = getClosePointsByDistance(P1, distance)
            val D2 = getClosePointsByDistance(P2, distance)
            val D3 = getClosePointsByDistance(P3, distance)

/*            // output input sets
            for (climb in P1) {
                Log.i("MVM, P1: ", climb.name!!)
            }
            println()
            for (climb in P2) {
                Log.i("MVM, P2: ", climb.name!!)
            }
            println()
            for (climb in P3) {
                Log.i("MVM, P3: ", climb.name!!)
            }
            println()
            for (climb in D1){
                Log.i("MVM, D1: ", climb.first.name + " | " + climb.second.name)
            }
            println()
            for (climb in D2){
                Log.i("MVM, D2: ", climb.first.name + " | " + climb.second.name)
            }
            println()
            for (climb in D3){
                Log.i("MVM, D3: ", climb.first.name + " | " + climb.second.name)
            }
            println()

            Log.i("MVM", "P1: ${P1.size} | P2: ${P2.size} | P3: ${P3.size} ")
            Log.i("MVM", "D1: ${D1.size} | D2: ${D2.size} | D3: ${D3.size} ")
            println()*/

            // convert to CNF
            convertCNF(P1, P2, P3, D1, D2, D3, n , m, p)
        }
    }

    // this should be able to take GradeRange instead of List<String> ?
    private suspend fun fetchProblemsByAreaAndGrade(areaName: String, grades: List<String>) {
        val problems = problemRepository.getProblemsByAreaAndGrade(areaName, grades)
            .map { it.convert() }

        gradeSearchResult.clear()
        gradeSearchResult.addAll(problems)
    }

    private suspend fun getProblemsByAreaAndGrade(areaName: String, grades: List<String>): List<Problem> {
        val job = viewModelScope.launch {
            fetchProblemsByAreaAndGrade(areaName, grades)
        }
        job.join()

        return gradeSearchResult.toList()
    }

    fun fetchAllProblemsByArea(areaName: String) {
        viewModelScope.launch {
            val problems = problemRepository.getAllProblemsByArea(areaName)
                .map { it.convert() }

            areaSearchResult.clear()
            areaSearchResult.addAll(problems)
        }
    }

    private fun getClosePointsByDistance(problems: List<Problem>, distance: Float): List<Pair<Problem, Problem>> {
        val closeProblems = GPSDistanceAlgorithm().pointsWithinDistance(problems, distance)

        return closeProblems.toList()
    }

    fun fetchProblemByName(problemName: String?) {
        viewModelScope.launch {
            val pattern = problemName
                ?.takeIf { it.isNotBlank() }
                ?.let { "%${it.normalized()}%" }
                .orEmpty()
            val problems = problemRepository.problemsByName(pattern)
                .map { it.convert() }
        }
    }

    private fun String.normalized() =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(REGEX_EXCLUDED_CHARS, "")
            .lowercase()
    companion object {
        private val REGEX_EXCLUDED_CHARS = Regex("[^0-9a-zA-Z]")
    }

    data class GradeState(
        val gradeRangeButtonTitle: String,
        val grades: List<String>
    )

    sealed interface AreaState {
        object Undefined : AreaState

        data class Area(
            val id: Int,
            val name: String
        ) : AreaState
    }
}
