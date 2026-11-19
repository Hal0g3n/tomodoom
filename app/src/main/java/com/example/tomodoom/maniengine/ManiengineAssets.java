package com.example.tomodoom.maniengine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.model.PetSpecies;

import java.util.Locale;

/**
 * Maps the WebView's virtual HTTPS origin onto {@code app/src/main/assets/maniengine/}. Pure Java
 * (callers pass the already-parsed URL parts), so path handling is unit-tested.
 *
 * <p>The export is served from {@code https://appassets.androidplatform.net/assets/maniengine/…}
 * rather than {@code file://}, so ES modules, {@code fetch()} of {@code .glb}/textures and WASM
 * work as on a normal web server, with no file-access settings enabled. Only files under
 * {@link #ROOT_DIR} are served.</p>
 */
public final class ManiengineAssets {

    /** Host of the virtual origin (the same one {@code androidx.webkit.WebViewAssetLoader} uses). */
    public static final String HOST = "appassets.androidplatform.net";
    /** URL path prefix that maps onto the APK's {@code assets/}. */
    public static final String PATH_PREFIX = "/assets/";
    /** The only asset directory that is served; the Maniengine export goes here. */
    public static final String ROOT_DIR = "maniengine/";
    /** The export's entry page inside {@link #ROOT_DIR}. */
    public static final String ENTRY_FILE = "index.html";
    /** URL the renderer loads. */
    public static final String ENTRY_URL = "https://" + HOST + PATH_PREFIX + ROOT_DIR + ENTRY_FILE;

    private ManiengineAssets() {
    }

    /**
     * @param species a {@link PetSpecies} ID; an unknown one becomes {@link PetSpecies#DEFAULT}.
     * @return the entry URL showing that pet ({@code index.html?pet=<id>}).
     */
    @NonNull
    public static String entryUrl(@Nullable String species) {
        return ENTRY_URL + "?pet=" + PetSpecies.orDefault(species);
    }

    /** @return true if a URL with this scheme and host belongs to the virtual origin. */
    public static boolean isLocalOrigin(@Nullable String scheme, @Nullable String host) {
        return "https".equalsIgnoreCase(scheme) && HOST.equalsIgnoreCase(host);
    }

    /**
     * @param scheme URL scheme
     * @param host   URL host
     * @param path   decoded URL path (e.g. {@code /assets/maniengine/models/pet.glb})
     * @return the asset path to open (e.g. {@code maniengine/models/pet.glb}), or {@code null} if
     * the URL isn't on the virtual origin, is outside {@link #ROOT_DIR}, or tries to escape it.
     * A path ending in {@code /} resolves to its {@code index.html}.
     */
    @Nullable
    public static String assetPathFor(@Nullable String scheme, @Nullable String host,
                                      @Nullable String path) {
        if (!isLocalOrigin(scheme, host) || path == null || !path.startsWith(PATH_PREFIX)) {
            return null;
        }
        String relative = path.substring(PATH_PREFIX.length());
        if (!relative.startsWith(ROOT_DIR) || relative.indexOf('\\') >= 0
                || relative.indexOf('\0') >= 0) {
            return null;
        }
        if (relative.endsWith("/")) {
            relative = relative + ENTRY_FILE;
        }
        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return null;
            }
        }
        return relative;
    }

    /** @return the MIME type to serve {@code assetPath} with (by extension). */
    @NonNull
    public static String mimeTypeFor(@NonNull String assetPath) {
        int dot = assetPath.lastIndexOf('.');
        int slash = assetPath.lastIndexOf('/');
        String ext = dot > slash ? assetPath.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        switch (ext) {
            case "html":
            case "htm":
                return "text/html";
            case "js":
            case "mjs":
                return "text/javascript";
            case "css":
                return "text/css";
            case "json":
                return "application/json";
            case "txt":
                return "text/plain";
            case "svg":
                return "image/svg+xml";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "webp":
                return "image/webp";
            case "gif":
                return "image/gif";
            case "ktx2":
                return "image/ktx2";
            case "glb":
                return "model/gltf-binary";
            case "gltf":
                return "model/gltf+json";
            case "wasm":
                return "application/wasm";
            case "mp3":
                return "audio/mpeg";
            case "ogg":
                return "audio/ogg";
            case "wav":
                return "audio/wav";
            case "mp4":
                return "video/mp4";
            case "woff":
                return "font/woff";
            case "woff2":
                return "font/woff2";
            case "ttf":
                return "font/ttf";
            default:
                return "application/octet-stream";
        }
    }

    /** @return true for MIME types served with a {@code utf-8} charset. */
    public static boolean isText(@NonNull String mimeType) {
        return mimeType.startsWith("text/") || mimeType.equals("application/json")
                || mimeType.equals("image/svg+xml") || mimeType.equals("model/gltf+json");
    }
}
