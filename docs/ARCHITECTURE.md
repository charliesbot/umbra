# Umbra - Multi-Platform Architecture

## Overview

Multi-platform Android app architecture with **shared modules** and **multiple feature/platform modules**.

**Shared modules:**

- `:core` - Business logic, data, shared UI
- `:terminal` - Native NDK code (libghostty-vt, Vulkan renderer, JNI bridge, networking)

**Feature modules:**

- `:features:sessions` - Terminal session management (create, switch, close sessions)
- `:features:hosts` - SSH host/connection profile management
- `:features:settings` - App and terminal preferences, theme selection
- (add more as needed)

**Platform modules:**

- `:app` - Mobile Android
- `:wearos` - WearOS
- `:tv` - Android TV (optional)
- `:auto` - Android Auto (optional)

## What is a Feature?

A **feature** is a complete user journey or business capability, not just a single screen. This distinction is crucial for creating a clean and maintainable structure.

### ✅ Good Features (Business Capabilities)

- **sessions**: Full session lifecycle (create, switch, resize, close terminal sessions).
- **hosts**: Connection profile management (SSH hosts, credentials, jump hosts).
- **settings**: App and terminal preferences (themes, fonts, key bindings).

### ❌ Poor Features (Just Screens)

- **login-screen**: Too granular. This should be part of a broader feature.
- **theme-picker**: Should be part of the `settings` feature.

By creating feature modules for business capabilities, you create cohesive and self-contained units that are easier to manage, test, and benefit from incremental build caching.

## Module Structure

```
umbra/
├── app/                      # Mobile Android app module
│   └── src/main/kotlin/com/yourpackage/
│       ├── MainActivity.kt
│       ├── di/
│       │   └── AppModule.kt          # Loads all DI modules (core + features)
│       └── navigation/               # Navigation 3 setup
│           ├── AppNavigation.kt      # NavDisplay, entryProvider, sceneStrategy
│           └── NavigationRoutes.kt   # Defines all serializable NavKey objects
│
├── wearos/                   # WearOS app module
│   └── src/main/kotlin/com/yourpackage/wear/
│       ├── MainActivity.kt
│       ├── di/
│       │   └── WearAppModule.kt      # Loads DI modules for wear platform
│       └── navigation/               # WearOS-specific navigation
│           ├── WearNavigation.kt     # Contains SwipeDismissableNavHost
│           └── WearRoutes.kt         # Sealed class routes for type safety
│
├── terminal/                 # Native terminal engine (NDK/C++)
│   ├── src/main/cpp/         # C/C++ source (JNI glue, Vulkan, etc.)
│   └── src/main/kotlin/      # Kotlin JNI bindings
│
├── core/                     # Unified core module
│   └── src/main/kotlin/com/yourpackage/core/
│       ├── common/           # Pure Kotlin utilities, Result class, extensions
│       ├── data/             # ALL repositories, DAOs, network APIs, Room/Retrofit setup
│       │   ├── local/        # Room database, DAOs, entities
│       │   ├── remote/       # Retrofit APIs, DTOs, network layer
│       │   └── repository/   # Repository implementations
│       ├── domain/           # ALL domain models and use cases
│       │   ├── model/        # Business models (User, etc.)
│       │   ├── repository/   # Repository interfaces
│       │   └── usecase/      # Use cases
│       ├── ui/               # Shared design system
│       │   ├── theme/        # App theme, colors, typography
│       │   ├── component/    # Reusable UI components
│       │   └── util/         # UI utilities
│       └── di/               # Core infrastructure DI (repositories, network, database)
│
└── features/
    ├── sessions/             # Session management feature (presentation only)
    │   ├── build.gradle.kts
    │   └── src/main/kotlin/com/yourpackage/features/sessions/
    │       ├── di/
    │       │   └── SessionsModule.kt # DI for the ViewModel
    │       ├── SessionsViewModel.kt  # SHARED ViewModel for mobile & wear
    │       └── SessionsScreen.kt     # Mobile or shared UI screen
    │
    ├── hosts/                # Host/connection profile management
    │   ├── build.gradle.kts
    │   └── src/main/kotlin/com/yourpackage/features/hosts/
    │       ├── di/
    │       │   └── HostsModule.kt
    │       ├── HostsViewModel.kt
    │       └── HostsScreen.kt
    │
    └── settings/             # App and terminal preferences
        ├── build.gradle.kts
        └── src/main/kotlin/com/yourpackage/features/settings/
            └── ...
```

