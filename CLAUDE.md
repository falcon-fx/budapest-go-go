# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Budapest public transport (BKK Futár) Android application targeting legacy devices (minSdk 18, including BlackBerry 10 Passport via Android runtime). The app downloads and caches GTFS timetable data and real-time vehicle positions from the BKK Futár API.

**Key technical considerations:**
- **Dynamic SSL certificate management**: User-imported SSL certificates stored in `context.filesDir/certs/`. Supports 1-3 certificates with `go_bkk_hu.pem` required. No bundled certificates in raw resources. Certificate validation blocks network operations until certs are imported via AuthFragment.
- **TLS 1.2 backport**: Conscrypt security provider injected at app startup for TLS 1.2 support on Android 4.1-4.4.
- **MultiDex**: Enabled due to method count from Room, Retrofit, Hilt, and GTFS dependencies.
- **Optimized database operations**: GTFS timetable contains ~2M records. Highly optimized bulk insert with single-transaction batching, disabled FK checks during import, and optimized clear operation. Load time: ~7 minutes (down from 12 minutes).

## CRITICAL: API Level 18 Compatibility Requirement

**This app targets Android 4.3 (API level 18) for legacy device support. ALL code and dependencies MUST be compatible with API 18.**

- Only use classes, methods, and APIs available in Android 4.3 (API 18)
- When adding dependencies, ensure they support API 18
- If a dependency no longer supports API 18 in recent versions, use an older version that DID support API 18
- Check AndroidX compatibility - many modern AndroidX libraries require API 21+
- Avoid Java 8+ language features not available through desugaring on API 18
- Test all API usage against API 18 compatibility before implementation

## CRITICAL: MVVM / Clean Architecture Requirements

**This app follows MVVM (Model-View-ViewModel) architecture with strict layer separation.**

### Layer Responsibilities

**UI Layer** (Fragments, Activities):
- Handle user interactions (button clicks, text input)
- Observe ViewModels via LiveData
- Display data and UI state
- Show dialogs, toasts, navigation
- **NEVER contain business logic**
- **NEVER directly access repositories or data sources**

**ViewModel Layer**:
- Manage UI state via LiveData
- Orchestrate business logic by calling repositories
- Handle coroutine scopes (viewModelScope)
- Transform repository data for UI consumption
- **NEVER reference Android framework classes** (Context, View, Fragment, Activity) except via Hilt `@ApplicationContext` when absolutely necessary
- **NEVER perform UI operations** (show dialogs, navigation)

**Repository Layer** (data/db/repo/):
- Single source of truth for data access
- Coordinate between local (Room) and remote (Retrofit) data sources
- Implement business logic for data operations
- Handle data mapping and transformation
- **NEVER reference UI components**
- **NEVER use LiveData** (return plain data types, let ViewModels wrap in LiveData)

**Data Sources** (DAOs, API Services):
- Direct database or network access
- Simple CRUD operations
- No business logic

### Design Pattern Conventions

- Use constructor injection via Hilt (@Inject constructor)
- Repository interfaces with production implementations (TimetableRepo → ProdTimetableRepo)
- Event wrapper for one-shot UI events (navigation, showing messages)
- Observe LiveData in Fragments using viewLifecycleOwner
- Coroutines for async work (viewModelScope in ViewModels, Dispatchers.IO for heavy operations)

## Build Commands (Developer Reference)

**IMPORTANT FOR CLAUDE:** NEVER BUILD THE APP. Do NOT run gradle build, assemble, or install commands. Only run unit tests (`./gradlew test`) if needed. All app building and manual testing is done solely by the developer.

### Common Development Commands (Manual - Developer Use Only)

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Note: installPassport task exists but does not work (BB10 uses proprietary ADB connection SDK)

# Run unit tests (Claude: you may run this)
./gradlew test

# Run instrumented tests (Claude: you may run this)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

### Testing Individual Components (Claude: you may run tests)

