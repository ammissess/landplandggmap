    package com.hung.landplanggmap.ui.map

    import android.Manifest
    import android.app.Dialog
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
    import com.hung.landplanggmap.data.model.LandParcel
    import com.hung.landplanggmap.utils.*
    import com.google.android.gms.location.FusedLocationProviderClient
    import com.google.android.gms.location.LocationServices
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.firestore.FirebaseFirestore
    import com.mapbox.geojson.Point
    import com.mapbox.geojson.Polygon
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
    import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotation
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
    import com.hung.landplanggmap.data.model.LatLng
    import com.hung.landplanggmap.utils.getTime
    import com.hung.landplanggmap.utils.centerOfLatLngPolygon
    import com.hung.landplanggmap.utils.isPolygonIntersect
    import com.hung.landplanggmap.ui.map.theme.getLandColorHex
    import com.mapbox.maps.plugin.compass.compass
    import android.content.Context
    import android.graphics.Color
    import android.view.Gravity
    import androidx.compose.foundation.clickable
    import java.text.Normalizer
    import androidx.compose.ui.platform.LocalContext
    import androidx.core.content.ContextCompat
    import androidx.core.view.WindowCompat
    import com.hung.landplanggmap.R
    import com.hung.landplanggmap.databinding.FragmentMapBinding
    import com.mapbox.geojson.LineString
    import com.mapbox.maps.extension.style.layers.addLayer
    import com.mapbox.maps.extension.style.layers.generated.lineLayer
    import com.mapbox.maps.extension.style.sources.addSource
    import com.mapbox.maps.plugin.gestures.addOnMapClickListener
    import kotlinx.coroutines.delay
    import okhttp3.OkHttpClient
    import okhttp3.Request
    import org.json.JSONArray
    import org.json.JSONObject
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.setValue
    import androidx.compose.runtime.mutableStateOf
    import com.mapbox.maps.plugin.logo.logo


    @AndroidEntryPoint
    class MapFragment : Fragment() {

        companion object {
            private const val TAG = "MapFragment"
            fun newInstance() = MapFragment()
        }

        //bien giới hạn biên
        private var allowedDistrictPolygon: Polygon? = null

        //bien kiem tra xac dinh vi tri
        private var isLocationDetermined = false

        //nút bật tắt vùng quy hoạch
        private var isLandsVisible = false

        //chi tiết đất
        private var selectedLand: LandParcel? = null

        // Theo dõi dialog hiện tại
        private var currentDialog: BottomSheetDialog? = null


        private var _binding: FragmentMapBinding? = null
        private val binding get() = _binding!!

        private val landViewModel: LandViewModel by viewModels()
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
        private var showAddLandDialog by mutableStateOf(false)
        private var lastArea by mutableStateOf(0L)
        private var lastCoordinates by mutableStateOf(emptyList<LatLng>())

        // Saved polygons dialog
        private var mSavedPolygonsBottomSheetDialog: BottomSheetDialog? = null
        private var mSavedPolygonsAdapter: SavedPolygonsAdapter? = null

        // Kiểm tra xem dialog có đang hiển thị không
        private fun isDialogShowing(): Boolean {
            return currentDialog?.isShowing == true
        }

        // Hủy dialog hiện tại
        private fun dismissCurrentDialog() {
            currentDialog?.dismiss()
            currentDialog = null
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val window = requireActivity().window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT

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


        //view được tạo bởi polygon
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

            // Đặt vị trí mặc định khi bản đồ được tải, khởi động sẽ hiển thị tại Hà nội
            mMapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS) {
                val defaultLocation = Point.fromLngLat(105.8342, 21.0278) // Hà Nội, Việt Nam
                moveToPosition(defaultLocation, showPositionMarker = true, zoomLevel = 12.0)
            }

            // ComposeView cho login/logout
            val composeView = ComposeView(requireContext()).apply {
                setContent {
                    val user = FirebaseAuth.getInstance().currentUser
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 30.dp), // Căn trái và cạnh bên trên
                        contentAlignment = Alignment.BottomStart
                    ) {
                        LoginButton(
                            isLoggedIn = user != null,
                            userEmail = user?.email,
                            onLoginClick = {
                                startActivity(Intent(requireActivity(), LoginActivity::class.java))
                            },
                            onLogoutClick = {
                                FirebaseAuth.getInstance().signOut()
                                Toast.makeText(
                                    requireContext(),
                                    "Đăng xuất tài khoản thành công",
                                    Toast.LENGTH_SHORT
                                ).show()
                                startActivity(
                                    Intent(
                                        requireActivity(),
                                        LoginActivity::class.java
                                    )
                                ) // Chuyển về LoginActivity
                                requireActivity().finish() // Đóng Fragment và Activity hiện tại
                            }
                        )
                    }
                    // Dialog nhập thông tin thửa đất
                    if (showAddLandDialog) {
                        AddLandDialog(
                            area = lastArea,
                            onDismiss = { showAddLandDialog = false },
                            onSave = { ownerName, phone, landType, _ ->
                                lifecycleScope.launch {
                                    val center = lastCoordinates.centerOfLatLngPolygon()
                                    val address =
                                        getAddressFromLatLng(center.lat, center.lng) ?: "Không xác định"
                                    val land = LandParcel(
                                        address = address, // Đảm bảo lấy địa chỉ từ API
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
                                    Toast.makeText(
                                        requireContext(),
                                        "Lưu mảnh đất thành công",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    clearMapView()
                                }
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

                    // Thêm sự kiện click trên map , xác định polygon bằng tọa độ click
                    mMapView?.getMapboxMap()?.addOnMapClickListener { point ->
                        val annotations =
                            mPolygonAnnotationManager?.annotations?.filterIsInstance<PolygonAnnotation>()
                        annotations?.forEach { annotation ->
                            if (annotation.geometry is Polygon) {
                                val polygon = annotation.geometry as Polygon
                                val coordinates = polygon.coordinates().first()
                                val isClickedInside = isPointInPolygon(
                                    point,
                                    coordinates.map { Point.fromLngLat(it.longitude(), it.latitude()) }
                                )

                                if (isClickedInside) {
                                    val clickedLand = lands.find { land ->
                                        val landPoints =
                                            land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                                        landPoints.size == coordinates.size && coordinates.containsAll(
                                            landPoints
                                        )
                                    }

                                    clickedLand?.let { land ->
                                        // Kiểm tra và hủy dialog cũ nếu đang hiển thị
                                        if (selectedLand == land && isDialogShowing()) {
                                            return@addOnMapClickListener true
                                        }
                                        dismissCurrentDialog()
                                        selectedLand = land

                                        // Gọi nháy và hiển thị dialog
                                        lifecycleScope.launch {
                                            flashPolygon(annotation)
                                            showLandDetailDialog(land)
                                        }
                                        return@addOnMapClickListener true
                                    }
                                }
                            }
                        }
                        false
                    }
                }
            }
        }

        // Cập nhật phương thức moveToPosition, zoom màn hình bản đồ
        private fun moveToPosition(
            location: Point,
            showPositionMarker: Boolean = false,
            zoomLevel: Double = 17.0
        ) {
            val cameraPosition = CameraOptions.Builder()
                .zoom(zoomLevel) // Đặt zoom level khoảng 17.0 cho khoảng 100 feet
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

        //hàm chớp nháy màu mảnh đất
        private suspend fun flashPolygon(annotation: PolygonAnnotation) {
            val originalColor = annotation.fillColorInt ?: Color.GREEN // fallback màu gốc nếu null
            val highlightColor = Color.BLACK
            val originalOpacity = annotation.fillOpacity ?: 0.4

            repeat(2) {
                // Tạo polygon nháy
                val updated = PolygonAnnotationOptions()
                    .withPoints(annotation.points)
                    .withFillColor(highlightColor)
                    .withFillOpacity(originalOpacity)

                val tempAnnotation = mPolygonAnnotationManager?.create(updated)
                delay(250)

                // Xóa polygon nháy
                tempAnnotation?.let { mPolygonAnnotationManager?.delete(it) }

                // Vẽ lại polygon gốc với màu ban đầu
                val original = PolygonAnnotationOptions()
                    .withPoints(annotation.points)
                    .withFillColor(originalColor)
                    .withFillOpacity(originalOpacity)

                mPolygonAnnotationManager?.create(original)
                delay(250)
            }
        }


        // Hàm kiểm tra xem điểm có nằm trong polygon không
        private fun isPointInPolygon(point: Point, polygonPoints: List<Point>): Boolean {
            var inside = false
            var j = polygonPoints.size - 1
            for (i in 0 until polygonPoints.size) {
                val xi = polygonPoints[i].longitude()
                val yi = polygonPoints[i].latitude()
                val xj = polygonPoints[j].longitude()
                val yj = polygonPoints[j].latitude()

                val intersect = ((yi > point.latitude()) != (yj > point.latitude())) &&
                        (point.longitude() < (xj - xi) * (point.latitude() - yi) / (yj - yi) + xi)
                if (intersect) inside = !inside
                j = i
            }
            return inside
        }

        private fun prepareViews() {
            try {
                mMapView = binding.mapView
                mMapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS)
                mAnnotationApi = mMapView?.annotations
                mPolygonAnnotationManager = mAnnotationApi?.createPolygonAnnotationManager()

                // Ẩn biểu tượng la bàn
                mMapView?.compass?.updateSettings {
                    enabled = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in prepareViews: ${e.message}")
                e.printStackTrace()
            }
        }

        //nút bật tắt tất cả mảnh đất đã vẽ
        private fun initListeners() {

            binding.fabLocation.setOnClickListener {
                getUserLocation { location ->
                    if (location != null) {
                        val point = Point.fromLngLat(location.longitude, location.latitude)
                        moveToPositionNow(point)
                        isLocationDetermined = true
                        lifecycleScope.launch {
                            //ten quận huyện hiện tại
                            val currentDistrict =
                                getDistrictFromLatLngNominatim(point.latitude(), point.longitude())
                            // Kiểm tra vị trí trong allowedDistrict
                            if (isPointInAllowedDistrict(point)) {
                                Toast.makeText(
                                    requireContext(),
                                    "Vị trí: ${currentDistrict ?: "NoName"} được quy hoạch !",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Vị trí: ${currentDistrict ?: "NoName"} không quy hoạch !",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            val userId = firebaseAuth.currentUser?.uid
                            if (userId != null) {
                                val userLands =
                                    landViewModel.lands.value.filter { it.createdBy == userId }
                                if (userLands.isNotEmpty()) {
                                    // Chỉ xóa mảnh đất đã lưu, không ảnh hưởng mảnh đất tạm
                                    if (!isLandsVisible) {
                                        val existingAnnotations =
                                            mPolygonAnnotationManager?.annotations?.filterIsInstance<PolygonAnnotation>()
                                        if (existingAnnotations != null) {
                                            val savedAnnotations =
                                                existingAnnotations.filter { annotation ->
                                                    userLands.any { land ->
                                                        val landPoints = land.coordinates.map {
                                                            Point.fromLngLat(it.lng, it.lat)
                                                        }
                                                        val annotationPoints =
                                                            (annotation.geometry as? Polygon)?.coordinates()
                                                                ?.get(0)
                                                                ?.map {
                                                                    Point.fromLngLat(
                                                                        it.longitude(),
                                                                        it.latitude()
                                                                    )
                                                                }
                                                                ?: emptyList()
                                                        landPoints.size == annotationPoints.size && landPoints.containsAll(
                                                            annotationPoints
                                                        )
                                                    }
                                                }
                                            if (savedAnnotations.isNotEmpty()) {
                                                mPolygonAnnotationManager?.delete(savedAnnotations)
                                            }
                                        }
                                    }
                                    if (isLandsVisible) {
                                        mPolygonAnnotationManager?.deleteAll() // Xóa tất cả trước khi vẽ lại
                                        userLands.forEach { land ->
                                            val points = land.coordinates.map {
                                                Point.fromLngLat(
                                                    it.lng,
                                                    it.lat
                                                )
                                            }
                                            if (points.size > 2) {
                                                val colorHex = getLandColorHex(land.landType)
                                                val polygonAnnotationOptions =
                                                    PolygonAnnotationOptions()
                                                        .withPoints(listOf(points))
                                                        .withFillColor(colorHex)
                                                        .withFillOpacity(0.4)
                                                mPolygonAnnotationManager?.create(
                                                    polygonAnnotationOptions
                                                )
                                            }
                                        }
                                    }
                                    // Luôn vẽ lại mảnh đất tạm, không bị xóa
                                    drawCurrentPolygon()
                                    //Toast.makeText(
    //                                    requireContext(),
    //                                    "Hiển thị mảnh đất của bạn",
    //                                    Toast.LENGTH_SHORT
                                    //)
                                    //.show()
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "Chưa quy hoạch mảnh đất nào",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Không thể xác định vị trí hiện tại",
                            Toast.LENGTH_SHORT
                        ).show()
                        isLocationDetermined = false
                    }
                }
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
                    Toast.makeText(
                        requireContext(),
                        "Yêu cầu đăng nhập tài khoản",
                        Toast.LENGTH_SHORT
                    ).show()
                    startActivity(Intent(requireActivity(), LoginActivity::class.java))
                    return@setOnClickListener
                }
                if (mPointsList.size < 3) {
                    Toast.makeText(
                        requireContext(),
                        "Cần ít nhất 3 điểm để quy hoạch mảnh đất",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                val area = mPointsList.calcPolygonArea()
                val coordinates = mPointsList.toLatLngList()

                val oldPolygons = landViewModel.lands.value.map { it.coordinates }

                if (isPolygonIntersect(coordinates, oldPolygons)) {
                    Toast.makeText(
                        requireContext(),
                        "Mảnh đất bị chồng lấn với mảnh khác!",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                showAddLandDialog = true
                lastArea = area
                lastCoordinates = coordinates
            }

            binding.btnList.setOnClickListener {
                mSavedPolygonsBottomSheetDialog?.show()
            }

            binding.btnDrawPolygon.setOnClickListener {
                if (firebaseAuth.currentUser == null) {
                    Toast.makeText(
                        requireContext(),
                        "Yêu cầu đăng nhập tài khoản",
                        Toast.LENGTH_SHORT
                    ).show()
                    startActivity(Intent(requireActivity(), LoginActivity::class.java))
                    return@setOnClickListener
                }
                binding.layoutAddPolygonNavigator.visibility = View.VISIBLE
                binding.imgCursor.visibility = View.VISIBLE
                binding.layoutMainNavigator.visibility = View.GONE
                drawCurrentPolygon() // Đảm bảo mảnh đất tạm được vẽ lại
            }

            binding.btnCloseDrawing.setOnClickListener {
                binding.layoutAddPolygonNavigator.visibility = View.GONE
                binding.imgCursor.visibility = View.GONE
                binding.lblArea.visibility = View.GONE
                binding.layoutMainNavigator.visibility = View.VISIBLE
                mPointsList.clear()
                if (isLandsVisible) {
                    drawAllLands(landViewModel.lands.value)
                }
                drawCurrentPolygon() // Giữ mảnh đất tạm nếu có
            }

    // Sự kiện nhấn vào biểu tượng tìm kiếm (chỉ tìm kiếm địa điểm trong allowedDistrict)
            binding.ivSearchIcon.setOnClickListener {
                val dialogView =
                    LayoutInflater.from(requireContext()).inflate(R.layout.dialog_search_new, null)
                val searchEditText =
                    dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtSearchNew)
                val textInputLayout =
                    dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.textInputLayout)

                val dialog = Dialog(requireContext(), R.style.CustomDialogAnimation)
                dialog.setContentView(dialogView)
                val displayMetrics = resources.displayMetrics
                val height = (displayMetrics.heightPixels * 0.2).toInt() // Đặt chiều cao cố định 25%
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
                dialog.window?.setGravity(Gravity.TOP)
                dialog.window?.setBackgroundDrawable(null) // Loại bỏ nền mặc định để bo góc hiển thị

                textInputLayout?.setEndIconOnClickListener {
                    val searchText = searchEditText?.text?.toString()?.trim() ?: ""
                    if (searchText.isNotEmpty()) {
                        lifecycleScope.launch {
                            val location = searchLocation(searchText)
                            if (location != null) {
                                val point = Point.fromLngLat(location.longitude(), location.latitude())
                                if (isPointInAllowedDistrict(point)) {
                                    moveToPosition(point, true, 17.0)
                                    Toast.makeText(
                                        requireContext(),
                                        "Đã tìm thấy: $searchText",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "Ngoài phạm vi tìm kiếm, không được phép!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Không tìm thấy địa điểm: $searchText",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            dialog.dismiss()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Vui lòng nhập địa điểm!", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                dialog.show()
            }


    // nut Loc ket qua quyhoach dat
            binding.btnSearchh.setOnClickListener {
                val dialogView =
                    LayoutInflater.from(requireContext()).inflate(R.layout.dialog_search, null)
                val searchEditText =
                    dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtSearch)
                val searchButton =
                    dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSearchDialog)
                val cancelButton =
                    dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelDialog)

                val dialog = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
                dialog.setContentView(dialogView)
                val bottomSheet =
                    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_rounded_dialog)

                searchButton.setOnClickListener {
                    val searchText = searchEditText.text.toString().trim()
                    if (searchText.isNotEmpty()) {
                        lifecycleScope.launch {
                            val filteredLands = filterLands(searchText)
                            if (filteredLands.isNotEmpty()) {
                                updateMapWithFilteredLands(filteredLands)
                                mSavedPolygonsAdapter?.renewItems(filteredLands)
                                mSavedPolygonsBottomSheetDialog?.show()
                                Toast.makeText(
                                    requireContext(),
                                    "Đã lọc ${filteredLands.size} mảnh đất",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Không tìm thấy mảnh đất phù hợp!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            dialog.dismiss()
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Vui lòng nhập giá trị lọc!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                cancelButton.setOnClickListener {
                    dialog.dismiss()
                }

                dialog.show()
            }

    //fab_help
            binding.fabHelp.setOnClickListener {
                lifecycleScope.launch {
                    allowedDistrict?.let { district ->
                        val polygon = getDistrictPolygonFromNominatim(district)
                        if (polygon != null) {
                            allowedDistrictPolygon = polygon // Để kiểm tra sau khi user vẽ

                            mMapView?.getMapboxMap()?.getStyle { style ->
                                if (style.styleLayerExists("outline-layer")) {
                                    style.removeStyleLayer("outline-layer")
                                }
                                if (style.styleSourceExists("outline-source")) {
                                    style.removeStyleSource("outline-source")
                                }

                                style.addSource(geoJsonSource("outline-source") {
                                    geometry(polygon)
                                })
                                style.addLayer(lineLayer("outline-layer", "outline-source") {
                                    lineColor("#0000FF")
                                    lineWidth(2.0)
                                    lineOpacity(1.0)
                                })
                            }

                            moveToPosition(getPolygonCenter(polygon), false)
                            // Di chuyển đến giữa polygon
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Không lấy được dữ liệu ranh giới $district",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } ?: run {
                        Toast.makeText(
                            requireContext(),
                            "Không có quận huyện được chọn",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }



            binding.fabToggleLands.setOnClickListener {
                isLandsVisible = !isLandsVisible
                if (isLandsVisible) {
                    val lands = landViewModel.lands.value
                    mPolygonAnnotationManager?.deleteAll() // Xóa tất cả trước khi vẽ lại
                    val userId = firebaseAuth.currentUser?.uid
                    if (userId != null) {
                        val userLands = lands.filter { it.createdBy == userId }
                        userLands.forEach { land ->
                            val points = land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                            if (points.size > 2) {
                                val colorHex = getLandColorHex(land.landType)
                                val polygonAnnotationOptions = PolygonAnnotationOptions()
                                    .withPoints(listOf(points))
                                    .withFillColor(colorHex)
                                    .withFillOpacity(0.4)
                                mPolygonAnnotationManager?.create(polygonAnnotationOptions)
                            }
                        }
                    }
                    binding.fabToggleLands.setImageResource(R.drawable.ic_toggle_on)
                    //Toast.makeText(requireContext(), "Hiển thị mảnh đất của bạn", Toast.LENGTH_SHORT).show()
                } else {
                    val existingAnnotations =
                        mPolygonAnnotationManager?.annotations?.filterIsInstance<PolygonAnnotation>()
                    if (existingAnnotations != null) {
                        val userId = firebaseAuth.currentUser?.uid
                        if (userId != null) {
                            val userLands = landViewModel.lands.value.filter { it.createdBy == userId }
                            val savedAnnotations = existingAnnotations.filter { annotation ->
                                userLands.any { land ->
                                    val landPoints =
                                        land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                                    val annotationPoints =
                                        (annotation.geometry as? Polygon)?.coordinates()?.get(0)?.map {
                                            Point.fromLngLat(it.longitude(), it.latitude())
                                        } ?: emptyList()
                                    landPoints.size == annotationPoints.size && landPoints.containsAll(
                                        annotationPoints
                                    )
                                }
                            }
                            if (savedAnnotations.isNotEmpty()) {
                                mPolygonAnnotationManager?.delete(savedAnnotations)
                            }
                        }
                    }
                    binding.fabToggleLands.setImageResource(R.drawable.ic_toggle_off)
                    //Toast.makeText(requireContext(), "Ẩn mảnh đất của bạn", Toast.LENGTH_SHORT).show()
                }
                drawCurrentPolygon() // Luôn giữ mảnh đất tạm
            }
        }

        private fun getPolygonCenter(polygon: Polygon): Point {
            val allPoints = polygon.coordinates()[0]
            val avgLat = allPoints.map { it.latitude() }.average()
            val avgLng = allPoints.map { it.longitude() }.average()
            return Point.fromLngLat(avgLng, avgLat)
        }

        private fun loadSavedPolygonsList() {
            if (mSavedPolygonsBottomSheetDialog == null) {
                mSavedPolygonsBottomSheetDialog = BottomSheetDialog(requireContext())
                mSavedPolygonsBottomSheetDialog?.setContentView(R.layout.dialog_saved_polygons_list)
                val rcvSavedPolygons =
                    mSavedPolygonsBottomSheetDialog?.findViewById<RecyclerView>(R.id.rcvSavedPolygons)
                mSavedPolygonsAdapter =
                    SavedPolygonsAdapter(ArrayList(), object : LandItemClickListener {


                        override fun deleteLand(land: LandParcel) {
                            lifecycleScope.launch {
                                val db = FirebaseFirestore.getInstance()
                                val query = db.collection("lands")
                                    .whereEqualTo("createdBy", land.createdBy)
                                    .whereEqualTo("registerDate", land.registerDate)
                                    .get()
                                    .await()
                                for (doc in query.documents) {
                                    doc.reference.delete()
                                }
                                Toast.makeText(requireContext(), "Đã xóa thửa đất!", Toast.LENGTH_SHORT)
                                    .show()
                                landViewModel.fetchLands()
                            }
                        }

                        override fun copyLand(land: LandParcel) {
                            val clipboard =
                                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val text =
                                "Tên chủ đất: ${land.ownerName}\nDiện tích: ${land.area}\nLoại đất: ${land.landType}\nTọa độ: ${land.coordinates.joinToString()}"
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "Land Info",
                                    text
                                )
                            )
                            Toast.makeText(requireContext(), "Đã copy thông tin!", Toast.LENGTH_SHORT)
                                .show()
                        }

                        override fun displayOnMap(land: LandParcel) {
                            showLandDetailDialog(land)
                        }

                        override fun onDisplayPolygon(land: LandParcel) {
                            lifecycleScope.launch {
                                // Ẩn BottomSheetDialog
                                mSavedPolygonsBottomSheetDialog?.dismiss()

                                mPolygonAnnotationManager?.deleteAll() // Xóa tất cả trước khi vẽ lại
                                val points = land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                                if (points.size > 2) {
                                    val colorHex = getLandColorHex(land.landType)
                                    val polygonAnnotationOptions = PolygonAnnotationOptions()
                                        .withPoints(listOf(points))
                                        .withFillColor(colorHex)
                                        .withFillOpacity(0.4)
                                    mPolygonAnnotationManager?.create(polygonAnnotationOptions)

                                    // Lấy trung tâm của polygon
                                    val center = land.coordinates.centerOfLatLngPolygon().toPoint()

                                    // Lấy mức zoom hiện tại và di chuyển camera mà không thay đổi zoom
                                    val currentCameraState = mMapView?.getMapboxMap()?.cameraState
                                    if (currentCameraState != null) {
                                        val cameraOptions = CameraOptions.Builder()
                                            .center(center)
                                            .zoom(currentCameraState.zoom) // Giữ nguyên mức zoom hiện tại
                                            .build()
                                        mMapView?.getMapboxMap()?.setCamera(cameraOptions)
                                    }

                                    Toast.makeText(
                                        requireContext(),
                                        "Hiển thị mảnh đất của ${land.ownerName}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                // Vẽ lại mảnh đất tạm nếu có
                                drawCurrentPolygon()
                            }
                        }


                    })
                rcvSavedPolygons?.adapter = mSavedPolygonsAdapter
            }
        }


        //hàm lấy biên giới đất
        suspend fun getDistrictPolygonFromNominatim(districtName: String): Polygon? {
            val url =
                "https://nominatim.openstreetmap.org/search.php?q=$districtName&polygon_geojson=1&format=jsonv2"

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LandApp")
                .build()

            return withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() > 0) {
                        val geo = jsonArray.getJSONObject(0).getJSONObject("geojson")
                        if (geo.getString("type") == "Polygon") {
                            val coordinates = geo.getJSONArray("coordinates").getJSONArray(0)
                            val points = mutableListOf<Point>()
                            for (i in 0 until coordinates.length()) {
                                val coord = coordinates.getJSONArray(i)
                                val lng = coord.getDouble(0)
                                val lat = coord.getDouble(1)
                                points.add(Point.fromLngLat(lng, lat))
                            }
                            return@withContext Polygon.fromLngLats(listOf(points))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DistrictPolygon", "Error: ${e.message}")
                }
                return@withContext null
            }
        }


        //ham Loc dat
        // Hàm lọc danh sách mảnh đất dựa trên giá trị nhập
        private fun filterLands(searchText: String): List<LandParcel> {
            val allLands = landViewModel.lands.value
            val normalizedSearch = searchText.trim().lowercase()
            val searchWithDiacritics = normalizeVietnameseDiacritics(normalizedSearch)

            return when {
                // Trường hợp nhập số diện tích đất (dưới hoặc bằng 6 chữ số)
                searchText.matches(Regex("^\\d{1,6}$")) -> {
                    val targetArea = searchText.toLong()
                    val minArea = (targetArea * 0.5).toLong()
                    val maxArea = (targetArea * 1.5).toLong()
                    allLands.filter { it.area in minArea..maxArea }
                }

                searchText.matches(Regex("^>[\\d]+$")) -> {
                    val targetArea = searchText.drop(1).toLongOrNull() ?: 0L
                    allLands.filter { it.area > targetArea }
                }

                searchText.matches(Regex("^<[\\d]+$")) -> {
                    val targetArea = searchText.drop(1).toLongOrNull() ?: 0L
                    allLands.filter { it.area < targetArea }
                }
                // Trường hợp nhập số điện thoại (hơn 6 chữ số)
                searchText.length > 6 -> {
                    val cleanedPhone = normalizedSearch.replace(Regex("[^0-9+]"), "")
                    when {
                        cleanedPhone.startsWith("+84") && cleanedPhone.length == 12 && cleanedPhone.matches(
                            Regex("^\\+84\\d{9}$")
                        ) -> {
                            val phoneNumber = cleanedPhone
                            val results = allLands.filter { land ->
                                land.phone?.trim()?.lowercase() == phoneNumber
                            }
                            when {
                                results.isNotEmpty() -> {
                                    Toast.makeText(
                                        requireContext(),
                                        "Tìm thấy số điện thoại phù hợp",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    val firstLand = results.first()
                                    val center = firstLand.coordinates.centerOfLatLngPolygon().toPoint()
                                    mMapView?.getMapboxMap()?.setCamera(
                                        CameraOptions.Builder().center(center).zoom(14.0).build()
                                    )
                                }

                                else -> Toast.makeText(
                                    requireContext(),
                                    "Số điện thoại không tìm thấy",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            results
                        }

                        cleanedPhone.length == 10 && cleanedPhone.matches(Regex("^\\d{10}$")) -> {
                            val phoneNumber = cleanedPhone
                            val results = allLands.filter { land ->
                                land.phone?.trim()?.lowercase() == phoneNumber
                            }
                            when {
                                results.isNotEmpty() -> {
                                    Toast.makeText(
                                        requireContext(),
                                        "Tìm thấy số điện thoại phù hợp",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    val firstLand = results.first()
                                    val center = firstLand.coordinates.centerOfLatLngPolygon().toPoint()
                                    mMapView?.getMapboxMap()?.setCamera(
                                        CameraOptions.Builder().center(center).zoom(14.0).build()
                                    )
                                }

                                else -> Toast.makeText(
                                    requireContext(),
                                    "Số điện thoại không tìm thấy",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            results
                        }
                        // Thêm kiểm tra số đầu Việt Nam (tùy chọn)
                        cleanedPhone.length in 9..11 && (cleanedPhone.startsWith("09") || cleanedPhone.startsWith(
                            "03"
                        ) || cleanedPhone.startsWith("07") || cleanedPhone.startsWith("08")) && cleanedPhone.matches(
                            Regex("^\\d{9,11}$")
                        ) -> {
                            val phoneNumber = cleanedPhone
                            val results = allLands.filter { land ->
                                land.phone?.trim()?.lowercase() == phoneNumber
                            }
                            when {
                                results.isNotEmpty() -> {
                                    Toast.makeText(
                                        requireContext(),
                                        "Tìm thấy số điện thoại phù hợp",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    val firstLand = results.first()
                                    val center = firstLand.coordinates.centerOfLatLngPolygon().toPoint()
                                    mMapView?.getMapboxMap()?.setCamera(
                                        CameraOptions.Builder().center(center).zoom(16.0).build()
                                    )
                                }

                                else -> Toast.makeText(
                                    requireContext(),
                                    "Số điện thoại không tìm thấy",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            results
                        }

                        else -> {
                            Toast.makeText(
                                requireContext(),
                                "Nhập sai định dạng số điện thoại",
                                Toast.LENGTH_SHORT
                            ).show()
                            emptyList()
                        }
                    }
                }
                // Trường hợp nhập chuỗi (có dấu hoặc không dấu)
                else -> {
                    val results = mutableListOf<LandParcel>()
                    val searchWords = normalizedSearch.split(" ")

                    allLands.forEach { land ->
                        land.ownerName?.let { ownerName ->
                            val normalizedOwner = ownerName.lowercase()
                            val ownerWithDiacritics = normalizeVietnameseDiacritics(normalizedOwner)

                            if (ownerWithDiacritics.contains(searchWithDiacritics, ignoreCase = true)) {
                                results.add(land)
                            } else if (normalizedOwner.contains(normalizedSearch, ignoreCase = true)) {
                                results.add(land)
                            } else {
                                val similarity = calculateSimilarity(normalizedSearch, normalizedOwner)
                                if (similarity > 0.7) {
                                    results.add(land)
                                }
                            }
                        }
                    }

                    results.sortWith(compareByDescending { land ->
                        val ownerName = land.ownerName?.lowercase() ?: ""
                        val isDiacriticMatch =
                            ownerName.contains(searchWithDiacritics, ignoreCase = true)
                        val isNoDiacriticMatch = ownerName.contains(normalizedSearch, ignoreCase = true)
                        when {
                            isDiacriticMatch -> 2
                            isNoDiacriticMatch -> 1
                            else -> 0
                        }
                    })

                    if (results.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "Không tìm thấy mảnh đất phù hợp!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    results
                }
            }
        }

        // Hàm chuẩn hóa tiếng Việt (loại bỏ dấu)
        fun normalizeVietnameseDiacritics(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .replace("đ", "d")
                .replace("Đ", "D")
        }

        // Hàm tính độ tương đồng (Levenshtein Distance normalized)
        private fun calculateSimilarity(s1: String, s2: String): Double {
            val maxLength = maxOf(s1.length, s2.length)
            if (maxLength == 0) return 1.0
            val distance = levenshteinDistance(s1, s2)
            return 1.0 - (distance.toDouble() / maxLength)
        }

        // Hàm tính khoảng cách Levenshtein
        private fun levenshteinDistance(s1: String, s2: String): Int {
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i
            for (j in 0..s2.length) dp[0][j] = j

            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = minOf(
                        dp[i - 1][j] + 1, // Xóa
                        dp[i][j - 1] + 1, // Thêm
                        dp[i - 1][j - 1] + cost // Thay đổi
                    )
                }
            }
            return dp[s1.length][s2.length]
        }

        // Hàm cập nhật bản đồ với danh sách mảnh đất đã lọc
        private fun updateMapWithFilteredLands(filteredLands: List<LandParcel>) {
            mPolygonAnnotationManager?.deleteAll() // Xóa tất cả polygon hiện tại
            val allLands = landViewModel.lands.value
            allLands.forEach { land ->
                val points = land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                if (points.size > 2 && !filteredLands.contains(land)) {
                    // Ẩn các mảnh không thuộc danh sách lọc
                    val existingAnnotations =
                        mPolygonAnnotationManager?.annotations?.filterIsInstance<PolygonAnnotation>()
                    existingAnnotations?.find { annotation ->
                        val annotationPoints =
                            (annotation.geometry as? Polygon)?.coordinates()?.get(0)?.map {
                                Point.fromLngLat(it.longitude(), it.latitude())
                            } ?: emptyList()
                        points.size == annotationPoints.size && points.containsAll(annotationPoints)
                    }?.let { mPolygonAnnotationManager?.delete(it) }
                }
            }
            filteredLands.forEach { land ->
                val points = land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                if (points.size > 2) {
                    val colorHex = getLandColorHex(land.landType)
                    val polygonAnnotationOptions = PolygonAnnotationOptions()
                        .withPoints(listOf(points))
                        .withFillColor(colorHex)
                        .withFillOpacity(0.4)
                    mPolygonAnnotationManager?.create(polygonAnnotationOptions)
                }
            }
            drawCurrentPolygon() // Giữ mảnh đất tạm nếu có
        }


        private suspend fun fetchUserPermissions() {
            val user = firebaseAuth.currentUser ?: return
            try {
                val document = firestore.collection("users").document(user.uid).get().await()
                allowedDistrict = document.getString("district")
                allowedProvince = document.getString("province")
                allowedCountry = document.getString("country")
                allowedDistrict?.let {
                    //Toast.makeText(requireContext(),
                    // "Bạn chỉ có quyền vẽ đường quy hoạch tại địa điểm Quận/Huyện: $it ",
                    //Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch user permissions", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to load district restrictions",
                    Toast.LENGTH_SHORT
                ).show()
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
                    .setTitle("Giới hạn quyền")
                    .setMessage("Bạn chỉ có quyền vẽ đường quy hoạch tại địa điểm Quận/Huyện: $allowedDistrict ")
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

        //ve tam thoi manh dat them moi
        private fun drawCurrentPolygon() {
            try {
                // Chỉ xóa mảnh đất tạm cũ, không ảnh hưởng mảnh đất đã lưu
                val existingAnnotations =
                    mPolygonAnnotationManager?.annotations?.filterIsInstance<PolygonAnnotation>()
                if (existingAnnotations != null) {
                    val annotationsToDelete = existingAnnotations.filter { annotation ->
                        val annotationPoints =
                            (annotation.geometry as? Polygon)?.coordinates()?.firstOrNull()
                                ?: emptyList()
                        annotationPoints.size == mPointsList.size && annotationPoints.containsAll(
                            mPointsList
                        )
                    }
                    if (annotationsToDelete.isNotEmpty()) {
                        mPolygonAnnotationManager?.delete(annotationsToDelete)
                    }
                }

                if (mPointsList.size > 2) {
                    val polygonAnnotationOptions = PolygonAnnotationOptions()
                        .withPoints(listOf(mPointsList))
                        .withFillColor("#00CED1") // Màu xanh nước biển mặc định
                        .withFillOpacity(0.4)
                    mPolygonAnnotationManager?.create(polygonAnnotationOptions)

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
                if (isLandsVisible) {
                    val existingAnnotations =
                        mPolygonAnnotationManager?.annotations?.filterIsInstance<PolygonAnnotation>()
                    if (existingAnnotations != null) {
                        val savedAnnotations = existingAnnotations.filter { annotation ->
                            val annotationPoints =
                                (annotation.geometry as? Polygon)?.coordinates()?.firstOrNull()
                                    ?: emptyList()
                            lands.any { land ->
                                val landPoints =
                                    land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                                annotationPoints.size == landPoints.size && annotationPoints.containsAll(
                                    landPoints
                                )
                            }
                        }
                        if (savedAnnotations.isNotEmpty()) {
                            mPolygonAnnotationManager?.delete(savedAnnotations)
                        }
                    }

                    lands.forEach { land ->
                        val points = land.coordinates.map { Point.fromLngLat(it.lng, it.lat) }
                        if (points.size > 2) {
                            val colorHex = getLandColorHex(land.landType)
                            val polygonAnnotationOptions = PolygonAnnotationOptions()
                                .withPoints(listOf(points))
                                .withFillColor(colorHex)
                                .withFillOpacity(0.4)
                            mPolygonAnnotationManager?.create(polygonAnnotationOptions)
                        }
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

        //pop up hiển thị chi tiết mảnh đất
        // Thêm hàm hiển thị popup
        private fun showLandDetailDialog(land: LandParcel) {
            selectedLand = land
            val dialogView =
                LayoutInflater.from(requireContext()).inflate(R.layout.dialog_land_detail, null)
            val dialog = BottomSheetDialog(requireContext())
            dialog.setContentView(dialogView)

            // Gán giá trị cho các TextView
            val tvPhone =
                dialogView.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tvPhone)
            tvPhone.text = "Số điện thoại: ${land.phone}"
            tvPhone.setOnClickListener {
                land.phone?.let { phone ->
                    val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Phone", phone))
                    Toast.makeText(
                        requireContext(),
                        "Số điện thoại đã được sao chép",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Mở ứng dụng gọi điện
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = android.net.Uri.parse("tel:$phone")
                    }
                    startActivity(intent)
                }
            }

            dialogView.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tvOwnerName).text =
                "Tên chủ sở hữu: ${land.ownerName}"
            dialogView.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tvArea).text =
                "Diện tích: ${land.area} m²"
            dialogView.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tvAddress).text =
                "Địa chỉ: ${land.address}"
            dialogView.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tvRegisterDate).text =
                "Ngày đăng ký: ${land.registerDate}"

            // Thiết lập chiều cao 40% màn hình bằng cách điều chỉnh BottomSheet
            val displayMetrics = resources.displayMetrics
            val height = (displayMetrics.heightPixels * 0.4).toInt()
            val layoutParams = dialogView.layoutParams as? ViewGroup.MarginLayoutParams
                ?: ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    height
                )
            layoutParams.height = height
            dialogView.layoutParams = layoutParams

            // Nút Chỉ đường
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNavigate)
                .setOnClickListener {
                    if (!isLocationDetermined) {
                        Toast.makeText(
                            requireContext(),
                            "Cần xác định vị trí hiện tại",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    getUserLocation { currentLocation ->
                        if (currentLocation != null) {
                            val startPoint =
                                Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
                            val endPoint = land.coordinates.centerOfLatLngPolygon().toPoint()
                            drawRoute(startPoint, endPoint) { success ->
                                if (success) {
                                    dialog.dismiss() // Ẩn dialog chỉ khi vẽ đường thành công
                                }
                            }
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Không thể xác định vị trí hiện tại",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

            // Nút đóng
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClosee)
                .setOnClickListener {
                    dialog.dismiss()
                }

            dialog.show()
        }

        //vẽ chỉ đường dựa vào api mapbox
        private fun drawRoute(startPoint: Point, endPoint: Point, onComplete: (Boolean) -> Unit) {
            mMapView?.getMapboxMap()?.getStyle { style ->
                // Xóa layer và source cũ nếu tồn tại
                if (style.styleLayerExists("route-layer")) {
                    style.removeStyleLayer("route-layer")
                }
                if (style.styleSourceExists("route-source")) {
                    style.removeStyleSource("route-source")
                }

                val accessToken =
                    "sk.eyJ1IjoiYW1taXNzZXNzIiwiYSI6ImNtY2MwZHFqNzAxcWgyanFzaHNrNXVhNzEifQ.joiQ_GqrlotS5FkxJAf_9w" // Thay bằng token thật
                val url = "https://api.mapbox.com/directions/v5/mapbox/driving/" +
                        "${startPoint.longitude()},${startPoint.latitude()};" +
                        "${endPoint.longitude()},${endPoint.latitude()}?access_token=$accessToken&geometries=geojson"

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val client = OkHttpClient()
                        val request = Request.Builder().url(url).build()
                        val response = client.newCall(request).execute()
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val routes = json.getJSONArray("routes")
                            if (routes.length() > 0) {
                                val route = routes.getJSONObject(0)
                                val geometry = route.getJSONObject("geometry")
                                val lineString = LineString.fromJson(geometry.toString())
                                val coordinates = lineString.coordinates()

                                withContext(Dispatchers.Main) {
                                    style.addSource(geoJsonSource("route-source") {
                                        geometry(LineString.fromLngLats(coordinates))
                                    })
                                    style.addLayer(lineLayer("route-layer", "route-source") {
                                        lineColor("#FF0000")
                                        lineWidth(4.0)
                                        lineOpacity(0.8)
                                    })
                                    onComplete(true) // Báo hiệu vẽ đường thành công
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onComplete(false) // Không có tuyến đường
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onComplete(false) // Không có dữ liệu
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error drawing route: ${e.message}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Không thể vẽ đường đi",
                                Toast.LENGTH_SHORT
                            ).show()
                            onComplete(false) // Báo lỗi
                        }
                    }
                }
            }
        }


        private fun clearMapView() {
            mMapView?.getMapboxMap()?.getStyle()?.removeStyleLayer("flag_layer_id")
            mMapView?.getMapboxMap()?.getStyle()?.removeStyleLayer("position_layer_id")
            if (!isLandsVisible) {
                mPolygonAnnotationManager?.deleteAll()
            }
            mPointsList.clear()
        }

        private fun getUserLocation(onLocationResult: (Location?) -> Unit) {
            val context = context ?: return
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    onLocationResult(location)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Error getting location: ${e.message}")
                    onLocationResult(null)
                }
            } else {
                onLocationResult(null)
            }
        }


        //Hàm xac dinh vi tri hien tai
        private fun moveToPositionNow(location: Point, showPositionMarker: Boolean = true) {
            val mapboxMap = mMapView?.getMapboxMap() ?: return

            if (showPositionMarker) {
                mapboxMap.loadStyle(
                    styleExtension = style(Style.MAPBOX_STREETS) {
                        +image("ic_position") {
                            bitmap(BitmapFactory.decodeResource(resources, R.drawable.ic_man_standing))
                        }
                        +geoJsonSource("position_source_id") {
                            geometry(location)
                        }
                        +symbolLayer("position_layer_id", "position_source_id") {
                            iconImage("ic_position")
                            iconAnchor(IconAnchor.BOTTOM)
                        }
                    }
                ) {
                    val cameraPosition = CameraOptions.Builder()
                        .zoom(17.0) // Zoom rõ vị trí hiện tại
                        .center(location)
                        .build()
                    mapboxMap.setCamera(cameraPosition)
                }
            } else {
                val cameraPosition = CameraOptions.Builder()
                    .zoom(17.0)
                    .center(location)
                    .build()
                mapboxMap.setCamera(cameraPosition)
            }
        }


        //cái này là zoom khi sur dung tim kiem dia diem
        private fun moveToPosition2(location: Point, showPositionMarker: Boolean = false) {
            val mapboxMap = mMapView?.getMapboxMap() ?: return

            if (showPositionMarker) {
                mapboxMap.loadStyle(
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
                ) {
                    // ✅ Đặt camera sau khi style đã load xong
                    val cameraPosition = CameraOptions.Builder()
                        .zoom(17.0) // zoom to rõ vị trí
                        .center(location)
                        .build()
                    mapboxMap.setCamera(cameraPosition)
                }
            } else {
                // ✅ Nếu không cần marker, vẫn zoom bình thường
                val cameraPosition = CameraOptions.Builder()
                    .zoom(17.0)
                    .center(location)
                    .build()
                mapboxMap.setCamera(cameraPosition)
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

        private suspend fun searchLocation(query: String): Point? {
            val url = "https://nominatim.openstreetmap.org/search?format=json&q=${
                query.replace(
                    " ",
                    "+"
                )
            }&addressdetails=1"
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "YourAppName")
                .build()
            return withContext(Dispatchers.IO) {
                try {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching location: ${e.message}")
                    null
                }
            }
        }

        private suspend fun moveToDistrict(district: String) {
            val center = getLatLngFromDistrictName(district)
            if (center != null) {
                moveToPosition(center, false)
            }
        }

        private suspend fun getLatLngFromDistrictName(district: String): Point? {
            val url =
                "https://nominatim.openstreetmap.org/search?format=json&q=${district.replace(" ", "+")}"
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
            .header("User-Agent", "YourAppName") // bắt buộc phải có
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val json = org.json.JSONObject(body)
                val address = json.optJSONObject("address") ?: return@withContext null

                // Ưu tiên cấp xã/phường
                val wardLevel = listOf(
                    "village",         // xã
                    "hamlet",          // thôn
                    "town",            // thị trấn
                    "suburb",          // vùng ven
                    "neighbourhood",   // khu phố
                    "quarter",         // phường
                    "municipality",    // đơn vị hành chính tương đương xã
                    "residential",     // khu dân cư
                    "locality"         // địa phương
                )

                for (key in wardLevel) {
                    val value = address.optString(key)
                    if (!value.isNullOrBlank()) {
                        return@withContext value
                    }
                }

                // Nếu không có xã/phường thì trả về quận/huyện
                val districtLevel = listOf(
                    "city_district",
                    "district",
                    "county",
                    "state_district",
                    "region"
                )

                for (key in districtLevel) {
                    val value = address.optString(key)
                    if (!value.isNullOrBlank()) {
                        return@withContext value
                    }
                }

                return@withContext null
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
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }

        // Chuyển đổi màu Android sang màu Compose
        val whiteColor = androidx.compose.ui.graphics.Color(android.graphics.Color.WHITE)
        val blackColor = androidx.compose.ui.graphics.Color(android.graphics.Color.BLACK)

        Box(
            modifier = Modifier
                .size(50.dp)
                //.shadow(6.dp, CircleShape)
                //.background(whiteColor, CircleShape)  // Sử dụng màu đã chuyển đổi
                //.clip(CircleShape)
                .clickable {
                    if (isLoggedIn) expanded = true else onLoginClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Account",
                tint = blackColor,  // Sử dụng màu đã chuyển đổi
                modifier = Modifier.size(32.dp)
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (isLoggedIn) {
                    DropdownMenuItem(
                        text = { Text(userEmail ?: "Account") },
                        onClick = {},
                        enabled = false
                    )
                    DropdownMenuItem(
                        text = { Text("Đăng xuất") },
                        onClick = {
                            expanded = false
                            Toast.makeText(context, "Đã đăng xuất", Toast.LENGTH_SHORT).show()
                            onLogoutClick()
                        }
                    )
                }
            }
        }
    }

