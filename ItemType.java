package org.example.item;

public enum ItemType {
    // potions
    HP_POTION,
    MP_POTION,

    // swords (small scaling boosts)
    SWORD_WORN,      // +0/+1
    SWORD_BRONZE,    // +1/+1
    SWORD_IRON,      // +1/+2
    SWORD_STEEL,     // +2/+2
    SWORD_KNIGHT,    // +2/+3

    // spell tomes (consumed to learn)
    TOME_ICE_SHARD,
    TOME_FLASH_FREEZE,
    TOME_FIRE_SWORD,
    TOME_HEAL
}