```bash
# Run single test class
./gradlew test --tests com.falconfx.gtfsviewer.ExampleUnitTest

# Run specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.falconfx.gtfsviewer.ExampleInstrumentedTest
```

## Architecture

### Package Structure

```
com.falconfx.gtfsviewer/
├── BkkApp.kt                    # Application class (Hilt entry, Conscrypt setup, MultiDex)
├── MainActivity.kt              # Single activity with Navigation Component
├── data/
│   ├── AppModule.kt            # Hilt DI module (network, database, repos)
│   ├── api/
│   │   └── BkkApiService.kt    # Retrofit interface for BKK Futár API
│   ├── db/
│   │   ├── BkkDatabase.kt      # Room database with optimized fastClearAll() (single-pass delete)
│   │   ├── dao/                # DAOs for timetable and vehicle data
│   │   ├── repo/               # Repository interfaces and implementations
│   │   │   ├── CertificateRepo.kt          # Certificate management interface
│   │   │   ├── ProdCertificateRepo.kt      # Handles ZIP import, validation, storage
│   │   │   ├── LoadingProgress.kt          # Progress tracking data class with phase-based percentages
│   │   │   └── CertificateImportException.kt  # Custom exception for cert errors
│   │   ├── *Entity.kt          # Room entities (Route, Stop, Timetable, Trip, Vehicle)
│   │   └── RouteTypes.kt       # Enum for BKK route types (bus, tram, metro, etc.)
│   └── util/
│       ├── BkkTypeConverters.kt    # Room type converters
│       └── Tls12SocketFactory.kt   # TLS 1.2 wrapper for Android 4.1-5.0
└── ui/
    ├── auth/                   # Certificate import and API key management
    │   ├── AuthFragment.kt     # ZIP file picker, auto-skip if certs exist
    │   └── AuthViewModel.kt    # Orchestrates cert import, shows errors/restart prompts
    ├── map/                    # Main map view with route/stop selection
    │   ├── MapFragment.kt      # Shows progress dialog during import, handles route/direction expansion
    │   ├── MapViewModel.kt     # Coordinates timetable fetch with progress tracking
    │   ├── RoutesAdapter.kt    # RecyclerView adapter for route/direction/stop list
    │   └── item_direction.xml  # Direction header layout (displays "To [TerminusName]")
    └── Event.kt                # Single-event wrapper for LiveData
```

### Data Flow

1. **Certificate Import**: AuthFragment allows user to select ZIP file containing SSL certificates:
   - Reads ZIP to ByteArray (fixes stream lifecycle issues)
   - ProdCertificateRepo validates: 1-3 .pem files, go_bkk_hu.pem required, X.509 format
   - Atomically writes to `context.filesDir/certs/` using .tmp → rename pattern
   - Sets SharedPreferences flag, triggers app restart to reload OkHttpClient
   - Auto-skip navigation if certificates already exist (prevents back button loop)

2. **Timetable fetch**: MapViewModel triggers TimetableRepo.fetchAndStoreTimetable() with progress callback:
   - Shows non-dismissible progress dialog with real-time percentage updates
   - Downloads budapest_gtfs.zip (~60-70 MB) to cache
   - **Optimized clear**: Single-pass DELETE with FK disabled, single WAL checkpoint (11s vs 98s)
   - Extracts 4 CSV files to cache (~400 MB total)
   - **Two-pass parsing**: Count lines (fast), then parse with accurate progress
   - **Transaction-wrapped batches**: Each table's batches in single transaction (reduces index updates)
   - **FK disabled during import**: Re-enabled after all inserts complete
   - Bulk inserts into Room in 50k batches (2M+ records)
   - **Cache cleanup**: Deletes extracted CSV files and ZIP after successful import
   - **Total time: ~7 minutes** (down from 12 minutes, 42% improvement)
   - Progress phases: DOWNLOADING → CLEARING → EXTRACTING → PARSING_STOPS → PARSING_ROUTES → PARSING_TRIPS → PARSING_TIMETABLE → COMPLETE

