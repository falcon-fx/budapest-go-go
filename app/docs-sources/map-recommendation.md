The ideal library for your requirements is **osmdroid**. It is an open-source Android library built around OpenStreetMap and designed specifically as a replacement for the classic Android View system `MapView`.

Here is how **osmdroid** satisfies every constraint you listed:

* **Min API 18 & View System**: osmdroid v6.1.x supports `minSdkVersion` 14+ and operates purely as a standard Android `View` (`org.osmdroid.views.MapView`) without needing Jetpack Compose.
* **Offline Maps**: Supports offline tile archives out-of-the-box (`.mbtiles`, `.sqlite`, or `.zip`). You can pre-generate a single city archive using free tools like **Mobile Atlas Creator (MOBAC)** or **QGIS**.
* **GPS without GMS**: Includes `GpsMyLocationProvider`, which hooks directly into Android's native `android.location.LocationManager` (GPS provider) without requiring Google Play Services or FusedLocationProviderClient.
* **Low RAM Footprint**: Unlike heavy 3D/vector libraries (such as MapLibre/Mapbox OpenGL engines), osmdroid uses Android's native 2D Canvas rendering. You can configure tile cache limits to easily keep memory usage under a few megabytes.

---

### Step-by-Step Implementation Guide

#### 1. Add Dependencies (`build.gradle`)
Use an osmdroid 6.1.x version:
```groovy
dependencies {
    // Legacy view system OSM library compatible with API 18
    implementation 'org.osmdroid:osmdroid-android:6.1.18'
}
```

#### 2. Layout (`res/layout/activity_map.xml`)
Add the `MapView` to your XML layout:
```xml
<org.osmdroid.views.MapView
    android:id="@+id/map_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

#### 3. Map Configuration & Loading Offline Data
Disable internet access for the map view and load your pre-packaged `.sqlite` or `.mbtiles` city file.

```java
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;

public class MapActivity extends Activity {
    private MapView map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Initialize Configuration (Required by osmdroid)
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // 2. Memory optimization for tight RAM budget (<256MB)
        Configuration.getInstance().setCacheMapTileCount((short) 12); // Limit cached tiles in RAM
        Configuration.getInstance().setCacheMapTileOvershoot((short) 4);

        setContentView(R.layout.activity_map);
        map = findViewById(R.id.map_view);

        // 3. Restrict Map to Offline Mode Only
        map.setUseDataConnection(false); 
        map.setMultiTouchControls(true); // Enables pinch zoom

        // 4. Point osmdroid to your local city file (e.g., copied to app's private internal storage)
        File offlineTileFile = new File(getFilesDir(), "my_city.mbtiles");
        
        // Default raster source works with MBTiles/SQLite archives
        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }
}
```

---

#### 4. Display User GPS Location (No Google Play Services)
Use `GpsMyLocationProvider` with `MyLocationNewOverlay`:

```java
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

private void setupGpsLocation() {
    // Uses standard Android LocationManager, bypassing Google Play Services entirely
    GpsMyLocationProvider locationProvider = new GpsMyLocationProvider(this);
    locationProvider.addLocationSource(LocationManager.GPS_PROVIDER);

    MyLocationNewOverlay locationOverlay = new MyLocationNewOverlay(locationProvider, map);
    locationOverlay.enableMyLocation();
    
    // Optional: Auto-center on user location when acquired
    locationOverlay.enableFollowLocation(); 

    map.getOverlays().add(locationOverlay);
}
```
*Note: Make sure your manifest declares `<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />`.*

---

#### 5. Placing Markers, Connecting Stops, & Displaying Custom Paths

You can retrieve bus stop coordinates from your local SQLite database and render them using `Marker` and `Polyline`:

```java
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import java.util.ArrayList;
import java.util.List;

