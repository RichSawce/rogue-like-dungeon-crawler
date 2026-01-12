package org.example.world;

import org.example.game.util.RNG;

import java.util.ArrayList;
import java.util.List;

public final class BuildingGenerator {
    private final RNG rng;

    public BuildingGenerator(RNG rng) {
        this.rng = rng;
    }

    public Dungeon generateInterior(BuildingType type) {
        final int W = 21;
        final int H = 15;

        Dungeon d = new Dungeon(W, H);
        d.setFogEnabled(false);

        // Put the door on the outer border so it truly represents an "exit"
        int doorX = 0;
        int doorY = H / 2;

        // Player starts just inside the door
        int startX = 1;
        int startY = doorY;

        int roomCount = 1 + rng.nextInt(3); // 1..3
        List<Rect> rooms = new ArrayList<>();

        Rect entry = makeEntryRoom(W, H, startX, startY);
        carveRoom(d, entry);
        d.addRoom(entry);
        rooms.add(entry);

        // Connect start tile into the entry room
        carveCorridorL(d, startX, startY, entry.cx(), entry.cy());

        Rect last = entry;

        for (int i = 1; i < roomCount; i++) {
            boolean placed = false;

            for (int tries = 0; tries < 30 && !placed; tries++) {
                int stubLen = 1 + rng.nextInt(3);

                int rw = 5 + rng.nextInt(4);
                int rh = 4 + rng.nextInt(3);

                int dir = rng.nextInt(3); // 0=RIGHT, 1=UP, 2=DOWN

                int sx, sy, ex, ey;
                Rect cand;

                if (dir == 0) { // RIGHT
                    int y = clamp(last.y + 1 + rng.nextInt(Math.max(1, last.h - 2)), 1, H - 2);
                    sx = last.x2() + 1; sy = y;
                    ex = sx + stubLen;  ey = y;

                    int rx = ex + 1;
                    int ry = y - rh / 2;
                    cand = new Rect(rx, ry, rw, rh);

                } else if (dir == 1) { // UP
                    int x = clamp(last.x + 1 + rng.nextInt(Math.max(1, last.w - 2)), 1, W - 2);
                    sx = x; sy = last.y - 1;
                    ex = x; ey = sy - stubLen;

                    int rx = x - rw / 2;
                    int ry = ey - rh;
                    cand = new Rect(rx, ry, rw, rh);

                } else { // DOWN
                    int x = clamp(last.x + 1 + rng.nextInt(Math.max(1, last.w - 2)), 1, W - 2);
                    sx = x; sy = last.y2() + 1;
                    ex = x; ey = sy + stubLen;

                    int rx = x - rw / 2;
                    int ry = ey + 1;
                    cand = new Rect(rx, ry, rw, rh);
                }

                if (!inBoundsRoom(cand, W, H)) continue;

                boolean ok = true;
                for (Rect r : rooms) {
                    if (cand.intersects(expand(r, 1))) { ok = false; break; }
                }
                if (!ok) continue;

                carveStraight(d, sx, sy, ex, ey);
                carveCorridorL(d, ex, ey, cand.cx(), cand.cy());

                carveRoom(d, cand);
                d.addRoom(cand);
                rooms.add(cand);

                last = cand;
                placed = true;
            }
        }
        // ✅ IMPORTANT: set these AFTER carving so they can't be overwritten
        d.setTile(doorX, doorY, Tile.DOOR);
        d.setTile(startX, startY, Tile.FLOOR);
        d.setStart(startX, startY);
        spawnNpcForBuilding(type, d, rooms);
        return d;
    }

    private Rect makeEntryRoom(int W, int H, int startX, int startY) {
        int rw = 7 + rng.nextInt(3);
        int rh = 5 + rng.nextInt(2);

        int rx = 1;
        int ry = clamp(startY - rh / 2, 1, H - rh - 1);

        rx = clamp(rx, 1, W - rw - 1);
        ry = clamp(ry, 1, H - rh - 1);

        return new Rect(rx, ry, rw, rh);
    }

