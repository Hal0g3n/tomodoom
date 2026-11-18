package com.example.tomodoom.maniengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManiengineAssetsTest {

    private static String resolve(String path) {
        return ManiengineAssets.assetPathFor("https", ManiengineAssets.HOST, path);
    }

    @Test
    public void entryUrl_resolvesToEntryAsset() {
        assertEquals("https://appassets.androidplatform.net/assets/maniengine/index.html",
                ManiengineAssets.ENTRY_URL);
        assertEquals("maniengine/index.html", resolve("/assets/maniengine/index.html"));
    }

    @Test
    public void nestedFilesAndDirectoryIndex() {
        assertEquals("maniengine/models/pet.glb", resolve("/assets/maniengine/models/pet.glb"));
        assertEquals("maniengine/index.html", resolve("/assets/maniengine/"));
        assertEquals("maniengine/js/index.html", resolve("/assets/maniengine/js/"));
    }

    @Test
    public void rejectsOtherOriginsAndDirectories() {
        assertNull(ManiengineAssets.assetPathFor("http", ManiengineAssets.HOST,
                "/assets/maniengine/index.html"));
        assertNull(ManiengineAssets.assetPathFor("https", "example.com",
                "/assets/maniengine/index.html"));
        assertNull(resolve("/assets/trufar/kafka_agent.json"));
        assertNull(resolve("/res/maniengine/index.html"));
        assertNull(resolve(null));
    }

    @Test
    public void rejectsTraversalAndOddSegments() {
        assertNull(resolve("/assets/maniengine/../trufar/kafka_agent.json"));
        assertNull(resolve("/assets/maniengine/./index.html"));
        assertNull(resolve("/assets/maniengine//index.html"));
        assertNull(resolve("/assets/maniengine/a\\b.js"));
    }

    @Test
    public void hostAndSchemeAreCaseInsensitive() {
        assertTrue(ManiengineAssets.isLocalOrigin("HTTPS", "AppAssets.AndroidPlatform.net"));
        assertFalse(ManiengineAssets.isLocalOrigin(null, null));
    }

    @Test
    public void mimeTypes() {
        assertEquals("text/html", ManiengineAssets.mimeTypeFor("maniengine/index.html"));
        assertEquals("text/javascript", ManiengineAssets.mimeTypeFor("maniengine/three.module.JS"));
        assertEquals("text/javascript", ManiengineAssets.mimeTypeFor("maniengine/app.mjs"));
        assertEquals("model/gltf-binary", ManiengineAssets.mimeTypeFor("maniengine/pet.glb"));
        assertEquals("application/wasm", ManiengineAssets.mimeTypeFor("maniengine/draco.wasm"));
        assertEquals("application/octet-stream", ManiengineAssets.mimeTypeFor("maniengine/a.bin"));
        assertEquals("application/octet-stream", ManiengineAssets.mimeTypeFor("maniengine.d/noext"));
        assertTrue(ManiengineAssets.isText("text/css"));
        assertFalse(ManiengineAssets.isText("model/gltf-binary"));
    }
}
