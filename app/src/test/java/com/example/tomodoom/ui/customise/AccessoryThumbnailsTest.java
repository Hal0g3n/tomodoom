package com.example.tomodoom.ui.customise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.example.tomodoom.model.AccessoryCatalog;
import com.example.tomodoom.model.AccessoryItem;

import org.junit.Test;

public class AccessoryThumbnailsTest {

    @Test
    public void everyCatalogItemHasAThumbnail() {
        for (AccessoryItem item : AccessoryCatalog.getAllItems()) {
            assertNotEquals(item.getId(), 0, AccessoryThumbnails.forItem(item.getId()));
        }
    }

    @Test
    public void unknownItemHasNoThumbnail() {
        assertEquals(0, AccessoryThumbnails.forItem("not_in_catalog"));
    }
}
