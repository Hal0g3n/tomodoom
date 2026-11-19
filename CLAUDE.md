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
| chatbot, Gemini, LLM, personas, quizzes, MCP tools, chat networking | **Chat/MCP Agent** | §4 `ChatService` + `McpPetActionHandler`, §5.2 | new package `com.example.tomodoom.chat` (and/or `.mcp`), manifest INTERNET permission |
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
| You pass a quiz from the chatbot | Pet **gains** HP (applied via an MCP tool) | Gemini chat + MCP bridge |
| You stay out of the app for over 30 minutes | Pet **loses** 30% of its HP per 8 h away, applied when you return | `InactivityDecayTracker` |
| You equip clothes or accessories | The pet's look changes | Maniengine renderer |

The app has five bottom-navigation tabs, left to right: **Customise** (live pet preview + slot tabs
and a grid of item circles), **Pets** (HP and the pet viewport; the default page on launch), **Chat**
(Gemini chatbot + quizzes) and **Stats** (time since the last doom-scroll, doom-scrolling per day and
app, the pet's health over 24 h / 7 days, and LLM tokens per model) and **Settings** (rename the pet,
switch to another pet, choose the tracked apps, chat text size). There is no action bar on any tab. The bottom bar shows icons only: the
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
├── TomodoomApplication.java      Application subclass; owns the AppContainer; starts the monitor;
│                                 counts started activities → ChatRepository background/foreground
├── MainActivity.java             Main activity (not exported); swipeable ViewPager2 (Customise | Pets | Chat | Stats) ↔ BottomNavigationView;
│                                 starts on Pets; Back off Pets → Pets; no action bar (NoActionBar theme);
│                                 hidden reset: long-press the Wardrobe tab → confirm → wipe all data;
│                                 hidden debug panel: long-press the Pets tab (all builds)
│
├── model/                        Immutable, pure-Java value objects (no android.* imports)
│   ├── PetState                  name, currentHp, maxHp, equipped items (slot → itemId), petSpecies
│   ├── PetSpecies                pet model IDs: RABBIT="rabbit" (default), CAT="cat" (= pet-scene.js PETS keys)
│   ├── PetNames                  pet-name validation: trim, collapse whitespace, 1–20 chars
│   ├── PetMood                   THRIVING/OKAY/STRUGGLING/FAINTED from HP (the only HP thresholds)
│   ├── AccessorySlot             slot IDs: HAT="hat", ACCESSORY="accessory"
│   ├── AccessoryItem             id, slot, displayName
│   ├── AccessoryCatalog          placeholder item list per slot (Maniengine agent may replace it)
│   ├── ChatMessage               id, type (USER | BOT | SYSTEM), text, timestamp
│   ├── QuizPrompt                quizId, question, options, hpReward
│   ├── QuizEvaluation            quizId, passed, hpReward, feedback
│   ├── McpToolResult             success, message, data map
│   ├── DoomScrollEvent           sourcePackage, durationMillis, hpPenalty, timestamp
│   ├── WatchedApps               default watched-app package IDs + set/sort helpers for the picker
│   ├── InstalledApp              packageName, label (a launcher app in the watched-apps picker)
│   └── ChatTextScale             chat text sizes offered in Settings (0.8–1.5×, default 1.0) + snapping
│
├── contracts/                    Interfaces that downstream agents implement (see §4)
│   ├── ManiengineAssetRenderer
│   ├── ChatService         (+ nested ChatService.Listener)
│   ├── McpPetActionHandler
│   ├── DoomScrollMonitor
│   ├── DoomScrollListener
│   └── TokenUsageListener        tokens per successful LLM request (model, in, out, total)
│
├── repository/                   Reactive state holders and domain glue
│   ├── PetStateRepository        SINGLE SOURCE OF TRUTH for pet state (SharedPreferences + LiveData)
│   ├── ChatRepository            app-scoped chat transcript; routes chat events → MCP handler;
│   │                             new conversation after 10 min in the background
│   ├── BackgroundTimer           pure Java: how long the app was in the background
│   ├── DoomScrollHpBridge        DoomScrollListener → PetStateRepository.decreaseHp(...)
│   ├── InactivityDecayPolicy     pure Java: HP lost to time away (30 min grace, keeps 70% per 8 h)
│   ├── InactivityDecayTracker    time away (prefs tomodoom_decay) → decreaseHp on return + notice
│   ├── SettingsRepository        Settings page preferences (prefs `tomodoom_settings` + LiveData): chat text scale
│   ├── WatchedAppsRepository     the user's watched apps (prefs `tomodoom_doomscroll` + LiveData); read by the monitor
│   ├── StatsRepository           Stats page history (prefs `tomodoom_stats` + LiveData<StatsSnapshot>)
│   └── StatsRecordingDoomScrollListener   records each DoomScrollEvent in StatsRepository, then forwards
│
├── stubs/                        Stubs and default implementations of the contracts
│   ├── PlaceholderManiengineRenderer   text-only placeholder in the pet viewport
│   ├── MockChatService                 echo bot + scripted quiz flow + /tool debug command
│   ├── DefaultMcpPetActionHandler      REAL implementation wired to PetStateRepository
│   └── StubDoomScrollMonitor           no-op monitor with a debug trigger
│
├── chat/                         Chat/MCP Agent: the Gemini chat client (§5.2)
│   ├── GeminiChatService         runs the pet's personas on Gemini's OpenAI-compatible endpoint
│   ├── AgentProfile, AgentProfileLoader   agent export (assets/agents/*.json) → persona + model settings;
│   │                             withPetName() swaps the agent's name for the pet's
│   ├── PersonaSelector           pure Java: Kafka (low HP) / Charon (high HP) with 40/60 hysteresis
│   ├── PersonaPrompts            pure Java: the one-turn hidden mood-shift note after a switch
│   ├── QuizMarkerParser          pure Java: strips [[CORRECT]]/[[WRONG]] grading markers from replies
│   ├── ChatHistoryWindow         pure Java: last-N-turns conversation memory
│   ├── ModelFallback             pure Java: model list parsing + retry/fallback policy for overloaded LLMs
│   ├── TokenUsageParser          pure Java: the OpenAI-compatible `usage` object → token counts
│   └── JsonHttpClient            package-private HTTPS + JSON helper used by GeminiChatService;
│                                 non-2xx → HttpStatusException (an IOException with the status code)
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
│   ├── DoomScrollMonitorService  foreground service that keeps polling alive
│   └── LauncherAppsQuery         lists launcher apps (label + package) for the watched-apps picker
│
├── stats/                        pure Java: the Stats page's ledgers (JSON-serialisable, JUnit-tested)
│   ├── TokenUsageLedger          requests + prompt/completion/total tokens per model, all time
│   ├── HpHistory                 HP step samples (5-min coalescing, 8 days) + daily HP gained/lost by cause
│   ├── DoomScrollLog             counted time per local day and app (30 days), sessions, clean streaks
│   ├── LocalDays                 local-day numbers without java.time
│   ├── StatsSnapshot             immutable copy of the three ledgers (what StatsRepository publishes)
│   ├── StatsReport               the numbers the page shows, computed for a time + time zone
│   └── DurationSplit             a duration → its two largest units ("2 h 15 min")
│
├── di/
│   ├── AppContainer              THE ONLY PLACE implementations are chosen ("SWAP POINT" comments)
│   └── ViewModelFactory          constructor injection for ViewModels
│
├── viewmodel/
│   ├── PetsViewModel             exposes PetState + the inactivity-decay notice
│   ├── DebugViewModel            debug panel actions, each through the feature's real path (§8)
│   ├── CustomiseViewModel        exposes PetState, selected slot, catalog items, equip action
│   ├── OnboardingViewModel       onboarding flag, suggested name, species list + selectedSpecies, submitName(),
│   │                             permission-step state (usage skipped, notifications handled)
│   ├── OnboardingPermissionFlow  pure Java: next permission step (Usage Access → notifications → done)
│   ├── ChatViewModel             exposes transcript + active quiz, sends messages
│   ├── StatsViewModel            StatsReport per snapshot / refresh(); selected health-chart range
│   ├── SettingsViewModel         rename (PetNames), species switch, watched-app count, chat text scale
│   └── WatchedAppsViewModel      installed apps (loaded once, off the main thread) + watched set, toggles
│
└── ui/
    ├── onboarding/OnboardingActivity   LAUNCHER: picks + names the pet on first launch only, then asks
    │                             for Usage Access, then notifications; otherwise forwards straight to
    │                             MainActivity and finishes
    ├── NotificationPrompt        Android 13+ notification permission; one "asked this launch" flag
    │                             shared by onboarding and Pets
    ├── onboarding/PetChoiceAdapter, PetPortraits   two-column pet portrait cards; species ID → portrait/name
    │                             (public: the Settings page uses the same cards)
    ├── MainPagerAdapter          FragmentStateAdapter: page index ↔ bottom-nav item id
    │                             (Customise=0, Pets=1 = default, Chat=2, Stats=3, Settings=4)
    ├── PetStage                  MainActivity's ONE pet renderer, kept in `pet_layer` behind the pager and
    │                             framed onto the resumed page's viewport (shrinks Pets ↔ Customise on swipe)
    ├── PetTransitionMath         pure Java: frame for a pager position (lift included), model box
    ├── PetLayer                  `pet_layer`: holds the pet for good; takes only forwarded touches
    ├── PetViewport               `pet_viewport` on Pets/Customise: forwards its touches to the PetLayer
    ├── AppReset                  the hidden full reset (confirm dialog + clearApplicationUserData)
    ├── debug/DebugPanelFragment  hidden debug bottom sheet (long-press the Pets tab)
    ├── customise/CustomiseFragment   pet preview viewport (via PetStage) + full-width slot tabs + item grid
    ├── customise/AccessoryGridAdapter, AccessoryThumbnails   two-column item circles; item ID → thumbnail
    ├── pets/PetsFragment         full-page pet viewport (FrameLayout) under the Usage Access card and health bar
    ├── chat/ChatFragment, ChatAdapter   RecyclerView (3 view types) + input bar;
    │                             bot bubbles: prose (Markdown + LaTeX) and code blocks, in order
    ├── chat/ChatMarkdown         Markwon (inline parser + JLatexMath) for bot prose; caches parsed text
    ├── chat/MarkdownSegments     pure Java: a reply → prose / fenced-code segments
    ├── chat/LatexNormalizer      pure Java: \(…\), \[…\], $…$, $$…$$ → Markwon's $$-only syntax
    ├── stats/StatsFragment       four cards; refreshes every 30 s while visible
    ├── settings/SettingsFragment four cards: pet name, pet species, tracked apps (→ WatchedAppsActivity
    │                             or Usage Access settings), chat text size slider + preview bubble
    ├── stats/HpLineChartView, BarChartView   Canvas charts in the palette (no chart library)
    ├── stats/StatsFormatter      durations, token counts and app labels → display text
    └── watchedapps/WatchedAppsActivity, WatchedAppsAdapter   picker: one switch per launcher app
                                  (opened from the Pets Usage Access card; not exported)
```

Resources: `res/values/colors.xml` + `res/values/themes.xml` (palette and theme, §6.3; no
`values-night/`), `res/layout/fragment_pets.xml` (a `ConstraintLayout`: a borderless `pet_viewport` fills the page, with
`usage_access_card` (Usage Access prompt, §5.3) at the top and `hp_card` (the health bar) at the bottom
floating over it; both cards are transparent),
`fragment_customise.xml` (`pet_viewport` at 40% of the page height, at the top with no title, `slot_tabs`,
`item_grid` RecyclerView), `fragment_chat.xml`, `item_chat_{user,bot,system}.xml`
(`item_chat_bot.xml` is an empty `message_container` filled with `item_chat_bot_prose.xml` and
`item_chat_code_block.xml` + `drawable/bg_chat_code_block.xml`),
`item_accessory_circle.xml`, `activity_watched_apps.xml` + `item_watched_app.xml` (watched-apps
picker), `res/drawable/bg_accessory_circle.xml`, `ic_item_none_24dp.xml`,
`res/drawable-nodpi/thumb_<itemId>.png` and `thumb_pet_<speciesId>.png` (GENERATED item thumbnails and
pet portraits, §5.1), `item_pet_choice.xml` + `res/drawable/bg_pet_choice.xml` (onboarding pet cards),
`res/drawable/ic_hanger_24dp.xml` (Customise tab icon), `ic_stats_24dp.xml` (Stats tab icon), `ic_settings_24dp.xml` (Settings tab icon, a generated gear),
`fragment_stats.xml` + `item_stats_row.xml` + `values/styles_stats.xml` (Stats page cards; `Widget.Tomodoom.StatsCard`
is reused by `fragment_settings.xml`, the Settings page),
launcher icon: `mipmap-anydpi-v26/ic_launcher{,_round}.xml` (adaptive: `@color/launcher_background`
+ GENERATED `drawable/ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml`) and GENERATED legacy
`mipmap-*/ic_launcher{,_round}.webp` for API 24–25, GENERATED `drawable/ic_notification.xml` (notification
small icon, used by both doom-scroll notifications),
`activity_main.xml` (`pet_layer` under `main_pager` ViewPager2, `nav_view`), `activity_onboarding.xml` (`name_step`: title,
`pet_choices` grid, `pet_name_layout`/`pet_name_input`, `start_button`; then `permissions_step`:
`usage_row`/`notifications_row` (the current one drawn selected), `permission_button`, `permission_skip_button`), `res/menu/bottom_nav_menu.xml`,
`fragment_debug_panel.xml` + `values/strings_debug.xml` + `values/styles_debug.xml` (debug panel).
`res/values/dimens.xml` (`pet_viewport_stroke`, `pet_viewport_corner_radius`).
`res/font/` + `res/font-v26/` (`playpen_sans`, `unkempt` families and their static TTFs; §6.3) and
`res/font/fira_code_retina.ttf` (code), with the font licences in `assets/licenses/`.
Assets: `assets/agents/kafka_agent.json` and `charon_agent.json` (the two persona exports from the TruFar editor, icons stripped; §5.2),
`assets/maniengine/index.html` (the pet page, §5.1), `pet-scene.js` (model setup + add-ons, shared with the
thumbnail tool; `PETS` profiles per model), `models/rabbit.js` and `models/cat.js` (GENERATED from the scene-3
and scene-5 exports) and `vendor/three/` (three.js 0.187.0).
Tools: `tools/maniengine/extract_model.py` (+ `source/scene-3.html`, `source/scene-5.html`), `make_thumbnails.py` (+ `thumbnails.html`);
`tools/launcher_icon/make_launcher_icon.py` (+ `logo.svg`, the app logo) regenerates every launcher-icon file
listed above, plus the GENERATED full-bleed PNGs `app/src/main/ic_launcher-playstore.png` (512px, Play
Store; outside `res/`, so not packaged) and `tools/launcher_icon/export/ic_launcher-{1024,2048}.png`
(`EXPORTS`, for stores, splash art and large-icon launchers) (needs `rsvg-convert` and Pillow; WSL has both). Edit the logo, then re-run it.

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
                              ChatService  (Gemini, or the offline mock)
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

 DoomScrollMonitor ─► DoomScrollNotifier ─► StatsRecordingDoomScrollListener ─► DoomScrollHpBridge
                                                       │                             │
                                                       ▼                             ▼
                                          StatsRepository.recordDoomScroll   PetStateRepository.decreaseHp(...)

 Stats inputs: PetStateRepository ─(HpChangeListener)─► StatsRepository.recordHpChange
               GeminiChatService  ─(TokenUsageListener)─► StatsRepository.recordTokens
               StatsRepository ─► LiveData<StatsSnapshot> ─► StatsViewModel ─► StatsFragment
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
  - `boolean isOnboardingComplete()` / `PetState completeOnboarding(String petName, String species)`:
    the onboarding flag; `completeOnboarding` also sets the name (validate it with
    `PetNames.normalize`) and the `PetSpecies` (unknown → default). A one-argument overload keeps
    the current species. `resetPetState()` keeps the species too.
  - `PetState renamePet(String petName)` / `PetState changeSpecies(String species)`: the Settings
    page's edits. They keep HP, equipped items and the onboarding flag; validate the name with
    `PetNames.normalize` first (empty throws), and an unknown species becomes the default.
- Persistence: prefs file **`tomodoom_pet_state`**, keys `pet_name`, `current_hp`, `max_hp`,
  `equipped.<slotId>`, `onboarding_complete`, `pet_species` (a `PetSpecies` ID; missing or unknown →
  `rabbit`). **Only `PetStateRepository` may read or write this file.** A write that
  bypasses it won't reach LiveData observers and will be overwritten.
- **Hidden reset** (`ui.AppReset`, from a long-press on the Wardrobe tab or the debug panel's
  Reset button, then confirm): calls
  `ActivityManager.clearApplicationUserData()`, which wipes *all* app data (this prefs file,
  WebView storage) and kills the process, so the next launch is a fresh install with onboarding.
  It's the one sanctioned bypass of the repository, since nothing survives to observe it.
- **HP change listener:** `setHpChangeListener(HpChangeListener)` (one listener, wired in
  `AppContainer` to `StatsRepository::recordHpChange`) is told `(previous, next, reason)` after every
  HP change from `increaseHp`/`decreaseHp` and after `resetPetState` (reason `"reset"`). It runs on the
  mutating thread **while the repository lock is held**, so it must be quick and must never call
  back into `PetStateRepository` (deadlock).
- `PetState` is **immutable**. Make modified copies with `withHp(...)` / `withName(...)` /
  `withEquippedItem(...)` / `withPetSpecies(...)`.
  Never hold on to a state and assume it is current. Re-read it or observe it.

### 3.2 Threading contract

| Rule | Why |
|---|---|
| Every repository mutator is `synchronized` and can be called from **any** thread | Doom-scroll services and network callbacks run off the main thread |
| Repositories publish **only with `postValue()`, called while holding the repository lock**, and **never** call `setValue()` | Mixing the two lets an older pending `postValue` overwrite a newer `setValue`. With all posts under one lock, the last value delivered is always the newest snapshot |
| A consequence of the above: right after a mutation, `getPetState().getValue()` may be one frame stale | Use `getCurrentState()` (or the mutator's return value) when you need the value straight away |
| SharedPreferences writes use `apply()`, never `commit()` | `apply()` updates memory synchronously and writes to disk asynchronously, so the caller never blocks |
| Contract callbacks (`ChatService.Listener`, `DoomScrollListener`) **may arrive on any thread** | Consumers (repositories) are thread-safe. UI code only sees LiveData, which is always delivered on the main thread |
| `DoomScrollMonitor.startFromForeground()` is **main-thread only**, called while the app is visible | Android 12+ blocks starting a foreground service from the background |
| `ManiengineAssetRenderer` methods are **main-thread only** | They touch views. Do decoding/loading on a background thread inside the implementation, then post back to the main thread |
| Don't block the main thread: no network or disk I/O apart from the repositories' first prefs load | ANR safety |
| The whole app runs in **one process**. Don't declare `android:process` on services | SharedPreferences and in-memory singletons aren't shared across processes |

### 3.3 Other state

- `ChatRepository` keeps the chat transcript **in memory only** (it survives tab switches and
  rotation, but not process death). Persisting it is up to the Chat/MCP agent, if needed.
- **New conversation after inactivity:** `TomodoomApplication` counts started activities. When
  the count goes 1 → 0 it calls `ChatRepository.onAppBackgrounded`, and 0 → 1 calls
  `onAppForegrounded`, both with `SystemClock.elapsedRealtime()`. If the app was away for
  `ChatRepository.INACTIVITY_RESET_MILLIS` (10 minutes) or more and the chat had been opened,
  `startNewConversation()` runs:
  1. `ChatService.disconnect()`;
  2. clear the transcript and the active quiz;
  3. `connect()`, which sends a new greeting.

  Rotation and switching between activities don't count. The doom-scroll foreground service
  isn't an activity, so it doesn't keep the chat alive.
- **Inactivity HP decay:** the same 1 → 0 / 0 → 1 activity count also calls
  `InactivityDecayTracker.onAppBackgrounded` / `onAppForegrounded`, with the **wall clock**
  (`System.currentTimeMillis()`), since the time away must survive a reboot. The tracker owns prefs
  file **`tomodoom_decay`**, key `away_since_ms` (removed on return). On return it calls
  `PetStateRepository.decreaseHp(loss, "inactivity_decay")` (stats count it as "other" HP lost)
  and posts `loss` to `getPendingNotice()`; `PetsFragment` shows it once as a Snackbar while it is
  the resumed page. `InactivityDecayPolicy.hpLoss`: no loss for 30 min, then
  `hp × 0.7^(t / 8 h)` remains (100 HP → ~70 after 8.5 h, ~12 after 2 days, 0 after a week). It is
  proportional and memoryless on purpose: frequent check-ins never cost more than staying away.
  No decay before onboarding, on first launch, if the clock went backwards, or if the process died
  while visible (no stamp). Rotation and switching our own activities don't count as away.
- `GeminiChatService` saves the persona in use in its own prefs file **`tomodoom_chat`**, key
  `persona` (`low_hp` | `high_hp`), so a new conversation or a cold start keeps the personality.
  This is separate from `tomodoom_pet_state`, which only `PetStateRepository` touches.
- `WatchedAppsRepository` owns prefs file **`tomodoom_doomscroll`**, key `watched_packages` (a
  string set of package IDs; missing → `WatchedApps.DEFAULT_PACKAGES`). It follows the §3.2 rules
  (synchronized mutators, `postValue` under the lock, `apply()`), and only it touches that file.
  The monitor reads `getCurrent()` on its polling thread at every poll.
- `StatsRepository` owns prefs file **`tomodoom_stats`**: `tracking_since` (ms, set on first run;
  nothing earlier is known), `token_usage` (JSON array), `hp_history` and `doom_scroll_log` (JSON
  objects; the ledgers' own `toJson`). Same §3.2 rules: synchronized recorders callable from any
  thread, `postValue` under the lock, `apply()`. Each record rewrites that ledger's JSON (HP history
  is at most ~2,500 samples). It records HP through the listener above, tokens through
  `TokenUsageListener` (only `GeminiChatService` reports; the mock bot uses none) and doom-scrolling
  through `StatsRecordingDoomScrollListener`. The hidden reset wipes it with everything else.
  `DoomScrollLog` splits sessions on gaps over 2 minutes (the monitor reports one event per counted
  minute), so its "session" is wall-clock span, while the per-day minutes are counted time.
- `SettingsRepository` owns prefs file **`tomodoom_settings`**, key `chat_text_scale` (float, a
  `ChatTextScale` step; missing → 1.0, other values snap to the nearest step). Same §3.2 rules.
- ViewModels hold only UI state (e.g. the selected customisation tab). Domain state lives in
  repositories.

---

## 4. Contracts (the `contracts` package)

| Interface | Implementation (stub in `stubs`) | Owner agent | Where it's wired |
|---|---|---|---|
| `ManiengineAssetRenderer` | `maniengine.HtmlManiengineRenderer` (stub: `PlaceholderManiengineRenderer`) | **Maniengine Agent** | `AppContainer.createAssetRenderer()` (factory: one instance per `MainActivity`, held by `ui.PetStage`) |
| `ChatService` | `MockChatService` | **Chat/MCP Agent** | `AppContainer` constructor, `// SWAP POINT (Chat/MCP Agent)` |
| `McpPetActionHandler` | `DefaultMcpPetActionHandler` (production-capable) | **Chat/MCP Agent** | `AppContainer` constructor, `// SWAP POINT (Chat/MCP Agent)` |
| `DoomScrollMonitor` (+ `DoomScrollListener`) | `StubDoomScrollMonitor` | **Doom Scroll Agent** | `AppContainer` constructor, `// SWAP POINT (Doom Scroll Agent)` |
| `TokenUsageListener` | `StatsRepository::recordTokens` (Core) | reported by the **Chat/MCP Agent**'s `GeminiChatService` | `AppContainer` constructor, passed to `GeminiChatService` |

The full Javadoc on each interface is normative. What follows is a summary.

### `ManiengineAssetRenderer`
```java
@MainThread void render(@NonNull ViewGroup container, @NonNull PetState state);
@MainThread void release(@NonNull ViewGroup container);
@MainThread default void pause(@NonNull ViewGroup container) {}    // page onPause() via PetStage
@MainThread default void resume(@NonNull ViewGroup container) {}   // page onResume() via PetStage
@MainThread default boolean supportsFrames() { return false; }
@MainThread default void setFrame(@NonNull ViewGroup container, @Nullable RectF frame, float cornerRadiusPx, float lift) {}
@MainThread default void setTouchBox(@NonNull ViewGroup container, @Nullable RectF box) {}
```
- **One renderer per `MainActivity`**, so only one pet is rendered at a time. `ui.PetStage` owns it
  and lends it to whichever page is resumed (Customise or Pets): the page calls
  `attach(viewport, state)` in `onResume`, `detach(viewport)` in `onPause`/`onDestroyView`, and
  `render(viewport, state)` on each emission (ignored unless that page owns the pet).
- **Frame-capable renderers** (`supportsFrames()`, as `HtmlManiengineRenderer` is) get **one fixed
  container for their whole life**: `MainActivity`'s `pet_layer` (a `ui.PetLayer` the size of the
  pager, *behind* it). They are never moved or resized; `PetStage` places the pet with `setFrame`
  (container px, rounded corners, plus a 0–1 `lift`: 1 on Pets, where the health bar floats over the
  bottom, 0 on Customise), sent on every scroll frame and layout change, so keep it cheap. The pet is
  drawn only in the frame, framed as in a viewport of that size, and the rest stays transparent;
  `null` = the whole container. `setTouchBox` says where drags turn the pet (`null` while scrolling).
  Touches arrive forwarded from the visible `ui.PetViewport`, already in layer coordinates.