    private boolean inBoundsRoom(Rect r, int W, int H) {
        return r.x >= 1 && r.y >= 1 && r.x2() <= W - 2 && r.y2() <= H - 2;
    }

    private Rect expand(Rect r, int pad) {
        return new Rect(r.x - pad, r.y - pad, r.w + pad * 2, r.h + pad * 2);
    }

    private void carveRoom(Dungeon d, Rect r) {
        for (int x = r.x; x < r.x + r.w; x++) {
            for (int y = r.y; y < r.y + r.h; y++) {
                d.setTile(x, y, Tile.FLOOR);
            }
        }
    }

    private void carveStraight(Dungeon d, int x1, int y1, int x2, int y2) {
        if (x1 == x2) {
            int yMin = Math.min(y1, y2);
            int yMax = Math.max(y1, y2);
            for (int y = yMin; y <= yMax; y++) d.setTile(x1, y, Tile.FLOOR);
        } else if (y1 == y2) {
            int xMin = Math.min(x1, x2);
            int xMax = Math.max(x1, x2);
            for (int x = xMin; x <= xMax; x++) d.setTile(x, y1, Tile.FLOOR);
        }
    }

    private void carveCorridorL(Dungeon d, int x1, int y1, int x2, int y2) {
        if (rng.chance(0.5)) {
            carveH(d, x1, x2, y1);
            carveV(d, y1, y2, x2);
        } else {
            carveV(d, y1, y2, x1);
            carveH(d, x1, x2, y2);
        }
    }

    private void carveH(Dungeon d, int x1, int x2, int y) {
        int start = Math.min(x1, x2);
        int end = Math.max(x1, x2);
        for (int x = start; x <= end; x++) d.setTile(x, y, Tile.FLOOR);
    }

    private void carveV(Dungeon d, int y1, int y2, int x) {
        int start = Math.min(y1, y2);
        int end = Math.max(y1, y2);
        for (int y = start; y <= end; y++) d.setTile(x, y, Tile.FLOOR);
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    private void spawnNpcForBuilding(BuildingType type, Dungeon d, List<Rect> rooms) {
        NpcType npcType = switch (type) {
            case MAGIC_SHOP -> NpcType.SHOPKEEPER_ITEMS;      // your “Item shop”
            case WEAPON_SHOP -> NpcType.BLACKSMITH_WEAPONS;
            case INN -> NpcType.INNKEEPER;
            default -> null;
        };

        if (npcType == null) return;

        String name = switch (npcType) {
            case SHOPKEEPER_ITEMS -> "Shopkeeper";
            case BLACKSMITH_WEAPONS -> "Blacksmith";
            case INNKEEPER -> "Innkeeper";
        };

        int[] pos = pickNpcSpawnTile(d, rooms);
        d.addNpc(new Npc(npcType, name, pos[0], pos[1]));
    }

    private int[] pickNpcSpawnTile(Dungeon d, List<Rect> rooms) {
        // Prefer the LAST room (feels like “behind the counter” / back of building)
        Rect r = rooms.get(rooms.size() - 1);

        for (int tries = 0; tries < 200; tries++) {
            int x = r.x + 1 + rng.nextInt(Math.max(1, r.w - 2));
            int y = r.y + 1 + rng.nextInt(Math.max(1, r.h - 2));

            // Needs a walkable floor tile and not the start tile
            if (!d.isWalkable(x, y)) continue;

            int[] start = d.getStart();
            if (x == start[0] && y == start[1]) continue;

            // Don’t spawn on the door tile either (door is at border in your interiors)
            if (d.tile(x, y) == Tile.DOOR) continue;

            return new int[]{x, y};
        }

        // Fallback: center of last room
        return new int[]{ r.cx(), r.cy() };
    }
}