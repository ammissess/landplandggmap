package com.arashjahani.mappolygonpointsdraw.ui.map

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.collections.ArrayList
import androidx.recyclerview.widget.RecyclerView
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Compose imports
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView

@AndroidEntryPoint
class MapFragment : Fragment(), PolygonsItemClickListener {

    companion object {
        private const val TAG = "MapFragment"
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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareViews()
        initObservers()
        initListeners()

        lifecycleScope.launch {
            fetchUserPermissions()
            if (firebaseAuth.currentUser != null) {
                checkInitialLocationPermission()
            }
        }

        // Thêm ComposeView động cho nút login/logout ở góc trên bên phải
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                val user = FirebaseAuth.getInstance().currentUser
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, end = 12.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    LoginButton(
                        isLoggedIn = user != null,
                        userEmail = user?.email,
                        onLoginClick = {
                            startActivity(Intent(requireActivity(), LoginActivity::class.java))
                        },
                        onLogoutClick = {
                            FirebaseAuth.getInstance().signOut()
                            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
                            // Có thể reload lại fragment/activity nếu muốn
                        }
                    )
                }
            }
        }
        (binding.root as ViewGroup).addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Tự động di chuyển đến district nếu có
        val district = activity?.intent?.getStringExtra("district")
        if (!district.isNullOrEmpty()) {
            lifecycleScope.launch {
                moveToDistrict(district)
            }
        }
    }

    private fun prepareViews() {
        try {
            mMapView = binding.mapView
            Log.d(TAG, "MapView initialized")

            mMapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS)
            Log.d(TAG, "Style loaded")

            mAnnotationApi = mMapView?.annotations
            Log.d(TAG, "Annotation API initialized")

            mPolygonAnnotationManager = mAnnotationApi?.createPolygonAnnotationManager()
            Log.d(TAG, "PolygonAnnotationManager created")

            mPointsList.add(ArrayList())
            loadSavedPolygonsList()
        } catch (e: Exception) {
            Log.e(TAG, "Error in prepareViews: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun initObservers() {
        lifecycleScope.launch {
            try {
                mMapViewModel.getAllPolygons().collect {
                    mSavedPolygonsAdapter?.renewItems(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in observer: ${e.message}")
            }
        }
    }

    private fun initListeners() {
        binding.fabLocation.setOnClickListener {
            getUserLocation()
        }

        binding.btnAddPoint.setOnClickListener {
            mMapView?.getMapboxMap()?.cameraState?.center?.let { point ->
                lifecycleScope.launch {
                    if (isPointInAllowedDistrict(point)) {
                        mPointsList.first().add(point)
                        drawPolygon(mPointsList)
                    } else {
                        showDistrictWarning()
                    }
                }
            }
        }

        binding.btnSavePolygon.setOnClickListener {
            if (firebaseAuth.currentUser == null) {
                Toast.makeText(requireContext(), "Please log in to save polygons", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireActivity(), LoginActivity::class.java))
                return@setOnClickListener
            }
            savePolygon()
        }

        binding.btnDrawPolygon.setOnClickListener {
            if (firebaseAuth.currentUser == null) {
                Toast.makeText(requireContext(), "Please log in to draw polygons", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireActivity(), LoginActivity::class.java))
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
                startActivity(Intent(requireActivity(), LoginActivity::class.java))
                return@setOnClickListener
            }
            mSavedPolygonsBottomSheetDialog?.show()
        }
    }

    private suspend fun fetchUserPermissions() {
        val user = firebaseAuth.currentUser ?: return

        try {
            val document = firestore.collection("users").document(user.uid).get().await()
            allowedDistrict = document.getString("district")
            allowedProvince = document.getString("province")
            allowedCountry = document.getString("country")

            allowedDistrict?.let {
                Toast.makeText(requireContext(),
                    "You can only draw in $it district",
                    Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user permissions", e)
            Toast.makeText(requireContext(),
                "Failed to load district restrictions",
                Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun isPointInAllowedDistrict(point: Point): Boolean {
        val user = firebaseAuth.currentUser ?: return false
        val document = firestore.collection("users").document(user.uid).get().await()
        val allowedDistrict = document.getString("district") ?: return true

        val district = getDistrictFromLatLngNominatim(point.latitude(), point.longitude())
        Log.d("DistrictCheck", "Nominatim: '$district', Allowed: '$allowedDistrict'")

        // Loại bỏ tiền tố "Quận", "Huyện", "Thị xã", "Thành phố" nếu có
        fun normalize(s: String?): String =
            s?.replace(Regex("^(Quận|Huyện|Thị xã|Thành phố)\\s*"), "")?.trim() ?: ""

        return normalize(district).equals(normalize(allowedDistrict), ignoreCase = true)
                || normalize(district).contains(normalize(allowedDistrict), ignoreCase = true)
    }

    private fun showDistrictWarning() {
        if (!isAdded) return // Check if Fragment is attached

        activity?.runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle("District Restriction")
                .setMessage("You can only draw polygons in $allowedDistrict district")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private suspend fun checkInitialLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                mFusedLocationClient.lastLocation.await()?.let { location ->
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    if (!isPointInAllowedDistrict(point)) {
                        showDistrictWarning()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking initial location", e)
            }
        }
    }

    private fun savePolygon() {
        if (mPointsList.first().size < 3) {
            Toast.makeText(requireContext(), "Add at least 3 points to save polygon", Toast.LENGTH_SHORT).show()
            return
        }

        val area = mPointsList.first().calcPolygonArea()
        mMapViewModel.savePolygonWithPoints(area, mPointsList.first())
        Toast.makeText(requireContext(), "Polygon saved", Toast.LENGTH_SHORT).show()
        clearMapView()
    }

    private fun drawPolygon(points: List<List<Point>>) {
        try {
            mPolygonAnnotationManager?.deleteAll()
            val polygonAnnotationOptions = PolygonAnnotationOptions()
                .withPoints(points)
                .withFillColor("#ee4e8b")
                .withFillOpacity(0.4)
            mPolygonAnnotationManager?.create(polygonAnnotationOptions)

            points.firstOrNull()?.let {
                if (it.size > 2) {
                    drawPolygonCenterMarker(it.centerOfPolygon())
                    binding.lblArea.visibility = View.VISIBLE
                    binding.lblArea.text = "Area: ${it.calcPolygonArea().areaFormat()} m²"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing polygon: ${e.message}")
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
        val context = context ?: return

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    // Không kiểm tra district ở đây nữa
                    moveToPosition(point, true)
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

    override fun onDestroyView() {
        super.onDestroyView()
        mPolygonAnnotationManager?.deleteAll()
        mPolygonAnnotationManager = null
        mAnnotationApi = null
        mSavedPolygonsBottomSheetDialog?.dismiss()
        mSavedPolygonsBottomSheetDialog = null
        _binding = null
    }

    // --- Thêm các hàm suspend ở dưới cùng class ---

    // Hàm tự động di chuyển đến district (gọi trong coroutine)
    private suspend fun moveToDistrict(district: String) {
        val center = getLatLngFromDistrictName(district)
        if (center != null) {
            moveToPosition(center, false)
        }
    }

    // Hàm này dùng Nominatim để lấy toạ độ trung tâm district
    private suspend fun getLatLngFromDistrictName(district: String): Point? {
        val url = "https://nominatim.openstreetmap.org/search?format=json&q=${district.replace(" ", "+")}"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YourAppName")
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val arr = org.json.JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val lat = obj.getDouble("lat")
                    val lon = obj.getDouble("lon")
                    Point.fromLngLat(lon, lat)
                } else null
            }
        }
    }
}

// Hàm lấy district từ Nominatim (OpenStreetMap)
suspend fun getDistrictFromLatLngNominatim(lat: Double, lng: Double): String? {
    val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng"
    val client = OkHttpClient()
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "YourAppName")
        .build()
    return withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val address = json.optJSONObject("address")
            Log.d("NominatimRaw", json.toString())
            // Trả về tất cả các trường có thể
            val fields = listOf("county", "district", "suburb", "city_district", "state_district", "region", "quarter")
            fields.mapNotNull { address?.optString(it) }
                .firstOrNull { it.isNotBlank() }
        }
    }
}

// Composable cho nút login/logout
@Composable
fun LoginButton(
    isLoggedIn: Boolean,
    userEmail: String?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = {
            if (isLoggedIn) expanded = true else onLoginClick()
        }) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Account"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (isLoggedIn) {
                DropdownMenuItem(
                    text = { Text(userEmail ?: "Account") },
                    onClick = { /* Không làm gì */ },
                    enabled = false
                )
                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        expanded = false
                        onLogoutClick()
                    }
                )
            }
        }
    }
}