- **Other renderers** (the placeholder): `container` is the visible page's `R.id.pet_viewport`
  (`fragment_pets.xml` or `fragment_customise.xml`). The renderer owns all of its children. **The
  container changes between calls**: `render` with a new container must *move* the existing views
  there (remove from the old parent, keep loaded state); it must not rebuild or reload. A hidden
  page's viewport may keep the paused view until another page takes it.
- `render` is called on **every** `PetState` emission (HP changes as well as equip changes), so it
  must be idempotent and cheap: diff against what's already displayed and update in place.
- `release` is called once from `MainActivity.onDestroy()` (rotation included) with the last
  container. Remove the child views, cancel animations and free bitmaps. The instance is discarded
  afterwards (the new activity gets a new instance).
- `pause`/`resume` bracket the time the current container's page is visible (pages stay alive,
  `STARTED`, while hidden). Stop per-frame work while paused. Both are optional default no-ops.
- `HtmlManiengineRenderer` forwards `setFrame`/`setTouchBox` as `ManiengineProtocol.frameScript` /
  `touchBoxScript` (deduplicated, re-sent after a page load), and keeps the WebView at alpha 0 until
  its first frame has been delivered, so a full-size pet never flashes.
- The asset format is **HTML**: Maniengine exports a 3D scene as a web page, which
  `HtmlManiengineRenderer` shows (§5.1). Nothing outside the renderer may depend on that.

