package com.arashjahani.mappolygonpointsdraw.ui.map

import android.Manifest
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
import com.arashjahani.mappolygonpointsdraw.data.model.LandParcel
import com.arashjahani.mappolygonpointsdraw.databinding.FragmentMapBinding
import com.arashjahani.mappolygonpointsdraw.ui.map.theme.getLandColor
import com.arashjahani.mappolygonpointsdraw.utils.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView
import com.arashjahani.mappolygonpointsdraw.data.model.LatLng
import com.arashjahani.mappolygonpointsdraw.utils.getTime
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import com.arashjahani.mappolygonpointsdraw.utils.centerOfLatLngPolygon
import com.arashjahani.mappolygonpointsdraw.utils.isPolygonIntersect
import com.arashjahani.mappolygonpointsdraw.utils.toJtsPolygon
import com.arashjahani.mappolygonpointsdraw.ui.map.theme.getLandColorHex
import android.content.Context
@AndroidEntryPoint
class MapFragment : Fragment() {

    companion object {
        private const val TAG = "MapFragment"
        fun newInstance() = MapFragment()
    }

    private val landViewModel: LandViewModel by viewModels()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var mMapView: MapView? = null
    private var mPointsList = ArrayList<Point>()

    private var mAnnotationApi: AnnotationPlugin? = null
    private var mPolygonAnnotationManager: PolygonAnnotationManager? = null

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var allowedDistrict: String? = null
    private var allowedProvince: String? = null
    private var allowedCountry: String? = null

    // Compose dialog state
    private var showAddLandDialog = false
    private var lastArea: Long = 0L
    private var lastCoordinates: List<LatLng> = emptyList()

    // Saved polygons dialog
    private var mSavedPolygonsBottomSheetDialog: BottomSheetDialog? = null
    private var mSavedPolygonsAdapter: SavedPolygonsAdapter? = null

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
        initListeners()
        loadSavedPolygonsList()

        lifecycleScope.launch {
            fetchUserPermissions()
            if (firebaseAuth.currentUser != null) {
                checkInitialLocationPermission()
            }
        }

