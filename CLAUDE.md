# CLAUDE.md — Tomodoom architecture & agent contracts

This is the source of truth for the architecture, package layout, threading rules and integration
contracts. Every agent working in this repo (human or AI) follows it. If the code and this file
disagree, fix one of them in the same commit.

**This file is the full briefing.** A task message only says *what* to do. Everything about *how*
to do it in this repo (role, workflow, rules, verification, commits, reporting) is here. Read §0
completely before you start, then the sections it points you to.

---

## 0. Start here: standard operating procedure

### 0.1 Work out your role from the task

Match the task to a row. If it fits several, take the first row that fits. Each role's ownership
and acceptance criteria are in the section listed.

| The task mentions… | Your role | Read | You mainly touch |
|---|---|---|---|
| pet rendering, sprites, animation, clothes/accessory art, assets, the pet viewport, `AccessoryCatalog`, "Maniengine" | **Maniengine Agent** | §4 `ManiengineAssetRenderer`, §5.1 | new package `com.example.tomodoom.maniengine`, `model/AccessoryCatalog`, assets |
| chatbot, TruFar, LLM, backend, quizzes, MCP tools, chat networking | **TruFar/MCP Agent** | §4 `TruFarChatService` + `McpPetActionHandler`, §5.2 | new package `com.example.tomodoom.trufar` (and/or `.mcp`), manifest INTERNET permission, the relay backend in `backend/` |
| doomscroll detection, usage stats, accessibility service, screen-time tracking, monitoring permissions | **Doom Scroll Agent** | §4 `DoomScrollMonitor`, §5.3 | new package `com.example.tomodoom.doomscroll`, manifest, permission UI on Pets |
| anything else: UI polish, new screens, bug fixes, refactors, tests, build config, docs | **Core Agent** | §2, §3, §6 | the existing packages, following the dependency rules in §2 |

A role is a scope, not a permission to edit everything. Stay inside your role's files. If the task
truly needs a change outside them (a contract change, a new `PetState` field, a shared layout),
make the smallest change that works, keep it backward compatible, and call it out in the commit
message and your final report.

### 0.2 Workflow (every task)

1. **Orient.** Run `git status` and `git log --oneline -10`. Read this file, then the Javadoc of
   every interface or class you'll touch. Javadoc in `contracts/` is normative.
2. **Check the baseline.** Build it with the §7 command before changing anything. If the baseline
   is already broken, say so in your report, and fix it first only if the fix is trivial and in scope.
3. **Plan small milestones.** Split the work into steps that each compile on their own (for example:
   interface/model changes, then implementation, then wiring, then UI).
4. **Implement** under the rules in §2, §3 and §6. Prefer extending existing code to duplicating it.
   Don't delete the stubs in `stubs/`.
5. **Verify** with the §7 checks after each milestone. Add or extend unit tests for any pure-Java
   logic (§7.2).
6. **Commit** each milestone following §6.1.
7. **Update this file** in the same commit whenever you change a contract, the package map,
   persistence keys, threading behaviour, build commands, or §9 status.
8. **Report** as described in §0.4.
9. **Update CLAUDE.md at the end of every turn.** Before you hand control back (end of a task, a
   milestone, or any turn where you changed code or learned something about the repo), re-read the
   sections your work touched and bring them up to date: package map, contracts, persistence keys,
   threading, build commands, palette/theming (§6.3), the §8 manual check and §9 status. Record any
   new gotcha you hit in §6.2. Commit the update with the work it describes; if the turn made no
   other commit, commit it on its own as `docs: update CLAUDE.md`. If nothing needs changing, say
   so in your report.

### 0.3 Decisions and ambiguity

- If the task is underspecified, choose the option that best fits this architecture, keep it easy
  to change, and record the choice in the commit message and your report. Don't stop to ask unless
  the choice would be destructive, irreversible, or contradicts this file.
- Things you **must not** do without being told explicitly: push to a remote, rewrite or amend
  published history, change the tech stack (§1), raise `compileSdk`/`targetSdk`/AGP/Gradle, add
  Kotlin, Room, Hilt/Dagger or RxJava, edit `git config`, commit `local.properties`/`build/`/IDE
  caches, delete the stubs, or weaken the HP safeguards (replay guard, award cap).
- If something in this file looks wrong or out of date, follow the code's actual behaviour, fix the
  file in your commit, and mention it.

### 0.4 Definition of done & final report

A task is done when:
- the §7 checks pass (build, unit tests, lint with 0 errors and no new warnings, javac with 0 warnings),
- every change is committed and `git status` is clean (§6.1),
- this file still matches the code, and was reviewed at the end of the turn (§0.2 step 9).

End with a short report covering: what changed (commit hashes and one line each), what you verified
and how, what you **couldn't** verify (on-device behaviour is usually manual, see §7.3), any
decisions or deviations, and suggested follow-ups.

---

## 1. Project overview

**Tomodoom** is an Android app that gamifies cutting down on doomscrolling. You look after a
virtual pet whose **HP** reflects your digital habits:

| Event | Effect | Subsystem |
|---|---|---|
| A doom-scroll session is detected | Pet **loses** HP | Doom Scroll monitor |
| You pass a quiz from the TruFar chatbot | Pet **gains** HP (applied via an MCP tool) | TruFar chat + MCP bridge |
| You equip clothes or accessories | The pet's look changes | Maniengine renderer |

The app has three bottom-navigation tabs, left to right: **Customise** (live pet preview + slot tabs
and a grid of item circles), **Pets** (HP and the pet viewport; the default page on launch) and **Chat**
(TruFar chatbot + quizzes). There is no action bar on any tab. The bottom bar shows icons only: the
open tab widens, shows its label and enlarges its icon.

### Tech stack (fixed — don't change it without updating this file)

| Item | Value                                                                                                                                                                    |
|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Language | **Java only** (no Kotlin sources)                                                                                                                                        |
| Java source level | **1.8**. Lambdas and method refs are fine. **No** `var`, switch expressions, records, text blocks, `List.of`/`Map.of`/`Set.of`, `String.isBlank()` or any other Java 9+ API |
| Android | `compileSdk = 34`, `targetSdk = 34`, **`minSdk = 24`** (check API levels before calling newer APIs)                                                                      |
| Build | AGP 8.1.1, Gradle 8.0 wrapper, Kotlin DSL build scripts (`*.gradle.kts`)                                                                                                 |
| UI | XML layouts + **ViewBinding**, `Activity`/`Fragment`, swipeable `ViewPager2` pages + `BottomNavigationView` (no Jetpack Navigation), Material Components (`Theme.MaterialComponents.Light.NoActionBar`, light only)  |
| Architecture | **MVVM**: `ViewModel` + `LiveData` (lifecycle 2.7.0), repositories as single sources of truth                                                                            |
| Persistence | **`SharedPreferences`** (no Room, no DataStore)                                                                                                                          |
| DI | Hand-written service locator (`di.AppContainer`). No Hilt or Dagger                                                                                                      |
| Tests | JUnit 4 (`app/src/test`) for pure-Java logic                                                                                                                             |

---

## 2. Package architecture

Root package: `com.example.tomodoom`. Source root: `app/src/main/java/com/example/tomodoom/`.

