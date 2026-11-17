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
| chatbot, TruFar, LLM, backend, quizzes, MCP tools, chat networking | **TruFar/MCP Agent** | §4 `TruFarChatService` + `McpPetActionHandler`, §5.2 | new package `com.example.tomodoom.trufar` (and/or `.mcp`), manifest INTERNET permission |
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

The app has two bottom-navigation tabs: **Pets** (HP, pet viewport, customisation) and **Chat**
(TruFar chatbot + quizzes).

### Tech stack (fixed — don't change it without updating this file)

| Item | Value                                                                                                                                                                    |
|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Language | **Java only** (no Kotlin sources)                                                                                                                                        |
| Java source level | **1.8**. Lambdas and method refs are fine. **No** `var`, switch expressions, records, text blocks, `List.of`/`Map.of`/`Set.of`, `String.isBlank()` or any other Java 9+ API |
| Android | `compileSdk = 34`, `targetSdk = 34`, **`minSdk = 24`** (check API levels before calling newer APIs)                                                                      |
| Build | AGP 8.1.1, Gradle 8.0 wrapper, Kotlin DSL build scripts (`*.gradle.kts`)                                                                                                 |
| UI | XML layouts + **ViewBinding**, `Activity`/`Fragment`, Jetpack Navigation + `BottomNavigationView`, Material Components (`Theme.MaterialComponents.Light.DarkActionBar`, light only)  |
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
├── MainActivity.java             Single activity; BottomNavigationView ↔ NavController (Pets | Chat)
│
├── model/                        Immutable, pure-Java value objects (no android.* imports)
│   ├── PetState                  name, currentHp, maxHp, equipped items (slot → itemId)
│   ├── AccessorySlot             slot IDs: HAT="hat", OUTFIT="outfit", ACCESSORY="accessory"
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
├── di/
│   ├── AppContainer              THE ONLY PLACE implementations are chosen ("SWAP POINT" comments)
│   └── ViewModelFactory          constructor injection for ViewModels
│
├── viewmodel/
│   ├── PetsViewModel             exposes PetState, selected slot, equip/debug actions
│   └── ChatViewModel             exposes transcript + active quiz, sends messages
│
└── ui/
    ├── pets/PetsFragment         HP header, pet viewport (FrameLayout), customise FAB → right-side
    │                             DrawerLayout panel (END drawer) holding the customisation controls
    └── chat/ChatFragment, ChatAdapter   RecyclerView (3 view types) + input bar
```

Resources: `res/values/colors.xml` + `res/values/themes.xml` (palette and theme, §6.3; no
`values-night/`), `res/layout/fragment_pets.xml` (root `DrawerLayout` `pets_drawer`: main content + `<include>` of
`panel_customise.xml` as the END drawer), `fragment_chat.xml`, `item_chat_{user,bot,system}.xml`,
`item_accessory_chip.xml`, `res/drawable/ic_hanger_24dp.xml` (customise FAB), `ic_close_24dp.xml`, `res/menu/bottom_nav_menu.xml`, `res/navigation/mobile_navigation.xml`.

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

 DoomScrollMonitor ─► DoomScrollListener (= DoomScrollHpBridge) ─► PetStateRepository.decreaseHp(...)
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
  - `PetState resetPetState()`: wipes the prefs back to `PetState.createDefault()`.
- Persistence: prefs file **`tomodoom_pet_state`**, keys `pet_name`, `current_hp`, `max_hp`,
  `equipped.<slotId>`. **Only `PetStateRepository` may read or write this file.** A write that
  bypasses it won't reach LiveData observers and will be overwritten.
- `PetState` is **immutable**. Make modified copies with `withHp(...)` / `withEquippedItem(...)`.
  Never hold on to a state and assume it is current. Re-read it or observe it.

### 3.2 Threading contract

| Rule | Why |
|---|---|
| Every repository mutator is `synchronized` and can be called from **any** thread | Doom-scroll services and network callbacks run off the main thread |
| Repositories publish **only with `postValue()`, called while holding the repository lock**, and **never** call `setValue()` | Mixing the two lets an older pending `postValue` overwrite a newer `setValue`. With all posts under one lock, the last value delivered is always the newest snapshot |
| A consequence of the above: right after a mutation, `getPetState().getValue()` may be one frame stale | Use `getCurrentState()` (or the mutator's return value) when you need the value straight away |
| SharedPreferences writes use `apply()`, never `commit()` | `apply()` updates memory synchronously and writes to disk asynchronously, so the caller never blocks |
| Contract callbacks (`TruFarChatService.Listener`, `DoomScrollListener`) **may arrive on any thread** | Consumers (repositories) are thread-safe. UI code only sees LiveData, which is always delivered on the main thread |
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

| Interface | Default implementation (in `stubs`) | Owner agent | Where it's wired |
|---|---|---|---|
| `ManiengineAssetRenderer` | `PlaceholderManiengineRenderer` | **Maniengine Agent** | `AppContainer.createAssetRenderer()` (factory: new instance per Pets view) |
| `TruFarChatService` | `MockTruFarChatService` | **TruFar/MCP Agent** | `AppContainer` constructor, `// SWAP POINT (TruFar/MCP Agent)` |
| `McpPetActionHandler` | `DefaultMcpPetActionHandler` (production-capable) | **TruFar/MCP Agent** | `AppContainer` constructor, `// SWAP POINT (TruFar/MCP Agent)` |
| `DoomScrollMonitor` (+ `DoomScrollListener`) | `StubDoomScrollMonitor` | **Doom Scroll Agent** | `AppContainer` constructor, `// SWAP POINT (Doom Scroll Agent)` |

