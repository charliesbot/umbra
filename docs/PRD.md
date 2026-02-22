# Design Doc: Umbra-Android (libghostty-vt Wrapper)

## 1. Architectural Philosophy: The Dual-Plane Model

To ensure 120Hz responsiveness and maximum battery efficiency, Umbra strictly separates UI logic from the high-performance terminal engine.

**Data Plane (Native-Only):**

Includes libghostty-vt, Vulkan Renderer, Glyph Atlas, Scrollback Buffer, and Networking (libssh2/Mosh).

> **Rule:** Character grid data and pixel buffers never cross the JNI boundary.

**Control Plane (JNI-Bridge):**

Includes Session Metadata (Title, Bell), batched Cursor updates, Scroll Position, Resizes, and Input Events.

> **Rule:** JNI is used only for high-level signaling. Metadata is batched to prevent frequent JNI overhead.

---

## 2. Rendering & Font Engine (Data Plane)

**Vulkan Pipeline:** Native NDK implementation binding SurfaceView to `VkSurfaceKHR`.

**Font Stack:** HarfBuzz (shaping) + FreeType (rasterization).

**Scrollback Buffer:** Managed entirely in native memory within the VT-core. The Kotlin layer signals the visible window offset (Scroll Position) via the Control Plane.

**Variable Rate Rendering:** The render thread blocks on a `pthread_cond_wait`. The VT-core triggers `pthread_cond_signal` only when a cell is marked "dirty" or the scroll offset changes.

---

## 3. Input System (The Interface)

Input handling is split to ensure desktop-class responsiveness and mobile-first compatibility:

**Soft Keyboard (IME):** Implement a transparent `InputConnection` in Kotlin. Use `InputType.TYPE_NULL` (or equivalent flags) to explicitly disable autocorrect, autocapitalize, and spell-check.

**Virtual Accessory Bar:** A Compose-based row for modifiers (Ctrl, Alt, Esc, Tab, Fn). Tapping these toggles a bitmask sent to the native layer.

**Physical Keyboards:** Intercept events in Kotlin via `dispatchKeyEvent` before they hit the IME layer. Once captured, pass the key codes directly to the native layer for mapping to Ghostty sequences.

**Gesture Engine:** Swipe gestures for scrolling and session switching are processed in Kotlin. Vertical scroll delta is translated into a scroll-offset signal sent to the native data plane.

---

## 4. Networking: SSH & Mosh

**SSH Stack:** libssh2 for transport. Authentication via Android Keystore (hardware-backed Ed25519/ECDSA).

**Mosh Strategy:** Link against the official Mosh C++ core.

**Dependencies:** Cross-compile libprotobuf, libcrypto (OpenSSL), and ncurses as static libraries for the NDK layer.

**Research Task:** Adapt patched build scripts from `rjyo/mosh-android` or Termux.

**Persistence:** A Foreground Service manages the socket lifecycle to prevent OS-level termination.

---

## 5. Storage & UI (Control Plane)

**Host Management:** Use Android Room (SQLite) for connection profiles (Port, User, Jump Hosts, ProxyCommand).

**Theme Engine:** Kotlin-based parser supporting base16, iTerm2, and Ghostty formats. Color values are passed as a single struct to the Native layer at session init.

**Multi-ABI:** Ship `.so` binaries for `arm64-v8a` and `x86_64`.

---

## 6. JNI Bridge Design & Constraints

**Implementation:** Thin C++ JNI glue calling the Zig-compiled libghostty-vt.

**Decision Deadline:** Week 1 resolution for Pure Zig JNI vs. C++ Glue.

**Metadata Batching:** Signals like cursor movement and scroll position are batched into the "dirty state" notification.

**16KB Pages:** Mandatory linker flags for Pixel 9+ compatibility:

```
-Wl,-z,common-page-size=16384 -Wl,-z,max-page-size=16384
```

---

## 7. Development Phases

### Phase 1: Parallel Foundations

- **Native:** Compile libghostty-vt and baseline dependencies (libssh2) for aarch64.
- **Kotlin:** Build the Room DB for Host management and the Theme parser.

### Phase 2: The Native Pipeline

Implement the Vulkan SurfaceView bridge. Integrate HarfBuzz/FreeType and allocate the native scrollback buffer.

### Phase 3: Integration

Establish the first SSH session. Bridge batched input (IME and `dispatchKeyEvent`) from Kotlin to the Native VT.

### Phase 4: Persistence & Mosh

Implement the Foreground Service and compile the Mosh dependency chain (protobuf, ncurses, crypto).

> **Contingency:** If Mosh dependency resolution exceeds 2 weeks of effort, ship Phase 4 as SSH-only and defer Mosh to a later milestone.

### Phase 5: Polish

Add SPIR-V shaders and finalize the Virtual Accessory Bar UX.

---

## 8. Competitive Edge

- **Professional-Grade Security:** SSH keys never leave the hardware-backed TEE (Android Keystore).
- **Ultra-Responsive Performance:** Near-zero JVM overhead through a native-to-native rendering pipeline (90Hz/120Hz).
- **Rock-Solid Session Persistence:** Native networking managed by a Foreground Service.
- **Modern Android Standard:** Native 16KB page alignment and Scoped Storage compliance.
