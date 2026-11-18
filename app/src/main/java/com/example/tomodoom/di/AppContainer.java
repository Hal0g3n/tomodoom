package com.example.tomodoom.di;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.example.tomodoom.BuildConfig;
import com.example.tomodoom.TomodoomApplication;
import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.contracts.ManiengineAssetRenderer;
import com.example.tomodoom.contracts.McpPetActionHandler;
import com.example.tomodoom.contracts.TruFarChatService;
import com.example.tomodoom.doomscroll.UsageStatsDoomScrollMonitor;
import com.example.tomodoom.maniengine.HtmlManiengineRenderer;
import com.example.tomodoom.repository.ChatRepository;
import com.example.tomodoom.repository.DoomScrollHpBridge;
import com.example.tomodoom.repository.PetStateRepository;
import com.example.tomodoom.stubs.DefaultMcpPetActionHandler;
import com.example.tomodoom.stubs.MockTruFarChatService;
import com.example.tomodoom.trufar.HttpTruFarChatService;
import com.example.tomodoom.trufar.OpenAiCompatibleChatService;

/**
 * Hand-written service locator: the <b>only</b> place where concrete implementations are chosen.
 *
 * <p>Downstream agents swap stubs for production implementations on the lines marked
 * {@code SWAP POINT (<agent>)}. Everything else in the app depends only on the
 * {@code contracts} interfaces and the repositories. See CLAUDE.md §4–5.</p>
 *
 * <p>Created once in {@link TomodoomApplication#onCreate()}; obtain it with {@link #from(Context)}.
 * All singletons it holds live for the whole process.</p>
 */
public final class AppContainer {

    /** The TruFar agent export (persona + model settings) the chat runs. */
    private static final String KAFKA_AGENT_ASSET = "trufar/kafka_agent.json";

    @NonNull
    private final PetStateRepository petStateRepository;
    @NonNull
    private final McpPetActionHandler mcpPetActionHandler;
    @NonNull
    private final TruFarChatService truFarChatService;
    @NonNull
    private final ChatRepository chatRepository;
    @NonNull
    private final DoomScrollHpBridge doomScrollHpBridge;
    @NonNull
    private final DoomScrollMonitor doomScrollMonitor;

    public AppContainer(@NonNull Context context) {
        Context appContext = context.getApplicationContext();

        // Single source of truth for pet state. Never replace or duplicate it.
        petStateRepository = new PetStateRepository(appContext);

        // SWAP POINT (TruFar/MCP Agent): MCP pet tool bridge (the default is production-capable).
        mcpPetActionHandler = new DefaultMcpPetActionHandler(petStateRepository);

        // SWAP POINT (TruFar/MCP Agent): the Kafka agent on an OpenAI-compatible endpoint, else the
        // session API, else the offline mock (CLAUDE.md §5.2).
        if (!BuildConfig.TRUFAR_LLM_URL.trim().isEmpty()) {
            truFarChatService = new OpenAiCompatibleChatService(appContext, KAFKA_AGENT_ASSET,
                    BuildConfig.TRUFAR_LLM_URL, BuildConfig.TRUFAR_LLM_API_KEY,
                    BuildConfig.TRUFAR_LLM_MODEL);
        } else if (!BuildConfig.TRUFAR_API_URL.trim().isEmpty()) {
            truFarChatService = new HttpTruFarChatService(BuildConfig.TRUFAR_API_URL,
                    BuildConfig.TRUFAR_API_TOKEN, mcpPetActionHandler.getSupportedToolNames());
        } else {
            truFarChatService = new MockTruFarChatService();
        }

        chatRepository = new ChatRepository(truFarChatService, mcpPetActionHandler);

        // SWAP POINT (Doom Scroll Agent): Usage Access-based foreground-app session detector.
        doomScrollMonitor = new UsageStatsDoomScrollMonitor(appContext);

        // Doom-scroll detections → HP loss. Keep this wiring when swapping the monitor.
        doomScrollHpBridge = new DoomScrollHpBridge(petStateRepository);
        doomScrollMonitor.setListener(doomScrollHpBridge);
    }

    /** @return the process-wide container held by {@link TomodoomApplication}. */
    @NonNull
    public static AppContainer from(@NonNull Context context) {
        return ((TomodoomApplication) context.getApplicationContext()).getAppContainer();
    }

    /**
     * Factory: a <b>new</b> renderer for each Pets view lifecycle (see
     * {@link ManiengineAssetRenderer} for the ownership rules).
     */
    @MainThread
    @NonNull
    public ManiengineAssetRenderer createAssetRenderer() {
        // SWAP POINT (Maniengine Agent): the Maniengine HTML export (assets/maniengine/index.html)
        // in a WebView. PlaceholderManiengineRenderer is the text-only reference stub.
        return new HtmlManiengineRenderer();
    }

    @NonNull
    public PetStateRepository getPetStateRepository() {
        return petStateRepository;
    }

    @NonNull
    public McpPetActionHandler getMcpPetActionHandler() {
        return mcpPetActionHandler;
    }

    @NonNull
    public TruFarChatService getTruFarChatService() {
        return truFarChatService;
    }

    @NonNull
    public ChatRepository getChatRepository() {
        return chatRepository;
    }

    @NonNull
    public DoomScrollMonitor getDoomScrollMonitor() {
        return doomScrollMonitor;
    }

    /**
     * The listener that applies doom-scroll penalties. Exposed so debug tooling can inject
     * synthetic {@code DoomScrollEvent}s through the same path real detections take.
     */
    @NonNull
    public DoomScrollListener getDoomScrollListener() {
        return doomScrollHpBridge;
    }
}
