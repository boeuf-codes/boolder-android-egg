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
    private val areaSearchResult = mutableListOf<Problem>()

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

    //this should be able to take GradeRange instead of List<String>
    fun fetchProblemsByAreaAndGrade(areaName: String, grades: List<String>) {
        viewModelScope.launch {
            val problems = problemRepository.getProblemsByAreaAndGrade(areaName, grades)
                .map { it.convert() }

            gradeSearchResult.clear()
            gradeSearchResult.addAll(problems)

            Log.i("problemsByAreaAndGrade", problems.count().toString())
        }
    }

    fun fetchAllProblemsByArea(areaName: String) {
        viewModelScope.launch {
            val problems = problemRepository.getAllProblemsByArea(areaName)
                .map { it.convert() }

            areaSearchResult.clear()
            areaSearchResult.addAll(problems)

           /* for (problem in problems) {
                problem.name?.let { Log.i("ProblemsByArea", it) }
            }*/
            Log.i("ProblemsByArea", problems.count().toString())
        }
    }

    fun fetchClosePointsByDistance(distance: Float): List<Pair<Problem, Problem>> {
        val closeProblems = GPSDistanceAlgorithm().pointsWithinDistance(areaSearchResult, distance)

        Log.i("closeProblems", closeProblems.count().toString())

        return closeProblems
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