        // ComposeView cho login/logout
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
                        }
                    )
                }
                // Dialog nhập thông tin thửa đất
                if (showAddLandDialog) {
                    AddLandDialog(
                        area = lastArea,
                        onDismiss = { showAddLandDialog = false },
                        onSave = { ownerName, phone, address, landType ->
                            val land = LandParcel(
                                address = address,
                                registerDate = getTime(),
                                ownerName = ownerName,
                                area = lastArea,
                                landType = landType,
                                coordinates = lastCoordinates,
                                phone = phone,
                                createdBy = firebaseAuth.currentUser?.uid ?: "",
                                district = allowedDistrict ?: "",
                                province = allowedProvince ?: "",
                                country = allowedCountry ?: ""
                            )
                            landViewModel.addLand(land)
                            showAddLandDialog = false
                            Toast.makeText(requireContext(), "Polygon saved", Toast.LENGTH_SHORT).show()
                            clearMapView()
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

        // Quan sát danh sách thửa đất và vẽ lên map, đồng thời cập nhật adapter
        lifecycleScope.launch {
            landViewModel.fetchLands()
            landViewModel.lands.collect { lands ->
                drawAllLands(lands)
                mSavedPolygonsAdapter?.renewItems(lands)
            }
        }
    }

    private fun prepareViews() {
        try {
            mMapView = binding.mapView
            mMapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS)
            mAnnotationApi = mMapView?.annotations
            mPolygonAnnotationManager = mAnnotationApi?.createPolygonAnnotationManager()
        } catch (e: Exception) {
            Log.e(TAG, "Error in prepareViews: ${e.message}")
            e.printStackTrace()
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
                        mPointsList.add(point)
                        drawCurrentPolygon()
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
            if (mPointsList.size < 3) {
                Toast.makeText(requireContext(), "Add at least 3 points to save polygon", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val area = mPointsList.calcPolygonArea()
            val coordinates = mPointsList.toLatLngList()

            // Lấy danh sách các polygon đã lưu (dạng List<List<LatLng>>)
            val oldPolygons = landViewModel.lands.value.map { it.coordinates }

            if (isPolygonIntersect(coordinates, oldPolygons)) {
                Toast.makeText(requireContext(), "Mảnh đất bị chồng lấn với mảnh khác!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Nếu không giao nhau, cho phép nhập thông tin và lưu
            showAddLandDialog(area, coordinates)
        }

        // Sự kiện bấm nút "Saved"
        binding.btnList.setOnClickListener {
            mSavedPolygonsBottomSheetDialog?.show()
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
    }

    private fun loadSavedPolygonsList() {
        if (mSavedPolygonsBottomSheetDialog == null) {
            mSavedPolygonsBottomSheetDialog = BottomSheetDialog(requireContext())
            mSavedPolygonsBottomSheetDialog?.setContentView(R.layout.dialog_saved_polygons_list)
            val rcvSavedPolygons = mSavedPolygonsBottomSheetDialog?.findViewById<RecyclerView>(R.id.rcvSavedPolygons)
            mSavedPolygonsAdapter = SavedPolygonsAdapter(ArrayList(), object : LandItemClickListener {
                override fun deleteLand(land: LandParcel) {
                    // Xóa khỏi Firestore
                    lifecycleScope.launch {
                        val db = FirebaseFirestore.getInstance()
                        val query = db.collection("lands")
                            .whereEqualTo("createdBy", land.createdBy)
                            .whereEqualTo("registerDate", land.registerDate)
                            .get().await()
                        for (doc in query.documents) {
                            doc.reference.delete()
                        }
                        Toast.makeText(requireContext(), "Đã xóa thửa đất!", Toast.LENGTH_SHORT).show()
                        landViewModel.fetchLands()
                    }
                }

                override fun copyLand(land: LandParcel) {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val text = "Tên chủ đất: ${land.ownerName}\nDiện tích: ${land.area}\nLoại đất: ${land.landType}\nTọa độ: ${land.coordinates.joinToString()}"
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Land Info", text))
                    Toast.makeText(requireContext(), "Đã copy thông tin!", Toast.LENGTH_SHORT).show()
                }

                override fun displayOnMap(land: LandParcel) {
                    // Vẽ lại polygon này trên bản đồ với màu theo loại đất
                    val points = land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                    mPolygonAnnotationManager?.deleteAll()
                    val colorHex = com.arashjahani.mappolygonpointsdraw.ui.map.theme.getLandColorHex(land.landType)
                    val polygonAnnotationOptions = PolygonAnnotationOptions()
                        .withPoints(listOf(points))
                        .withFillColor(colorHex)
                        .withFillOpacity(0.4)
                    mPolygonAnnotationManager?.create(polygonAnnotationOptions)
                    // Di chuyển camera đến polygon
                    if (points.isNotEmpty()) {
                        val center = points[0]
                        moveToPosition(center, false)
                    }
                    mSavedPolygonsBottomSheetDialog?.dismiss()
                }
            })
            rcvSavedPolygons?.adapter = mSavedPolygonsAdapter
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
        fun normalize(s: String?): String =
            s?.replace(Regex("^(Quận|Huyện|Thị xã|Thành phố)\\s*"), "")?.trim() ?: ""
        return normalize(district).equals(normalize(allowedDistrict), ignoreCase = true)
                || normalize(district).contains(normalize(allowedDistrict), ignoreCase = true)
    }

    private fun showDistrictWarning() {
        if (!isAdded) return
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

    private fun drawCurrentPolygon() {
        try {
            mPolygonAnnotationManager?.deleteAll()
            if (mPointsList.size > 2) {
                val polygonAnnotationOptions = PolygonAnnotationOptions()
                    .withPoints(listOf(mPointsList))
                    .withFillColor("#ee4e8b")
                    .withFillOpacity(0.4)
                mPolygonAnnotationManager?.create(polygonAnnotationOptions)

                // Chuyển mPointsList thành List<LatLng>
                val latLngList = mPointsList.toLatLngList()
                drawPolygonCenterMarker(latLngList.centerOfLatLngPolygon().toPoint())

                binding.lblArea.visibility = View.VISIBLE
                binding.lblArea.text = "Diện tích: ${mPointsList.calcPolygonArea().areaFormat()} m²"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi vẽ đa giác: ${e.message}")
        }
    }
    private fun LatLng.toPoint(): Point {
        return Point.fromLngLat(this.lng, this.lat)
    }

    private fun drawAllLands(lands: List<LandParcel>) {
        try {
            mPolygonAnnotationManager?.deleteAll()
            lands.forEach { land ->
                val points = land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                if (points.size > 2) {
                    val colorHex = getLandColorHex(land.landType) // Trả về "#E53935"
                    val polygonAnnotationOptions = PolygonAnnotationOptions()
                        .withPoints(listOf(points))
                        .withFillColor(colorHex) // Đúng kiểu String
                        .withFillOpacity(0.4)
                    mPolygonAnnotationManager?.create(polygonAnnotationOptions)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing all lands: ${e.message}")
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

    private fun clearMapView() {
        mMapView?.getMapboxMap()?.getStyle()?.removeStyleLayer("flag_layer_id")
        mMapView?.getMapboxMap()?.getStyle()?.removeStyleLayer("position_layer_id")
        mPolygonAnnotationManager?.deleteAll()
        mPointsList.clear()
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
        _binding = null
    }

    // --- Thêm các hàm suspend ở dưới cùng class ---

    private suspend fun moveToDistrict(district: String) {
        val center = getLatLngFromDistrictName(district)
        if (center != null) {
            moveToPosition(center, false)
        }
    }

    private suspend fun getLatLngFromDistrictName(district: String): Point? {
        val url = "https://nominatim.openstreetmap.org/search?format=json&q=${district.replace(" ", "+")}"
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
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
    private fun showAddLandDialog(area: Long, coordinates: List<LatLng>) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_land, null)
        val edtOwnerName = dialogView.findViewById<EditText>(R.id.edtOwnerName)
        val edtPhone = dialogView.findViewById<EditText>(R.id.edtPhone)
        val spnLandType = dialogView.findViewById<Spinner>(R.id.spnLandType)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Đất thổ cư", "Đất thổ cảnh", "Loại khác")
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnLandType.adapter = adapter

        // Lấy địa chỉ tự động từ điểm trung tâm polygon
        val center = coordinates.centerOfLatLngPolygon()
        lifecycleScope.launch {
            val address = getAddressFromLatLng(center.lat, center.lng) ?: "Không xác định"
            AlertDialog.Builder(requireContext())
                .setTitle("Nhập thông tin thửa đất")
                .setView(dialogView)
                .setPositiveButton("Lưu") { _, _ ->
                    val land = LandParcel(
                        address = address,
                        registerDate = getTime(),
                        ownerName = edtOwnerName.text.toString(),
                        area = area,
                        landType = spnLandType.selectedItemPosition + 1,
                        coordinates = coordinates,
                        phone = edtPhone.text.toString(),
                        createdBy = firebaseAuth.currentUser?.uid ?: "",
                        district = allowedDistrict ?: "",
                        province = allowedProvince ?: "",
                        country = allowedCountry ?: ""
                    )
                    landViewModel.addLand(land)
                    Toast.makeText(requireContext(), "Polygon saved", Toast.LENGTH_SHORT).show()
                    clearMapView()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }
    private suspend fun getAddressFromLatLng(lat: Double, lng: Double): String? {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng"
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "YourAppName")
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val json = org.json.JSONObject(body)
                json.optString("display_name")
            }
        }
    }
}
suspend fun getDistrictFromLatLngNominatim(lat: Double, lng: Double): String? {
    val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng"
    val client = okhttp3.OkHttpClient()
    val request = okhttp3.Request.Builder()
        .url(url)
        .header("User-Agent", "YourAppName")
        .build()
    return withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext null
            val json = org.json.JSONObject(body)
            val address = json.optJSONObject("address")
            val fields = listOf("county", "district", "suburb", "city_district", "state_district", "region", "quarter")
            fields.mapNotNull { address?.optString(it) }
                .firstOrNull { it.isNotBlank() }
        }
    }
}
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
