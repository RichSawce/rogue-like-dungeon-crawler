package org.example.entity;

import org.example.item.ItemType;

public final class Chest {
    public final int x, y;
    public final ItemType loot;
    public boolean opened = false;

    public Chest(int x, int y, ItemType loot) {
        this.x = x;
        this.y = y;
        this.loot = loot;
    }
}