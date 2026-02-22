# Umbra - Architecture

## Overview

Modular Android app architecture with **shared modules** and **feature modules**.

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

> **Future/Unplanned:** `:wearos`, `:tv`, and `:auto` modules are not in scope for initial development. A terminal on a watch or in a car is not a viable product. These may be explored after the core mobile experience ships, if at all.

## Current State vs. Target State

**Current State:** The codebase contains only the `:app` module with a hello-world JNI stub. No Compose UI, no Koin DI, no Room database, and no native terminal engine. The module structure described below is the **target architecture**.

**Target State:** The full multi-module architecture described in this document, with a working libghostty-vt integration, SSH/Mosh networking, and Vulkan rendering pipeline.

Development will incrementally build toward the target state, starting with the Phase 0 libghostty spike (see `docs/PRD.md`).

---

## What is a Feature?

A **feature** is a complete user journey or business capability, not just a single screen. This distinction is crucial for creating a clean and maintainable structure.

### Good Features (Business Capabilities)

- **sessions**: Full session lifecycle (create, switch, resize, close terminal sessions).
- **hosts**: Connection profile management (SSH hosts, credentials, jump hosts).
- **settings**: App and terminal preferences (themes, fonts, key bindings).

### Poor Features (Just Screens)

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
├── terminal/                 # Native terminal engine (NDK/C++)
│   ├── src/main/cpp/         # C/C++ source (JNI glue, Vulkan, etc.)
│   │   ├── jni_bridge.cpp    # JNI entry points (see Terminal Internals below)
│   │   ├── vulkan_renderer/  # Vulkan surface, pipeline, glyph atlas
│   │   ├── terminal_engine/  # TerminalEngine C interface (wraps libghostty or fallback)
│   │   └── networking/       # libssh2 integration, Mosh protocol
│   └── src/main/kotlin/      # Kotlin JNI bindings & TerminalEngine interface
│       └── com/yourpackage/terminal/
│           ├── TerminalEngine.kt     # Abstract interface for VT backends
│           ├── GhosttyEngine.kt      # libghostty-vt implementation
│           ├── SessionManager.kt     # Session state machine (see PRD Section 8)
│           └── NativeBridge.kt       # JNI external function declarations
│
├── core/                     # Unified core module
│   └── src/main/kotlin/com/yourpackage/core/
│       ├── common/           # Pure Kotlin utilities, Result class, extensions
│       ├── data/             # ALL repositories, DAOs, Room setup
│       │   ├── local/        # Room database, DAOs, entities
│       │   └── repository/   # Repository implementations
│       ├── domain/           # ALL domain models and use cases
│       │   ├── model/        # Business models (Host, TerminalProfile, KnownHost, etc.)
│       │   ├── repository/   # Repository interfaces
│       │   └── usecase/      # Use cases
│       ├── ui/               # Shared design system
│       │   ├── theme/        # App theme, colors, typography
│       │   ├── component/    # Reusable UI components
│       │   └── util/         # UI utilities
│       └── di/               # Core infrastructure DI (repositories, database)
│
└── features/
    ├── sessions/             # Session management feature (presentation only)
    │   ├── build.gradle.kts
    │   └── src/main/kotlin/com/yourpackage/features/sessions/
    │       ├── di/
    │       │   └── SessionsModule.kt # DI for the ViewModel
    │       ├── SessionsViewModel.kt  # ViewModel for session lifecycle
    │       └── SessionsScreen.kt     # Terminal display + session switching UI
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

**`:app` Module**: Uses the Navigation 3 library (`androidx.navigation3`) to handle adaptive layouts with scenes, a savable back stack with keys, and a central `NavDisplay`.

The feature modules provide `@Composable` screens. The `:app` module is responsible for calling those screens using the navigation library.

## Why This Works Well

**Incremental Build Caching**: Each feature module is cached independently. Editing one feature doesn't recompile the others.

**Multi-platform Ready**: Feature modules are platform-agnostic and can be shared if additional platform modules are added later.

**Clean Dependencies**: Feature modules cannot depend on each other, only on `:core` and `:terminal`. This prevents your project from becoming a "ball of mud."

**Easy Scaffolding**: New features just need a directory with a `build.gradle.kts`. The wildcard include handles registration automatically.

## The Dependency Flow

The fundamental principle is strictly enforced. The dependency direction is always:

```
app → features:* → core
                  → terminal
```

## Example Module Dependencies

