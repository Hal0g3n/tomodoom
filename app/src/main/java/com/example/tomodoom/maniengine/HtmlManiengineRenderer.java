package com.example.tomodoom.maniengine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import com.example.tomodoom.BuildConfig;
import com.example.tomodoom.R;
import com.example.tomodoom.contracts.ManiengineAssetRenderer;
import com.example.tomodoom.model.PetState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * Renders the Maniengine HTML export (a 3D scene) in a {@link WebView} filling the pet viewport.
 *
 * <ul>
 *     <li>The page is {@code assets/maniengine/index.html}, served with its sibling files from a
 *     virtual HTTPS origin by {@link ManiengineAssets} (no {@code file://} access).</li>
 *     <li>Pet state is pushed with {@link ManiengineProtocol#deliveryScript} after the page loads
 *     and on every change. Unchanged states are not re-sent.</li>
 *     <li>No JavaScript interface is exposed, and the main frame can't navigate off the origin.</li>
 *     <li>If the WebView can't be created, or its render process dies (e.g. a WebGL crash), the
 *     viewport shows a message instead of crashing the app.</li>
 * </ul>
 *
 * <p>Main thread only, like the contract. Asset reads happen on WebView's own IO thread
 * ({@code shouldInterceptRequest}).</p>
 */
public final class HtmlManiengineRenderer implements ManiengineAssetRenderer {

    private static final String TAG = "HtmlManiengine";

    @Nullable
    private PetWebView webView;
    @Nullable
    private TextView messageView;
    /** True once the entry page has finished loading; state can be delivered from then on. */
    private boolean pageReady;
    /** Set when the WebView is unusable for the rest of this renderer's life. */
    @StringRes
    private int failureMessage;
    private boolean paused;
    /** Latest state JSON, delivered or waiting for the page to be ready. */
    @Nullable
    private String latestJson;
    @Nullable
    private String deliveredJson;
    /** The {@code PetSpecies} the WebView's page was loaded with. */
    @Nullable
    private String loadedSpecies;
    /** Last model box script sent to the loaded page. */
    @Nullable
    private String deliveredTouchBox;
    /** Last transition frame script sent to the loaded page, or null while it has none. */
    @Nullable
    private String deliveredFrame;

    @MainThread
    @Override
    public void render(@NonNull ViewGroup container, @NonNull PetState state) {
        latestJson = ManiengineProtocol.stateJson(state);
        if (failureMessage != 0) {
            showMessage(container, failureMessage);
            return;
        }
        if (webView == null) {
            createWebView(container, state.getPetSpecies());
            if (webView == null) {
                return;
            }
        } else if (webView.getParent() != container) {
            moveWebView(webView, container); // shared renderer: keep the loaded page, no reload
        }
        if (!state.getPetSpecies().equals(loadedSpecies)) {
            loadPage(webView, state.getPetSpecies()); // another pet: the page picks it at load
        }
        deliverIfReady();
    }

    @MainThread
    @Override
    public void pause(@NonNull ViewGroup container) {
        paused = true;
        if (webView != null) {
            webView.onPause();
        }
    }

    @MainThread
    @Override
    public void resume(@NonNull ViewGroup container) {
        paused = false;
        if (webView != null) {
            webView.onResume();
        }
    }

    @MainThread
    @Override
    public boolean supportsFramedTransition() {
        return true;
    }

    @MainThread
    @Override
    public void setFrame(@NonNull ViewGroup container, @Nullable RectF frame,
                         float cornerRadiusPx) {
        PetWebView view = webView;
        if (view == null || !pageReady) {
            return; // nothing to frame; the page starts unframed
        }
        String script;
        if (frame == null) {
            if (deliveredFrame == null) {
                return;
            }
            script = ManiengineProtocol.CLEAR_FRAME_SCRIPT;
        } else {
            // The WebView fills the container minus webViewParams' inset; the page counts CSS px.
            int inset = insetFor(container);
            float density = container.getResources().getDisplayMetrics().density;
            script = ManiengineProtocol.frameScript((frame.left - inset) / density,
                    (frame.top - inset) / density, frame.width() / density,
                    frame.height() / density, cornerRadiusPx / density,
                    (container.getWidth() - 2 * inset) / density,
                    (container.getHeight() - 2 * inset) / density);
            if (script.equals(deliveredFrame)) {
                return;
            }
        }
        view.evaluateJavascript(script, null);
        deliveredFrame = frame == null ? null : script;
    }

    @MainThread
    @Override
    public void release(@NonNull ViewGroup container) {
        destroyWebView(container);
        if (messageView != null) {
            container.removeView(messageView);
            messageView = null;
        }
        container.setClipToOutline(false);
        latestJson = null;
    }

    // ---------------------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled") // the page is a bundled asset; no JS interface is added
    @MainThread
    private void createWebView(@NonNull ViewGroup container, @NonNull String species) {
        destroyWebView(container);
        container.removeAllViews();
        messageView = null;
        Context context = container.getContext();
        PetWebView view;
        try {
            view = new PetWebView(context);
        } catch (RuntimeException e) { // no WebView provider installed, or it is being updated
            Log.w(TAG, "WebView unavailable", e);
            failureMessage = R.string.maniengine_unavailable;
            showMessage(container, failureMessage);
            return;
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG); // chrome://inspect in debug

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        view.setBackgroundColor(Color.TRANSPARENT); // the viewport background shows while loading
        view.setWebViewClient(new Client(context.getAssets()));
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                        oldBottom) -> v.post(this::updateTouchBox));

        container.setClipToOutline(true);
        container.addView(view, webViewParams(container));

        webView = view;
        if (paused) {
            view.onPause();
        }
        loadPage(view, species);
    }

    /** (Re)loads the entry page for {@code species}; state is re-sent once it has loaded. */
    @MainThread
    private void loadPage(@NonNull WebView view, @NonNull String species) {
        pageReady = false;
        deliveredJson = null;
        deliveredTouchBox = null;
        deliveredFrame = null;
        loadedSpecies = species;
        view.loadUrl(ManiengineAssets.entryUrl(species));
    }

    /** Moves the live WebView (and its loaded page) from its current viewport into {@code container}. */
    @MainThread
    private void moveWebView(@NonNull WebView view, @NonNull ViewGroup container) {
        ViewParent oldParent = view.getParent();
        if (oldParent instanceof ViewGroup) {
            ViewGroup old = (ViewGroup) oldParent;
            old.removeView(view);
            old.setClipToOutline(false);
        }
        container.removeAllViews();
        messageView = null;
        container.setClipToOutline(true);
        container.addView(view, webViewParams(container));
    }

    /**
     * Fills the viewport. A viewport with a border (a background) keeps it visible and clips the
     * page to its rounded corners; a borderless one is filled edge to edge.
     */
    @NonNull
    private static FrameLayout.LayoutParams webViewParams(@NonNull ViewGroup container) {
        int inset = insetFor(container);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(inset, inset, inset, inset);
        return params;
    }

    /** The WebView's margin inside {@code container}: the border's width if it has one. */
    private static int insetFor(@NonNull ViewGroup container) {
        return container.getBackground() == null ? 0
                : container.getResources().getDimensionPixelSize(R.dimen.pet_viewport_stroke);
    }

    @MainThread
    private void destroyWebView(@NonNull ViewGroup container) {
        WebView view = webView;
        webView = null;
        pageReady = false;
        deliveredJson = null;
        deliveredTouchBox = null;
        deliveredFrame = null;
        loadedSpecies = null;
        if (view == null) {
            return;
        }
        container.removeView(view);
        view.stopLoading();
        view.destroy();
    }

    @MainThread
    private void deliverIfReady() {
        if (webView == null || !pageReady || latestJson == null
                || latestJson.equals(deliveredJson)) {
            return; // idempotent: the page already has this state
        }
        webView.evaluateJavascript(ManiengineProtocol.deliveryScript(latestJson), null);
        deliveredJson = latestJson;
    }

    /**
     * Works out where drags rotate the pet: lined up with the health bar when the page has one,
     * otherwise inset from the viewport's sides. Sends the page the box whenever it changes.
     */
    @MainThread
    private void updateTouchBox() {
        PetWebView view = webView;
        if (view == null || !(view.getParent() instanceof ViewGroup)
                || view.getWidth() == 0 || view.getHeight() == 0) {
            return;
        }
        ViewGroup container = (ViewGroup) view.getParent();
        int width = view.getWidth();
        int height = view.getHeight();
        float inset = view.getResources().getDimension(R.dimen.pet_model_box_inset);
        float left = inset;
        float top = 0f;
        float right = width - inset;
        float bottom = height * (1f - (float) ManiengineProtocol.TAB_SWIPE_BOTTOM_FRACTION);
        ViewParent page = container.getParent();
        View healthBar = page instanceof ViewGroup
                ? ((ViewGroup) page).findViewById(R.id.hp_card) : null;
        if (healthBar != null) { // the bar sits below the rabbit and stays inside the box
            int offsetX = container.getLeft() + view.getLeft();
            left = healthBar.getLeft() - offsetX;
            top = inset;
            right = healthBar.getRight() - offsetX;
            bottom = healthBar.getBottom() - (container.getTop() + view.getTop());
        }
        view.setModelBox(left, top, right, bottom);
        String script = ManiengineProtocol.touchBoxScript(left / width, top / height,
                right / width, bottom / height);
        if (pageReady && !script.equals(deliveredTouchBox)) {
            view.evaluateJavascript(script, null);
            deliveredTouchBox = script;
        }
    }

    @MainThread
    private void onPageReady(@NonNull WebView view) {
        if (view != webView) {
            return; // a destroyed WebView's late callback
        }
        pageReady = true;
        view.evaluateJavascript(ManiengineProtocol.TOUCH_POLICY_QUERY, result -> {
            if (view == webView) {
                String policy = ManiengineProtocol.touchPolicy(result);
                webView.capturesTouch = ManiengineProtocol.TOUCH_CAPTURE.equals(policy);
                webView.capturesCentre = ManiengineProtocol.TOUCH_CENTRE.equals(policy);
            }
        });
        updateTouchBox();
        deliverIfReady();
    }

    @MainThread
    private void onRenderProcessGone(@NonNull WebView view) {
        if (view != webView) {
            return;
        }
        Log.w(TAG, "Maniengine page's render process is gone");
        ViewParent parent = view.getParent();
        failureMessage = R.string.maniengine_crashed;
        if (parent instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) parent;
            destroyWebView(container);
            showMessage(container, failureMessage);
        } else {
            webView = null;
            view.destroy();
        }
    }

    @MainThread
    private void showMessage(@NonNull ViewGroup container, @StringRes int message) {
        if (messageView != null && messageView.getParent() == container) {
            return;
        }
        if (messageView != null && messageView.getParent() instanceof ViewGroup) {
            ((ViewGroup) messageView.getParent()).removeView(messageView); // left in another viewport
        }
        container.removeAllViews();
        TextView view = new TextView(container.getContext());
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setText(message);
        container.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        messageView = view;
    }

    // ---------------------------------------------------------------------------------------

    /**
     * WebView that stops ancestors (the tab pager) from intercepting a multi-touch gesture, so the
     * page can pinch-zoom, and any drag the page asked to keep through its touch policy.
     */
    private static final class PetWebView extends WebView {

        /** Page asked to keep drags (see {@link ManiengineProtocol#TOUCH_CAPTURE}). */
        boolean capturesTouch;
        /** Page asked to keep model box drags (see {@link ManiengineProtocol#TOUCH_CENTRE}). */
        boolean capturesCentre;
        private float boxLeft;
        private float boxTop;
        private float boxRight;
        private float boxBottom;
        /** Measures a model box drag until it is a tab flick or too old to become one. */
        @Nullable
        private VelocityTracker flickTracker;
        private long touchDownTime;

        PetWebView(@NonNull Context context) {
            super(context);
        }

        PetWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                stopFlickTracking();
                boolean inModelBox = capturesCentre && ManiengineProtocol.isModelBox(
                        event.getX(), event.getY(), boxLeft, boxTop, boxRight, boxBottom);
                if (inModelBox) {
                    flickTracker = VelocityTracker.obtain();
                    touchDownTime = event.getEventTime();
                }
                if (capturesTouch || inModelBox) {
                    setPagerBlocked(true);
                }
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                stopFlickTracking();
                setPagerBlocked(true);
            }
            trackFlick(event, action);
            return super.dispatchTouchEvent(event);
        }

        @Override
        protected void onDetachedFromWindow() {
            stopFlickTracking();
            super.onDetachedFromWindow();
        }

        void setModelBox(float left, float top, float right, float bottom) {
            boxLeft = left;
            boxTop = top;
            boxRight = right;
            boxBottom = bottom;
        }

        /** Hands a fast flick to the pager, which then cancels this touch for the page. */
        private void trackFlick(@NonNull MotionEvent event, int action) {
            if (flickTracker == null) {
                return;
            }
            flickTracker.addMovement(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                stopFlickTracking();
            } else if (action == MotionEvent.ACTION_MOVE) {
                long elapsed = event.getEventTime() - touchDownTime;
                float density = getResources().getDisplayMetrics().density;
                flickTracker.computeCurrentVelocity(1000);
                if (ManiengineProtocol.isTabFlick(flickTracker.getXVelocity() / density,
                        flickTracker.getYVelocity() / density, elapsed)) {
                    stopFlickTracking();
                    setPagerBlocked(false);
                } else if (elapsed > ManiengineProtocol.TAB_FLICK_WINDOW_MS) {
                    stopFlickTracking();
                }
            }
        }

        private void stopFlickTracking() {
            if (flickTracker != null) {
                flickTracker.recycle();
                flickTracker = null;
            }
        }

        private void setPagerBlocked(boolean blocked) {
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(blocked);
            }
        }
    }

    private final class Client extends WebViewClient {

        @NonNull
        private final AssetManager assets;

        Client(@NonNull AssetManager assets) {
            this.assets = assets;
        }

        @WorkerThread
        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!ManiengineAssets.isLocalOrigin(url.getScheme(), url.getHost())) {
                return null; // network resources (e.g. a CDN script) load normally
            }
            String path = ManiengineAssets.assetPathFor(url.getScheme(), url.getHost(),
                    url.getPath());
            if (path == null) {
                return notFound();
            }
            String mime = ManiengineAssets.mimeTypeFor(path);
            try {
                InputStream in = assets.open(path);
                return new WebResourceResponse(mime,
                        ManiengineAssets.isText(mime) ? "utf-8" : null, in);
            } catch (IOException e) {
                Log.w(TAG, "Missing Maniengine asset: " + path);
                return notFound();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view,
                                                @NonNull WebResourceRequest request) {
            // Stay on the bundled page; links never navigate the viewport elsewhere.
            Uri url = request.getUrl();
            return !ManiengineAssets.isLocalOrigin(url.getScheme(), url.getHost());
        }

        @Override
        public void onPageFinished(@NonNull WebView view, @NonNull String url) {
            onPageReady(view);
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @Override
        public boolean onRenderProcessGone(@NonNull WebView view,
                                           @NonNull RenderProcessGoneDetail detail) {
            HtmlManiengineRenderer.this.onRenderProcessGone(view);
            return true; // handled: don't kill the app
        }

        @NonNull
        private WebResourceResponse notFound() {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                    Collections.<String, String>emptyMap(), new ByteArrayInputStream(new byte[0]));
        }
    }
}