The full Javadoc on each interface is normative. What follows is a summary.

### `ManiengineAssetRenderer`
```java
@MainThread void render(@NonNull ViewGroup container, @NonNull PetState state);
@MainThread void release(@NonNull ViewGroup container);
```
- `container` is the `FrameLayout` `R.id.pet_viewport` in `fragment_pets.xml`. The renderer owns
  all of its children. Nothing else adds views to it.
- `render` is called on **every** `PetState` emission (HP changes as well as equip changes), so it
  must be idempotent and cheap: diff against what's already displayed and update in place.
- `release` is called from `PetsFragment.onDestroyView()`. Remove the child views, cancel animations
  and free bitmaps. The instance is discarded afterwards (a new view gets a new instance).
- Asset/file format is **undecided**. Nothing outside the renderer may assume one.

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
boolean isPermissionGranted();
void setListener(@Nullable DoomScrollListener listener);

interface DoomScrollListener { void onDoomScrollDetected(@NonNull DoomScrollEvent event); }  // ANY thread
```
- `startMonitoring()`/`stopMonitoring()` are idempotent. `startMonitoring()` is a no-op that logs
  if the permission isn't granted.
- `AppContainer` sets the listener to `DoomScrollHpBridge`, which calls
  `PetStateRepository.decreaseHp(event.getHpPenalty(), "doom_scroll:" + pkg)`. **The monitor never
  touches the repository directly.** It reports events, and the monitor decides `hpPenalty`
  (default `DoomScrollEvent.DEFAULT_HP_PENALTY` = 5).
- `TomodoomApplication.onCreate()` calls `startMonitoring()` if `isPermissionGranted()`.

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
- **Implement:** `contracts.ManiengineAssetRenderer`.
- **Replace:** `stubs.PlaceholderManiengineRenderer` → return your class from
  `AppContainer.createAssetRenderer()`.
- **Inputs:** `PetState` (HP for mood/animation, `getEquippedItems()` for the layers, keyed by
  `AccessorySlot` IDs and holding `AccessoryItem` IDs).
- **You also own:** `model.AccessoryCatalog`. Replace the placeholder items with your real asset
  manifest, keeping the item IDs stable once shipped (they're persisted in prefs), and the asset
  files under `app/src/main/assets/` or `res/` in whatever format you choose.
- **Layout note:** the customise FAB (`customise_fab`, ~56dp, a sibling of the viewport) floats over
  the viewport's top-right corner about 12dp in from the edges, so keep essential art clear of it. The
  customisation controls are in the right-side panel (`panel_customise.xml`), not under the viewport.
- **Acceptance:** equipping/unequipping on the Pets tab updates the viewport immediately. Rotating
  or switching tabs doesn't leak views (`release` cleans up). Nothing happens off the main thread
  in `render`.

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
- **Must:** emit `DoomScrollEvent`s with a sensible `hpPenalty`, debounce so one session doesn't
  drain all the HP, never touch `PetStateRepository` directly, and stay in the default process.
- **Acceptance:** after the permission is granted, a long scroll session in a target app lowers HP
  on the Pets tab. Battery impact is documented in your commit message.

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
- **Theme:** `Theme.MaterialComponents.Light.DarkActionBar`, not Material3. Use
  MaterialComponents attributes (`?attr/textAppearanceHeadline6`, `?attr/materialButtonOutlinedStyle`).
  The app is **light only**: don't add `values-night/` resources. Colours follow §6.3.
- **Navigation:** the host is a `FragmentContainerView` NavHostFragment. Get the `NavController`
  from `NavHostFragment#getNavController()`, not `Navigation.findNavController(activity, id)`.
  A new top-level tab means a menu item **and** a nav-graph destination with the same id, plus that
  id in `MainActivity`'s `AppBarConfiguration`.