3. **Vehicle positions**: VehicleRepo downloads GTFS-RT VehiclePositions.pb protobuf (requires API key from AuthRepo/SharedPreferences)

4. **Repository pattern**: All data access through repo interfaces (TimetableRepo, VehicleRepo, AuthRepo, CertificateRepo) with Prod* implementations injected via Hilt

### Dependency Injection (Hilt)

- `@HiltAndroidApp` on BkkApp
- `@HiltViewModel` on all ViewModels
- AppModule provides: BkkDatabase, Repos, Retrofit/OkHttpClient with custom SSL, BkkApiService
- **OkHttpClient config**: 240s read timeout, 60s connect timeout (GTFS zip is ~60MB)

### Network Layer Details

**Custom SSL Trust Chain** (AppModule.buildSslFactoryFromFiles):
- Loads user-imported certificates from `context.filesDir/certs/`
- Required because eszigno.hu CA not in Android <5.0 trust store
- Throws IllegalStateException if no certificates available (blocks network calls)
- Tls12SocketFactory wraps SSLSocketFactory on API 16-21 to enable TLS 1.2
- ViewModels check CertificateRepo.hasCertificates() before network operations

**Certificate Management**:
- User must import go_bkk_hu.pem (required) via ZIP file picker in AuthFragment
- Optional: eszigno_intermediate.pem, eszigno_root.pem (if needed for older Android versions)
- Validation: 1-3 .pem files max, must be valid X.509 PEM format (not DER)
- Storage: `context.filesDir/certs/` with atomic .tmp → rename writes
- SharedPreferences flag: `has_certs` tracks import status

**API Endpoints** (base URL in strings.xml):
- Static GTFS: `/static/v1/public-gtfs/budapest_gtfs.zip` (no auth)
- Vehicle positions: `/query/v1/ws/gtfs-rt/full/VehiclePositions.pb?key={API_KEY}` (requires key)

## Development Notes

### When Modifying Database Schema

- Room version is always `1` with `.fallbackToDestructiveMigration()` - schema changes wipe data
- Large table operations must batch in 50k chunks (memory constraint for API 18 devices)
- **CRITICAL**: Wrap multi-batch operations in single transaction using `bkkDatabase.withTransaction { }` to avoid 200+ index rebuilds
- Disable foreign keys during bulk operations: `db.execSQL("PRAGMA foreign_keys = OFF")` then re-enable after
- Single WAL checkpoint at end of operation, not after each chunk
- See BkkDatabase.fastClearAll() for optimized deletion pattern (single-pass DELETE vs chunked)

### When Changing Network Code

- Test on both modern Android and emulated API 18-21 to verify SSL handshake
- If adding new HTTPS endpoints, verify CA trust chain compatibility
- Long timeouts (60s connect, 240s read) are intentional for slow devices/networks

### Adding New Features

- Use Hilt for DI - do not manually create repos/ViewModels
- Follow repository pattern - UI never directly calls Room DAOs or Retrofit services
- LiveData for UI updates, coroutines (viewModelScope) for async work
- Navigation Component for fragment transitions (see navigation graph in res/navigation/)

### Route Directions and Stops Display

**Direction Handling**:
- GTFS trips have `direction_id` (Boolean): false=A, true=B. Routes can have stops in both directions.
- UI displays routes as expandable items; clicking shows a **direction header** with the terminal stop name
- Direction header displays "To [TerminusName]" (e.g., "To Kamaraerdei ifjúsági park")
- Clicking the direction header swaps to the opposite direction, reloading stops for that direction
- Always starts with direction A (direction_id=false)

**Stop Queries**:
- `TimetableDao.getStopsOfRoute*(routeId, directionId)` filters by both route AND direction
- Queries include `GROUP BY stops.id` to deduplicate (each stop appears once per direction despite multiple timetable entries)
- Terminal stop fetched via `TimetableDao.getFinalStopNameOfRoute(routeId, directionId)` ordered by `stop_seq DESC LIMIT 1`

