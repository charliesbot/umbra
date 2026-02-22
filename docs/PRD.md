# Design Doc: Umbra-Android (libghostty-vt Wrapper)

## 1. Architectural Philosophy: The Dual-Plane Model

To ensure display refresh rate-matched rendering and maximum battery efficiency, Umbra strictly separates UI logic from the high-performance terminal engine.

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

**Display Refresh Rate-Matched Rendering:** The render thread blocks on a `pthread_cond_wait`. The VT-core triggers `pthread_cond_signal` only when a cell is marked "dirty" or the scroll offset changes. This is a power-saving mechanism — under idle conditions the GPU does zero work.

**Frame Dropping Under High Throughput:** When the VT-core produces output faster than the display refresh rate (e.g., `cat /dev/urandom`), the renderer batches dirty cells and drops intermediate frames. Only the most recent terminal state is presented each vsync. This prevents GPU queue saturation and keeps the UI thread responsive.

---

## 3. Input System (The Interface)

Input handling is split to ensure desktop-class responsiveness and mobile-first compatibility:

**Soft Keyboard (IME):** Implement a transparent `InputConnection` in Kotlin. Use `InputType.TYPE_NULL` (or equivalent flags) to explicitly disable autocorrect, autocapitalize, and spell-check.

**Virtual Accessory Bar:** A Compose-based row for modifiers (Ctrl, Alt, Esc, Tab, Fn). Tapping these toggles a bitmask sent to the native layer.

**Physical Keyboards:** Intercept events in Kotlin via `dispatchKeyEvent` before they hit the IME layer. Once captured, pass the key codes directly to the native layer for mapping to Ghostty sequences.

**Gesture Engine:**

- **Pan Scroll:** Vertical swipe gestures for scrollback scrolling. Horizontal swipe for session switching. Processed in Kotlin, scroll delta sent to native data plane.
- **Pinch-to-Zoom:** Two-finger pinch to adjust font size in real-time. The new font size is persisted to the active terminal profile and triggers a glyph atlas rebuild in the native layer.
- **Long-Press Selection:** Long-press initiates text selection mode with haptic feedback (`HapticFeedbackConstants.LONG_PRESS`). Drag handles appear for adjusting selection bounds.
- **Two-Finger Paste:** Two-finger tap pastes from the system clipboard into the active terminal session.
- **Haptic Feedback:** Selection start, handle drag, and copy confirmation all trigger appropriate haptic patterns.

**Text Selection & Clipboard:**

- Long-press to begin selection; drag handles to adjust start/end positions.
- Selection highlights rendered as an overlay in the Vulkan pipeline (translucent rectangle behind selected cells).
- Copy action writes to the Android system clipboard via `ClipboardManager`.
- Paste reads from the system clipboard and injects content as terminal input.
- **OSC 52 Support:** The terminal engine handles OSC 52 clipboard escape sequences, allowing remote applications to read/write the system clipboard (with user consent prompt on first use per session).

---

## 4. Networking: SSH & Mosh

**SSH Stack:** libssh2 for transport. Authentication via Android Keystore (hardware-backed Ed25519/ECDSA).

**Mosh Strategy: Clean-Room Kotlin/Native Reimplementation**

> **GPL Risk:** The official Mosh C++ core is GPL-3.0. Linking it statically or dynamically contaminates the entire binary with GPL obligations. This is incompatible with a proprietary or permissive-licensed application.

Instead of linking the official Mosh client, Umbra will implement the Mosh client protocol from scratch in Kotlin/Native:

- **AES-128-OCB3** via `javax.crypto` (Android platform) or BoringSSL (NDK). No OpenSSL dependency.
- **Protobuf serialization** hand-coded for the Mosh wire format. No libprotobuf dependency.
- **STUN NAT detection** for UDP hole-punching and traversal.
- **State Sync Protocol (SSP)** with terminal state diffs — the core of Mosh's responsiveness.
- **Network roaming** support for WiFi-to-cellular transitions without session loss.

This eliminates three heavy cross-compiled dependencies (libprotobuf, libcrypto/OpenSSL, ncurses) and all GPL risk. ncurses is not needed — it is only used by `mosh-server` for terminal capability detection, not by the client protocol.

**Persistence:** A Foreground Service manages the socket lifecycle to prevent OS-level termination.

**Connection Health Monitoring:** The native layer tracks RTT, packet loss, and last-acknowledged sequence number. A heartbeat signal crosses the JNI bridge at 1Hz to update the UI with connection quality indicators (green/yellow/red). If no server response is received for 15 seconds, the UI shows "connection lost" with a reconnect option.

---

## 5. Storage & UI (Control Plane)

**Host Management:** Use Android Room (SQLite) for connection profiles (Port, User, Jump Hosts, ProxyCommand).

**Terminal Profile Model:** Each connection can be associated with a terminal profile that specifies:

- Font family and size
- Color theme (reference to a theme ID)
- Scrollback buffer size (lines, default: 10,000)
- Cursor style (block, underline, bar) and blink behavior
- Bell behavior (audible, visual flash, vibrate, none)
- A "Default" profile is applied to connections with no explicit profile.

**Theme Engine:** Kotlin-based parser supporting base16, iTerm2, and Ghostty formats. Color values are passed as a single struct to the Native layer at session init.

**Bundled Themes:** Umbra ships with 8 default themes to ensure a polished out-of-box experience:

- Light: Solarized Light, One Light, GitHub Light
- Dark: Solarized Dark, Dracula, Nord, Gruvbox Dark, One Dark

Users can import additional themes via the three supported formats.

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

### Phase 0: libghostty Cross-Compilation Spike (1 Week Timebox)

Prove that libghostty-vt cross-compiles for `aarch64-linux-android` via Zig's NDK target. Deliverable: a minimal native binary that initializes a VT state machine and renders one frame to a test surface.

> **Fallback:** If the spike fails (Zig cross-compilation issues, POSIX/termios desktop-only assumptions in libghostty), fall back to libvterm or a custom VT parser behind the `TerminalEngine` interface (see ARCHITECTURE.md). The rest of the architecture is unaffected because all VT interaction is behind this abstraction.

### Phase 1: Parallel Foundations

- **Native:** Compile libghostty-vt and baseline dependencies (libssh2) for aarch64.
- **Kotlin:** Build the Room DB for Host management, Theme parser, and terminal profile model.
- **Interface:** Define the `TerminalEngine` abstraction in `:terminal` to decouple VT backend from the rest of the app.

### Phase 2: The Native Pipeline

Implement the Vulkan SurfaceView bridge. Integrate HarfBuzz/FreeType and allocate the native scrollback buffer. Implement text selection overlay rendering.

### Phase 3: Integration

Establish the first SSH session. Bridge batched input (IME and `dispatchKeyEvent`) from Kotlin to the Native VT. Implement text selection gestures, clipboard integration, and OSC 52 support.

### Phase 4: Persistence & Mosh

Implement the Foreground Service, session state machine, and auto-reconnection logic. Begin clean-room Mosh client protocol implementation (AES-128-OCB3, SSP, STUN).

> **Contingency:** If the clean-room Mosh implementation exceeds the phase budget, ship Phase 4 as SSH-only with session persistence. Mosh defers to a later milestone.

### Phase 5: Polish

Add SPIR-V shaders, finalize the Virtual Accessory Bar UX, implement pinch-to-zoom, bundle default themes, and conduct APK size optimization.

---

## 8. Session Lifecycle

**Session States:** Every terminal session transitions through a well-defined state machine:

```
CONNECTING → ACTIVE → SUSPENDED → DISCONNECTED → DEAD
                ↑         │              │
                └─────────┘              │
              (auto-resume)              │
                ↑                        │
                └────────────────────────┘
                    (manual reconnect)
```

- **CONNECTING:** SSH handshake or Mosh key exchange in progress.
- **ACTIVE:** Terminal data flowing. Connection health is monitored.
- **SUSPENDED:** App backgrounded or network lost. The Foreground Service keeps the socket alive. For Mosh, the client continues sending keepalives.
- **DISCONNECTED:** No server response for the staleness threshold (default: 550 seconds for Mosh, 30 seconds for SSH). UI shows "Disconnected" with a reconnect action.
- **DEAD:** User explicitly closes the session, or reconnection fails after max retries (3 attempts with exponential backoff).

**Auto-Resume:** When the app returns to foreground, SUSPENDED sessions automatically attempt to resume. For SSH, the existing TCP connection is tested; if dead, a new connection is established. For Mosh, the UDP session resumes transparently.

**Process Death Recovery:** Session metadata (host, port, user, session state, Mosh key if applicable) is persisted to Room. If the OS kills the Foreground Service, the app restores sessions on next launch and attempts reconnection.

---

## 9. Security Model

**Key Generation:**

- Ed25519 (default, recommended) and ECDSA-P256 keys generated inside Android Keystore (hardware-backed TEE on supported devices).
- RSA-4096 available as a fallback for legacy servers.
- Private keys are non-exportable by default. An explicit "Export Public Key" action is provided for copying the public key to clipboard or sharing via Android's share sheet.

**Key Import:**

- Import OpenSSH PEM and PKCS#8 private keys from the filesystem (via Android Storage Access Framework / file picker).
- Imported keys are stored in Android Keystore with hardware-backing when available.
- Passphrase-protected keys are decrypted at import time; the passphrase is not stored.

**Known Hosts:**

- On first connection, the server's host key fingerprint is displayed to the user for verification (TOFU — Trust On First Use).
- Accepted host keys are stored in a Room table (`known_hosts`).
- On subsequent connections, the presented host key is compared against the stored fingerprint. Mismatches trigger a prominent warning with options to abort, accept once, or update the stored key.