```
com.example.tomodoom
├── TomodoomApplication.java      Application subclass; owns the AppContainer; starts the monitor
├── MainActivity.java             Main activity (not exported); swipeable ViewPager2 (Customise | Pets | Chat) ↔ BottomNavigationView;
│                                 starts on Pets; Back off Pets → Pets; no action bar (NoActionBar theme)
│
├── model/                        Immutable, pure-Java value objects (no android.* imports)
│   ├── PetState                  name, currentHp, maxHp, equipped items (slot → itemId)
│   ├── PetNames                  pet-name validation: trim, collapse whitespace, 1–20 chars
│   ├── PetMood                   THRIVING/OKAY/STRUGGLING/FAINTED from HP (the only HP thresholds)
│   ├── AccessorySlot             slot IDs: HAT="hat", ACCESSORY="accessory"
│   ├── AccessoryItem             id, slot, displayName
│   ├── AccessoryCatalog          placeholder item list per slot (Maniengine agent may replace it)
│   ├── ChatMessage               id, type (USER | BOT | SYSTEM), text, timestamp
│   ├── QuizPrompt                quizId, question, options, hpReward
│   ├── QuizEvaluation            quizId, passed, hpReward, feedback
│   ├── McpToolResult             success, message, data map
│   └── DoomScrollEvent           sourcePackage, durationMillis, hpPenalty, timestamp
│
├── contracts/                    Interfaces that downstream agents implement (see §4)
│   ├── ManiengineAssetRenderer
│   ├── TruFarChatService         (+ nested TruFarChatService.Listener)
│   ├── McpPetActionHandler
│   ├── DoomScrollMonitor
│   └── DoomScrollListener
│
├── repository/                   Reactive state holders and domain glue
│   ├── PetStateRepository        SINGLE SOURCE OF TRUTH for pet state (SharedPreferences + LiveData)
│   ├── ChatRepository            app-scoped chat transcript; routes TruFar events → MCP handler
│   └── DoomScrollHpBridge        DoomScrollListener → PetStateRepository.decreaseHp(...)
│
├── stubs/                        Stubs and default implementations of the contracts
│   ├── PlaceholderManiengineRenderer   text-only placeholder in the pet viewport
│   ├── MockTruFarChatService           echo bot + scripted quiz flow + /tool debug command
│   ├── DefaultMcpPetActionHandler      REAL implementation wired to PetStateRepository
│   └── StubDoomScrollMonitor           no-op monitor with a debug trigger
│
├── trufar/                       TruFar/MCP Agent: real chat clients (§5.2)
│   ├── OpenAiCompatibleChatService   runs the Kafka agent export on any OpenAI-compatible endpoint
│   ├── AgentProfile, AgentProfileLoader   TruFar agent export (assets/trufar/*.json) → persona + model settings
│   ├── QuizMarkerParser          pure Java: strips [[CORRECT]]/[[WRONG]] grading markers from replies
│   ├── ChatHistoryWindow         pure Java: last-N-turns conversation memory
│   ├── HttpTruFarChatService     client for the /v1/chat/sessions event API (served by the backend/ relay)
│   └── JsonHttpClient            package-private HTTPS + JSON helper shared by both clients
│
├── maniengine/                   Maniengine Agent: the HTML (3D) pet page in a WebView (§5.1)
│   ├── HtmlManiengineRenderer    WebView renderer wired in AppContainer; pushes state, pause/resume
│   ├── ManiengineProtocol        pure Java: state JSON + JS delivery script + touch policy (page contract)
│   └── ManiengineAssets          pure Java: virtual-origin URL → assets/maniengine/ path, MIME types
│
├── doomscroll/                   Doom Scroll Agent (§5.3)
│   ├── UsageStatsDoomScrollMonitor   polls Usage Access every 15 s; reads portrait from the display
│   ├── DoomScrollSessionTracker  pure Java: per-minute penalties, 10-minute warnings, session rules
│   ├── DoomScrollNotifier        wraps DoomScrollHpBridge; posts the warning notification
│   └── DoomScrollMonitorService  foreground service that keeps polling alive
│
├── di/
│   ├── AppContainer              THE ONLY PLACE implementations are chosen ("SWAP POINT" comments)
│   └── ViewModelFactory          constructor injection for ViewModels
│
├── viewmodel/
│   ├── PetsViewModel             exposes PetState + debug actions
│   ├── CustomiseViewModel        exposes PetState, selected slot, catalog items, equip action
│   ├── OnboardingViewModel       onboarding flag, suggested name, submitName()
│   └── ChatViewModel             exposes transcript + active quiz, sends messages
│
└── ui/
    ├── onboarding/OnboardingActivity   LAUNCHER: names the pet on first launch only,
    │                             otherwise forwards straight to MainActivity and finishes
    ├── MainPagerAdapter          FragmentStateAdapter: page index ↔ bottom-nav item id
    │                             (Customise=0, Pets=1 = default, Chat=2)
    ├── PetStage                  MainActivity's ONE pet renderer, lent to the resumed page's viewport
    ├── customise/CustomiseFragment   pet preview viewport (via PetStage) + full-width slot tabs + item grid
    ├── customise/AccessoryGridAdapter, AccessoryThumbnails   two-column item circles; item ID → thumbnail
    ├── pets/PetsFragment         full-page pet viewport (FrameLayout) under the HP header, Usage Access card, debug row
    └── chat/ChatFragment, ChatAdapter   RecyclerView (3 view types) + input bar
```

Resources: `res/values/colors.xml` + `res/values/themes.xml` (palette and theme, §6.3; no
`values-night/`), `res/layout/fragment_pets.xml` (a `ConstraintLayout`: a borderless `pet_viewport` fills the page, with
`hp_card`, `usage_access_card` (Usage Access prompt, §5.3) and `debug_panel` floating over it; both cards
are transparent),
`fragment_customise.xml` (`customise_header`, `pet_viewport` at 40% of the page height, `slot_tabs`,
`item_grid` RecyclerView), `fragment_chat.xml`, `item_chat_{user,bot,system}.xml`,
`item_accessory_circle.xml`, `res/drawable/bg_accessory_circle.xml`, `ic_item_none_24dp.xml`,
`res/drawable-nodpi/thumb_<itemId>.png` (GENERATED item thumbnails, §5.1),
`res/drawable/ic_hanger_24dp.xml` (Customise tab icon),
`activity_main.xml` (`main_pager` ViewPager2 + `nav_view`), `activity_onboarding.xml` (title,
`pet_name_layout`/`pet_name_input`, `start_button`), `res/menu/bottom_nav_menu.xml`.
`res/values/dimens.xml` (`pet_viewport_stroke`).
Assets: `assets/trufar/kafka_agent.json` (the Kafka TruFar agent export, icon stripped; §5.2),
`assets/maniengine/index.html` (the pet page, §5.1), `pet-scene.js` (model setup + add-ons, shared with the
thumbnail tool), `pet-model.js` (GENERATED from the scene-3 export) and `vendor/three/` (three.js 0.187.0).
Tools: `tools/maniengine/extract_model.py` (+ `source/scene-3.html`), `make_thumbnails.py` (+ `thumbnails.html`).

Outside the app: **`backend/`** is the Python relay server that connects the app's chat to Kafka on
trufar.cn. It's Flask, hosted on **Vercel** (free Hobby plan, no card), with Firestore on Firebase's
free Spark plan. It's a flat folder of modules (`app.py` WSGI entry, `trufar_client.py`,
`mcp_server.py`, `store.py`, `markers.py`, `config.py`) with pytest tests in `backend/tests/`.

Deploy config:
- Vercel: `vercel.json` (region `hkg1`) and `.vercelignore`. The Vercel project's Root Directory
  is `backend`, and Vercel's zero-config Flask support finds `app.py` → `app`.
- Shared: `.python-version` (3.12).
- Cloud Run (needs a billing card, kept as an alternative): `Procfile` (gunicorn) and `.gcloudignore`.

It isn't part of the Gradle build; see §5.2 and `backend/README.md`.

### Dependency direction (enforced by convention)

```
ui  ──►  viewmodel  ──►  repository  ──►  contracts  ◄──  stubs / real implementations
 │                          │                ▲
 │                          ▼                │
 └──────────────────────►  model  ◄──────────┘          di.AppContainer wires them all
```

- `model` depends on nothing (except `androidx.annotation`).
- `contracts` depend only on `model` (plus `android.view.ViewGroup` for the renderer).
- `ui` and `viewmodel` **never** reference `stubs.*` or a concrete implementation. They get
  everything through `AppContainer`/`ViewModelFactory`, typed as the interfaces.
- Fragments hold no business logic. They observe LiveData and forward user intents to ViewModels.

### End-to-end data flow

```
 User types answer ─► ChatFragment ─► ChatViewModel.sendMessage()
                                         │
                                         ▼
                              ChatRepository.sendUserMessage()
                                         │  (active quiz? → submitQuizAnswer : sendMessage)
                                         ▼
                              TruFarChatService  (Mock now, real TruFar later)
                                         │  Listener callbacks — ANY thread
                                         ▼
                              ChatRepository.onQuizEvaluated(passed)
                                         │
                                         ▼
                              McpPetActionHandler.onQuizPassed(quizId, hpReward)
                                         │
                                         ▼
 PetsFragment ◄─ LiveData ◄─ PetStateRepository.increaseHp(...) ──► SharedPreferences
      │
      └─► ManiengineAssetRenderer.render(viewport, state)

 DoomScrollMonitor ─► DoomScrollNotifier ─► DoomScrollHpBridge ─► PetStateRepository.decreaseHp(...)
```

---

## 3. State & threading rules (read before touching any state)

### 3.1 `PetStateRepository` is the single source of truth

- It is created once in `AppContainer` and shared by the Pets UI, the chat/MCP subsystem and the
  doom-scroll tracker. **Never construct a second instance.**
- Public API:
  - `LiveData<PetState> getPetState()`: observe from the UI (main thread).
  - `PetState getCurrentState()`: synchronous snapshot, safe from **any** thread.
  - `PetState increaseHp(int amount, String reason)` / `PetState decreaseHp(int amount, String reason)`:
    `amount` must be `>= 0` (negative throws `IllegalArgumentException`). HP is clamped to `[0, maxHp]`.
    Returns the new snapshot. `reason` is a short machine-readable tag (`"quiz_passed:<id>"`,
    `"doom_scroll:<pkg>"`, `"mcp:award_hp"`, `"debug"`) that gets logged.
  - `PetState equipItem(String slot, @Nullable String itemId)`: `null` unequips the slot.
  - `PetState resetPetState()`: restores the default HP and unequips everything. It keeps the pet's
    name and the onboarding flag, so onboarding only ever runs once.
  - `boolean isOnboardingComplete()` / `PetState completeOnboarding(String petName)`: the
    onboarding flag; `completeOnboarding` also sets the name (validate it with `PetNames.normalize`).
- Persistence: prefs file **`tomodoom_pet_state`**, keys `pet_name`, `current_hp`, `max_hp`,
  `equipped.<slotId>`, `onboarding_complete`. **Only `PetStateRepository` may read or write this file.** A write that
  bypasses it won't reach LiveData observers and will be overwritten.
- `PetState` is **immutable**. Make modified copies with `withHp(...)` / `withName(...)` / `withEquippedItem(...)`.
  Never hold on to a state and assume it is current. Re-read it or observe it.

### 3.2 Threading contract

