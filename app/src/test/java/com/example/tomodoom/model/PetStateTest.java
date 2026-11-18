package com.example.tomodoom.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PetStateTest {

    @Test
    public void createDefault_hasDocumentedDefaults() {
        PetState s = PetState.createDefault();
        assertEquals(PetState.DEFAULT_PET_NAME, s.getPetName());
        assertEquals(PetState.DEFAULT_START_HP, s.getCurrentHp());
        assertEquals(PetState.DEFAULT_MAX_HP, s.getMaxHp());
        assertTrue(s.getEquippedItems().isEmpty());
        assertTrue(s.getCurrentHp() < s.getMaxHp());
    }

    @Test
    public void withHp_clampsToRange() {
        PetState s = PetState.createDefault();
        assertEquals(s.getMaxHp(), s.withHp(10_000).getCurrentHp());
        assertEquals(0, s.withHp(-5).getCurrentHp());
        assertTrue(s.withHp(-5).isFainted());
        assertEquals(42, s.withHp(42).getCurrentHp());
    }

    @Test
    public void withName_changesOnlyTheName() {
        PetState s = PetState.createDefault().withHp(70).withEquippedItem(AccessorySlot.HAT, "hat_cap");
        PetState renamed = s.withName("Mochi");
        assertEquals("Mochi", renamed.getPetName());
        assertEquals(70, renamed.getCurrentHp());
        assertEquals(s.getMaxHp(), renamed.getMaxHp());
        assertEquals(s.getEquippedItems(), renamed.getEquippedItems());
        assertEquals(PetState.DEFAULT_PET_NAME, s.getPetName());
    }

    @Test
    public void constructor_clampsAndSanitises() {
        Map<String, String> equipped = new HashMap<>();
        equipped.put(AccessorySlot.HAT, "hat_cap");
        equipped.put(AccessorySlot.OUTFIT, "");
        equipped.put("", "x");
        PetState s = new PetState("Rex", 500, 0, equipped);
        assertEquals(1, s.getMaxHp());
        assertEquals(1, s.getCurrentHp());
        assertEquals(1, s.getEquippedItems().size());
        assertEquals("hat_cap", s.getEquippedItem(AccessorySlot.HAT));
    }

    @Test
    public void withEquippedItem_equipsAndUnequips() {
        PetState s = PetState.createDefault()
                .withEquippedItem(AccessorySlot.HAT, "hat_cap")
                .withEquippedItem(AccessorySlot.OUTFIT, "outfit_hoodie");
        assertEquals("hat_cap", s.getEquippedItem(AccessorySlot.HAT));
        assertEquals("outfit_hoodie", s.getEquippedItem(AccessorySlot.OUTFIT));

        PetState cleared = s.withEquippedItem(AccessorySlot.HAT, null);
        assertNull(cleared.getEquippedItem(AccessorySlot.HAT));
        assertEquals("outfit_hoodie", cleared.getEquippedItem(AccessorySlot.OUTFIT));
    }

    @Test
    public void isImmutable() {
        PetState original = PetState.createDefault();
        PetState changed = original.withHp(99).withEquippedItem(AccessorySlot.HAT, "hat_crown");
        assertEquals(PetState.DEFAULT_START_HP, original.getCurrentHp());
        assertNull(original.getEquippedItem(AccessorySlot.HAT));
        assertFalse(original.equals(changed));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void equippedItems_isUnmodifiable() {
        PetState.createDefault().getEquippedItems().put(AccessorySlot.HAT, "hat_cap");
    }

    @Test(expected = IllegalArgumentException.class)
    public void withEquippedItem_rejectsEmptySlot() {
        PetState.createDefault().withEquippedItem("", "hat_cap");
    }

    @Test
    public void equalsAndHashCode_areValueBased() {
        PetState a = PetState.createDefault().withEquippedItem(AccessorySlot.HAT, "hat_cap");
        PetState b = PetState.createDefault().withEquippedItem(AccessorySlot.HAT, "hat_cap");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