**RecyclerView Adapter** (RoutesAdapter.kt):
- Three sealed `ListItem` types: RouteItem, DirectionItem (header), StopItem
- `DirectionItem` stores routeId, directionId, terminusName for "To X" display
- Expanding a route inserts direction header + stops for that direction
- Collapsing removes both direction header and all stops
- Direction header click triggers `onDirectionToggle()` in MapFragment

**Data Flow**:
1. User clicks route → Fragment calls `MapViewModel.getStopsOfRoute(routeId, directionId=false, reverse=false)`
2. ViewModel calls repo → DAO returns stops (grouped by id, ordered by stop_seq)
3. Fragment simultaneously fetches `MapViewModel.getFinalStopNameOfRoute(routeId, directionId)` for terminus
4. Both passed to adapter → renders DirectionItem + StopItems
5. User clicks direction header → Fragment toggles directionId, repeats flow with new direction

### Certificate and API Key Management

**Certificates** (AuthFragment):
- User imports ZIP file containing SSL certificates (.pem files)
- AuthFragment reads stream to ByteArray before passing to ViewModel (avoids stream closed errors)
- Shows import success/error via Event-wrapped LiveData
- Prompts for app restart after successful import (OkHttpClient must reload)
- Auto-skip navigation if certificates already present (avoids back button navigation loop)
- Navigation uses popUpTo to remove auth from back stack after successful navigation

**API Key** (AuthFragment):
- API key stored in SharedPreferences via AuthRepo
- Required only for vehicle position feed, not for timetable download
- Key entered via simple EditText + button

## Testing Strategy

- Unit tests: Business logic in repos/ViewModels
- Instrumented tests: Database operations, SSL handshake verification on API 18-21
- Manual testing on BB10 Passport required for TLS/SSL validation (gradlew installPassport)

## Progress Tracking System

**LoadingProgress.kt** provides phase-based progress tracking:
- 8 phases: DOWNLOADING, CLEARING, EXTRACTING, PARSING_STOPS, PARSING_ROUTES, PARSING_TRIPS, PARSING_TIMETABLE, COMPLETE
- Weighted percentage calculation based on observed timings (Timetable=50%, Clear=15%, Extract=10%, etc.)
- Uses actual line counts from files (two-pass: count then parse) instead of hardcoded estimates
- String resources in strings.xml with `%1$d` format for percentage display
- ViewModel exposes progress via LiveData, Fragment shows non-dismissible dialog
- Progress emitted every 10 batches during timetable parse to reduce overhead

## Performance Characteristics

**Database Load Time**: ~7 minutes (down from 12 minutes)
- DB Clear: 11 seconds (down from 98 seconds) - single-pass DELETE with one WAL checkpoint
- stop_times.txt: 2-3 minutes (down from 6-8 minutes) - transaction wrapping + FK disabled
- Cache cleanup: Automatic deletion of extracted CSV files and ZIP after import

**Storage Usage**:
- Database: ~1.1 GB (2M+ timetable records with composite keys and indices)
- Cache: ~0 MB after import (all temp files cleaned up)

## Common Issues

**"SSLHandshakeException" on API 18-21**: User must import SSL certificates via AuthFragment. App blocks network calls until certificates exist. Verify go_bkk_hu.pem is imported.

**"OutOfMemoryError during timetable parse"**: Adjust batchSize in MapViewModel.fetchTimetable() (currently 50k). Lower for devices with <512MB RAM.

**"Navigation loop between auth and map"**: Fixed via popUpTo in navigation graph. Auth fragment is removed from back stack when navigating to map.

**"Stream Closed error during cert import"**: Fixed by reading InputStream to ByteArray in Fragment before passing to ViewModel async operation.

**"Progress jumping backwards during import"**: Fixed by extracting all files first, then parsing sequentially. No per-file extraction progress.