**Agent Forwarding:** SSH agent forwarding is supported but disabled by default. When enabled per-connection, Umbra presents a per-request consent dialog showing which remote host is requesting the key operation.

**Constant-Time Operations:** All cryptographic comparisons (host key verification, HMAC checks) use constant-time comparison functions to prevent timing side-channel attacks.

---

## 10. Unicode Handling

- **CJK Double-Width Characters:** The renderer allocates two cell widths for characters with East Asian Width property "Wide" or "Fullwidth" (Unicode UAX #11).
- **Emoji:** Emoji sequences (including ZWJ sequences and skin tone modifiers) are rendered via HarfBuzz shaping with fallback to system emoji font.
- **Combining Characters:** Combining marks are rendered on top of their base character within the same cell.
- **Grapheme Cluster Segmentation:** Cell width calculations follow Unicode UAX #29 grapheme cluster boundaries.

---

## 11. Error Handling Strategy

Errors are surfaced to the user through a consistent pattern:

| Error Type                             | User-Facing Behavior                                                                                                            |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **Network timeout**                    | Snackbar with "Connection timed out. Retry?" action. Session moves to DISCONNECTED.                                             |
| **Auth failure** (wrong key, denied)   | Dialog with error detail + option to select a different key or edit credentials.                                                |
| **Host key mismatch**                  | Full-screen warning explaining MITM risk. Options: abort, accept once, update stored key.                                       |
| **Host key first-use**                 | Dialog showing fingerprint (SHA-256) with accept/reject.                                                                        |
| **Mosh UDP blocked**                   | Snackbar: "UDP port unreachable. Falling back to SSH." Auto-fallback to SSH session.                                            |
| **Foreground Service killed**          | On next app launch: "Sessions were interrupted. Reconnect all?"                                                                 |
| **libghostty crash**                   | Native crash handler captures signal, logs stack trace. Session marked DEAD. Other sessions unaffected (process isolation TBD). |
| **Disk full** (scrollback, key import) | Toast with "Storage full" message. Graceful degradation (scrollback stops growing).                                             |

---

## 12. Licensing

**Umbra's License:** TBD (intended: permissive or proprietary).

**Dependency Licenses:**

| Dependency          | License            | Risk                                                    |
| ------------------- | ------------------ | ------------------------------------------------------- |
| libghostty-vt       | MIT                | None                                                    |
| libssh2             | BSD-3-Clause       | None                                                    |
| HarfBuzz            | MIT                | None                                                    |
| FreeType            | FTL / GPL-2 (dual) | Use FTL license — permissive, no contamination          |
| Vulkan (NDK)        | Apache-2.0         | None                                                    |
| Mosh (official C++) | **GPL-3.0**        | **Not used.** Clean-room reimplementation avoids GPL.   |
| OpenSSL / libcrypto | Apache-2.0 (3.x)   | **Not used.** Platform crypto or BoringSSL instead.     |
| libprotobuf         | BSD-3-Clause       | **Not used.** Hand-coded protobuf for Mosh wire format. |
| ncurses             | MIT-like (X11)     | **Not used.** Only needed by mosh-server, not client.   |

> **Key Decision:** By clean-room reimplementing the Mosh client protocol and avoiding OpenSSL/libprotobuf/ncurses, Umbra's native layer is entirely free of GPL dependencies.

---

## 13. APK Size Budget

**Target:** Under 25 MB total APK size (per ABI, via App Bundle).

**Estimated native library sizes (arm64-v8a):**

| Library                  | Estimated Size |
| ------------------------ | -------------- |
| libghostty-vt            | ~3-5 MB        |
| Vulkan shaders (SPIR-V)  | ~200 KB        |
| HarfBuzz                 | ~1.5 MB        |
| FreeType                 | ~800 KB        |
| libssh2                  | ~500 KB        |
| JNI glue + Mosh protocol | ~300 KB        |
| **Total native**         | **~6-8 MB**    |

**Kotlin/DEX + resources:** ~5-8 MB (Compose, Room, themes, assets).

**Mitigation if over budget:**

- Strip debug symbols (`-s` linker flag) — already standard for release builds.
- LTO (Link-Time Optimization) for native libraries.
- R8 full mode for Kotlin/DEX.
- Ship only `arm64-v8a` in the initial release; add `x86_64` for emulator support via App Bundle splits.

---

## 14. Competitive Edge

- **Professional-Grade Security:** SSH keys never leave the hardware-backed TEE (Android Keystore). Full known hosts verification and key management.
- **Ultra-Responsive Performance:** Near-zero JVM overhead through a native-to-native rendering pipeline, matched to display refresh rate.
- **Rock-Solid Session Persistence:** Native networking managed by a Foreground Service with automatic reconnection and session state machine.
- **Modern Android Standard:** Native 16KB page alignment and Scoped Storage compliance.
- **License-Clean Mosh:** Clean-room protocol implementation avoids GPL contamination.
- **Rich Theme Ecosystem:** 8 bundled themes + import from base16, iTerm2, and Ghostty formats.
