# ♟ Chess Assistant — Android Overlay

Real-time move suggestions that draw a **green arrow** directly over your screen while you play on [Chess.com](https://www.chess.com) — in the app or in any browser.

---

## How it works

| Layer | Mechanism |
|---|---|
| **Board reading** | Android Accessibility Service traverses the view tree of Chess.com and parses piece content-descriptions (ARIA labels) |
| **Move engine** | Pure-Java negamax alpha-beta, depth 4, with piece-square tables |
| **Arrow overlay** | `SYSTEM_ALERT_WINDOW` foreground service draws a custom `ArrowOverlayView` on top of all apps |

---

## Requirements

- Android 7.0+ (API 24)
- Android Studio Hedgehog (2023.1.1) or later
- JDK 11+
- Chess.com app **or** Chrome / Firefox / Edge on Android

---

## Build from source

### 1. Clone

```bash
git clone https://github.com/<your-username>/ChessAssistant.git
cd ChessAssistant
```

### 2. Open in Android Studio

`File → Open → select the ChessAssistant folder`

Android Studio will sync Gradle automatically. If it doesn't:

```
View → Tool Windows → Gradle → click the sync elephant icon
```

### 3. Build & install

Connect your device (enable USB debugging in Developer Options) then:

```bash
./gradlew installDebug
```

Or press **▶ Run** in Android Studio.

---

## First-run setup (two permissions required)

### Permission 1 — Display over other apps

1. Open **Chess Assistant** on your device
2. Tap **▶ START ASSISTANT**
3. Android redirects you to `Settings → Apps → Chess Assistant → Display over other apps`
4. Toggle it **ON**, then press back
5. Tap **▶ START ASSISTANT** again — the floating panel appears

### Permission 2 — Accessibility Service

The app needs to read the board state from Chess.com's view hierarchy.

1. Tap **⚙ Open Accessibility Settings** inside the app  
   *or* go to `Settings → Accessibility → Installed apps`
2. Find **Chess Assistant** in the list
3. Tap it → toggle **Use Chess Assistant** to ON
4. Confirm the permission dialog ("Allow Chess Assistant to observe your actions…")

> **Why this permission?** The accessibility service reads piece labels (e.g. "White Pawn at e2") from Chess.com's UI. It observes only Chess.com and selected browsers — no other apps, no keylogging, no data leaves the device.

---

## Usage

1. Start Chess Assistant (floating panel appears in the top-left corner — drag it anywhere)
2. Open Chess.com in its app or in Chrome/Firefox
3. Start or join a game
4. A **green arrow** draws over the board pointing from the piece to its best destination
5. The floating panel shows the move in algebraic notation (e.g. `▶ E2E4`)

### Controls

| Button | Action |
|---|---|
| **×** (grey) | Clear the arrow without stopping the service |
| **🗑** (red) | Stop the overlay service entirely |

---

## Project structure

```
ChessAssistant/
├── app/src/main/
│   ├── java/com/vanta/chessassist/
│   │   ├── MainActivity.java              # Entry point, permission flow
│   │   ├── chess/
│   │   │   ├── Piece.java                 # Piece type + color constants
│   │   │   ├── Move.java                  # Move representation + algebraic I/O
│   │   │   ├── Board.java                 # 64-sq array, FEN parse/write, move apply
│   │   │   ├── MoveGenerator.java         # Pseudo-legal + legal move generation
│   │   │   └── ChessEngine.java           # Negamax α-β, depth 4, PST evaluation
│   │   ├── detection/
│   │   │   └── ChessAccessibilityService.java  # Reads Chess.com board via a11y tree
│   │   └── overlay/
│   │       ├── OverlayService.java        # Foreground service, WindowManager views
│   │       └── ArrowOverlayView.java      # Custom view — draws the move arrow
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── layout/overlay_control.xml
│   │   ├── values/strings.xml
│   │   ├── values/colors.xml
│   │   └── xml/accessibility_service_config.xml
│   └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Extending the engine

The current engine is depth-4 minimax — fast enough to run on the UI thread's background executor but not GM-strength. To upgrade:

### Option A — Increase depth

In `ChessEngine.java`, change:
```java
private static final int DEPTH = 4;
```
to `5` or `6`. Each increment roughly 4× search time.

### Option B — Stockfish via JNI (recommended for strength)

1. Download the [Stockfish Android binary](https://stockfishchess.org/download/)
2. Place `.so` files in `app/src/main/jniLibs/<abi>/`
3. Write a JNI wrapper that sends FEN via UCI and reads `bestmove`
4. Replace the `engine.getBestMove(board)` call in `ChessAccessibilityService`

---

## Board position calibration

The arrow coordinates are estimated from screen dimensions with a heuristic assuming the board occupies ~90% of the screen. If the arrow is offset:

- The board rect is set in `OverlayService.onBestMove()`:

```java
int boardSize = (int)(Math.min(sw, sh) * 0.90f);
int boardLeft = (sw - boardSize) / 2;
int boardTop  = (sw - boardSize) / 2;
```

Adjust the multiplier and offsets to match your device / Chess.com layout. A future calibration screen can be added to `MainActivity` with sliders.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Arrow never appears | Confirm both permissions (overlay + accessibility) are granted |
| Arrow on wrong squares | Adjust `boardSize` multiplier in `OverlayService.onBestMove()` |
| No suggestion after move | Chess.com didn't fire an accessibility event — tap a piece to trigger re-read |
| Works in app, not browser | Enable "Enhanced web accessibility" in Chrome → `Settings → Accessibility` |
| `TYPE_APPLICATION_OVERLAY` crash on API < 26 | The code falls back to `TYPE_PHONE` automatically |

---

## Privacy

- **No network calls** — all analysis is on-device
- **No data storage** — board state is processed in memory and discarded
- **Accessibility scope** — only Chess.com and listed browsers; `packageNames` in `accessibility_service_config.xml` restricts the service

---

## License

MIT — do whatever you like with it.
