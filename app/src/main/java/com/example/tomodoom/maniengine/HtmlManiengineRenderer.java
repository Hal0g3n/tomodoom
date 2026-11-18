package com.example.tomodoom.maniengine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
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

    @MainThread
    @Override
    public void render(@NonNull ViewGroup container, @NonNull PetState state) {
        latestJson = ManiengineProtocol.stateJson(state);
        if (failureMessage != 0) {
            showMessage(container, failureMessage);
            return;
        }
        if (webView == null || webView.getParent() != container) {
            createWebView(container);
            if (webView == null) {
                return;
            }
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
    private void createWebView(@NonNull ViewGroup container) {
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

        // Keep the viewport's 2dp border visible and clip the page to its rounded corners.
        int inset = context.getResources().getDimensionPixelSize(R.dimen.pet_viewport_stroke);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(inset, inset, inset, inset);
        container.setClipToOutline(true);
        container.addView(view, params);

        webView = view;
        pageReady = false;
        deliveredJson = null;
        if (paused) {
            view.onPause();
        }
        view.loadUrl(ManiengineAssets.ENTRY_URL);
    }

    @MainThread
    private void destroyWebView(@NonNull ViewGroup container) {
        WebView view = webView;
        webView = null;
        pageReady = false;
        deliveredJson = null;
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

    @MainThread
    private void onPageReady(@NonNull WebView view) {
        if (view != webView) {
            return; // a destroyed WebView's late callback
        }
        pageReady = true;
        view.evaluateJavascript(ManiengineProtocol.TOUCH_POLICY_QUERY, result -> {
            if (view == webView) {
                webView.capturesTouch = ManiengineProtocol.capturesTouch(result);
            }
        });
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
     * WebView that, when the page asked to capture touch, stops ancestors (the tab pager) from
     * intercepting drags that start on it.
     */
    private static final class PetWebView extends WebView {

        /** Page asked to keep drags (see {@link ManiengineProtocol#TOUCH_CAPTURE}). */
        boolean capturesTouch;

        PetWebView(@NonNull Context context) {
            super(context);
        }

        PetWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
            if (capturesTouch && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
            }
            return super.dispatchTouchEvent(event);
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