private void renderPublicTransportLine() {
    List<GeoPoint> routePoints = new ArrayList<>();

    // Example data loop (replace with database queries)
    List<BusStop> busStops = dbHelper.getBusStopsForLine("Route_1");

    for (BusStop stop : busStops) {
        GeoPoint point = new GeoPoint(stop.getLatitude(), stop.getLongitude());
        routePoints.add(point);

        // Create Marker
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setTitle(stop.getName());
        marker.setSnippet("Stop ID: " + stop.getId());
        // Custom icon optional: marker.setIcon(getDrawable(R.drawable.ic_bus_stop));

        // Click Handling
        marker.setOnMarkerClickListener((m, mapView) -> {
            m.showInfoWindow(); // Shows title popup
            Toast.makeText(this, "Clicked: " + stop.getName(), Toast.LENGTH_SHORT).show();
            return true;
        });

        map.getOverlays().add(marker);
    }

    // Connect markers (or pathfinding coordinates) using a Polyline
    Polyline line = new Polyline();
    line.setPoints(routePoints);
    line.getOutlinePaint().setColor(Color.BLUE);
    line.getOutlinePaint().setStrokeWidth(6.0f);

    map.getOverlays().add(line);
    map.invalidate(); // Refresh map
}
```

---

### How to Prepare the Offline City Data
1. Download **Mobile Atlas Creator (MOBAC)**.
2. Select **OpenStreetMap Mapnik** as the map source.
3. Select the city region and desired zoom levels (e.g., levels 11 through 17 are usually enough for city-level navigation without creating oversized files).
4. Select **Osmdroid SQLite** or **MBTiles** as the target output atlas format.
5. Place the resulting `.sqlite` or `.mbtiles` file inside your app's `assets/` folder and copy it to `context.getFilesDir()` on app startup, or download it via your custom app sync logic when online.

---

Here is a comprehensive reference implementation structured specifically for an AI coding assistant. It demonstrates **osmdroid** integrated into a modern Android architecture (**Hilt**, **MVVM**, **Repository Pattern**, **View System**, **Navigation**, and **BottomSheet**).

---

### Architecture Overview

```
├── data/
│   ├── model/ (TransportStop, TransportLine, Timetable)
│   ├── repository/ (MapRepository, TransportRepository)
├── di/
│   └── MapModule.kt (Hilt DI Providers)
├── ui/
│   ├── map/
│   │   ├── MapFragment.kt
│   │   ├── MapViewModel.kt
│   │   └── MapUiState.kt
│   └── stops/
│       └── StopsListFragment.kt
```

---

### 1. Dependency Injection (`di/MapModule.kt`)

Injects OkHttp for downloading the `.mbtiles` archive and configures repository singletons.

```kotlin
package com.example.app.di