- **Dependencies** must be compatible with compileSdk 34 / AGP 8.1.1. Many current AndroidX
  releases need compileSdk 35+, so check before you bump anything. Pin exact versions.
- **Pets customisation panel (`DrawerLayout`):** `androidx.drawerlayout` (1.1.1) and
  `androidx.activity` come in transitively as compile-scope dependencies of Material. Don't pin
  drawerlayout 1.1.1 in `build.gradle.kts`: that adds a `GradleDependency` lint warning. Rules:
  - Drawer content must be clickable or taps on empty panel space fall through to the views beneath.
    `PetsFragment` sets it in Java, because XML `clickable` triggers `KeyboardInaccessibleWidget`.
  - When DrawerLayout restores an open drawer it fires **no** `DrawerListener` callback, so
    `PetsFragment` re-syncs the lock mode and Back callback in `onViewStateRestored`.
  - Back closes the panel through a view-lifecycle `OnBackPressedCallback` that is only enabled
    while the panel is open.
  - The drawer is `LOCK_MODE_LOCKED_CLOSED` while closed (no edge swipe; it opens only from the
    FAB) and unlocked while open.
  - Lint flags `Overdraw` on an included drawer root that has a background. It's a false positive
    and is suppressed with `tools:ignore`.
- **Switch on strings** works with the `AccessorySlot` / `McpPetActionHandler.TOOL_*` constants
  because they are compile-time constants. Keep new constants `static final` literals.

---

### 6.3 Colour palette & theming

The app uses one fixed, light-only palette, defined as tokens in `res/values/colors.xml`:

| Token | Hex | Role | Theme attributes it feeds |
|---|---|---|---|
| `floral_white` | `#FBF7EF` | Background and surfaces; text/icons on dark fills | `android:windowBackground`, `android:colorBackground`, `colorSurface`, `colorOnPrimary`, `colorOnSecondary` |
| `eggshell` | `#F7F0DE` | Borders, dividers, subtle fills (bot bubble, system notice) | none (use directly or through a semantic alias) |
| `charcoal` | `#585B5E` | Primary: action bar, user bubble, buttons, selected chip/tab, progress, input underline | `colorPrimary`, `colorSecondary` |
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
  (as the pet viewport does), and cards are flat (`cardElevation="0dp"`) with a 1dp eggshell stroke.
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

**macOS / Linux (JDK 17 on PATH, Linux/macOS SDK):**
```bash
./gradlew assembleDebug                # full debug APK build (compile + resource linking + dex)
./gradlew compileDebugJavaWithJavac    # fast Java-only compile check
./gradlew testDebugUnitTest            # JVM unit tests (app/src/test)
./gradlew lintDebug                    # Android lint → app/build/reports/lint-results-debug.html
./gradlew installDebug                 # install on a connected device/emulator
```

