package org.example.world;

public enum Tile {
    WALL('#', false),
    FLOOR('.', true),
    STAIRS_DOWN('>', true);

    public final char glyph;
    public final boolean walkable;

    Tile(char glyph, boolean walkable) {
        this.glyph = glyph;
        this.walkable = walkable;
    }
}