| Rule | Why |
|---|---|
| Every repository mutator is `synchronized` and can be called from **any** thread | Doom-scroll services and network callbacks run off the main thread |
| Repositories publish **only with `postValue()`, called while holding the repository lock**, and **never** call `setValue()` | Mixing the two lets an older pending `postValue` overwrite a newer `setValue`. With all posts under one lock, the last value delivered is always the newest snapshot |
| A consequence of the above: right after a mutation, `getPetState().getValue()` may be one frame stale | Use `getCurrentState()` (or the mutator's return value) when you need the value straight away |
| SharedPreferences writes use `apply()`, never `commit()` | `apply()` updates memory synchronously and writes to disk asynchronously, so the caller never blocks |
| Contract callbacks (`TruFarChatService.Listener`, `DoomScrollListener`) **may arrive on any thread** | Consumers (repositories) are thread-safe. UI code only sees LiveData, which is always delivered on the main thread |
| `DoomScrollMonitor.startFromForeground()` is **main-thread only**, called while the app is visible | Android 12+ blocks starting a foreground service from the background |
| `ManiengineAssetRenderer` methods are **main-thread only** | They touch views. Do decoding/loading on a background thread inside the implementation, then post back to the main thread |
| Don't block the main thread: no network or disk I/O apart from the repositories' first prefs load | ANR safety |
| The whole app runs in **one process**. Don't declare `android:process` on services | SharedPreferences and in-memory singletons aren't shared across processes |

### 3.3 Other state

- `ChatRepository` keeps the chat transcript **in memory only** (it survives tab switches and
  rotation, but not process death). Persisting it is up to the TruFar agent, if needed.
- ViewModels hold only UI state (e.g. the selected customisation tab). Domain state lives in
  repositories.

---

## 4. Contracts (the `contracts` package)

| Interface | Implementation (stub in `stubs`) | Owner agent | Where it's wired |
|---|---|---|---|
| `ManiengineAssetRenderer` | `maniengine.HtmlManiengineRenderer` (stub: `PlaceholderManiengineRenderer`) | **Maniengine Agent** | `AppContainer.createAssetRenderer()` (factory: one instance per `MainActivity`, held by `ui.PetStage`) |
| `TruFarChatService` | `MockTruFarChatService` | **TruFar/MCP Agent** | `AppContainer` constructor, `// SWAP POINT (TruFar/MCP Agent)` |
| `McpPetActionHandler` | `DefaultMcpPetActionHandler` (production-capable) | **TruFar/MCP Agent** | `AppContainer` constructor, `// SWAP POINT (TruFar/MCP Agent)` |
| `DoomScrollMonitor` (+ `DoomScrollListener`) | `StubDoomScrollMonitor` | **Doom Scroll Agent** | `AppContainer` constructor, `// SWAP POINT (Doom Scroll Agent)` |

The full Javadoc on each interface is normative. What follows is a summary.

### `ManiengineAssetRenderer`
```java
@MainThread void render(@NonNull ViewGroup container, @NonNull PetState state);
@MainThread void release(@NonNull ViewGroup container);
@MainThread default void pause(@NonNull ViewGroup container) {}    // page onPause() via PetStage
@MainThread default void resume(@NonNull ViewGroup container) {}   // page onResume() via PetStage
```
- **One renderer per `MainActivity`**, so only one pet is rendered at a time. `ui.PetStage` owns it
  and lends it to whichever page is resumed (Customise or Pets): the page calls
  `attach(viewport, state)` in `onResume`, `detach(viewport)` in `onPause`/`onDestroyView`, and
  `render(viewport, state)` on each emission (ignored unless that page owns the pet).
- `container` is the visible page's `FrameLayout` `R.id.pet_viewport` (`fragment_pets.xml` or
  `fragment_customise.xml`). The renderer owns all of its children. Nothing else adds views to it.
- **The container changes between calls.** `render` with a new container must *move* the existing
  views there (remove from the old parent, keep loaded state); it must not rebuild or reload. A
  hidden page's viewport may keep the paused view until another page takes it.
- `render` is called on **every** `PetState` emission (HP changes as well as equip changes), so it
  must be idempotent and cheap: diff against what's already displayed and update in place.
- `release` is called once from `MainActivity.onDestroy()` (rotation included) with the last
  container. Remove the child views, cancel animations and free bitmaps. The instance is discarded
  afterwards (the new activity gets a new instance).
- `pause`/`resume` bracket the time the current container's page is visible (pages stay alive,
  `STARTED`, while hidden). Stop per-frame work while paused. Both are optional default no-ops.
- The asset format is **HTML**: Maniengine exports a 3D scene as a web page, which
  `HtmlManiengineRenderer` shows (§5.1). Nothing outside the renderer may depend on that.

### `TruFarChatService`
```java
void setListener(@Nullable Listener listener);
void connect();      void disconnect();      boolean isConnected();
void sendMessage(@NonNull String text);
void submitQuizAnswer(@NonNull String quizId, @NonNull String answer);

interface Listener {                                   // callbacks: ANY thread
    void onBotMessage(@NonNull String text);
    void onQuizPrompt(@NonNull QuizPrompt prompt);
    void onQuizEvaluated(@NonNull QuizEvaluation evaluation);
    @NonNull McpToolResult onToolCallRequested(@NonNull String toolName,
                                               @NonNull Map<String, Object> params);  // synchronous
    void onError(@NonNull Throwable error);
}
```
- All methods return immediately. Results arrive asynchronously through the `Listener`.
- `ChatRepository` is the only listener. It calls `connect()` once, the first time the Chat tab
  opens. While a quiz is active, the user's next message goes to `submitQuizAnswer` instead of
  `sendMessage`.
- `onToolCallRequested` is how the backend (acting as the MCP client) invokes the app's pet tools.
  It runs the tool **synchronously** on the calling thread and returns the result, which the
  implementation sends back to the backend. Don't call it on the main thread.

### `McpPetActionHandler`
```java
String TOOL_AWARD_HP      = "award_hp";       // params: amount (Number, required), reason (String, optional)
String TOOL_EQUIP_ITEM    = "equip_item";     // params: slot (String, required), itemId (String, optional; absent = unequip)
String TOOL_GET_PET_STATE = "get_pet_state";  // params: none

@NonNull McpToolResult onQuizPassed(@NonNull String quizId, int hpReward);
@NonNull McpToolResult executeMcpTool(@NonNull String toolName, @NonNull Map<String, Object> params);
@NonNull List<String> getSupportedToolNames();
```
- Thread-safe, callable from any thread. Never throws for bad input; it returns
  `McpToolResult.error(...)` instead.
- `onQuizPassed` is **idempotent per `quizId`**: a repeated award for the same quiz is rejected.
  A single award is capped at `DefaultMcpPetActionHandler.MAX_HP_PER_AWARD` (50).
- `McpToolResult.getMessage()` is shown to the user as a SYSTEM chat message
  (e.g. `"+10 HP awarded via MCP! (60/100 HP)"`).

### `DoomScrollMonitor` / `DoomScrollListener`
```java
void startMonitoring();   void stopMonitoring();   boolean isMonitoring();
@MainThread default void startFromForeground() { startMonitoring(); }
boolean isPermissionGranted();
void setListener(@Nullable DoomScrollListener listener);

interface DoomScrollListener {                         // callbacks: ANY thread
    void onDoomScrollDetected(@NonNull DoomScrollEvent event);
    default void onDoomScrollWarning(@NonNull String sourcePackage, long sessionMillis) {}
}
```
- `startMonitoring()`/`stopMonitoring()` are idempotent. `startMonitoring()` is a no-op that logs
  if the permission isn't granted.
- `AppContainer` sets the listener to `DoomScrollNotifier`, which posts warnings and forwards
  penalties to `DoomScrollHpBridge`, which calls
  `PetStateRepository.decreaseHp(event.getHpPenalty(), "doom_scroll:" + pkg)`. **The monitor never
  touches the repository directly.** It reports events, and the monitor decides `hpPenalty`
  (the real monitor charges 1 HP per minute; `DoomScrollEvent.DEFAULT_HP_PENALTY` = 5 is for the
  stub and the debug button).
- `TomodoomApplication.onCreate()` calls `startMonitoring()` if `isPermissionGranted()`. The Pets
  page calls `startFromForeground()` on resume, which starts the foreground service.

---

## 5. Agent integration guide

General rules for every agent:
1. Implement the interface in a **new class in a new package you own** (e.g. `com.example.tomodoom.maniengine`).
   Don't edit the contracts unless this document is updated in the same commit, and keep them
   backward compatible for the other agents.
