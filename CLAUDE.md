# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build Android app
./gradlew :composeApp:assembleDebug

# Build iOS framework (requires macOS)
./gradlew :composeApp:linkDebugFrameworkIosArm64

# Run all checks
./gradlew build

# Clean build
./gradlew clean

# Sync Gradle (useful after dependency changes)
./gradlew --refresh-dependencies
```

## Architecture Overview

This is a **Kotlin Multiplatform** (KMP) project targeting **Android** and **iOS** using **Compose Multiplatform** for UI.

### Module Structure

- **`:composeApp`** - Main application entry point, contains platform-specific code and DI setup
- **`:navigation`** - Centralized navigation routes and graph definitions
- **`:platform`** - Platform abstractions (expect/actual declarations for platform-specific APIs)
- **`:backend-kmp`** - Business logic services for order calculations and tools
- **`:mapController`** - Map integration abstractions

**Core modules (`core/`):**
- `remote` - Ktor HTTP client setup, API configuration, network interceptors
- `database` - Room database setup and DAOs
- `storage` - Key-value storage (KVault for secure, MultiplatformSettings for preferences)
- `store` - State/data stores accessible across features
- `analytics` - Firebase Analytics wrapper
- `connection` - Network connectivity monitoring
- `datetime` - Date/time utilities

**Data layer (`data/`):**
- `model` - Data classes, DTOs, domain models (kotlinx.serialization)
- `repository` - Repository implementations connecting remote and local data sources

**Feature modules (`features/`):**
Each feature is self-contained with its own: screens, ViewModels, DI module, navigation graph
- `auth`, `home`, `profile`, `orders`, `newOrder`, `employees`, `prices`, `payment`, `company`, `warehouse`, `downloader`, `dillersLoc`

**UI modules (`ui/`):**
- `designsystem` - Theme, colors, typography, common styles
- `component` - Reusable UI components

### Key Patterns

**Dependency Injection:** Koin - each module exposes a Koin module (e.g., `authModule()`) aggregated in `Koin.common.kt`

**State Management:** MVVM with:
- `StateFlow` for UI state
- `SharedFlow` for one-time effects
- ViewModels extend `androidx.lifecycle.ViewModel` and implement `KoinComponent`
- Event/State pattern: ViewModels expose `onEvent(Event)` function and `uiState: StateFlow<State>`

**Navigation:** Jetpack Compose Navigation with type-safe routes defined in `:navigation` module

**Networking:** Ktor client with:
- `BuildConfig.API_URL` for production
- `BuildConfig.DEV_API_URL` for development
- Chucker for debug network inspection (Android)

**Platform-specific code:** Uses Kotlin's `expect`/`actual` mechanism. Common code in `commonMain`, platform implementations in `androidMain`/`iosMain`

## Code Style Rules

- Never use fully qualified class names (FQN) inline — always add an `import` and use the short name
- Correct: `import com.example.Foo` then use `Foo`
- Incorrect: `com.example.Foo()` inline in code

## Uzbek Language Rules

When writing Uzbek text in string resources or UI:
- Use **modifier letter turned comma** (`ʻ` - U+02BB) instead of apostrophe (`'`)
- Correct: `Oʻzbekiston`, `Qoʻshish`, `Oʻchirish`
- Incorrect: `O'zbekiston`, `Qo'shish`, `O'chirish`
- In XML strings, use `ʻ` directly (not `\'` escape sequences)
