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
    private val closeProblemsResult = mutableListOf<Pair<Problem, Problem>>()

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

        //Log.i("convertCNF, C2", converter.clausesC2.size.toString())
        //Log.i("convertCNF, C3", converter.clausesC3.size.toString())
        //Log.i("convertCNF, C5", converter.clausesC5.size.toString())
        //Log.i("convertCNF, C6", converter.clausesC6.size.toString())

        Log.i("convertCNF, DIMACS", dimacsString)
    }
    fun convertCNF(areaName: String, wuGrades: List<String>, pGrades: List<String>, cdGrades: List<String>, distance: Float,
                   n: Int, m: Int, p:Int) {
        viewModelScope.launch {
            // collect sets P1, P2 and P3
            val P1 = getProblemsByAreaAndGrade(areaName, wuGrades)
            val P2 = getProblemsByAreaAndGrade(areaName, pGrades)
            val P3 = getProblemsByAreaAndGrade(areaName, cdGrades)

            // calculate sets D1, D2 and D3
            val D1 = getClosePointsByDistance(P1, distance)
            val D2 = getClosePointsByDistance(P2, distance)
            val D3 = getClosePointsByDistance(P3, distance)

            // output input sets
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
            println()

            // convert to CNF
            convertCNF(P1, P2, P3, D1, D2, D3, n , m, p)
        }
    }

    // this should be able to take GradeRange instead of List<String> ?
    private suspend fun fetchProblemsByAreaAndGrade(areaName: String, grades: List<String>)  {
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

    private fun fetchClosePointsByDistance(problems: List<Problem>, distance: Float) {
            val closeProblems = GPSDistanceAlgorithm().pointsWithinDistance(problems, distance)

            closeProblemsResult.clear()
            closeProblemsResult.addAll(closeProblems)
    }

    private suspend fun getClosePointsByDistance(problems: List<Problem>, distance: Float): List<Pair<Problem, Problem>> {
        val job = viewModelScope.launch {
            fetchClosePointsByDistance(problems, distance)
        }
        job.join()

        return closeProblemsResult.toList()
    }


    fun fetchProblemByName(problemName: String?) {
        viewModelScope.launch {
            val pattern = problemName
                ?.takeIf { it.isNotBlank() }
                ?.let { "%${it.normalized()}%" }
                .orEmpty()
            val problems = problemRepository.problemsByName(pattern)
                .map { it.convert() }

            Log.i("fetchProblemByName", problems.toString())
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