2. Swap the implementation in **`di/AppContainer.java` only**, on the line marked `// SWAP POINT (<your agent>)`.
   Leave the stub in `stubs/` (it's the reference behaviour, used in tests and debugging).
3. Don't modify `PetStateRepository` semantics (§3). If you need a new state field, add it to
   `PetState` + the repository + this document in one commit.
4. Add new dependencies to `app/build.gradle.kts` with an exact version and say why in the commit.
5. Run the build commands in §7 before every commit. Every commit must compile.

### 5.1 Maniengine Agent — pet rendering & customisation assets
- **Implement:** `contracts.ManiengineAssetRenderer`. **Done:** `maniengine.HtmlManiengineRenderer`
  is wired in `AppContainer.createAssetRenderer()`. `stubs.PlaceholderManiengineRenderer` stays as
  the text-only reference.
- **Inputs:** `PetState` (HP for mood/animation, `getEquippedItems()` for the layers, keyed by
  `AccessorySlot` IDs and holding `AccessoryItem` IDs).
- **You also own:** `model.AccessoryCatalog`. Replace the placeholder items with your real asset
  manifest, keeping the item IDs stable once shipped (they're persisted in prefs), and the asset
  files under `app/src/main/assets/maniengine/`.

#### Dropping in the Maniengine HTML export

Maniengine's output is a single `.html` file (an "m3d" export). It holds **no mesh data**: the
model is three.js code building named meshes from primitives, wrapped in editor plumbing (the
`m3dLoader` live-edit runtime with a `/*O*/[...]/*O*/` override table, WASD camera controls, and a
CDN importmap). So the export isn't dropped in as is. Put it in `tools/maniengine/source/` and run
**`python3 tools/maniengine/extract_model.py [export.html]`** (stdlib only; see its README). It
writes `assets/maniengine/pet-model.js` (`buildModel(THREE, scene, renderer)` + `OVERRIDES`) and
vendors the matching three.js into `assets/maniengine/vendor/three/`. Never edit `pet-model.js` by
hand. `assets/maniengine/pet-scene.js` is hand-written: it builds the model, applies `OVERRIDES`,
drops the hidden room meshes and builds primitive add-ons for the `AccessoryCatalog` IDs. It looks
parts up by mesh name, so a new export with renamed parts needs those lookups updated.
`assets/maniengine/index.html` (also hand-written) frames the camera on the rabbit, animates by
`mood` and toggles the add-ons. Other asset types (`.glb`, textures, `.wasm`) can
still sit beside it with relative URLs.

The Customise circles are PNGs of those add-ons. After changing an add-on, run
**`python3 tools/maniengine/make_thumbnails.py`** (headless Edge) to regenerate
`res/drawable-nodpi/thumb_<itemId>.png`. A new catalog item also needs a case in
`ui/customise/AccessoryThumbnails`; `AccessoryThumbnailsTest` fails until it has one.

How the page is hosted (`HtmlManiengineRenderer`):
- Served from the virtual origin **`https://appassets.androidplatform.net/assets/maniengine/`**
  (`ManiengineAssets`), not `file://`, so ES modules, `fetch()` and WASM behave as on a web server.
  Only files under `assets/maniengine/` are served; anything else on that origin is a 404. Files
  are read on WebView's IO thread. MIME types come from the extension (`ManiengineAssets.mimeTypeFor`):
  add a case there for a new file type.
- JavaScript and DOM storage are on. File/content access is off, there's no JS interface, and the
  main frame can't navigate off the origin. Network URLs (e.g. a CDN script) still load, but bundle
  libraries locally so the pet works offline.
- The WebView background is transparent (the window's `floral_white` shows through). In a viewport
  with a background (Customise) it is inset by `@dimen/pet_viewport_stroke` and clipped to the rounded
  corners; in the borderless Pets viewport it fills the page edge to edge.
- `pause`/`resume` call `WebView.onPause()`/`onResume()`. A page should still stop its own
  `requestAnimationFrame` loop on `visibilitychange` if it does heavy work.
- If there's no usable WebView, or the render process dies (e.g. a WebGL crash), the viewport shows
  `maniengine_unavailable`/`maniengine_crashed` instead of crashing the app.
- Debug builds enable `WebView.setWebContentsDebuggingEnabled`, so you can inspect the page from
  `chrome://inspect`.

**Page contract** (`ManiengineProtocol`, normative):
- On load and on every state change, the app sets `window.tomodoomPetState = state` and dispatches
  `window` event **`tomodoom:petstate`** with `event.detail = state`. A page listens for the event
  **and** reads `window.tomodoomPetState` at startup (the first event may fire before late-loading
  code runs). Unchanged states aren't re-sent.
- `state` is
  `{ name, hp, maxHp, hpFraction, mood, fainted, equipped: { <slotId>: { id, name } } }`.
  `mood` is a `PetMood` id (`thriving` | `okay` | `struggling` | `fainted`). `equipped` lists only
  filled slots (`hat`, `accessory`), with the catalog display name.
- **Touch:** by default, horizontal drags on the pet swipe between tabs. A page that needs drags
  declares a policy with `<meta name="tomodoom-touch" content="…">`. `capture` keeps every drag that
  *starts* on the viewport. `centre` (used by `index.html`) keeps only drags starting in
  `ManiengineProtocol.isCentreZone`; the outer 15% on each side and the bottom 20% stay with the tab
  pager. `index.html` mirrors those fractions (`TAB_EDGE`, `TAB_BOTTOM`), and `ManiengineProtocolTest`
  fails if they drift. A centre drag turns the `rabbit` group (`rotation.y`, one viewport width = one
  full turn) and a flick coasts to a stop; the rug stays still. A second finger always keeps the gesture in the page (`PetWebView` blocks the pager on
  `ACTION_POINTER_DOWN`), which is how `index.html` pinch-zooms (`camera.zoom`, 0.75x to 3x).
- The page talks only one way (app → page). If it ever needs to call back (e.g. "item tapped"),
  extend `ManiengineProtocol` and this section together.
- **Layout note:** there is **one** WebView, moved (not reloaded) between two viewports: the large
  one on Pets and a preview at 40% of the page height on Customise, above the slot tabs and item
  circles. On Pets the cards and debug row float over the pet, so `fit()` sits the rabbit lower in tall
  viewports. The page is resized on each move, so it must refit to any size without distorting the
  model (`index.html` does, with a `ResizeObserver` calling `fit()`). The page that isn't shown has an empty viewport, and
  while you drag between pages the incoming viewport stays empty until the swipe settles.
- **Acceptance:** equipping/unequipping on the Customise tab updates the viewport immediately. Rotating
  or switching tabs doesn't leak views (`release` cleans up). Nothing happens off the main thread
  in `render`. The export loads offline from `assets/maniengine/` and reacts to `tomodoom:petstate`.

### 5.2 TruFar/MCP Agent — chatbot backend & MCP tool bridge
- **Implement:** `contracts.TruFarChatService` (network client) and, optionally, extend
  `stubs.DefaultMcpPetActionHandler` or replace it with your own `contracts.McpPetActionHandler`.
- **Replace:** `stubs.MockTruFarChatService` (and optionally the handler) in `AppContainer`.
- **Must:** deliver `QuizPrompt`s via `onQuizPrompt`, evaluate answers from `submitQuizAnswer`
  and report the result via `onQuizEvaluated` (with `hpReward` set when passed). Advertise the
  tools in `getSupportedToolNames()` to the model, and forward model tool calls through
  `Listener.onToolCallRequested` (call it off the main thread), sending the returned
  `McpToolResult` back to the backend.
- **Must not:** call `PetStateRepository` directly from the chat service. HP changes always go
  through `McpPetActionHandler`, which keeps the anti-replay and award caps in one place.
- **Network:** add `<uses-permission android:name="android.permission.INTERNET"/>` to the manifest
  when you add networking. Do I/O on your own executor.
- **Acceptance:** the manual E2E script in §8 passes against the real backend. No main-thread
  network. Errors show up as SYSTEM messages via `onError`.

#### Kafka agent (the chatbot in use)

The chatbot is **Kafka**, a TruFar agent exported from the TruFar editor as JSON. The export is
bundled as `assets/trufar/kafka_agent.json`, a copy of the export without the base64 icon. To
change the bot, edit or replace that file; no code change is needed. `AgentProfileLoader` reads:
- `agent_basic_info.name`
- `config.pre_prompt` (the persona)
- `config.model.name` (`deepseek-v4-pro`)
- `config.model.params.temperature` (0.84)
- `config.chat_memory_window.window_size` (5 turns)

Everything else in the export (icon, tools, opening statement) is ignored.

TruFar publishes no API for running an agent, and the export only names the model provider
(`trufar_tokenhub`). There are two ways to run Kafka:
- **On trufar.cn itself, through the relay backend** (the intended setup; see *Relay backend* below).
  The app uses `HttpTruFarChatService` (`TRUFAR_API_URL`).
- **In the app against an OpenAI-compatible LLM** (no TruFar involved). The app runs the persona
  itself with `trufar.OpenAiCompatibleChatService` against **any OpenAI-compatible
  `POST {base}/chat/completions`**. Set these Gradle properties (e.g. in an untracked
  `~/.gradle/gradle.properties`):

| Property | Meaning |
|---|---|
| `TRUFAR_LLM_URL` | HTTPS base URL; `/chat/completions` is appended (e.g. `https://api.deepseek.com`) |
| `TRUFAR_LLM_API_KEY` | bearer token (compiled into the APK: dev/testing only, same caveat as below) |
| `TRUFAR_LLM_MODEL` | optional; overrides the export's model name (e.g. `deepseek-chat` if the endpoint doesn't know `deepseek-v4-pro`) |

`AppContainer` picks the first configured option: `TRUFAR_LLM_URL` → `OpenAiCompatibleChatService`,
then `TRUFAR_API_URL` → `HttpTruFarChatService`, otherwise `MockTruFarChatService` (offline demo).
So for the relay, leave `TRUFAR_LLM_URL` **unset**.

How `OpenAiCompatibleChatService` behaves:
- `connect()` loads the asset on the worker thread. It then sends the hidden user turn `__START__`,
  which the persona prompt answers with Kafka's greeting. `__START__` never appears in the transcript.
- Each request is `{model, temperature, stream:false, messages:[system, …last 5 turns, user]}`. A
  turn is remembered only if its request succeeds. Read timeout is 90 s.
- **HP / quiz protocol:** Kafka quizzes in free text, so the service never emits `QuizPrompt`s. The
  app appends `APP_PROTOCOL_PROMPT` to the persona, which asks the model to end any reply that grades
  an answer with `[[CORRECT]]` or `[[WRONG]]`. `QuizMarkerParser` strips the markers (it's lenient
  about case and brackets, and the last marker wins). A graded reply is delivered as
  `onQuizEvaluated(quizId = "trufar-agent:<session UUID>:<n>", hpReward = CORRECT_ANSWER_HP = 10)`.
  `ChatRepository` shows it as one bot bubble and, when it passed, calls `onQuizPassed`, so the
  replay guard and cap still apply. Ungraded replies arrive through `onBotMessage`. A marker in the
  greeting is ignored.
- No MCP tools are advertised (the export has none). HTTP errors include the server's
  `error.message` and surface as SYSTEM messages via `onError`.

#### Relay backend (`backend/`): Kafka on trufar.cn

```
app (HttpTruFarChatService) ──/v1/chat/sessions──► relay (Flask, Vercel)    ──► trufar.cn web chat
          ▲  quiz_evaluated event                    ▲  POST /mcp award_hp               │ Kafka
          └──────────────────────────────────────────┴───────────────────────────────────┘
```
The relay implements the session API below. Deployment, env vars and the TruFar-side setup are
in `backend/README.md`.

**Hosting: Vercel (primary; free Hobby plan, no card)**
- The Vercel project's Root Directory is `backend`. Zero-config Flask runs `app.py` → `app`.
  `vercel.json` pins functions to `hkg1` (Hong Kong), and functions may run up to 300 s.
- Each request is its own function invocation, so state can't live in memory. The Firestore
  store is **required** there: it holds the turn lease, which is how Kafka's mid-turn `/mcp`
  call finds the turn. Firestore runs on Firebase's free Spark plan.
- Env vars live in Vercel's *Settings → Environment Variables* (encrypted): `BACKEND_TOKEN`,
  `MCP_KEY`, `TRUFAR_COOKIE`, `TRUFAR_CONVERSATION_ID`, and `FIREBASE_SERVICE_ACCOUNT` (the key
  JSON). A change only applies after a redeploy. Pushes to `main` redeploy automatically.
- The endpoints are public on purpose: the app and TruFar must reach them, and the app token
  and MCP key are the protection.

**Alternative: Cloud Run (needs a billing card)**
- Service `tomodoom-relay` in `asia-east1`, deployed with `--allow-unauthenticated`.
- gunicorn runs 1 worker with **8 threads** (`Procfile`), so `/mcp` is served while a
  `/messages` request waits on TruFar.
- Secrets come from **Secret Manager** as env vars: `backend-token` → `BACKEND_TOKEN`,
  `mcp-key` → `MCP_KEY`, `trufar-cookie` → `TRUFAR_COOKIE`. `TRUFAR_CONVERSATION_ID` and
  `GOOGLE_CLOUD_PROJECT` are plain env vars.

**Credentials and store selection (all hosts)**
- `Config.from_env()` picks the Firestore store automatically when `FIREBASE_SERVICE_ACCOUNT`,
  `GOOGLE_APPLICATION_CREDENTIALS` or `K_SERVICE` (set by Cloud Run) is present, otherwise memory.
  Firestore credentials, in order:
  - `FIREBASE_SERVICE_ACCOUNT`: the whole service-account key JSON as one env var, for hosts
    with no file system for a key file. It's validated by `store.parse_service_account`, whose
    errors never contain the value.
  - otherwise Application Default Credentials: a key file, or on Cloud Run the service's own
    account, which needs `roles/secretmanager.secretAccessor` and `roles/datastore.user`.

  A bad key doesn't crash the app: `GET /` reports it under `missing`. Secret fields of
  `Config` are `repr=False`.
- PythonAnywhere is still documented in the README, but its **free plan is confirmed to block
  trufar.cn** (§6.2).

**Captured TruFar web protocol** (undocumented; taken from the browser's DevTools, so it may change):
- `POST https://trufar.cn/api/mashang/core/answer/{conversationId}` with body
  `{"issue_content": text, "issue_config": {}, "entrance": "normal"}`. Auth is the browser
  **login cookie** only (env `TRUFAR_COOKIE`). A new chat in TruFar gets a new `conversationId`;
  the request that creates one hasn't been captured, so the relay uses one fixed
  `TRUFAR_CONVERSATION_ID` for every app session.
- The response is a stream of `data: {...}` events. Each event is a **cumulative snapshot**, not a
  delta, and events are not always newline-separated (`}data: {`). `reply_config.replyAll[]` holds
  components: `reasonContent` (thinking, dropped) and `replyContent` (the reply). The final text
  is the last `replyContent.value`. A non-empty `reply_error` means failure. `trufar_client.StreamParser`
  splits on the `}data:` boundary, because Kafka's own text may contain `data:`.

**Turns and HP:**
- `POST /v1/chat/sessions` creates a session and relays the hidden `__START__` turn; Kafka's
  greeting comes back as a `message` event. The greeting never awards HP.
- Each `/messages` call takes the **turn lease** (`store.begin_turn`), relays the text, and then
  releases it. The lease lives in Firestore (`relay/current`), not in process memory, because
  Cloud Run may run several instances (and PythonAnywhere several worker processes), and Kafka's
  MCP call can land in a different one.
  Only one turn runs at a time; a second gets HTTP 409 "Kafka is busy". The lease expires after
  `TURN_LEASE_S` (300 s) if a worker dies.
- HP: Kafka calls the MCP tool **`award_hp(amount 1–10, reason)`** on `POST /mcp` while it
  answers, and the award attaches to the turn in flight. Fallback: a `[[CORRECT]]` marker in the
  reply (`markers.py`, a port of `QuizMarkerParser`). Either signal produces **one**
  `quiz_evaluated` event (`quiz_id = "trufar:<session>:<turn>"`, `hp_reward` ≤ 10, `feedback` =
  the reply without markers). The app then applies the HP through `McpPetActionHandler.onQuizPassed`,
  so the replay guard and cap stay on the device. The relay never sends `tool_call` events.
- `/mcp` is a minimal **stateless Streamable-HTTP MCP server**: JSON responses only, with
  `initialize`/`ping`/`tools/list`/`tools/call`; notifications get 202 and GET/DELETE get 405.
  Auth is `X-MCP-Key`, a Bearer token or `?key=` (env `MCP_KEY`), separate from the app's
  `BACKEND_TOKEN` (= the app's `TRUFAR_API_TOKEN`).
- Errors come back as `{"error": {"message": …}}`, which `JsonHttpClient` shows in the app's
  SYSTEM message: 401 bad token, 404 unknown/closed session, 409 busy, 502 TruFar failure,
  503 expired cookie ("TruFar login expired: refresh TRUFAR_COOKIE") or relay not configured.
- Firestore layout: `sessions/{id}` `{created, turn_count, closed}`,
  `sessions/{id}/turns/{n}` `{awarded, amount, reason, at}` (audit; **no message text is stored**),
  `relay/current` (the lease). The relay never logs message content.

#### Session API contract (`HttpTruFarChatService`)

The Android client is `trufar.HttpTruFarChatService`. Configure `TRUFAR_API_URL` and optionally
`TRUFAR_API_TOKEN` as Gradle project properties (for example in an untracked `~/.gradle/gradle.properties`).
The URL must be an HTTPS origin/base URL. With `TRUFAR_API_URL` unset or empty, `AppContainer`
keeps `MockTruFarChatService` wired so the offline demo remains usable. A configured bearer token
is compiled into the APK and is suitable only for local development/testing; do not put a
long-lived production secret in a mobile app.

The API is JSON over HTTPS. Every successful response uses an `events` array:

| Request | JSON body | Response |
|---|---|---|
| `POST /v1/chat/sessions` | `{ "client": "tomodoom", "tools": [...] }` | `{ "session_id": "...", "events": [...] }` |
| `POST /v1/chat/sessions/{id}/messages` | `{ "text": "..." }` | `{ "events": [...] }` |
| `POST /v1/chat/sessions/{id}/quiz-answers` | `{ "quiz_id": "...", "answer": "..." }` | `{ "events": [...] }` |
| `POST /v1/chat/sessions/{id}/tool-results` | `{ "call_id": "...", "result": { "success": true, "message": "...", "data": {} } }` | `{ "events": [...] }` |
| `DELETE /v1/chat/sessions/{id}` | no body | any 2xx response |

Supported event shapes are:

```json
{ "type": "message", "text": "..." }
{ "type": "quiz_prompt", "quiz_id": "...", "question": "...", "options": [], "hp_reward": 10 }
{ "type": "quiz_evaluated", "quiz_id": "...", "passed": true, "hp_reward": 10, "feedback": "..." }
{ "type": "tool_call", "call_id": "...", "name": "get_pet_state", "arguments": {} }
{ "type": "error", "message": "..." }
```

The client advertises the three `McpPetActionHandler` tools when creating a session, executes
`tool_call` events synchronously on its serial background worker through the registered listener,
and posts each result before processing returned events. Tool-call round trips are limited to eight
per response to prevent a backend loop. An HTTP or protocol error is surfaced through `onError`.
The read timeout is 120 s, because relayed Kafka turns are slow. `backend/` implements this contract.
It accepts `quiz-answers` and `tool-results` but returns no events for them.

### 5.3 Doom Scroll Agent — doomscroll detection
- **Implement:** `contracts.DoomScrollMonitor` (e.g. with `UsageStatsManager` polling or an
  `AccessibilityService`).
- **Replace:** `stubs.StubDoomScrollMonitor` in `AppContainer`. The listener wiring to
  `DoomScrollHpBridge` stays as it is.
- **You own:** the manifest entries for your permission/service (e.g. `PACKAGE_USAGE_STATS`, an
  accessibility service declaration plus its `res/xml` config, or a foreground-service type), and
  the permission UI flow. Suggested approach: add a permission card to `PetsFragment`, driven by
  `isPermissionGranted()`, that opens the right Settings screen and calls `startMonitoring()` once
  permission is granted.
- **Must:** emit `DoomScrollEvent`s with a sensible `hpPenalty`, never charge the same time twice,
  never touch `PetStateRepository` directly, and stay in the default process.
- **Acceptance:** after the permission is granted, a long scroll session in a target app lowers HP
  on the Pets tab. Battery impact is documented in your commit message.

#### Current implementation

`doomscroll.UsageStatsDoomScrollMonitor` uses the special Android Usage Access setting; the Pets
tab provides a button to open that setting and starts the monitor when the user returns with access
granted. It watches Instagram, TikTok, Reddit, YouTube, X/Twitter, Facebook and Snapchat.

Session rules (`DoomScrollSessionTracker`):
- The pet loses **1 HP per counted minute**, from the first minute. Part-minutes carry over.
- A notification appears at **every 10 counted minutes** of a session.
- Time counts only in a watched app, in **portrait** (display height ≥ width), with the screen on.
  Landscape pauses the session without losing progress.
- 60 seconds away from watched apps ends the session. Shorter gaps, in-app screen changes and
  switching between watched apps keep it going.
- Only time seen live between polls counts. Replayed usage history and poll gaps over 45 s are
  never charged.

Polling is every 15 seconds on a single daemon worker. `DoomScrollMonitorService` (type
`specialUse`, low-priority ongoing notification) keeps the process alive so Android doesn't freeze
the worker. It doesn't restart after a reboot until the app is opened. Portrait video that isn't
fullscreen still counts as scrolling, and scrolling in landscape isn't penalised.

---

## 6. Conventions

- **Nullability:** annotate every public API parameter and return value with
  `androidx.annotation.NonNull`/`Nullable`. Use `@MainThread`/`@WorkerThread`/`@AnyThread` where relevant.
- **Strings:** every user-visible string produced by the UI layer (`ui`, layouts, renderers) goes in
  `res/values/strings.xml` (format args like `%1$d`). Log messages, mock-bot dialogue, and text
  produced in non-UI layers without a `Context` (`McpToolResult` messages, `ChatRepository`
  notices) may be inline English for now. Localising those is a known TODO.
- **Logging:** `android.util.Log` with `private static final String TAG = "<ClassName>"`. Never log
  user message content at INFO or above.
- **ViewBinding:** inflate with `XxxBinding.inflate(...)`, null the binding out in `onDestroyView()`,
  and observe with `getViewLifecycleOwner()`.
- **ViewModels:** obtain them through `new ViewModelProvider(this, ViewModelFactory.from(requireContext()))`.
  Constructors take their dependencies explicitly (no `AndroidViewModel`).
- **New packages:** each agent's code goes in its own package under `com.example.tomodoom`
  (see §0.1). Don't put production implementations in `stubs/`.

### 6.1 Git workflow

- Commit on the current branch (normally `main`). **Never push** unless the task says so.
- **One logical milestone per commit**, and every commit must compile and pass the §7 checks.
  Don't save everything for one commit at the end.
- **Conventional Commits:** `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`, `build:`.
  A summary line of 72 characters or fewer, a blank line, then a body explaining what changed and
  why (bullets are fine). Mention any change outside your role's scope and any new dependency.
- **Stage explicit paths** (`git add <paths>`), never `git add -A` / `git add .`. Check
  `git status --short` before committing.
- **Commit identity:** if `git commit` fails with "Author identity unknown" (WSL git has none
  set on this machine), reuse the identity of the existing history for that one command instead of
  editing config:
  ```bash
  git -c user.name="$(git log -1 --format=%an)" -c user.email="$(git log -1 --format=%ae)" commit ...
  ```
- **Line endings** are normalised by `.gitattributes` (LF in the repo, CRLF for `*.bat`). Android
  Studio may write CRLF to disk. That's harmless and won't show up as a diff.
- Never commit `local.properties`, `build/`, `.gradle/`, `.idea/workspace.xml` or other IDE caches.

### 6.2 Gotchas specific to this project

- **Java 8 source level:** `List.of`, `Map.of`, `var`, `String.isBlank()`, `Optional.isEmpty()`,
  streams on minSdk-24-unsafe APIs and similar fail to compile or crash on old devices. Use
  `Arrays.asList`, `Collections.unmodifiable*`, and explicit loops.
- **`android.nonTransitiveRClass=true`:** the app's `R` only contains app resources. Library
  resources need their own `R`, e.g. `com.google.android.material.R.style.…`. In XML,
  `?attr/…` and `@style/Widget.MaterialComponents.…` work as normal.
- **`BuildConfig` is enabled** (`buildFeatures.buildConfig = true`). Gate debug-only UI with
  `BuildConfig.DEBUG`.
- **Theme:** `Theme.MaterialComponents.Light.NoActionBar`, not Material3. Use
  MaterialComponents attributes (`?attr/textAppearanceHeadline6`, `?attr/materialButtonOutlinedStyle`).
  The app is **light only**: don't add `values-night/` resources. Colours follow §6.3.
- **No action bar:** the theme is `NoActionBar`, so `getSupportActionBar()` returns `null`; don't
  rely on it. Each page draws its own header
  (the HP card on Pets, a title on Customise); content starts right under the status bar.
- **Top-level pages (`ViewPager2`):** there is no Jetpack Navigation. `MainActivity` hosts the pages
  in `main_pager` and keeps it in sync with `nav_view` both ways. `viewpager2` (1.1.0-beta02) comes
  in transitively from Material, like drawerlayout, so don't pin it. Rules:
  - A new top-level tab means a menu item in `bottom_nav_menu.xml` **and** an entry in
    `MainPagerAdapter` (`MENU_ITEM_IDS` + `createFragment`), in the same order.
  - Page order is Customise (0), Pets (1), Chat (2). Pets is the default: `MainActivity` calls
    `setCurrentItem(PAGE_PETS, false)` only when `savedInstanceState == null`, so a restore keeps
    the saved page. Don't assume page 0 is the start page.
  - `FragmentStateAdapter` creates pages lazily (a page is created the first time you swipe or tap
    to it, or prefetched next to the current one) and only the current page is `RESUMED`; the
    others are capped at `STARTED`. Use
    `onResume`/`onPause` for "page became visible/hidden".
  - ViewPager2 restores its page **without** calling `onPageSelected`, so `MainActivity` re-syncs
    the bottom nav and Back callback in `onRestoreInstanceState`.
  - A child that needs horizontal drags calls `getParent().requestDisallowInterceptTouchEvent(true)`
    on `ACTION_DOWN` (as the Maniengine WebView does for its `tomodoom-touch` policy). If a whole page
    ever needs to suspend swiping, add a host callback calling `ViewPager2.setUserInputEnabled`
    (the old `PageSwipeHost` did this for the removed customisation drawer).
- **Bottom bar:** `labelVisibilityMode="selected"` makes Material widen the open tab and show only
  its label. `MainActivity.emphasiseNavIcon` adds the icon scale and lift. It finds the icon by type
  because Material's icon view id is private (referencing it triggers lint `PrivateResource`).
- **Dependencies** must be compatible with compileSdk 34 / AGP 8.1.1. Many current AndroidX
  releases need compileSdk 35+, so check before you bump anything. Pin exact versions.
- **`androidx.activity` / `drawerlayout` / `viewpager2`** come in transitively as compile-scope
  dependencies of Material. Don't pin them in `build.gradle.kts`: that adds `GradleDependency`
  lint warnings.
- **LLM-graded HP is persuadable:** a user can talk Kafka into emitting `[[CORRECT]]`. Each award
  is bounded by `OpenAiCompatibleChatService.CORRECT_ANSWER_HP` (10) and the per-award cap, but
  there's no rate limit. Add one in the handler if it gets abused.
- **PythonAnywhere free can't host the relay** (confirmed by a live test): its allowlist proxy
  blocks trufar.cn, and the relay answers *"TruFar unreachable from host (PythonAnywhere free
  allowlist?)"* instantly. With a single web worker, Kafka's `/mcp` call would also have to wait
  for the turn to end. Kafka's prompt still asks for the `[[CORRECT]]` marker as an HP fallback.
- **Hosting must be free with no card** (the user's requirement). Cloud Run, Koyeb and Hugging
  Face Docker Spaces need payment details, so the relay runs on **Vercel Hobby**. Hobby is
  non-commercial only, which is fine for a course project.
- **Vercel notes:**
  - Every request may run in a fresh function, so never set `TOMODOOM_STORE=memory` there.
  - The first request after idle has a few seconds of cold start; the app's timeout is 120 s.
  - Whether trufar.cn accepts requests from Vercel's Hong Kong addresses was still unconfirmed
    when this was written.
- **TruFar cookie expiry:** the relay authenticates with a copied cookie from a **separate TruFar
  bot account**, so your own login is never exposed. When it expires, the app shows "TruFar
  login expired: refresh TRUFAR_COOKIE". Update the `TRUFAR_COOKIE` env var in Vercel and
  redeploy (on Cloud Run: add a new `trufar-cookie` secret version and run `gcloud run services
  update`). Never commit it or paste it into chats.
- **org.json in JVM tests:** android.jar only has throwing stubs, so `org.json:json` is a
  `testImplementation` dependency. Tests can then call `AgentProfileLoader.parse(JSONObject)` on the
  real asset (unit tests run with `app/` as the working directory).
- **Shared pet WebView:** `PetStage` reparents one WebView between page viewports. It's created
  with the viewport's (activity) context, so `PetStage` lives in `MainActivity` and is released
  in its `onDestroy`. Never hold it in a ViewModel or `AppContainer`: that would leak the activity.
- **WebView (Maniengine):** an `OnTouchListener` on a WebView triggers lint
  `ClickableViewAccessibility`, so `HtmlManiengineRenderer` uses a private `WebView` subclass that
  overrides `dispatchTouchEvent`. `SetJavaScriptEnabled` is suppressed on purpose: the page is a
  bundled asset and no JS interface is added. Don't add `addJavascriptInterface` or file access
  without revisiting that. `new WebView(...)` can throw when the WebView provider is missing or
  updating, so catch it.
- **Launcher is `OnboardingActivity`**, not `MainActivity` (which is `exported="false"`). It
  checks `isOnboardingComplete()` before inflating anything and forwards to `MainActivity` if set.
  Anything that should run on every launch belongs in `TomodoomApplication` or `MainActivity`,
  not in the onboarding screen.
- **Read-only emulators lose app data:** an emulator started with `-read-only` discards its data
  on shutdown, so the app is a fresh install (with onboarding) after each emulator restart.
- **Maniengine overrides are applied by the page, not the editor.** `pet-scene.js` matches each
  `OVERRIDES` entry by unique top-level name, else by `scene.children` index (the camera is added
  first so the indices match the export). Nested paths (`"46/16"`) are skipped with a
  `console.warn`. Headless-Edge screenshots on this Windows machine report a viewport about 500px
  wide whatever `--window-size` says, so check framing at ≥500px.
- **Foreground service start:** start `DoomScrollMonitorService` only through
  `DoomScrollMonitor.startFromForeground()` while a page is resumed. `Application.onCreate` can run
  in the background, where Android 12+ throws `ForegroundServiceStartNotAllowedException`.
- **Switch on strings** works with the `AccessorySlot` / `McpPetActionHandler.TOOL_*` constants
  because they are compile-time constants. Keep new constants `static final` literals.
- **Commit messages from Windows PowerShell 5.1:** `git commit -m @'…'@` with embedded double
  quotes gets split into pathspecs ("did not match any file(s)"). Write the message to a file in
  the scratchpad and use `git commit -F <file>`.

---

### 6.3 Colour palette & theming

The app uses one fixed, light-only palette, defined as tokens in `res/values/colors.xml`:

| Token | Hex | Role | Theme attributes it feeds |
|---|---|---|---|
| `floral_white` | `#FBF7EF` | Background and surfaces; text/icons on dark fills | `android:windowBackground`, `android:colorBackground`, `colorSurface`, `colorOnPrimary`, `colorOnSecondary` |
| `eggshell` | `#F7F0DE` | Borders, dividers, subtle fills (bot bubble, system notice) | none (use directly or through a semantic alias) |
| `charcoal` | `#585B5E` | Primary: user bubble, buttons, selected chip/tab, progress, input underline | `colorPrimary`, `colorSecondary` |
| `jet` | `#383A3D` | Primary text, status bar, darker accents | `colorPrimaryVariant`, `colorSecondaryVariant`, `colorOnBackground`, `colorOnSurface`, `android:textColorPrimary` |
| `slate_grey` | `#8C8F92` | Secondary text, hints, de-emphasised labels (e.g. pet status) | `android:textColorSecondary`, `android:textColorHint` |

Rules:
- **No raw hex values** in layouts, drawables or Java. Reach for a theme attribute first
  (`?attr/colorPrimary`, `?android:attr/textColorSecondary`), then a palette token.
- For a component-specific colour, add a **semantic alias** in `colors.xml` that points at a token
  (as `bubble_bot_background`, `system_notice_background`, `viewport_background` and
  `viewport_stroke` do) rather than a new hex value. Adding a new token means a palette change:
  update the table above in the same commit.
- `eggshell` on `floral_white` has very low contrast. Borders that must be visible use a 2dp stroke
  (as the Customise pet viewport does). The Pets cards are flat, transparent and have no stroke, because
  they float over the pet.
- `slate_grey` on `floral_white` is about 3.0:1 (`jet` is about 10.7:1, `eggshell` about 1.06:1), so keep it for secondary or large text. Body text
  uses `jet`.
- Renderers (Maniengine) may use their own art colours inside the viewport, but the chrome around
  it follows this palette.
- Launcher icon colours (`ic_launcher_*`) are still the template defaults and are outside the palette.

## 7. Build & verification commands

### 7.1 Commands

**Standard check to run after every milestone** (use the variant for your environment below):
`assembleDebug testDebugUnitTest lintDebug`. The lint text report is
`app/build/reports/lint-results-debug.txt`; its last line is the error/warning count.

Requires **JDK 17** (Android Studio's bundled JBR works) and the Android SDK with platform 34
(`sdk.dir` in `local.properties`, which Android Studio generates and which is never committed).
Some machines have no Android Studio or SDK; one known example has only a standalone JDK at
`C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot`. There Gradle can't build, so the most
you can do is a `javac --release 8 -Xlint:all -Werror` check of pure-Java/`trufar` code against
stubs, plus running the JUnit tests directly. Say so in the report.

**macOS / Linux (JDK 17 on PATH, Linux/macOS SDK):**
```bash
./gradlew assembleDebug                # full debug APK build (compile + resource linking + dex)
./gradlew compileDebugJavaWithJavac    # fast Java-only compile check
./gradlew testDebugUnitTest            # JVM unit tests (app/src/test)
./gradlew lintDebug                    # Android lint → app/build/reports/lint-results-debug.html
./gradlew installDebug                 # install on a connected device/emulator
```

**Windows (PowerShell / cmd), with a JDK 17:**
```bat
set "JAVA_HOME=C:\Users\jordi\.jdks\jbr-17.0.14"
gradlew.bat assembleDebug testDebugUnitTest lintDebug
```
Android Studio's bundled JBR (`C:\Program Files\Android\Android Studio\jbr`) is now **JDK 25**.
Gradle 8.0 can't run on it and fails with just `What went wrong: 25.0.4`. On this machine, use
`C:\Users\jordi\.jdks\jbr-17.0.14` or `C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot`.
In PowerShell: `$env:JAVA_HOME="C:\Users\jordi\.jdks\jbr-17.0.14"; .\gradlew.bat …`.

**Relay backend (`backend/`, Python ≥ 3.10):** run this whenever you touch `backend/`. It isn't
part of the Gradle build.
```bash
python -m venv <somewhere outside the repo> && <venv>/Scripts/pip install -r backend/requirements-dev.txt
<venv>/Scripts/python -m pytest -q -p no:cacheprovider backend/tests
```
The tests fake TruFar and Firestore (`firebase-admin` isn't needed for them). The Firestore
store itself is exercised only on the deployed server.

**WSL on this machine:** WSL has no JDK and the SDK is a Windows install, so `./gradlew` won't work
inside WSL. Run the Windows wrapper through interop from the repo root (`/mnt/c/...`), passing
`JAVA_HOME` via `WSLENV`. Don't use `cmd.exe /c 'set "JAVA_HOME=..." && ...'`: WSL escapes the
inner quotes, `cmd` ignores the `set`, and Gradle falls back to a stale JDK 11 `JAVA_HOME`.
```bash
JAVA_HOME='C:\Program Files\Android\Android Studio\jbr' WSLENV=JAVA_HOME \
  cmd.exe /c gradlew.bat assembleDebug testDebugUnitTest --console=plain
```
(If you pipe the output, e.g. to `tail`, add `set -o pipefail` so a failing build isn't reported as exit code 0.)
To tell which environment you're in: if `uname -r` contains `microsoft` and `which java` finds
nothing, use the WSL command. The first build of a session can take a minute while the Gradle
daemon starts.

A change counts as verified when `assembleDebug` and `testDebugUnitTest` succeed with no errors and
`lintDebug` reports **0 errors**. The current lint baseline is **9 `GradleDependency` warnings**
(newer AndroidX versions require compileSdk 35+ or a newer AGP). Some setups also report one
accepted `OldTargetApi` warning (`targetSdk = 34` is fixed by the stack), for 10 in total.
Changes to `backend/` must also keep `pytest backend/tests` green. Don't introduce new lint warnings. javac currently
builds with zero warnings, so keep it that way.

### 7.2 Unit tests

- JVM tests live in `app/src/test/java/com/example/tomodoom/<package>/…Test.java`, using JUnit 4
  (plus `org.json:json` so JSON parsing can be tested). See `model/PetStateTest` for the style.
- Pure-Java code (`model/`, and any logic you extract from Android classes) must have tests.
  Keep new domain logic free of `android.*` so it stays testable on the JVM.
- Classes using `SharedPreferences`, `Log`, `LiveData` or views can't run in plain JVM tests (the
  android.jar stubs throw). Either move the logic into a pure-Java helper and test that, or add a
  test dependency such as `androidx.arch.core:core-testing` or Robolectric in a `build:` commit.
- Instrumented tests (`app/src/androidTest`) need a device or emulator. Write them if they're
  useful, but say in your report if you couldn't run them.

### 7.3 What you probably can't verify

Agents in this environment usually can't launch an emulator or device. Don't claim on-device
behaviour you didn't observe. Instead, list the relevant steps of the §8 script (plus any new steps
for your feature) as "manual verification needed", and add those new steps to §8.

---

## 8. Manual end-to-end check (debug build, with the stubs)

Debug helpers available in the debug build:
- **Mock chat commands:** `quiz` (new quiz), `help`, `/tool <name> key=value …` (calls an MCP tool
  as if the backend had), anything else is echoed back.
- **Pets debug row** (`BuildConfig.DEBUG` only): *Simulate doom-scroll* (goes through
  `DoomScrollHpBridge`) and *Reset pet*.
- **`StubDoomScrollMonitor.simulateDoomScroll(pkg, durationMs, penalty)`:** tests the
  monitor → listener path in code.
- **Logcat tags:** `PetStateRepository` (every HP change with its reason), `ChatRepository`,
  `DefaultMcpHandler`, `MockTruFarChatService`, `OpenAiChatService`, `StubDoomScrollMonitor`,
  `HtmlManiengine` (WebView failures, missing assets).
- **Maniengine page:** in a debug build, open `chrome://inspect` on a desktop Chrome with the
  device connected to inspect and live-edit the pet page.


0. Fresh install: the onboarding screen shows "Meet your new
   pet" with the name field prefilled with `Tomo`. Clearing it disables **Start**; the counter caps
   at 20. Type `Mochi` and tap **Start** (or the keyboard's Done): the Pets tab opens and its
   header shows `Mochi`. Rotation keeps the typed text. Relaunching skips onboarding. (The steps
   below assume you kept `Tomo`.)
1. Launch the app. The **Pets** tab has **no action bar** (the HP card sits right under the status
   bar) and shows `Tomo`, **HP 50 / 100** and status "Okay". Until Usage Access is granted, a Usage Access card with an **Enable** button
   sits under the HP card (it disappears once access is granted). The bottom bar shows three icons (Customise, Pets, Chat). Pets is open: its tile is wider, its icon is larger and only it shows a label.
2. Swipe right (or tap **Customise**). The Customise page shows "Customise pet", a pet preview and
   full-width Hat / Accessory tabs above a two-column grid of circles showing each item's model
   (None first). Tap the **Cap** circle on the Hat tab: its ring darkens and the preview (the 3D
   cream rabbit in a floral dress, on a rug) gains a blue cap at once. Swipe back to Pets: the large
   pet has the hat too. The pet moves between the pages without reloading (no blank flash or
   restart of the hop animation), and only one pet exists: in a debug build `chrome://inspect`
   lists a single page target. Also check:
   - the selected slot tab and the current page survive rotation;
   - Back on Customise or Chat returns to Pets; Back on Pets leaves the app;
   - **Swiping:** from Pets, starting on a side edge or the bottom strip of the page, swipe left to
     Chat and right to Customise. The bottom nav selection follows.
   - **Rotating:** on Pets and Customise, a one-finger drag starting in the centre of the pet turns
     it both ways without changing page, and a flick coasts to a stop. The rug stays still, worn items
     turn with the rabbit, and the angle is kept when moving between the two pages.
   - On Pets the pet has no border and fills the page behind transparent cards. Pinching with two
     fingers zooms it evenly (0.75x to 3x) without changing page.
   - Mood: HP ≥ the thriving threshold makes the rabbit hop, struggling droops its head and
     desaturates, and 0 HP (Simulate doom-scroll repeatedly) tips it over in grey.
3. Open **Chat**. There is no action bar; the transcript starts right under the status bar. The mock bot greets you. Type `quiz`, and the bot asks a question (e.g. "What is 2 + 2?").
4. Answer correctly (`4`). The bot confirms, and a SYSTEM message shows **"+10 HP awarded via MCP! (60/100 HP)"**.
5. Switch to **Pets**: **HP 60 / 100**.
6. In Chat, type `/tool get_pet_state` (or `/tool award_hp amount=5`). A SYSTEM message shows the MCP result.
7. Pets debug row: **Simulate doom-scroll** → HP −5. **Reset pet** → back to 50/100, nothing equipped, name unchanged.
8. Kill and relaunch the app. HP and equipped items persist (SharedPreferences). Onboarding doesn't show again, even if you used **Reset pet** in step 7. The chat transcript resets (in memory only).

**Kafka (real LLM)**: build with `TRUFAR_LLM_URL`/`TRUFAR_LLM_API_KEY` set (§5.2):

9. Open **Chat**. Kafka sends a sarcastic three-sentence greeting asking for a topic. No `__START__` bubble appears.
10. Name a topic and answer Kafka's question correctly. The reply shows no `[[…]]` marker, and a SYSTEM
    message shows **"+10 HP awarded via MCP!"**. A wrong answer gets a roast and no HP.
11. Use a bad key or URL. A SYSTEM `⚠ TruFar error: … HTTP 401…` message appears and the app doesn't crash.

**Doom-scroll detection (real device, watched app installed):**

12. Grant Usage Access and allow notifications. A low-priority "Tomodoom is watching over your pet" notification appears.
13. Scroll Instagram or YouTube Shorts in portrait. HP drops by 1 each minute.
14. Keep going to 10 and 20 minutes. A "Time to look up" notification appears each time.
15. Play a fullscreen landscape YouTube video. HP stops dropping, then resumes when you return to portrait.
16. Leave watched apps for over a minute, then return. The next warning comes 10 minutes into the new session.
17. Revoke Usage Access. Monitoring stops and its notification disappears.

**Kafka on trufar.cn via the relay**: deploy `backend/` (see its README), set `TRUFAR_API_URL`/`TRUFAR_API_TOKEN`
and leave `TRUFAR_LLM_URL` unset:

18. The relay URL (`https://<project>.vercel.app/`) returns `"ok": true`. Open **Chat** and wait
    (up to about a minute): Kafka's TruFar greeting appears, with no `__START__` bubble.
19. Answer Kafka's question correctly. The relay logs (*Vercel → project → Logs*) show
    `MCP award_hp amount=10 -> ok` (or `award=10 via marker`), the reply shows no `[[…]]`
    marker, and a SYSTEM message shows **"+10 HP awarded via MCP!"**. A wrong answer gets no HP.
20. Send a message while another is still being answered (e.g. from a second device). That one
    gets `⚠ TruFar error: … HTTP 409: Kafka is busy…`.
21. Set a stale `TRUFAR_COOKIE` in Vercel and redeploy: the app shows
    `HTTP 503: TruFar login expired: refresh TRUFAR_COOKIE`. A wrong
    `TRUFAR_API_TOKEN` shows `HTTP 401`. `TruFar unreachable from host` means the host can't
    reach trufar.cn (§6.2).

---

## 9. Project status & known gaps

Update this table in the same commit when your task changes a subsystem's status.

| Subsystem | Status | Implementation in `AppContainer` |
|---|---|---|
| Pet state + persistence | ✅ Done | `PetStateRepository` |
| MCP tool bridge | ✅ Production-capable default | `DefaultMcpPetActionHandler` |
| Pets UI (HP header, pet viewport, debug row) | ✅ Skeleton done | `PetsFragment` / `PetsViewModel` |
| Customise UI (pet preview, slot tabs, item circle grid) | ✅ Skeleton done | `CustomiseFragment` / `CustomiseViewModel` |
| Onboarding (name the pet) | ✅ Done | `OnboardingActivity` / `OnboardingViewModel` |
| Chat UI | ✅ Skeleton done | `ChatFragment` / `ChatViewModel` / `ChatRepository` |
| Pet rendering | ✅ scene-3 rabbit (three.js, offline) via the HTML/WebView pipeline; add-ons are primitive placeholders | `HtmlManiengineRenderer` + `assets/maniengine/` |
| Item catalog | ⏳ Placeholder (6 hard-coded items: hats and accessories) | `AccessoryCatalog` → Maniengine Agent |
| TruFar backend | ✅ Relay to Kafka on trufar.cn (`backend/`), set up for Vercel (free, no card) with Firestore on Spark, but not yet deployed there; tested locally; PythonAnywhere free confirmed blocked; Cloud Run packaging kept as an alternative. OpenAI-compatible client as an alternative; mock when unconfigured | `HttpTruFarChatService` (`TRUFAR_API_URL` → relay) / `OpenAiCompatibleChatService` (`TRUFAR_LLM_URL`) / `MockTruFarChatService` |
| Doom-scroll detection | ✅ Usage Access polling in a foreground service; portrait only, 1 HP per minute, warnings every 10 minutes (not yet tested on a device) | `UsageStatsDoomScrollMonitor` |

Known gaps and TODOs (not assigned to anyone yet):
- The relay (`backend/`) has been tested against a fake TruFar (pytest plus an HTTP smoke test)
  and deployed once to PythonAnywhere free (it ran, but trufar.cn was blocked). Not yet tested:
  - Vercel, Firestore, and a real trufar.cn reply
  - which MCP transport TruFar's client uses (the relay speaks Streamable HTTP with JSON responses)
  - how long the TruFar cookie lasts
- All app sessions share one TruFar conversation (`TRUFAR_CONVERSATION_ID`). Per-user
  conversations need the "new chat" request captured from trufar.cn.
- The chat transcript isn't persisted, and quiz replay protection only lasts for the process
  lifetime. Real backends must use globally unique quiz IDs.
- Non-UI user-facing text (MCP results, chat notices, mock bot) is inline English, not in `strings.xml`.
- Nothing happens yet when HP reaches 0 beyond the "Fainted" status label.
- No instrumented/UI tests yet. JVM tests only (`model`, `doomscroll`, `trufar`, `maniengine`),
  plus pytest for `backend/`.