```kotlin
// In app/build.gradle.kts
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
    // implementation(project(":features:hosts")) // This breaks isolation
}

// In core/build.gradle.kts
dependencies {
    // Core has no dependency on other project modules
    // Only external libraries (Room, etc.)
}
```

## Native Architecture

Umbra uses a Dual-Plane architecture. The **Data Plane** (native C/C++) handles rendering and networking. The **Control Plane** (Kotlin/JNI) handles UI signals and metadata. See `docs/PRD.md` for full details.

## Tech Stack

- **UI**: Jetpack Compose + Material 3
- **Dependency Injection**: Koin
- **Database**: Room
- **Navigation**: Navigation 3 (`androidx.navigation3`)
- **Terminal Engine**: libghostty-vt (via NDK), behind `TerminalEngine` interface (fallback: libvterm)
- **Rendering**: Vulkan (native NDK)
- **Font Stack**: HarfBuzz (shaping) + FreeType (rasterization)
- **SSH**: libssh2
- **Mosh**: Clean-room Kotlin/Native implementation (see PRD Section 4)
- **Build (Native)**: CMake (NDK native compilation)
- **Platforms**: Mobile Android

## Benefits

- **Faster Incremental Builds**: Gradle caches unchanged feature modules independently
- **Multi-platform Ready**: Features are platform-agnostic if additional platforms are added later
- **Solo Development Friendly**: Wildcard includes reduce boilerplate for new features
- **Terminal Backend Flexibility**: `TerminalEngine` interface allows swapping VT backends without affecting the rest of the app
- **Feature Isolation**: Features can't depend on each other, keeping architecture clean

## TerminalEngine Interface

The `:terminal` module defines a `TerminalEngine` interface that abstracts the VT backend. This is the critical decoupling point that allows swapping libghostty for a fallback (libvterm, custom parser) if cross-compilation fails.

```kotlin
interface TerminalEngine {
    fun initialize(config: TerminalConfig): Boolean
    fun processInput(data: ByteArray)
    fun resize(cols: Int, rows: Int)
    fun getCell(row: Int, col: Int): Cell
    fun getDirtyCells(): List<CellUpdate>
    fun destroy()
}
```

`GhosttyEngine` implements this interface by calling into libghostty-vt via JNI. A `LibvtermEngine` fallback can be built against the same interface.

## Terminal Module Internals

**JNI Entry Points** (`jni_bridge.cpp`):

- `nativeCreateSession(config)` — Allocates a VT state machine and Vulkan surface.
- `nativeProcessInput(sessionId, bytes)` — Feeds raw bytes from SSH/Mosh into the VT.
- `nativeResize(sessionId, cols, rows)` — Triggers reflow in the VT and Vulkan viewport resize.
- `nativeGetSessionState(sessionId)` — Returns batched metadata (title, cursor pos, bell flag) for the Control Plane.
- `nativeDestroy(sessionId)` — Tears down VT state, frees Vulkan resources.

**Thread Model:**

- **Main thread (Kotlin):** UI rendering, gesture handling, IME input.
- **Render thread (native):** Blocks on `pthread_cond_wait`, wakes on dirty cells, submits Vulkan draw calls. One thread per session.
- **Network thread (native):** Reads from SSH/Mosh socket, feeds bytes to VT. One thread per session.
- **JNI bridge calls** happen on the main thread for input events and on a dedicated metadata polling thread for batched state updates.

**Vulkan Surface Lifecycle:**

1. `SurfaceView.surfaceCreated` → JNI call to create `VkSurfaceKHR` + swapchain.
2. `SurfaceView.surfaceChanged` → JNI call to recreate swapchain with new dimensions.
3. `SurfaceView.surfaceDestroyed` → JNI call to release Vulkan resources. Render thread pauses.
4. On app resume → Surface is recreated; render thread resumes from the last VT state.

## Getting Started

1. **Phase 0 spike**: Prove libghostty cross-compiles for Android NDK (see PRD)
2. **Create core module**: Build `:core` with Room database and domain models
3. **Create terminal module**: Build `:terminal` with `TerminalEngine` interface and JNI bridge
4. **Create first feature**: Add `features/sessions/` directory with `build.gradle.kts`
5. **Add wildcard include**: Set up auto-registration in `settings.gradle.kts`
6. **Platform setup**: Implement `:app` module with Navigation 3
7. **Iterate**: Add more feature modules as needed, each containing only presentation layer
