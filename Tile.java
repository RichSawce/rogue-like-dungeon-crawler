package org.example.world;

public enum Tile {
    // Dungeon / general solids
    WALL('#', false),
    FLOOR('.', true),
    STAIRS_UP('<', true),
    STAIRS_DOWN('>', true),


    // Your existing key/door system
    LOCKED_DOOR('+', false),
    KEY('k', true),

    // --- Town tiles ---
    GRASS(',', true),
    PATH(':', true),

    // Door is walkable (so the player can stand on it), but you can require "interact" to enter
    DOOR('D', true),

    // A special door marker for the crypt entrance if you want it distinct (optional)
    CRYPT_DOOR('C', true),

    // --- Town portal ---
    TOWN_PORTAL('O', true);

    public final char glyph;
    public final boolean walkable;

    Tile(char glyph, boolean walkable) {
        this.glyph = glyph;
        this.walkable = walkable;
    }
}