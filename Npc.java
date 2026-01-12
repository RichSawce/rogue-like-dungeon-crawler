package org.example.world;

import java.util.List;

public final class Npc {
    public final NpcType type;
    public final String name;
    public int x, y;

    public Npc(NpcType type, String name, int x, int y) {
        this.type = type;
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public List<String> dialogueLines() {
        return switch (type) {
            case SHOPKEEPER_ITEMS ->
                    List.of("Welcome! Need supplies?", "Press [Z] to browse items.");
            case BLACKSMITH_WEAPONS ->
                    List.of("Looking for something sharp?", "Press [Z] to browse weapons.");
            case INNKEEPER ->
                    List.of("Need a room?", "Press [Z] to rest (restore HP/MP).");
        };
    }
}