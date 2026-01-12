package org.example.game;

import org.example.item.ItemType;

public class ShopItem {
    public final ItemType type;
    public final int price;
    public final String description;

    public ShopItem(ItemType type, int price, String description) {
        this.type = type;
        this.price = price;
        this.description = description;
    }
}
