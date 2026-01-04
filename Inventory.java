package org.example.item;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

public final class Inventory {
    private final Map<ItemType, Integer> counts = new EnumMap<>(ItemType.class);

    public int count(ItemType type) {
        return counts.getOrDefault(type, 0);
    }

    public void add(ItemType type, int amount) {
        if (amount <= 0) return;
        counts.put(type, count(type) + amount);
    }

    public boolean consumeOne(ItemType type) {
        int c = count(type);
        if (c <= 0) return false;
        if (c == 1) counts.remove(type);
        else counts.put(type, c - 1);
        return true;
    }
    public List<ItemType> nonEmptyTypes() {
        ArrayList<ItemType> out = new ArrayList<>();
        // preserves enum order
        for (ItemType t : ItemType.values()) {
            if (count(t) > 0) out.add(t);
        }
        return out;
    }
}