### `ChatService`
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
- `ChatRepository` is the only listener. It calls `connect()` the first time the Chat tab
  opens, and `disconnect()` + `connect()` to start a new conversation after 10 minutes in the
  background (§3.3). A new session starts fresh, and events still pending for the old one are
  dropped. While a quiz is active, the user's next message goes to `submitQuizAnswer` instead of
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
  stub). `AppContainer.getDoomScrollListener()` returns that whole chain (notifier → stats → bridge),
  so the debug panel's synthetic minutes and warnings take the real path.
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
**`python3 tools/maniengine/extract_model.py export.html --pet <id>`** (stdlib only; see its README;
no arguments regenerates the rabbit). It writes `assets/maniengine/models/<id>.js`
(`buildModel(THREE, scene, renderer)` + `OVERRIDES`) and vendors the matching three.js into
`assets/maniengine/vendor/three/`. Never edit `models/*.js` by hand. Pets so far: `rabbit` (scene-3,
the default) and `cat` (scene-5).

`assets/maniengine/pet-scene.js` is hand-written. `loadPetModel(id)` imports `models/<id>.js` (an
unknown id falls back to `DEFAULT_PET` = `rabbit`); `buildPetScene(THREE, scene, renderer, model, id)`
builds it, applies `OVERRIDES`, drops the hidden room meshes plus `sky`/`ground`, and builds primitive
add-ons for the `AccessoryCatalog` IDs. Mesh names differ per export, so each pet has a **`PETS`
profile** (head, eyes, body, rug pattern, head-part pattern): a new pet needs a model file **and** a
profile, plus a `model.PetSpecies` constant and an onboarding portrait (below). `assets/maniengine/index.html`
(also hand-written) picks the pet from its `?pet=<id>` query parameter, frames the camera on it, animates
by `mood` and toggles the add-ons. `HtmlManiengineRenderer` loads `ManiengineAssets.entryUrl(state.getPetSpecies())`
and reloads the page if a later state has another species (the user picks it during onboarding). The item
thumbnails are always drawn on the default pet (add-ons aren't fitted per pet yet). Other asset types (`.glb`, textures, `.wasm`) can
still sit beside it with relative URLs.

The Customise circles are PNGs of those add-ons. After changing an add-on, run
**`python3 tools/maniengine/make_thumbnails.py`** (headless Edge) to regenerate
`res/drawable-nodpi/thumb_<itemId>.png`. A new catalog item also needs a case in
`ui/customise/AccessoryThumbnails`; `AccessoryThumbnailsTest` fails until it has one. The same run
renders each `PETS` pet whole, without its rug, to `thumb_pet_<id>.png`, the onboarding portraits; a new pet needs a
case in `ui/onboarding/PetPortraits` (portrait + name string), or `PetPortraitsTest` fails.

How the page is hosted (`HtmlManiengineRenderer`):
- Served from the virtual origin **`https://appassets.androidplatform.net/assets/maniengine/`**
  (`ManiengineAssets`), not `file://`, so ES modules, `fetch()` and WASM behave as on a web server.
  Only files under `assets/maniengine/` are served; anything else on that origin is a 404. Files
  are read on WebView's IO thread. MIME types come from the extension (`ManiengineAssets.mimeTypeFor`):
  add a case there for a new file type.
- JavaScript and DOM storage are on. File/content access is off, there's no JS interface, and the
  main frame can't navigate off the origin. Network URLs (e.g. a CDN script) still load, but bundle
  libraries locally so the pet works offline.
- The WebView background is transparent (the window's `floral_white` shows through), and it fills
  `pet_layer`. The pages are transparent over their viewports, so the pet shows through them: in the
  bordered Customise viewport (stroke only, no fill) the frame is inset by `@dimen/pet_viewport_stroke`
  with corner radius `pet_viewport_corner_radius - pet_viewport_stroke`; in the borderless Pets
  viewport it is the whole viewport, and the Pets cards draw over the pet.
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
  *starts* on the viewport. `centre` (used by `index.html`) keeps drags starting in the **model box**.
  `PetStage` works the box out (`PetTransitionMath.modelBox`, for the resumed page while the pager is
  idle) and the renderer publishes it as `window.tomodoomTouchBox` (`{left, top, right, bottom}`,
  fractions of the page): on Pets its left, right and bottom line up with
  `hp_card`, so the transparent health bar below the pet is inside it, and its top is
  `@dimen/pet_model_box_inset` from the page top. Without a health bar (Customise) it starts at the top
  with that side inset and ends 20% above the viewport's bottom.
  Drags starting outside the box change tab at any speed. Inside it, a horizontal flick reaching
  `ManiengineProtocol.TAB_FLICK_MIN_DP_PER_SEC` within `TAB_FLICK_WINDOW_MS` of touch-down also changes
  tab (`isTabFlick`). Other drags turn the `pet` group (`rotation.y`): `index.html` measures finger
  speed over the last 100 ms, so slow drags are precise, fast ones turn further and the release speed
  sets how far it coasts. The rug stays still. A second finger always keeps the gesture in the page (`PetWebView` blocks the pager on
  `ACTION_POINTER_DOWN`), which is how `index.html` pinch-zooms (`camera.zoom`, 0.75x to 3x).
- **Frame:** the host places the pet by setting
  `window.tomodoomFrame = { x, y, w, h, r, lift, hostW, hostH }` (CSS px; `lift` 0–1) and dispatching **`tomodoom:frame`**
  (`ManiengineProtocol.frameScript`; `CLEAR_FRAME_SCRIPT` sends `null`). The page draws the pet only
  inside that rect, with corner radius `r` (`setViewport`/`setScissor` plus a CSS `clip-path`), framed
  exactly as `fit()` frames a viewport of that size; the rest of the canvas stays transparent. A frame
  applies **only while the page's size is `hostW` x `hostH`** (±1px), so a frame sent just before or after
  a resize is harmless. While framed, the pet is raised by `TALL_LIFT * lift`; the host blends `lift`
  with the rect, so the pet never dips mid-swipe. (Unframed, e.g. the thumbnail tool, `fit()` still
  lifts tall viewports, blended over aspects `TALL_ASPECT - TALL_BLEND`…`TALL_ASPECT`.) Drag speeds are
  measured in frame widths. `fit()` resizes the drawing buffer only when the size changes.
- The page talks only one way (app → page). If it ever needs to call back (e.g. "item tapped"),
  extend `ManiengineProtocol` and this section together.
- **Layout note:** there is **one** WebView, and it never moves: it fills `pet_layer` behind the
  pager. `PetStage` frames it onto the large viewport on Pets or the preview at 40% of the page height
  on Customise (above the slot tabs and item circles). The frame follows the Pets viewport as its
  page slides (so the pet leaves with Pets towards Chat, and is off screen on Chat), and while only
  Customise and Pets are on screen it is interpolated between their pet areas by the scroll offset
  (`PetTransitionMath.frameFor`): the pet shrinks into the preview card, or grows out of it, with the
  finger, and the health bar slides away with Pets. Nothing is reparented or resized, so nothing
  jumps when the swipe settles. While the frame is wholly off screen (e.g. on Chat and beyond),
  `PetStage` makes `pet_layer` INVISIBLE (`PetTransitionMath.isOffScreen`), because a paused WebView
  keeps showing its last picture. A tab tap that jumps several pages (Pets → Settings) detaches Pets
  before its frame slides away; then `PetTransitionMath.parkedFrame` moves the last frame off the left
  edge. `MainActivity` sets `offscreenPageLimit(1)` so both viewports are laid
  out from the first frame. The page still refits to any size (`ResizeObserver` calling `fit()`).
- **Touch forwarding:** `PetViewport` hands its touches to `PetStage` while its page is resumed and
  the pager is idle (decided at `ACTION_DOWN`); they are offset into layer coordinates and dispatched
  through `PetLayer.forward`. `PetLayer.requestDisallowInterceptTouchEvent` passes the request on to
  the viewport's parents, so `PetWebView`'s pager blocking and flick hand-off work as if it were in
  the viewport; the pager's `ACTION_CANCEL` is forwarded too. Touches that fall through to the layer
  itself are ignored.
- **Acceptance:** equipping/unequipping on the Customise tab updates the viewport immediately. Rotating
  or switching tabs doesn't leak views (`release` cleans up). Nothing happens off the main thread
  in `render`. The export loads offline from `assets/maniengine/` and reacts to `tomodoom:petstate`.

### 5.2 Chat/MCP Agent — Gemini chatbot & MCP tool bridge
- **Implement:** `contracts.ChatService` (network client) and, optionally, extend
  `stubs.DefaultMcpPetActionHandler` or replace it with your own `contracts.McpPetActionHandler`.
- **Replace:** `stubs.MockChatService` (and optionally the handler) in `AppContainer`.
- **Must:** deliver `QuizPrompt`s via `onQuizPrompt`, evaluate answers from `submitQuizAnswer`
  and report the result via `onQuizEvaluated` (with `hpReward` set when passed). Advertise the
  tools in `getSupportedToolNames()` to the model, and forward model tool calls through
  `Listener.onToolCallRequested` (call it off the main thread), sending the returned
  `McpToolResult` back to the model.
- **Must not:** call `PetStateRepository` directly from the chat service. HP changes always go
  through `McpPetActionHandler`, which keeps the anti-replay and award caps in one place.
- **Network:** add `<uses-permission android:name="android.permission.INTERNET"/>` to the manifest
  when you add networking. Do I/O on your own executor.
- **Acceptance:** the manual E2E script in §8 passes against the real Gemini API. No main-thread
  network. Errors show up as SYSTEM messages via `onError`.

#### The pet's two personas (Kafka and Charon)

The chatbot is the pet itself, with two personalities exported from the TruFar editor as JSON
and bundled without their base64 icons:
- `assets/agents/kafka_agent.json`: **Kafka**, bitter and sarcastic, speaks while HP is **low**.
- `assets/agents/charon_agent.json`: **Charon**, cheerful and encouraging, speaks while HP is
  **high**.

The originals (with icons) may sit untracked in the repo root (`Kafka.json`, `Charon.json`).
To change a persona, edit or replace its asset; no code change is needed. `AgentProfileLoader`
reads:
- `agent_basic_info.name`
- `config.pre_prompt` (the persona)
- `config.model.name` (`deepseek-v4-pro`)
- `config.model.params.temperature` (Kafka 0.84, Charon 0.95)
- `config.chat_memory_window.window_size` (5 turns)

Everything else in the export (icon, tools, opening statement) is ignored.

TruFar publishes no API for running an agent, and the export only names the model provider
(`trufar_tokenhub`). So the app runs the persona itself against Google's **Gemini API** (free
tier), through its OpenAI-compatible `POST {base}/chat/completions` endpoint. There is no
TruFar server and no backend: the app calls Gemini directly.

Set these Gradle properties in the untracked `~/.gradle/gradle.properties` (never in the repo):

| Property | Meaning |
|---|---|
| `GEMINI_API_URL` | HTTPS base URL; `/chat/completions` is appended. In use: `https://generativelanguage.googleapis.com/v1beta/openai` (Gemini) |
| `GEMINI_API_KEY` | bearer token. For Gemini, a free key from aistudio.google.com/apikey (no card). It's compiled into the APK: dev/testing only; never ship a long-lived production key in a mobile app |
| `GEMINI_MODELS` | optional; overrides the export's model name (`deepseek-v4-pro`). May be a **comma-separated list**: the primary first, then fallbacks for when it's overloaded. In use: `gemini-3.1-flash-lite,gemini-flash-lite-latest,gemini-3.5-flash` |

Gemini was verified with the real persona (2026-11-19):
- `gemini-3.1-flash-lite` answers in ~2 s. It greets in character and grades both ways
  (`[[WRONG]]` for "Sydney", `[[CORRECT]]` for "Canberra"), so it's the primary.
- `gemini-3.5-flash` writes better replies but was slow (~44 s first call) and later hung past
  60 s or returned 503, so it's the last fallback.
- `gemini-3.8-flash` / `3.7-flash` returned 503 "high demand", and `gemini-2.5-flash` returned
  404 for this key.
- List the models available to a key with `GET {base}/models`.

`AppContainer` uses `GeminiChatService` when `GEMINI_API_URL` is set, otherwise
`MockChatService` (offline demo).

**Persona selection (`PersonaSelector`, hysteresis 40 / 60):**
- Before **every** request, `GeminiChatService` reads the pet through a read-only
  `Supplier<PetState>` (`PetStateRepository::getCurrentState`) and picks the persona:
  - no persona yet: HP ≥ 50 → Charon, otherwise Kafka (the default 50/100 pet starts with
    Charon);
  - while Charon: Kafka takes over once HP < 40;
  - while Kafka: Charon returns once HP ≥ 60.
- A choice is committed only when its reply succeeds, so a failed request doesn't swallow the
  switch. Switches happen between turns, never mid-reply.
- **Pet name:** `AgentProfile.withPetName` replaces every whole-word occurrence of the export's
  own name in its prompt ("You are Kafka") with the onboarding name, so both personas speak as
  the pet. It's read fresh for each request.
- **Seamless switch:**
  - both personas share one `ChatHistoryWindow`, session ID and quiz counter, so the topic and
    the open question carry over;
  - the first reply after a switch gets a hidden one-turn `PersonaPrompts.moodShiftNote`
    ("…your mood has soured / brightened… don't greet again… open with one short in-character
    line showing the change, then carry on").
- Switches are logged under `GeminiChatService` (persona and HP only).

How `GeminiChatService` behaves:
- `connect()` loads both assets on the worker thread. It then sends the hidden user turn
  `__START__`, which the current persona's prompt answers with its greeting. `__START__` never appears in the transcript.
  Only an asset-load failure leaves the service disconnected. If the greeting request fails, the
  error is shown, but the session stays open, so the user's next message starts the
  conversation.
- **New conversation** (`disconnect()` then `connect()`, §3.3):
  - memory, session ID and quiz counter start fresh, and the greeting comes from the persona
    `PersonaSelector` picks from the **saved** persona (`tomodoom_chat`/`persona`). So the
    personality carries over unless HP crossed 40/60 meanwhile.
  - A greeting never carries the mood-shift note.
  - `disconnect()` bumps a generation counter at once. Queued or in-flight requests from the old
    conversation drop their reply and errors, so nothing old lands in the new transcript.
- Each request is `{model, temperature, stream:false, messages:[system, …last 5 turns, user]}`. A
  turn is remembered only if its request succeeds. Read timeout is 90 s.
- **Retries and fallbacks (`ModelFallback`):** each turn tries the primary model twice, then each
  fallback once, for at most 4 attempts, waiting 1 s, then 2 s between attempts.
  - Only temporary failures move on: HTTP 408/429/500/502/503/504 or a timeout.
  - A timeout skips straight to the next model, because a hanging model costs a full 90 s read
    timeout per try.
  - 400/401/403/404 fail at once, so a bad key or model name is reported immediately.
  - Retries are logged under `GeminiChatService` (model and status only). The user sees an
    error only if every attempt fails.
- **HP / quiz protocol:** both personas quiz in free text, so the service never emits `QuizPrompt`s. The
  app appends `APP_PROTOCOL_PROMPT` to the persona, which asks the model to end any reply that grades
  an answer with `[[CORRECT]]` or `[[WRONG]]`. `QuizMarkerParser` strips the markers (it's lenient
  about case and brackets, and the last marker wins). A graded reply is delivered as
  `onQuizEvaluated(quizId = "gemini-chat:<session UUID>:<n>", hpReward = CORRECT_ANSWER_HP = 10)`.
  `ChatRepository` shows it as one bot bubble and, when it passed, calls `onQuizPassed`, so the
  replay guard and cap still apply. Ungraded replies arrive through `onBotMessage`. A marker in the
  greeting is ignored.
- No MCP tools are advertised (the exports have none). HTTP errors include the server's
  `error.message` and surface as SYSTEM messages via `onError`.

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

`doomscroll.UsageStatsDoomScrollMonitor` uses the special Android Usage Access setting. Onboarding's
second step asks for it first and then for notifications (Android 13+), each skippable with **Not now**.
As a fallback the Pets tab has a card with a button to open that setting, starts the monitor when the
user returns with access granted, and asks for notifications once per launch unless onboarding already
asked in that launch (`ui.NotificationPrompt`).

**Watched apps are the user's choice.** Only time in a watched app can cost HP; any other app (a
work or study app) never counts.
- Once access is granted, the same Pets card says so, and its **Choose apps** button opens
  `ui.watchedapps.WatchedAppsActivity`: a switch per launcher app (`LauncherAppsQuery`, which needs
  the manifest's `<queries>` MAIN/LAUNCHER intent on Android 11+; Tomodoom itself isn't listed).
  Watched apps come first, sorted once so rows don't jump while toggling.
- Changes save at once (`WatchedAppsRepository`, §3.3) and apply at the next poll
  (`DoomScrollSessionTracker.setMonitoredPackages`): unwatching the app on screen stops the count,
  and watching the app on screen starts it.
- The default selection (`model.WatchedApps.DEFAULT_PACKAGES`) is Instagram, TikTok (two package
  IDs), Reddit, YouTube, X/Twitter, Facebook and Snapchat. The hidden reset restores it.

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
  (the health bar below the pet on Pets; Customise has no title, its preview starts at the top); content starts right under the status bar.
- **Top-level pages (`ViewPager2`):** there is no Jetpack Navigation. `MainActivity` hosts the pages
  in `main_pager` and keeps it in sync with `nav_view` both ways. `viewpager2` (1.1.0-beta02) comes
  in transitively from Material, like drawerlayout, so don't pin it. Rules:
  - A new top-level tab means a menu item in `bottom_nav_menu.xml` **and** an entry in
    `MainPagerAdapter` (`MENU_ITEM_IDS` + `createFragment`), in the same order.
  - Page order is Customise (0), Pets (1), Chat (2), Stats (3), Settings (4). Five is
    BottomNavigationView's maximum, so a sixth tab needs another design. Pets is the default: `MainActivity` calls
    `setCurrentItem(PAGE_PETS, false)` only when `savedInstanceState == null`, so a restore keeps
    the saved page. Don't assume page 0 is the start page.
  - `FragmentStateAdapter` creates pages lazily (a page is created the first time you swipe or tap
    to it, or prefetched next to the current one; `offscreenPageLimit(1)` keeps both neighbours of the
    current page created) and only the current page is `RESUMED`; the
    others are capped at `STARTED`. Use
    `onResume`/`onPause` for "page became visible/hidden".
  - ViewPager2 restores its page **without** calling `onPageSelected`, so `MainActivity` re-syncs
    the bottom nav and Back callback in `onRestoreInstanceState`.
  - A child that needs horizontal drags calls `getParent().requestDisallowInterceptTouchEvent(true)`
    on `ACTION_DOWN` (as the Maniengine WebView does for its `tomodoom-touch` policy). To give a drag
    back mid-gesture, call it with `false`: the pager takes over on the next move and the child gets
    `ACTION_CANCEL` (how a tab flick on the pet works). If a whole page ever needs to suspend swiping, add a host callback calling `ViewPager2.setUserInputEnabled`
    (the old `PageSwipeHost` did this for the removed customisation drawer).
- **Bottom bar:** `labelVisibilityMode="selected"` makes Material widen the open tab and show only
  its label. `MainActivity.emphasiseNavIcon` adds the icon scale and lift. It finds the icon by type
  because Material's icon view id is private (referencing it triggers lint `PrivateResource`).
- **Dependencies** must be compatible with compileSdk 34 / AGP 8.1.1. Many current AndroidX
  releases need compileSdk 35+, so check before you bump anything. Pin exact versions.
- **`androidx.activity` / `drawerlayout` / `viewpager2`** come in transitively as compile-scope
  dependencies of Material. Don't pin them in `build.gradle.kts`: that adds `GradleDependency`
  lint warnings.
- **Gemini free tier (the LLM in use):**
  - Popular models often return **503 "high demand"** or simply hang. That's why
    `GEMINI_MODELS` lists fallbacks and `ModelFallback` retries automatically; a single 503
    used to surface as `⚠ Chat error … HTTP 503` and, on the greeting, left the chat
    "not connected" until restart (fixed).
  - The free tier has rate limits, and some projects report "free tier unavailable". If every
    listed model fails, change the list (`GET {base}/models` shows what the key can use), or use
    another free OpenAI-compatible provider. No code change is needed either way.
  - Worst case with all models hanging is about 3 × 90 s before the error appears. Fast-failing
    503s are retried within seconds.
  - Changing any `GEMINI_*` property needs a rebuild and reinstall, because they're compiled
    into `BuildConfig`.
  - The properties used to be called `TRUFAR_LLM_URL` / `TRUFAR_LLM_API_KEY` /
    `TRUFAR_LLM_MODEL`. The old names are ignored now, so a build with only the old names
    silently falls back to the mock bot.
- **LLM-graded HP is persuadable:** a user can talk either persona into emitting `[[CORRECT]]`. Each award
  is bounded by `GeminiChatService.CORRECT_ANSWER_HP` (10) and the per-award cap, but
  there's no rate limit. Add one in the handler if it gets abused.
- **org.json in JVM tests:** android.jar only has throwing stubs, so `org.json:json` is a
  `testImplementation` dependency. Tests can then call `AgentProfileLoader.parse(JSONObject)` on the
  real asset (unit tests run with `app/` as the working directory).
- **Shared pet WebView:** the one WebView lives in `MainActivity`'s `pet_layer`, **behind** the
  pager. Anything drawn over a pet viewport hides the pet: keep the pages and `pet_viewport` free of
  backgrounds there (`bg_pet_viewport` is a stroke only). The WebView only gets touches forwarded by
  `PetViewport`, so don't make the layer clickable or put the pager below it. It's created
  with the viewport's (activity) context, so `PetStage` lives in `MainActivity` and is released
  in its `onDestroy`. Never hold it in a ViewModel or `AppContainer`: that would leak the activity.
- **Pages more than one away from Pets** (Stats, Settings) have no Pets view laid out
  (`offscreenPageLimit(1)`), so `PetStage` can't follow it there; it parks and hides the pet instead
  (§5.1 layout note). Their pages are transparent too, so without that the pet showed between cards.
- **WebView (Maniengine):** an `OnTouchListener` on a WebView triggers lint
  `ClickableViewAccessibility`, so `HtmlManiengineRenderer` uses a private `WebView` subclass that
  overrides `dispatchTouchEvent`. `SetJavaScriptEnabled` is suppressed on purpose: the page is a
  bundled asset and no JS interface is added. Don't add `addJavascriptInterface` or file access
  without revisiting that. `new WebView(...)` can throw when the WebView provider is missing or
  updating, so catch it.
- **Launcher is `OnboardingActivity`**, not `MainActivity` (which is `exported="false"`). It
  checks `isOnboardingComplete()` before inflating anything and forwards to `MainActivity` if set.
  **Start** completes onboarding *before* the permission step, so that step exists only while the
  activity lives: a restore with the saved `permissions_step` flag (rotation) shows it again instead of
  forwarding, and a relaunch after process death skips it (Pets' card is the fallback).
  Anything that should run on every launch belongs in `TomodoomApplication` or `MainActivity`,
  not in the onboarding screen.
- **On-device checks are possible on this Windows machine:** the SDK has an AVD
  (`Medium_Phone_API_37.0`). Start it with
  `"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Medium_Phone_API_37.0 -no-snapshot-save`,
  wait for `adb shell getprop sys.boot_completed` to be `1`, then `adb install -r` the debug APK and
  drive it with `adb shell input tap/text/swipe` plus `adb exec-out screencap -p` (1080×2400; the
  bottom-nav icons sit at y≈2244). After `adb install -r`, the first tap on Send can land before the
  chat connects; wait for the greeting.
- **Launcher shows the old (robot) icon after an icon change:** the APK is fine, but the phone
  still has an older install or the launcher cached the icon (`versionCode` stays 1, and Android
  Studio's *Apply Changes* doesn't reinstall the package). Uninstall the app and reinstall it; if it
  persists, restart the launcher or phone. Check the APK with `unzip -l` for `mipmap-anydpi-v26/`.
- **Read-only emulators lose app data:** an emulator started with `-read-only` discards its data
  on shutdown, so the app is a fresh install (with onboarding) after each emulator restart.
- **Maniengine overrides are applied by the page, not the editor.** `pet-scene.js` matches each
  `OVERRIDES` entry by unique top-level name, else by `scene.children` index (the camera is added
  first so the indices match the export). Nested paths (`"46/16"`) are skipped with a
  `console.warn`. Headless-Edge screenshots on this Windows machine report a viewport about 500px
  wide whatever `--window-size` says, so check framing at ≥500px.
- **Pet parts aren't only meshes:** `pet-scene.js` groups meshes, lines and points (`isPart`); the
  cat's whiskers are `LineSegments` and stayed behind when the pet turned while it only took meshes.
  An export using another object type needs adding there.
- **Foreground service start:** start `DoomScrollMonitorService` only through
  `DoomScrollMonitor.startFromForeground()` while a page is resumed. `Application.onCreate` can run
  in the background, where Android 12+ throws `ForegroundServiceStartNotAllowedException`.
- **Inactivity decay uses the wall clock:** moving the device clock forward costs HP, and back
  costs nothing. That makes it easy to test (set the clock forward while the app is in the
  background), but don't switch it to `elapsedRealtime`, which resets on reboot.
- **Switch on strings** works with the `AccessorySlot` / `McpPetActionHandler.TOOL_*` constants
  because they are compile-time constants. Keep new constants `static final` literals.
- **Fonts:** the theme's Playpen Sans overrides the font of any `textAppearance`. Use a view
  style for a different font (§6.3).
- **Chat text size (Settings):** `ChatAdapter.setTextScale` sets user bubbles, bot prose and code
  blocks to their dimen × the `SettingsRepository` scale in code, overriding the styles' sizes;
  SYSTEM notices keep theirs. JLatexMath's size is fixed when its plugin is built, so a new scale
  rebuilds `ChatMarkdown` (and its cache) and rebinds every row.
- **Chat Markdown / LaTeX (Markwon 4.6.2):** bot replies only; user and SYSTEM text stays plain.
  `ChatAdapter` first splits off fenced code (`MarkdownSegments`) into its own monospace block
  (horizontal scroll, no wrapping, a **Copy** button; Android 13+ shows its own clipboard toast, so
  the app's toast is for API < 33). The prose goes through `LatexNormalizer`, because Markwon's LaTeX
  plugin reads **only** `$$…$$` (inline inside a sentence, a block on its own lines): don't feed it
  raw LLM text. Single `$` follows Pandoc's rule, so currency like "$5 and $10" stays text.
  JLatexMath typesets on Markwon's own executor and the formula appears when ready. Its first render
  in a process loads fonts, so the first formula may pop in a moment late. `APP_PROTOCOL_PROMPT`
  asks Gemini for `$…$` / `$$…$$` and language-tagged fences, the forms this handles best.
- **`adb shell input text` on the slow emulator** drops or scrambles characters and stops at a comma.
  Type short, punctuation-free test messages (`%s` for spaces).
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
  (as `bubble_bot_background`, `system_notice_background`, `viewport_stroke` and the Stats
  `stats_card_*` / `chart_*` aliases do) rather than a new hex value. Adding a new token means a palette change:
  update the table above in the same commit.
- **Chat code and maths are opaque:** code blocks in bot bubbles use `chat_code_block_background`
  (jet) with `chat_code_block_text` (floral_white) and `chat_code_block_label` (eggshell); inline
  code sits on `chat_inline_code_background` (floral_white); LaTeX is drawn in `chat_math_text` (jet).
- `eggshell` on `floral_white` has very low contrast. Borders that must be visible use a 2dp stroke
  (as the Customise pet viewport does). The Pets cards are flat, transparent and have no stroke, because
  they float over the pet.
- `slate_grey` on `floral_white` is about 3.0:1 (`jet` is about 10.7:1, `eggshell` about 1.06:1), so keep it for secondary or large text. Body text
  uses `jet`.
- Renderers (Maniengine) may use their own art colours inside the viewport, but the chrome around
  it follows this palette.
- The launcher icon comes from `tools/launcher_icon/logo.svg`, which uses only palette colours.
  `make_launcher_icon.py` writes them as `@color/<token>` references (it fails on a non-palette
  colour). On API 26+ the launcher mask stands in for the logo's eggshell emblem square: the adaptive
  background is `launcher_background` (→ `eggshell`) and the foreground is the phone, scaled so the
  emblem fills the 72dp visible area. The monochrome (themed-icon) layer keeps only the `jet` line work.
  `ic_notification.xml` is that same line work cropped to the phone at 24dp and painted
  `@android:color/white`: status-bar icons are alpha-only and tinted by the system, so it's the one
  drawable that uses a framework colour instead of a palette token.
  The large PNG exports flatten the adaptive icon (eggshell square, phone at the 108dp framing) with no
  alpha; Play rounds the corners itself. The in-app icons on API 26+ are vectors, so they're sharp at any
  launcher size; only the API 24–25 webps are fixed-size (up to xxxhdpi, 192px).

**Typography.** All UI text is **Playpen Sans**; user and bot chat bubbles use **Unkempt**
(`Widget.Tomodoom.ChatBubble`, applied with `style=`: Unkempt Bold, `@dimen/chat_bubble_text_size` (17sp) from its
text appearance; LaTeX uses the same size). SYSTEM chat notices stay in Playpen Sans. Code in bot bubbles is
**Fira Code Retina** with its programming ligatures (`=>`, `!=`, `>=` … drawn as one glyph):
fenced blocks through `Widget.Tomodoom.ChatCode` (`@dimen/chat_code_text_size`, 14sp,
`fontFeatureSettings` `"calt" 1, "liga" 1`, `letterSpacing` 0), inline and indented code through
Markwon's `codeTypeface` in `ChatMarkdown` (falls back to `Typeface.MONOSPACE`).
- Fira Code Retina (OFL, the official 6.2 release; licence `assets/licenses/FiraCode-OFL.txt`) is
  a single static weight (450), so it's a bare TTF resource with no family XML.
- Android drops optional ligatures when `letterSpacing` isn't 0. Fira Code's ligatures are `calt`,
  which survive it (inline code in the 0.01-spaced prose still gets them), but keep code styles at 0.
- Playpen Sans and Unkempt are bundled static Regular/Bold TTFs (Playpen Sans: OFL, from TypeTogether's repo, since
  Google Fonts ships only the variable font; Unkempt: Apache 2.0). Licences are in
  `assets/licenses/`.
- Each family XML exists twice: `res/font/` with `app:` attrs (AppCompat, API 24–25) and
  `res/font-v26/` with `android:` attrs. Putting both sets in one file adds lint
  `UnusedAttribute` warnings.
- `Theme.Tomodoom` sets `fontFamily`/`android:fontFamily` and maps every Material
  `textAppearance*` the app uses to a `TextAppearance.Tomodoom.*` style, because the Material
  defaults hard-code `sans-serif`. A layout using a new `textAppearance*` attribute needs a new
  override. Don't set `fontFamily` in layouts.
- **A text appearance can't change the font.** The theme-level `fontFamily` counts as a TextView
  attribute, which outranks `android:textAppearance`. A view that needs another font gets a
  `Widget.Tomodoom.*` style (`style=`, which outranks the theme) setting `fontFamily` and
  `android:fontFamily`, as the chat bubbles do.

## 7. Build & verification commands

### 7.1 Commands

**Standard check to run after every milestone** (use the variant for your environment below):
`assembleDebug testDebugUnitTest lintDebug`. The lint text report is
`app/build/reports/lint-results-debug.txt`; its last line is the error/warning count.

Requires **JDK 17** (Android Studio's bundled JBR works) and the Android SDK with platform 34
(`sdk.dir` in `local.properties`, which Android Studio generates and which is never committed).
Some machines have no Android Studio or SDK; one known example has only a standalone JDK at
`C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot`. There Gradle can't build, so the most
you can do is a `javac --release 8 -Xlint:all -Werror` check of pure-Java/`chat` code against
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
Don't introduce new lint warnings. javac currently
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

Debug helpers:
- **Debug panel (all builds):** long-press the **Pets** tab. A bottom sheet shows the live pet state
  (name, species, HP, mood, equipped) and offers: HP −10/−1/+1/+10, set HP to 0/30/50/80/max,
  unequip all; simulate one doom-scroll minute (−1 HP) or the 10-minute warning notification, Usage
  Access status and settings; chat backend (mock or Gemini + models), simulate a quiz pass (+10 HP
  through `McpPetActionHandler.onQuizPassed`), replay it (the replay guard should reject it), start a
  new conversation; version and the hidden reset. HP changes use reason `debug`.
- **Mock chat commands:** `quiz` (new quiz), `help`, `/tool <name> key=value …` (calls an MCP tool
  as if the backend had), anything else is echoed back.
- **`StubDoomScrollMonitor.simulateDoomScroll(pkg, durationMs, penalty)`:** tests the
  monitor → listener path in code.
- **Logcat tags:** `PetStateRepository` (every HP change with its reason), `ChatRepository`,
  `DefaultMcpHandler`, `MockChatService`, `GeminiChatService`, `StubDoomScrollMonitor`,
  `HtmlManiengine` (WebView failures, missing assets).
- **Maniengine page:** in a debug build, open `chrome://inspect` on a desktop Chrome with the
  device connected to inspect and live-edit the pet page.


0. Fresh install: the onboarding screen shows "Meet your new
   pet", portrait cards for every pet (Rabbit, Cat) with Rabbit ringed, and the name field prefilled
   with `Tomo`. Tapping Cat moves the ring (rotation keeps it); after **Start** the Pets and Customise
   viewports show the cat, and it stays the cat after a relaunch. Clearing it disables **Start**; the counter caps
   at 20. Type `Mochi` and tap **Start** (or the keyboard's Done): a "Help Mochi look after you" screen
   lists **Track app usage** (ringed) and **Notifications** (Android 13+ only). **Allow usage tracking**
   opens the Usage Access settings; grant it and go back: the row shows "✓ Allowed", the ring moves to
   Notifications and the button reads **Allow notifications**, which shows the system prompt. Then
   **Continue**: the Pets tab opens with no Usage Access card and no second notification prompt, and its
   health bar shows `Mochi`. **Not now** skips a step ("Skipped…"); after skipping usage the Pets card is
   still there. Rotating on the permission screen keeps it. Rotation keeps the typed text. Relaunching skips onboarding. (The steps
   below assume you kept `Tomo` and the Rabbit.)
1. The home-screen icon is the phone with a pet peeking out of its pocket, on eggshell (with any
   mask shape; on Android 13+ with themed icons on, a tinted line drawing). Launch the app. The **Pets** tab has **no action bar** and no debug buttons. The health bar sits below
   the rabbit and shows `Tomo`, **HP 50 / 100** and status "Okay". Until Usage Access is granted, a Usage Access card with an **Enable** button
   sits at the top of the page. Once access is granted it reads "Only scrolling in the apps you
   choose costs HP." with a **Choose apps** button (step 20). The bottom bar shows four icons (Customise, Pets, Chat, Stats). Pets is open: its tile is wider, its icon is larger and only it shows a label.
2. Swipe right (or tap **Customise**). The Customise page shows no title: a pet preview at the top and
   full-width Hat / Accessory tabs above a two-column grid of circles showing each item's model
   (None first). Tap the **Cap** circle on the Hat tab: its ring darkens and the preview (the 3D
   cream rabbit in a floral dress, on a rug) gains a blue cap at once. Swipe back to Pets: the large
   pet has the hat too. The pet moves between the pages without reloading (no blank flash or
   restart of the hop animation), and only one pet exists: in a debug build `chrome://inspect`
   lists a single page target. Also check:
   - **Transition:** swipe slowly from Pets to Customise. The pet stays on screen and shrinks, with
     the finger, into the preview card, its corners rounding as it goes, while the health bar slides
     away with Pets. It rises steadily as it shrinks (no dip halfway) and nothing moves when the swipe
     settles. Reverse the swipe halfway: it grows back. Tapping the Customise or Pets tab, and
     Chat → Customise, animate the same way. Pets → Chat: the pet slides away with Pets, and no pet
     shows through Chat. The health bar and Usage Access card draw over the pet; the Customise border
     sits on the pet's edge. After any transition, turning, pinching and flicking the pet to change tab
     still work on both pages, and the item circles and Usage Access button still respond.
   - the selected slot tab and the current page survive rotation;
   - Back on Customise or Chat returns to Pets; Back on Pets leaves the app;
   - **Swiping:** from Pets, swipe left to Chat and right to Customise, either from the 16dp page
     margins at any speed, or with a fast flick anywhere on the pet. The bottom nav
     selection follows, and the flick doesn't leave the rabbit turned.
   - **Rotating:** on Pets and Customise, a slower one-finger drag inside the model box (on Pets this
     includes the health bar) turns the pet both ways without changing page. Slow drags are precise,
     faster ones turn further, and the release speed sets how far it coasts. Starting slowly then
     speeding up stays on the page. The rug stays still, worn items turn with the rabbit, and the angle
     is kept when moving between the two pages. The Usage Access button still responds.
   - On Pets the pet has no border and fills the page behind transparent cards. Pinching with two
     fingers zooms it evenly (0.75x to 3x) without changing page.
   - Mood: HP ≥ the thriving threshold makes the rabbit hop, struggling droops its head and
     desaturates, and 0 HP tips it over in grey. Only real doom-scroll detection lowers HP in the app.
3. Open **Chat**. There is no action bar; the transcript starts right under the status bar. The mock bot greets you. Type `quiz`, and the bot asks a question (e.g. "What is 2 + 2?").
3a. **Markdown, maths and code** (bot bubbles): the mock bot echoes what you send, so send
   `Euler $e^{i\pi}+1=0$ costs $5` and, on a new line, `$$\int_0^1 x^2 dx$$`. The echo shows a typeset
   inline formula, "$5" as plain text and a centred display integral. Send a message with a
   ```` ```python ```` fenced block: it shows as a dark, monospace block labelled "python" that scrolls
   sideways instead of wrapping; **Copy** puts the code on the clipboard. `**bold**` is bold. With
   Gemini, ask for "the quadratic formula in LaTeX and a short Python function".
4. Answer correctly (`4`). The bot confirms, and a SYSTEM message shows **"+10 HP awarded via MCP! (60/100 HP)"**.
5. Switch to **Pets**: **HP 60 / 100**.
6. In Chat, type `/tool get_pet_state` (or `/tool award_hp amount=5`). A SYSTEM message shows the MCP result.
7. On **Customise**, equip an item and note the current HP.
8. Kill and relaunch the app. HP and equipped items persist (SharedPreferences). Onboarding doesn't show again. The chat transcript resets (in memory only). To start over, long-press the **Wardrobe** tab
   and confirm **Reset** (Cancel changes nothing): the app closes and the next launch shows onboarding
   again with HP 50 / 100 and nothing equipped. A plain tap on Wardrobe still just opens the page.
8a. Long-press the **Pets** tab: the debug panel opens fully expanded (a plain tap still just opens
   Pets; a second long-press doesn't stack a second panel). −10 lowers the health bar at once; set 0
   tips the pet over, 80 makes it hop. Simulate quiz pass shows "+10 HP awarded via MCP!", Replay shows
   "⚠ Quiz debug:… was already rewarded." Simulate warning posts "Time to look up". New conversation
   clears the Chat transcript and greets again. Reset all data shows the same confirm dialog as Wardrobe.

**Personas on Gemini (real LLM)**: build with `GEMINI_API_URL`/`GEMINI_API_KEY`/`GEMINI_MODELS` set (§5.2):

9. Fresh install at 50 HP, pet named `Mochi`. Open **Chat**: Charon's warm three-sentence greeting
   appears, calling itself Mochi (never "Charon"). No `__START__` bubble appears.
10. Name a topic and answer correctly. The reply shows no `[[…]]` marker, and a SYSTEM
    message shows **"+10 HP awarded via MCP!"**. A first wrong answer gets a hint and no HP.
11. Use a bad key or URL. A SYSTEM `⚠ Chat error: … HTTP 401…` message appears and the app doesn't crash.
12. Doom-scroll until HP < 40, then send a message. The reply opens with one line showing a soured
    mood, then continues the same topic in Kafka's sarcastic tone, still as Mochi. Between 40 and
    59 HP the persona doesn't change. Earn HP back to ≥ 60: the next reply brightens, then Charon.
13. With a conversation open, send the app to the background (Home) for under 10 minutes and
    return: the transcript is still there. Leave it for over 10 minutes and return: the
    transcript is cleared and a new greeting arrives from the **same** persona as before (unless
    HP crossed 40/60 meanwhile, e.g. through doom-scrolling). Rotating never resets the chat, and
    killing and relaunching the app keeps the persona.

**Doom-scroll detection (real device, watched app installed):**

14. Grant Usage Access and allow notifications. A low-priority "Tomodoom is watching over your pet" notification appears, with the white phone-and-pet logo (not a paw) in the status bar.
15. Scroll Instagram or YouTube Shorts in portrait. HP drops by 1 each minute.
16. Keep going to 10 and 20 minutes. A "Time to look up" notification appears each time.
17. Play a fullscreen landscape YouTube video. HP stops dropping, then resumes when you return to portrait.
18. Leave watched apps for over a minute, then return. The next warning comes 10 minutes into the new session.
19. Revoke Usage Access. Monitoring stops and its notification disappears.
20. With access granted, tap **Choose apps** on Pets. "Watched apps" lists your launcher apps with
    the default social apps switched on and listed first, and "N apps watched" under the text.
    Switch YouTube off and scroll YouTube Shorts for 2 minutes: HP doesn't drop. Switch on a
    non-social app (e.g. Chrome) and use it in portrait: HP drops by 1 each minute. Rotation keeps
    the list; relaunching keeps the choice.

**Settings tab:**

26. Five tab icons; the gear on the right opens Settings (also by swiping left from Stats). Tapping
    the gear straight from Pets shows no pet behind the cards. Under **Pet name** the field shows the
    current name and **Save name** stays disabled until it is a different valid name; save `Mochi`: a
    "Renamed to Mochi" Snackbar appears above the bottom bar, the Pets health bar says `Mochi`, and the
    next chatbot reply uses it. Under **Your pet**, tap Cat: the ring moves, "Mochi is now a Cat"
    appears, and Pets and Customise show the cat with the same HP and items. Relaunch: both stick.
27. **Tracked apps** shows "N apps watched"; without Usage Access it says so and offers **Enable**.
    **Choose apps** opens the Watched apps picker; changing it updates the count on return.
28. **Chat text size**: drag the slider (80%–150%, six stops). The percentage and the preview bubble
    change at once; on Chat, user and bot bubbles, code blocks and LaTeX use the new size, and system
    notices don't. Rotation and relaunch keep it. *(26–28 verified on an API 37 emulator, 2026-11-19,
    except the chatbot reply, LaTeX and relaunch.)*

**Stats tab:**

23. Fresh install: five tab icons, Stats on the right; swiping left from Chat opens it. "Since your
    last doom-scroll" counts up from install ("No doom-scrolling since tracking began …"), the bars
    and per-app list are empty, the health chart shows a dot at the current HP on the right, and
    "Tokens by model" says no tokens have been used yet.
24. With Gemini configured, chat a few turns and pass a quiz. "Tokens by model" lists the model
    that answered (e.g. `gemini-3.1-flash-lite`, N requests · in · out, total), the health line
    steps up by 10 and "+10 HP from quizzes this week" appears. 24 h / 7 days switches the chart
    range and survives rotation. *(Verified on an API 37 emulator, 2026-11-19.)*
25. After real doom-scrolling (steps 14–15): today's minutes, sessions, longest session, today's
    bar and the app row update, "−N HP to doom-scrolling" grows, and the clean-streak timer restarts
    and ticks up while the page is open. Kill and relaunch: everything is still there.

**Inactivity decay (needs a finished onboarding):**

21. Note the HP on Pets, press Home and come back within 30 minutes: HP is unchanged, with no Snackbar.
22. Press Home, set the device clock 8.5 hours forward, then reopen the app. HP is about 30% lower
    (100 → 70, 50 → 35), and a Snackbar above the health bar says "Tomo missed you: −N HP while
    you were away", once. If the app reopens on another tab, the Snackbar waits until you open Pets.
    Rotating or switching tabs never applies decay.

---

## 9. Project status & known gaps

Update this table in the same commit when your task changes a subsystem's status.

| Subsystem | Status | Implementation in `AppContainer` |
|---|---|---|
| Pet state + persistence | ✅ Done | `PetStateRepository` |
| Inactivity HP decay | ✅ 30 min grace, then 30% of HP per 8 h away, applied on return, with a Snackbar on Pets (not yet tested on a device) | `InactivityDecayTracker` |
| MCP tool bridge | ✅ Production-capable default | `DefaultMcpPetActionHandler` |
| Pets UI (pet viewport, health bar below the pet) | ✅ Skeleton done | `PetsFragment` / `PetsViewModel` |
| Customise UI (pet preview, slot tabs, item circle grid) | ✅ Skeleton done | `CustomiseFragment` / `CustomiseViewModel` |
| Onboarding (choose and name the pet, then Usage Access and notifications) | ✅ Done (permission step not yet tested on a device) | `OnboardingActivity` / `OnboardingViewModel` |
| Chat UI | ✅ Skeleton done; bot replies render Markdown, LaTeX (JLatexMath) and copyable code blocks (unit-tested; on a device only the plain greeting was seen through the new path) | `ChatFragment` / `ChatViewModel` / `ChatRepository` |
| Pet rendering | ✅ scene-3 rabbit (default) and scene-5 cat (three.js, offline) via the HTML/WebView pipeline; add-ons are primitive placeholders | `HtmlManiengineRenderer` + `assets/maniengine/` |
| Item catalog | ⏳ Placeholder (6 hard-coded items: hats and accessories) | `AccessoryCatalog` → Maniengine Agent |
| Chatbot (Gemini) | ✅ Kafka (low HP) / Charon (high HP) personas, 40/60 hysteresis, speaking as the pet's name, new conversation after 10 min in the background, on Google's free Gemini API (`gemini-3.1-flash-lite` with fallbacks, retries on 503/429/timeouts) via the in-app OpenAI-compatible client. It's verified from a PC; on a device, the first build hit a 503, and the fix is not yet re-tested there. Mock when unconfigured | `GeminiChatService` (`GEMINI_API_URL`) / `MockChatService` |
| Settings tab | ✅ Rename the pet, switch species, tracked apps (count + picker / Usage Access), chat text size (80–150%) with a live preview; verified on an emulator | `SettingsFragment` / `SettingsViewModel` / `SettingsRepository` |
| Stats tab | ✅ Time since the last doom-scroll, clean streaks, doom-scrolling per day/app/session, HP over 24 h / 7 d, HP by cause, tokens per model; tracking starts at install. Token and HP parts verified on an emulator; the doom-scroll parts are unit-tested only | `StatsRepository` / `StatsFragment` |
| Doom-scroll detection | ✅ Usage Access polling in a foreground service; only the apps the user picks (default: social apps), portrait only, 1 HP per minute, warnings every 10 minutes (not yet tested on a device) | `UsageStatsDoomScrollMonitor` |

Known gaps and TODOs (not assigned to anyone yet):
- The chat transcript isn't persisted (only the persona is), and quiz replay protection only lasts for the process
  lifetime. Quiz IDs include a random session UUID, so they stay unique across sessions.
- Non-UI user-facing text (MCP results, chat notices, mock bot) is inline English, not in `strings.xml`.
- The pet species is chosen at onboarding and can be changed in Settings. Add-ons are positioned
  for the rabbit, so they may sit oddly on the cat.
- Nothing happens yet when HP reaches 0 beyond the "Fainted" status label.
- To change HP for manual testing, use the debug panel (long-press the Pets tab). To start over,
  use the hidden reset (long-press the Wardrobe tab, or the panel). Whether it keeps Usage Access and
  the notification permission hasn't been checked on a device.
- The stationary pet layer (the Pets ↔ Customise shrink, touch forwarding) is verified on the page
  side (headless Edge) and by unit tests only; watch on a device for the frame trailing the finger
  (`evaluateJavascript` is asynchronous) and for touches on the pet (turn, pinch, flick to change tab).
- No instrumented/UI tests yet. JVM tests only (`model`, `doomscroll`, `chat`, `maniengine`, `viewmodel`, `repository`, `stats`, `ui`).
- Stats: token counts are those the provider reports (failed attempts aren't counted); there's no
  cost estimate. The clean streak only knows time since install, and a doom-scroll session is
  measured as wall-clock span (landscape pauses inside it count).
