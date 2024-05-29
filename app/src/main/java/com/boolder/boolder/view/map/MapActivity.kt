package com.boolder.boolder.view.map

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import com.boolder.boolder.R
import com.boolder.boolder.databinding.ActivityMainBinding
import com.boolder.boolder.domain.model.Area
import com.boolder.boolder.domain.model.GradeRange
import com.boolder.boolder.domain.model.Problem
import com.boolder.boolder.domain.model.Topo
import com.boolder.boolder.domain.model.TopoOrigin
import com.boolder.boolder.utils.LocationCallback
import com.boolder.boolder.utils.LocationProvider
import com.boolder.boolder.utils.MapboxStyleFactory
import com.boolder.boolder.utils.extension.launchAndCollectIn
import com.boolder.boolder.utils.viewBinding
import com.boolder.boolder.view.map.BoolderMap.BoolderMapListener
import com.boolder.boolder.view.map.animator.animationEndListener
import com.boolder.boolder.view.map.composable.AreaName
import com.boolder.boolder.view.map.filter.GradesFilterBottomSheetDialogFragment
import com.boolder.boolder.view.map.filter.GradesFilterBottomSheetDialogFragment.Companion.RESULT_GRADE_RANGE
import com.boolder.boolder.view.search.SearchActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment.STYLE_NORMAL
import com.mapbox.geojson.Geometry
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.lang.Double.max


class MapActivity : AppCompatActivity(), LocationCallback, BoolderMapListener {

    private val binding by viewBinding(ActivityMainBinding::inflate)

    private val mapViewModel by viewModel<MapViewModel>()
    private val layerFactory by inject<MapboxStyleFactory>()

    private lateinit var locationProvider: LocationProvider

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val topMargin = systemInsets.top + resources.getDimensionPixelSize(R.dimen.margin_search_component)
            val bottomMargin = systemInsets.bottom + resources.getDimensionPixelSize(R.dimen.margin_map_controls)

            binding.searchComponent
                .searchContainer
                .updateLayoutParams<MarginLayoutParams> { updateMargins(top = topMargin) }

            binding.mapView.applyCompassTopInset(systemInsets.top.toFloat())

            binding.gradesFilterButton
                .updateLayoutParams<MarginLayoutParams> { updateMargins(bottom = bottomMargin) }

            binding.fabLocation
                .updateLayoutParams<MarginLayoutParams> { updateMargins(bottom = bottomMargin) }

            binding.fabPlanRoute
                .updateLayoutParams<MarginLayoutParams> { updateMargins(bottom = bottomMargin) }