## Auto-Registering Feature Modules

To avoid manually adding each feature to `settings.gradle.kts`, use a wildcard include:

```kotlin
// settings.gradle.kts
file("features").listFiles()?.filter { it.isDirectory }?.forEach {
    include(":features:${it.name}")
}
```

This automatically picks up any subdirectory under `features/` as a module.

## Platform-Specific Navigation

A key strength of this architecture is how it isolates platform-specific implementations. Navigation is a perfect example of this.

**`:app` Module**: Uses the Navigation 3 library (`androidx.navigation3`) to handle adaptive layouts with scenes, a savable back stack with keys, and a central `NavDisplay`.

**`:wearos` Module**: Uses the specialized Wear Compose Navigation library (`androidx.wear.compose:compose-navigation`), which provides components tailored for watches, like the `SwipeDismissableNavHost`.

The feature modules simply provide the `@Composable` screens. The `:app` and `:wearos` modules are independently responsible for calling those screens using the correct navigation library for their platform.

## Why This Works Well

**Incremental Build Caching**: Each feature module is cached independently. Editing one feature doesn't recompile the others.

**Multi-platform Ready**: All platform modules (`:app`, `:wearos`, `:tv`, `:auto`, etc.) can share feature code from day one.

**Clean Dependencies**: Feature modules cannot depend on each other, only on `:core` and `:terminal`. This prevents your project from becoming a "ball of mud."

**Easy Scaffolding**: New features just need a directory with a `build.gradle.kts`. The wildcard include handles registration automatically.

## The Dependency Flow

The fundamental principle is strictly enforced. The dependency direction is always:

```
app/wearos → features:* → core
                         → terminal
```

## Example Module Dependencies

```kotlin
// In app/build.gradle.kts and wearos/build.gradle.kts
dependencies {
    implementation(project(":core"))
    implementation(project(":terminal"))
    implementation(project(":features:sessions"))
    implementation(project(":features:hosts"))
    implementation(project(":features:settings"))
}

// In features/sessions/build.gradle.kts (and other feature modules)
dependencies {
    // Feature modules depend on :core and :terminal
    implementation(project(":core"))
    implementation(project(":terminal"))

    // NO dependency on other feature modules allowed!
    // ❌ implementation(project(":features:hosts")) // This breaks isolation

    // NO dependency on app/wearos modules allowed!
    // ❌ implementation(project(":app")) // This would cause circular dependency
}

// In core/build.gradle.kts
dependencies {
    // Core has no dependency on other project modules
    // Only external libraries (Retrofit, Room, etc.)
}
```

## Native Architecture

Umbra uses a Dual-Plane architecture. The **Data Plane** (native C/C++) handles rendering and networking. The **Control Plane** (Kotlin/JNI) handles UI signals and metadata. See `docs/PRD.md` for full details.

## Tech Stack

- **Dependency Injection**: Koin
- **Networking**: Retrofit
- **Database**: Room
- **Navigation**:
  - Mobile: Navigation 3 (`androidx.navigation3`)
  - WearOS: Wear Compose Navigation (`androidx.wear.compose:compose-navigation`)
- **Terminal Engine**: libghostty-vt (via NDK)
- **Rendering**: Vulkan (native NDK)
- **Font Stack**: HarfBuzz (shaping) + FreeType (rasterization)
- **Networking (SSH)**: libssh2 (SSH), Mosh (optional)
- **Build (Native)**: CMake (NDK native compilation)
- **Platforms**: Mobile Android + WearOS

## Benefits

- **Faster Incremental Builds**: Gradle caches unchanged feature modules independently
- **Multi-platform Code Sharing**: Features work across all platforms from day one
- **Solo Development Friendly**: Wildcard includes reduce boilerplate for new features
- **Platform Flexibility**: Each platform uses optimal navigation solution
- **Feature Isolation**: Features can't depend on each other, keeping architecture clean

## Getting Started

1. **Create core module**: Build `:core` with all shared logic
2. **Create terminal module**: Build `:terminal` with NDK/native code
3. **Create first feature**: Add `features/sessions/` directory with `build.gradle.kts`
4. **Add wildcard include**: Set up auto-registration in `settings.gradle.kts`
5. **Platform setup**: Implement platform modules (`:app`, `:wearos`) with appropriate navigation
6. **Iterate**: Add more feature modules as needed, each containing only presentation layer
