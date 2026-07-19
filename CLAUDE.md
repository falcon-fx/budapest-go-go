# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Budapest public transport (BKK Futár) Android application targeting legacy devices (minSdk 18, including BlackBerry 10 Passport via Android runtime). The app downloads and caches GTFS timetable data and real-time vehicle positions from the BKK Futár API.

**Key technical considerations:**
- **Legacy TLS support**: Custom SSL handling for API servers that use Hungarian certificate authorities not trusted on Android 4.3-5.x. SSL certificates bundled in `app/src/main/res/raw/` (eszigno_root.pem, eszigno_intermediate.pem, go_bkk_hu.pem) with composite TrustManager in AppModule.
- **TLS 1.2 backport**: Conscrypt security provider injected at app startup for TLS 1.2 support on Android 4.1-4.4.
- **MultiDex**: Enabled due to method count from Room, Retrofit, Hilt, and GTFS dependencies.
- **Large database operations**: GTFS timetable contains ~100k+ entities. BkkDatabase.fastClearAll() deletes in 10k row chunks with WAL checkpointing to avoid OOM.

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

**IMPORTANT FOR CLAUDE:** Do not run gradle build/install commands. Only run tests if they exist. All app building and manual testing is done by the developer.

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
./gradlew test --tests com.example.myapplication.ExampleUnitTest

# Run specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.ExampleInstrumentedTest
```

## Architecture

### Package Structure

```
com.example.myapplication/
├── BkkApp.kt                    # Application class (Hilt entry, Conscrypt setup, MultiDex)
├── MainActivity.kt              # Single activity with Navigation Component
├── data/
│   ├── AppModule.kt            # Hilt DI module (network, database, repos)
│   ├── api/
│   │   └── BkkApiService.kt    # Retrofit interface for BKK Futár API
│   ├── db/
│   │   ├── BkkDatabase.kt      # Room database with fastClearAll() chunked deletion
│   │   ├── dao/                # DAOs for timetable and vehicle data
│   │   ├── repo/               # Repository interfaces and implementations
│   │   ├── *Entity.kt          # Room entities (Route, Stop, Timetable, Trip, Vehicle)
│   │   └── RouteTypes.kt       # Enum for BKK route types (bus, tram, metro, etc.)
│   └── util/
│       ├── BkkTypeConverters.kt    # Room type converters
│       └── Tls12SocketFactory.kt   # TLS 1.2 wrapper for Android 4.1-5.0
└── ui/
    ├── auth/                   # Authentication fragment/ViewModel (API key management)
    ├── map/                    # Main map view with route/stop selection
    │   ├── MapFragment.kt
    │   ├── MapViewModel.kt     # Coordinates timetable fetch (batchSize=50k for memory efficiency)
    │   └── RoutesAdapter.kt    # RecyclerView adapter for route list
    └── Event.kt                # Single-event wrapper for LiveData
```

### Data Flow

1. **Timetable fetch**: MapViewModel triggers TimetableRepo.fetchAndStoreTimetable() which:
   - Downloads budapest_gtfs.zip from BKK static endpoint
   - Parses GTFS files (routes.txt, stops.txt, trips.txt, stop_times.txt)
   - Bulk inserts into Room in 50k batches
   - **PERFORMANCE ISSUE**: Process takes 30+ minutes even on newer devices - this is the primary refactor target

2. **Vehicle positions**: VehicleRepo downloads GTFS-RT VehiclePositions.pb protobuf (requires API key from AuthRepo/SharedPreferences)

3. **Repository pattern**: All data access through repo interfaces (TimetableRepo, VehicleRepo, AuthRepo) with Prod* implementations injected via Hilt

### Dependency Injection (Hilt)

- `@HiltAndroidApp` on BkkApp
- `@HiltViewModel` on all ViewModels
- AppModule provides: BkkDatabase, Repos, Retrofit/OkHttpClient with custom SSL, BkkApiService
- **OkHttpClient config**: 240s read timeout, 60s connect timeout (GTFS zip is ~60MB)

### Network Layer Details

**Custom SSL Trust Chain** (AppModule.buildSslFactoryWithBundledCAs):
- Composite X509TrustManager tries system trust store first, falls back to bundled CAs
- Required because eszigno.hu CA not in Android <5.0 trust store
- Tls12SocketFactory wraps SSLSocketFactory on API 16-21 to enable TLS 1.2

**API Endpoints** (base URL in strings.xml):
- Static GTFS: `/static/v1/public-gtfs/budapest_gtfs.zip` (no auth)
- Vehicle positions: `/query/v1/ws/gtfs-rt/full/VehiclePositions.pb?key={API_KEY}` (requires key)

## Development Notes

### When Modifying Database Schema

- Room version is always `1` with `.fallbackToDestructiveMigration()` - schema changes wipe data
- Large table operations must batch (see BkkDatabase.fastClearAll() for pattern)
- WAL checkpoint after deletions is critical on low-memory devices

### When Changing Network Code

- Test on both modern Android and emulated API 18-21 to verify SSL handshake
- If adding new HTTPS endpoints, verify CA trust chain compatibility
- Long timeouts (60s connect, 240s read) are intentional for slow devices/networks

### Adding New Features

- Use Hilt for DI - do not manually create repos/ViewModels
- Follow repository pattern - UI never directly calls Room DAOs or Retrofit services
- LiveData for UI updates, coroutines (viewModelScope) for async work
- Navigation Component for fragment transitions (see navigation graph in res/navigation/)

### API Key Management

- API key stored in SharedPreferences via AuthRepo
- Required only for vehicle position feed, not for timetable download
- Key entered via AuthFragment

## Testing Strategy

- Unit tests: Business logic in repos/ViewModels
- Instrumented tests: Database operations, SSL handshake verification on API 18-21
- Manual testing on BB10 Passport required for TLS/SSL validation (gradlew installPassport)

## Common Issues

**"SSLHandshakeException" on API 18-21**: Missing Conscrypt provider or bundled CA certificates. Verify BkkApp.attachBaseContext() installs Conscrypt and raw/ directory contains .pem files.

**"OutOfMemoryError during timetable parse"**: Adjust batchSize in MapViewModel.fetchTimetable() (currently 50k). Lower for devices with <512MB RAM.

**Database load extremely slow (30+ minutes)**: This is a known issue and the primary performance bottleneck. The current GTFS parsing and Room insertion strategy needs optimization. Consider: streaming inserts, pre-indexing optimization, or alternative storage strategies.