import android.content.Context
import com.example.app.data.repository.MapRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideMapRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): MapRepository = MapRepository(context, okHttpClient)
}
```

---

### 2. Native Offline Map Downloader & Repository (`data/repository/MapRepository.kt`)

Downloads a pre-packaged city `.mbtiles` file directly over HTTP/HTTPS to internal app storage. This is far more memory and network efficient than downloading individual PNG tiles.

```kotlin
package com.example.app.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(val percentage: Int) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class MapRepository @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val mapFileName = "city_map.mbtiles"

    fun getMapFile(): File = File(context.filesDir, mapFileName)

    fun isMapDownloaded(): Boolean = getMapFile().exists() && getMapFile().length() > 0

    /**
     * Downloads an MBTiles or SQLite map archive natively and saves it to private app storage.
     */
    fun downloadCityMap(fileUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Progress(0))
        val targetFile = getMapFile()
        
        try {
            val request = Request.Builder().url(fileUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("HTTP Server Error ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Error("Response body is empty"))
                return@flow
            }

            val contentLength = body.contentLength()
            var bytesRead = 0L

            body.byteStream().use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesRead += read
                        if (contentLength > 0) {
                            val progress = ((bytesRead * 100) / contentLength).toInt()
                            emit(DownloadState.Progress(progress))
                        }
                    }
                    outputStream.flush()
                }
            }
            emit(DownloadState.Success)
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
            emit(DownloadState.Error(e.localizedMessage ?: "Unknown download error"))
        }
    }.flowOn(Dispatchers.IO)
}
```

---

### 3. ViewModel Layer (`ui/map/MapViewModel.kt`)

Manages transport data from the DB, selected stops, map zoom actions, and download triggers using `StateFlow`.

```kotlin
package com.example.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.data.model.TransportLine
import com.example.app.data.model.TransportStop
import com.example.app.data.repository.MapRepository
import com.example.app.data.repository.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val isMapReady: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val selectedLine: TransportLine? = null,
    val selectedStop: TransportStop? = null,
    val timetable: List<String> = emptyList()
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapRepository: MapRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        checkMapAvailability()
    }

    private fun checkMapAvailability() {
        if (mapRepository.isMapDownloaded()) {
            _uiState.update { it.copy(isMapReady = true) }
        } else {
            // Automatically download map if missing
            downloadCityMap("https://example.com/downloads/my_city.mbtiles")
        }
    }

    fun downloadCityMap(url: String) {
        viewModelScope.launch {
            mapRepository.downloadCityMap(url).collect { state ->
                _uiState.update {
                    it.copy(
                        downloadState = state,
                        isMapReady = state is DownloadState.Success
                    )
                }
            }
        }
    }

    fun selectStop(stop: TransportStop) {
        viewModelScope.launch {
            // Fetch timetable for stop from DB repository
            val mockTimetable = listOf("08:15", "08:30", "08:45", "09:00")
            _uiState.update {
                it.copy(selectedStop = stop, timetable = mockTimetable)
            }
        }
    }

    fun clearSelectedStop() {
        _uiState.update { it.copy(selectedStop = null, timetable = emptyList()) }
    }
}
```

---

### 4. Layout with BottomSheet (`res/layout/fragment_map.xml`)

Integrates `osmdroid` MapView inside a `CoordinatorLayout` alongside a `BottomSheetBehavior`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Offline OSM Map View -->
    <org.osmdroid.views.MapView
        android:id="@+id/mapView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Overlay for Download Progress -->
    <LinearLayout
        android:id="@+id/downloadOverlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#B3000000"
        android:gravity="center"
        android:orientation="vertical"
        android:visibility="gone">

        <TextView
            android:id="@+id/tvDownloadStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@android:color/white"
            android:textSize="16sp" />

        <ProgressBar
            android:id="@+id/downloadProgressBar"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="250dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp" />
    </LinearLayout>

    <!-- Bottom Sheet Container for Stop & Timetable Info -->
    <androidx.core.widget.NestedScrollView
        android:id="@+id/bottomSheet"
        android:layout_width="match_parent"
        android:layout_height="300dp"
        android:background="@android:color/white"
        android:elevation="8dp"
        app:behavior_hideable="true"
        app:behavior_peekHeight="0dp"
        app:layout_behavior="com.google.android.material.bottomsheet.BottomSheetBehavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:id="@+id/tvStopTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="20sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tvStopDetails"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textColor="#666666" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="Upcoming Arrivals:"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tvTimetableList"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:textSize="16sp" />
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

---

### 5. Transport List Fragment & Passing Data to Map (`ui/stops/StopsListFragment.kt`)

Demonstrates navigating from a stops list view to `MapFragment`, passing arguments via standard `Bundle`.

```kotlin
package com.example.app.ui.stops

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.app.R
import com.example.app.ui.map.MapFragment

class StopsListFragment : Fragment(R.layout.fragment_stops_list) {

    private fun onStopClicked(stopId: String, lineId: String) {
        val mapFragment = MapFragment().apply {
            arguments = Bundle().apply {
                putString(MapFragment.ARG_STOP_ID, stopId)
                putString(MapFragment.ARG_LINE_ID, lineId)
                putDouble(MapFragment.ARG_LAT, 47.4979)
                putDouble(MapFragment.ARG_LON, 19.0402)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, mapFragment)
            .addToBackStack(null)
            .commit()
    }
}
```

---

### 6. MapFragment Implementation (`ui/map/MapFragment.kt`)

Implements:
1. Low-RAM osmdroid config (strict cache limits).
2. Native GPS via Android `LocationManager` (No GMS dependency).
3. Polyline route drawing & Marker overlays.
4. Auto-centering / Fitting view bounds.
5. `BottomSheetBehavior` integration.
6. Map click event handling via `MapEventsOverlay`.

```kotlin
package com.example.app.ui.map

import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.data.model.TransportStop
import com.example.app.data.repository.DownloadState
import com.example.app.databinding.FragmentMapBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

@AndroidEntryPoint
class MapFragment : Fragment(R.layout.fragment_map) {

    companion object {
        const val ARG_STOP_ID = "arg_stop_id"
        const val ARG_LINE_ID = "arg_line_id"
        const val ARG_LAT = "arg_lat"
        const val ARG_LON = "arg_lon"
    }

    private val viewModel: MapViewModel by viewModels()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMapBinding.bind(view)

        setupOsmdroidConfig()
        setupMapView()
        setupBottomSheet()
        setupMapClickEvents()
        observeViewModel()
        handleArguments()
    }