            WindowInsetsCompat.CONSUMED
        }

        locationProvider = LocationProvider(this, this)

        bottomSheetBehavior = BottomSheetBehavior.from(binding.detailBottomSheet)
            .also { it.state = STATE_HIDDEN }

        setupMap()

        binding.fabPlanRoute.setOnClickListener {
            Log.i("fabPlanRoute", "Hello world! :)")
/*            // testing Tseitin
            var clause1 = Clause("A", "B", null, ClauseType.CONJUNCTION)
            var clause2 = Clause("C", "D", null, ClauseType.CONJUNCTION)
            var clause3 = Clause("E", "F", null, ClauseType.CONJUNCTION)

            //var init_clauses = mutableListOf<Clause>(clause1, clause2, clause3)
            val clause_queue: Queue<Clause> = LinkedList()
            clause_queue.add(clause1)
            clause_queue.add(clause2)
            clause_queue.add(clause3)

            val tseitin = TseitinTransform(clause_queue)*/

            // testing DIMACS conversion
            val areaName = "rocherdugeneral"
            val wu_cd_grades = listOf("3b","3b+","3c","3c+")
            val p_grades = listOf("5c", "5c+")
            val distance: Float = 75F //this is in METERS
            val m = 2
            val n = 5
            val p = 2

            mapViewModel.collectDataAndConvertCNF(areaName, wu_cd_grades, p_grades, wu_cd_grades, distance, m, n, p)
        }

        binding.fabLocation.setOnClickListener {
            locationProvider.askForPosition()
        }

        val searchRegister = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val resultData = result.data ?: return@registerForActivityResult

            when {
                resultData.hasExtra("AREA") -> flyToArea(
                    requireNotNull(resultData.getParcelableExtra("AREA"))
                )

                resultData.hasExtra("PROBLEM") -> onProblemSelected(
                    problemId = requireNotNull(resultData.getParcelableExtra<Problem>("PROBLEM")).id,
                    origin = TopoOrigin.SEARCH
                )
            }
        }

        binding.searchComponent.searchBar.isFocusable = false
        binding.searchComponent.searchBar.isClickable = false
        binding.searchComponent.searchBar.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            val option = ActivityOptionsCompat.makeSceneTransitionAnimation(this)
            searchRegister.launch(intent, option)
        }

        binding.gradesFilterButton.setOnClickListener {
            mapViewModel.withCurrentGradeRange {
                GradesFilterBottomSheetDialogFragment.newInstance(it)
                    .apply { setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme) }
                    .show(supportFragmentManager, GradesFilterBottomSheetDialogFragment.TAG)
            }
        }

        binding.topoView.apply {
            onSelectProblemOnMap = { problemId ->
                binding.mapView.selectProblem(problemId)
                mapViewModel.updateCircuitControlsForProblem(problemId)
            }
            onCircuitProblemSelected = {
                mapViewModel.fetchTopo(problemId = it, origin = TopoOrigin.CIRCUIT)
            }
        }

        mapViewModel.topoStateFlow.launchAndCollectIn(owner = this, collector = ::onNewTopo)

        mapViewModel.gradeStateFlow.launchAndCollectIn(owner = this) {
            binding.mapView.filterGrades(it.grades)
            binding.gradesFilterButton.text = it.gradeRangeButtonTitle
        }

        mapViewModel.areaStateFlow.launchAndCollectIn(owner = this) {
            binding.areaNameComposeView.setContent {
                AreaName(
                    name = (it as? MapViewModel.AreaState.Area)?.name,
                    onHideAreaName = ::onAreaLeft
                )
            }
        }

        supportFragmentManager.setFragmentResultListener(
            /* requestKey = */ GradesFilterBottomSheetDialogFragment.REQUEST_KEY,
            /* lifecycleOwner = */ this
        ) { _, bundle ->
            val gradeRange = requireNotNull(bundle.getParcelable<GradeRange>(RESULT_GRADE_RANGE))

            mapViewModel.onGradeRangeSelected(gradeRange)
        }
    }

    override fun onDestroy() {
        binding.topoView.apply {
            onSelectProblemOnMap = null
            onCircuitProblemSelected = null
        }
        super.onDestroy()
    }

    override fun onGPSLocation(location: Location) {
        val point = Point.fromLngLat(location.longitude, location.latitude)
        val zoomLevel = max(binding.mapView.getMapboxMap().cameraState.zoom, 17.0)

        binding.mapView.getMapboxMap()
            .setCamera(CameraOptions.Builder().center(point).zoom(zoomLevel).bearing(location.bearing.toDouble()).build())
        binding.mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
    }

    private fun setupMap() {
        binding.mapView.setup(this, layerFactory.buildStyle())
    }

    // Triggered when user click on a Problem on Map
    override fun onProblemSelected(problemId: Int, origin: TopoOrigin) {
        mapViewModel.fetchTopo(problemId = problemId, origin = origin)
    }

    override fun onProblemUnselected() {
        bottomSheetBehavior.state = STATE_HIDDEN
    }

    override fun onPoisSelected(poisName: String, stringProperty: String, geometry: Geometry?) {

        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_pois, binding.root, false)
        val bottomSheet = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)

        view.apply {
            findViewById<TextView>(R.id.pois_title).text = poisName
            findViewById<Button>(R.id.open).setOnClickListener {
                openGoogleMaps(stringProperty)
            }
            findViewById<Button>(R.id.close).setOnClickListener { bottomSheet.dismiss() }
        }
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    override fun onAreaVisited(areaId: Int) {
        mapViewModel.onAreaVisited(areaId)
    }

    override fun onAreaLeft() {
        mapViewModel.onAreaLeft()
    }

    private fun onNewTopo(nullableTopo: Topo?) {
        nullableTopo?.let { topo ->
            binding.topoView.setTopo(topo)

            val selectedProblem = topo.selectedCompleteProblem?.problemWithLine?.problem

            binding.mapView.updateCircuit(selectedProblem?.circuitId?.toLong())
            selectedProblem?.let { flyToProblem(problem = it, origin = topo.origin) }
        }
        bottomSheetBehavior.state = if (nullableTopo == null) STATE_HIDDEN else STATE_EXPANDED
    }

    private fun openGoogleMaps(url: String) {

        val sendIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val shareIntent = Intent.createChooser(sendIntent, null)
        try {
            startActivity(shareIntent)
        } catch (e: Exception) {
            Log.i("MAP", "No apps can handle this kind of intent")
        }
    }

    private fun flyToArea(area: Area) {
        val southWest = Point.fromLngLat(
            area.southWestLon.toDouble(),
            area.southWestLat.toDouble()
        )
        val northEst = Point.fromLngLat(
            area.northEastLon.toDouble(),
            area.northEastLat.toDouble()
        )
        val coordinates = CoordinateBounds(southWest, northEst)

        val cameraOptions = binding.mapView.getMapboxMap().cameraForCoordinateBounds(
            coordinates,
            EdgeInsets(60.0, 8.0, 8.0, 8.0),
            0.0,
            0.0
        )

        binding.mapView.camera.flyTo(
            cameraOptions = cameraOptions,
            animationOptions = defaultMapAnimationOptions {
                animatorListener(animationEndListener { onAreaVisited(area.id) })
            }
        )

        bottomSheetBehavior.state = STATE_HIDDEN
    }

    private fun flyToProblem(problem: Problem, origin: TopoOrigin) {
        binding.mapView.selectProblem(problem.id.toString())

        val point = Point.fromLngLat(
            problem.longitude.toDouble(),
            problem.latitude.toDouble()
        )

        val zoomLevel = binding.mapView.getMapboxMap().cameraState.zoom

        val cameraOptions = CameraOptions.Builder().run {
            if (origin in arrayOf(TopoOrigin.SEARCH, TopoOrigin.CIRCUIT)) center(point)

            padding(EdgeInsets(40.0, 0.0, (binding.mapView.height / 2).toDouble(), 0.0))
            zoom(if (zoomLevel <= 19.0) 20.0 else zoomLevel)
            build()
        }

        binding.mapView.camera.easeTo(
            cameraOptions = cameraOptions,
            animationOptions = defaultMapAnimationOptions {
                animatorListener(animationEndListener { onAreaVisited(problem.areaId) })
            }
        )
    }

    private fun defaultMapAnimationOptions(block: MapAnimationOptions.Builder.() -> Unit) =
        MapAnimationOptions.mapAnimationOptions {
            duration(300L)
            interpolator(AccelerateDecelerateInterpolator())
            block()
        }
}
