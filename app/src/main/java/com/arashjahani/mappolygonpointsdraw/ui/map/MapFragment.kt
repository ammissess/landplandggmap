package com.arashjahani.mappolygonpointsdraw.ui.map

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.coroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.arashjahani.mappolygonpointsdraw.R
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonWithPoints
import com.arashjahani.mappolygonpointsdraw.databinding.FragmentMapBinding
import com.arashjahani.mappolygonpointsdraw.utils.areaFormat
import com.arashjahani.mappolygonpointsdraw.utils.calcPolygonArea
import com.arashjahani.mappolygonpointsdraw.utils.centerOfPolygon
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.image.image
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.collections.ArrayList

@AndroidEntryPoint
class MapFragment : Fragment(), PolygonsItemClickListener {

    companion object {
        fun newInstance() = MapFragment()
    }

    private val mMapViewModel: MapViewModel by viewModels()

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var mMapView: MapView? = null
    private var mPointsList = ArrayList<ArrayList<Point>>()

    private var mAnnotationApi: AnnotationPlugin? = null
    private var mPolygonAnnotationManager: PolygonAnnotationManager? = null

    private var mSavedPolygonsBottomSheetDialog: BottomSheetDialog? = null
    private var mSavedPolygonsAdapter: SavedPolygonsAdapter? = null

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var allowedDistrict: String? = null
    private var allowedProvince: String? = null
    private var allowedCountry: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareViews()
        initObservers()
        initListeners()
        fetchUserPermissions()
    }

    private fun prepareViews() {
        mMapView = binding.mapView
        mMapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS)
        mAnnotationApi = mMapView?.annotations
        mPolygonAnnotationManager = mAnnotationApi?.createPolygonAnnotationManager(mMapView!!)
        mPointsList.add(ArrayList())
        loadSavedPolygonsList()
    }

    private fun initObservers() {
        lifecycle.coroutineScope.launch {
            mMapViewModel.getAllPolygons().collect {
                mSavedPolygonsAdapter?.renewItems(it)
            }
        }
    }

    private fun initListeners() {
        binding.fabLocation.setOnClickListener {
            getUserLocation()
        }

        binding.btnAddPoint.setOnClickListener {
            mMapView?.getMapboxMap()?.cameraState?.center?.let { point ->
                if (isPointInAllowedDistrict(point)) {
                    mPointsList.first().add(point)
                    drawPolygon(mPointsList)
                } else {
                    Toast.makeText(requireContext(), "You are not allowed to draw in this district", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnSavePolygon.setOnClickListener {
            if (firebaseAuth.currentUser == null) {
                Toast.makeText(requireContext(), "Please log in to save polygons", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            savePolygon()
        }

        binding.btnDrawPolygon.setOnClickListener {
            if (firebaseAuth.currentUser == null) {
                Toast.makeText(requireContext(), "Please log in to draw polygons", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.layoutAddPolygonNavigator.visibility = View.VISIBLE
            binding.imgCursor.visibility = View.VISIBLE
            binding.layoutMainNavigator.visibility = View.GONE
        }

        binding.btnCloseDrawing.setOnClickListener {
            binding.layoutAddPolygonNavigator.visibility = View.GONE
            binding.imgCursor.visibility = View.GONE
            binding.lblArea.visibility = View.GONE
            binding.layoutMainNavigator.visibility = View.VISIBLE
            clearMapView()
        }

        binding.btnList.setOnClickListener {
            if (firebaseAuth.currentUser == null) {
                Toast.makeText(requireContext(), "Please log in to view saved polygons", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            mSavedPolygonsBottomSheetDialog?.show()
        }
    }

    private fun fetchUserPermissions() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        allowedDistrict = document.getString("district")
                        allowedProvince = document.getString("province")
                        allowedCountry = document.getString("country")
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to fetch user permissions", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun isPointInAllowedDistrict(point: Point): Boolean {
        // Logic kiểm tra xem điểm có thuộc district được cấp phép hay không
        // Hiện tại giả định luôn đúng
        return true
    }

    private fun savePolygon() {
        // Hiển thị dialog thu thập thông tin polygon
        // Lưu polygon vào Firestore
    }

    private fun drawPolygon(points: List<List<Point>>) {
        mPolygonAnnotationManager?.deleteAll()
        val polygonAnnotationOptions = PolygonAnnotationOptions()
            .withPoints(points)
            .withFillColor("#ee4e8b")
            .withFillOpacity(0.4)
        mPolygonAnnotationManager?.create(polygonAnnotationOptions)

        points.first().let {
            if (it.size > 2) {
                drawPolygonCenterMarker(it.centerOfPolygon())
                binding.lblArea.visibility = View.VISIBLE
                binding.lblArea.text = "Area: ${it.calcPolygonArea().areaFormat()} m²"
            }
        }
    }

    private fun drawPolygonCenterMarker(point: Point) {
        mMapView?.getMapboxMap()?.loadStyle(
            styleExtension = style(Style.MAPBOX_STREETS) {
                +image("ic_flag") {
                    bitmap(BitmapFactory.decodeResource(resources, R.drawable.ic_flag))
                }
                +geoJsonSource("flag_source_id") {
                    geometry(point)
                }
                +symbolLayer("flag_layer_id", "flag_source_id") {
                    iconImage("ic_flag")
                    iconAnchor(IconAnchor.BOTTOM)
                }
            }
        )
    }

    private fun loadSavedPolygonsList() {
        if (mSavedPolygonsBottomSheetDialog == null) {
            mSavedPolygonsBottomSheetDialog = BottomSheetDialog(requireContext())
            mSavedPolygonsBottomSheetDialog?.setContentView(R.layout.dialog_saved_polygons_list)
            val rcvSavedPolygons = mSavedPolygonsBottomSheetDialog?.findViewById<RecyclerView>(R.id.rcvSavedPolygons)
            mSavedPolygonsAdapter = SavedPolygonsAdapter(ArrayList())
            mSavedPolygonsAdapter?.setListener(this)
            rcvSavedPolygons?.adapter = mSavedPolygonsAdapter
        }
    }

    private fun clearMapView() {
        mMapView?.getMapboxMap()?.getStyle()?.removeStyleLayer("flag_layer_id")
        mMapView?.getMapboxMap()?.getStyle()?.removeStyleLayer("position_layer_id")
        mPolygonAnnotationManager?.deleteAll()
        mPointsList.first().clear()
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    moveToPosition(Point.fromLngLat(longitude, latitude), true)
                } else {
                    Toast.makeText(requireContext(), "Location data not available", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun moveToPosition(location: Point, showPositionMarker: Boolean = false) {
        val cameraPosition = CameraOptions.Builder()
            .zoom(13.0)
            .center(location)
            .build()
        mMapView?.getMapboxMap()?.setCamera(cameraPosition)

        if (showPositionMarker) {
            mMapView?.getMapboxMap()?.loadStyle(
                styleExtension = style(Style.MAPBOX_STREETS) {
                    +image("ic_position") {
                        bitmap(BitmapFactory.decodeResource(resources, R.drawable.ic_place))
                    }
                    +geoJsonSource("position_source_id") {
                        geometry(location)
                    }
                    +symbolLayer("position_layer_id", "position_source_id") {
                        iconImage("ic_position")
                        iconAnchor(IconAnchor.BOTTOM)
                    }
                }
            )
        }
    }

    override fun deletePolygon(itemId: Long) {
        mMapViewModel.deletePolygon(itemId)
    }

    override fun copyPolygon(item: PolygonWithPoints) {
        try {
            val clipboardManager =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", item.toCopy()))
            Toast.makeText(requireContext(), "Copied.", Toast.LENGTH_SHORT).show()
        } catch (ex: Exception) {
            Toast.makeText(requireContext(), "Error!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun displayOnMap(item: PolygonWithPoints) {
        mSavedPolygonsBottomSheetDialog?.dismiss()
        val points = ArrayList<List<Point>>()
        points.add(item.toPointsList())
        moveToPosition(Point.fromLngLat(item.polygon.centerLng, item.polygon.centerLat))
        drawPolygon(points)
    }

    override fun onStart() {
        super.onStart()
        mMapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mMapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mMapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView?.onDestroy()
        _binding = null
    }
}