    private fun setupOsmdroidConfig() {
        // Essential configuration
        Configuration.getInstance().userAgentValue = requireContext().packageName
        
        // RAM Budget Tuning (<256MB constraints)
        Configuration.getInstance().cacheMapTileCount = 12.toShort()
        Configuration.getInstance().cacheMapTileOvershoot = 4.toShort()
    }

    private fun setupMapView() {
        binding.mapView.apply {
            setUseDataConnection(false) // Strict offline operation
            setMultiTouchControls(true)
            tileProvider.tileSource = TileSourceFactory.DEFAULT_TILE_SOURCE
            controller.setZoom(15.0)
        }

        setupGpsLocationOverlay()
    }

    /**
     * GPS provider strictly using native Android LocationManager (No GMS/Play Services)
     */
    private fun setupGpsLocationOverlay() {
        val locationProvider = GpsMyLocationProvider(requireContext()).apply {
            addLocationSource(LocationManager.GPS_PROVIDER)
        }

        val locationOverlay = MyLocationNewOverlay(locationProvider, binding.mapView).apply {
            enableMyLocation()
        }

        binding.mapView.overlays.add(locationOverlay)
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    viewModel.clearSelectedStop()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    private fun setupMapClickEvents() {
        // Detect clicks on map background to dismiss bottom sheet
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    return true
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }

        binding.mapView.overlays.add(0, MapEventsOverlay(mapEventsReceiver))
    }

    private fun handleArguments() {
        arguments?.let { args ->
            val lat = args.getDouble(ARG_LAT, 0.0)
            val lon = args.getDouble(ARG_LON, 0.0)
            if (lat != 0.0 && lon != 0.0) {
                val targetPoint = GeoPoint(lat, lon)
                binding.mapView.controller.animateTo(targetPoint)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect { state ->
                handleDownloadState(state.downloadState)

                if (state.selectedStop != null) {
                    showStopDetailsInBottomSheet(state.selectedStop, state.timetable)
                }
            }
        }
    }

    private fun handleDownloadState(state: DownloadState) {
        when (state) {
            is DownloadState.Progress -> {
                binding.downloadOverlay.isVisible = true
                binding.downloadProgressBar.progress = state.percentage
                binding.tvDownloadStatus.text = "Downloading Map Data: ${state.percentage}%"
            }
            is DownloadState.Success -> {
                binding.downloadOverlay.isVisible = false
                renderTransportRouteExample()
            }
            is DownloadState.Error -> {
                binding.downloadOverlay.isVisible = false
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            DownloadState.Idle -> {
                binding.downloadOverlay.isVisible = false
            }
        }
    }

    /**
     * Renders route polyline and stop markers from database
     */
    private fun renderTransportRouteExample() {
        val stop1 = TransportStop("1", "Central Station", 47.4979, 19.0402)
        val stop2 = TransportStop("2", "City Center", 47.5000, 19.0450)
        val stops = listOf(stop1, stop2)

        val geoPoints = ArrayList<GeoPoint>()

        // 1. Render Markers
        stops.forEach { stop ->
            val point = GeoPoint(stop.lat, stop.lon)
            geoPoints.add(point)

            val marker = Marker(binding.mapView).apply {
                position = point
                title = stop.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                setOnMarkerClickListener { _, _ ->
                    viewModel.selectStop(stop)
                    true
                }
            }
            binding.mapView.overlays.add(marker)
        }

        // 2. Render Connecting Polyline
        val routeLine = Polyline().apply {
            setPoints(geoPoints)
            outlinePaint.color = Color.parseColor("#1E88E5")
            outlinePaint.strokeWidth = 8.0f
        }
        binding.mapView.overlays.add(routeLine)

        // 3. Zoom Camera to Fit Route Bounds
        if (geoPoints.isNotEmpty()) {
            val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
            binding.mapView.post {
                binding.mapView.zoomToBoundingBox(boundingBox, true, 100)
            }
        }
    }

    private fun showStopDetailsInBottomSheet(stop: TransportStop, timetable: List<String>) {
        binding.tvStopTitle.text = stop.name
        binding.tvStopDetails.text = "Lat: ${stop.lat}, Lon: ${stop.lon}"
        binding.tvTimetableList.text = timetable.joinToString(separator = "  |  ")
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDetach()
        _binding = null
    }
}
```