**Windows (PowerShell / cmd), using Android Studio's JBR:**
```bat
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
gradlew.bat assembleDebug testDebugUnitTest
```

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
`lintDebug` reports **0 errors**. The current lint baseline is two accepted warning types:
`OldTargetApi` (`targetSdk = 34` is fixed by the stack) and `GradleDependency` (newer AndroidX
versions require compileSdk 35+ or a newer AGP). Don't introduce new lint warnings. javac currently
builds with zero warnings, so keep it that way.

### 7.2 Unit tests

- JVM tests live in `app/src/test/java/com/example/tomodoom/<package>/…Test.java`, using JUnit 4
  (the only test dependency). See `model/PetStateTest` for the style.
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
  `DefaultMcpHandler`, `MockTruFarChatService`, `StubDoomScrollMonitor`.


1. Launch the app. The **Pets** tab shows `Tomo`, **HP 50 / 100** and status "Okay".
2. Tap the **hanger FAB** (right side, just below the HP card). The **Customise pet** panel slides in
   from the right over a dimmed scrim. Choose **Hat → Cap**, then close the panel (✕, tap the scrim,
   Back, or swipe it right). The placeholder viewport text shows `Hat: Cap`. Also check:
   - a right-edge swipe does *not* open the panel;
   - the panel stays open across rotation and across a Pets → Chat → Pets round trip;
   - the bottom nav still switches tabs while the panel is open;
   - with the panel closed, Back leaves the app as before.
3. Open **Chat**. The mock bot greets you. Type `quiz`, and the bot asks a question (e.g. "What is 2 + 2?").
4. Answer correctly (`4`). The bot confirms, and a SYSTEM message shows **"+10 HP awarded via MCP! (60/100 HP)"**.
5. Switch to **Pets**: **HP 60 / 100**.
6. In Chat, type `/tool get_pet_state` (or `/tool award_hp amount=5`). A SYSTEM message shows the MCP result.
7. Pets debug row: **Simulate doom-scroll** → HP −5. **Reset pet** → back to 50/100, nothing equipped.
8. Kill and relaunch the app. HP and equipped items persist (SharedPreferences). The chat transcript resets (in memory only).

---

## 9. Project status & known gaps

Update this table in the same commit when your task changes a subsystem's status.

| Subsystem | Status | Implementation in `AppContainer` |
|---|---|---|
| Pet state + persistence | ✅ Done | `PetStateRepository` |
| MCP tool bridge | ✅ Production-capable default | `DefaultMcpPetActionHandler` |
| Pets UI (HP header, FAB-triggered customisation side panel, debug row) | ✅ Skeleton done | `PetsFragment` / `PetsViewModel` |
| Chat UI | ✅ Skeleton done | `ChatFragment` / `ChatViewModel` / `ChatRepository` |
| Pet rendering | ⏳ Stub (text placeholder) | `PlaceholderManiengineRenderer` → Maniengine Agent |
| Item catalog | ⏳ Placeholder (9 hard-coded items) | `AccessoryCatalog` → Maniengine Agent |
| TruFar backend | ⏳ Mock (local scripted bot) | `MockTruFarChatService` → TruFar/MCP Agent |
| Doom-scroll detection | ⏳ Stub (no detection) | `StubDoomScrollMonitor` → Doom Scroll Agent |

Known gaps and TODOs (not assigned to anyone yet):
- No doom-scroll permission/onboarding UI. The Doom Scroll Agent owns it (§5.3).
- The chat transcript isn't persisted, and quiz replay protection only lasts for the process
  lifetime. Real backends must use globally unique quiz IDs.
- Non-UI user-facing text (MCP results, chat notices, mock bot) is inline English, not in `strings.xml`.
- Nothing happens yet when HP reaches 0 beyond the "Fainted" status label. No notifications.
- No instrumented/UI tests yet. Only `PetStateTest` (JVM).
- On phones, the open customisation panel (280dp) covers most of the pet viewport, so equip changes
  re-render live but are only fully visible once the panel closes. A narrower panel or a bottom
  sheet would fix that if live preview matters.
