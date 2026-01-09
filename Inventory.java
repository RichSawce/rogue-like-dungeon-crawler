package org.example.item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Inventory {
    private final Map<ItemType, Integer> counts = new EnumMap<>(ItemType.class);

    public int count(ItemType type) {
        return counts.getOrDefault(type, 0);
    }

    public boolean has(ItemType type) {
        return count(type) > 0;
    }

    public boolean hasAtLeast(ItemType type, int amount) {
        return amount > 0 && count(type) >= amount;
    }

    public void add(ItemType type, int amount) {
        if (type == null || amount <= 0) return;
        counts.put(type, count(type) + amount);
    }

    /** Remove up to `amount` items; returns how many were removed. */
    public int remove(ItemType type, int amount) {
        if (type == null || amount <= 0) return 0;

        int c = count(type);
        if (c <= 0) return 0;

        int removed = Math.min(c, amount);
        int left = c - removed;

        if (left <= 0) counts.remove(type);
        else counts.put(type, left);

        return removed;
    }

    public boolean consumeOne(ItemType type) {
        return remove(type, 1) == 1;

    }

    public boolean consume(ItemType type, int amount) {
        if (!hasAtLeast(type, amount)) return false;
        remove(type, amount);
        return true;
    }

    public void clear(ItemType type) {
        if (type == null) return;
        counts.remove(type);
    }

    /** Enum-order list of types with count > 0 (good for UI). */
    public List<ItemType> nonEmptyTypes() {
        ArrayList<ItemType> out = new ArrayList<>();
        for (ItemType t : ItemType.values()) {
            if (count(t) > 0) out.add(t);
        }
        return out;
    }

    /** Optional: helpful for debugging/UI if you ever want counts directly. */
    public Map<ItemType, Integer> snapshot() {
        return new EnumMap<>(counts);
    }
}