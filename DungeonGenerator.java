package org.example.world;

import org.example.game.GameConfig;
import org.example.game.util.RNG;

import java.util.ArrayList;
import java.util.List;

public final class DungeonGenerator {
    private final RNG rng;

    public DungeonGenerator(RNG rng) {
        this.rng = rng;
    }

    /** Convenience: generate using the configured world size (bigger than viewport). */
    public Dungeon generate() {
        return generate(GameConfig.DUNGEON_W, GameConfig.DUNGEON_H);
    }

    public Dungeon generate(int w, int h) {
        Dungeon d = new Dungeon(w, h);
        List<Rect> rooms = new ArrayList<>();

        for (int i = 0; i < GameConfig.MAX_ROOMS; i++) {
            int rw = rng.range(GameConfig.ROOM_MIN, GameConfig.ROOM_MAX);
            int rh = rng.range(GameConfig.ROOM_MIN, GameConfig.ROOM_MAX);

            // Max top-left so the room stays inside the dungeon with a 1-tile border.
            int maxRx = (w - 2) - rw;  // room occupies [rx .. rx+rw-1] <= w-2
            int maxRy = (h - 2) - rh;

            if (maxRx < 1 || maxRy < 1) continue; // can't fit, try another

            int rx = rng.range(1, maxRx);
            int ry = rng.range(1, maxRy);

            Rect r = new Rect(rx, ry, rw, rh);

            boolean ok = true;
            for (Rect other : rooms) {
                if (r.intersects(expand(other, 1))) { ok = false; break; }
            }
            if (!ok) continue;

            carveRoom(d, r);

            if (!rooms.isEmpty()) {
                Rect prev = rooms.get(rooms.size() - 1);
                carveCorridor(d, prev.cx(), prev.cy(), r.cx(), r.cy());
            }

            rooms.add(r);
        }

        // Guarantee at least 2 rooms by brute fallback (ALWAYS in-bounds)
        if (rooms.size() < 2) {
            Rect a = clampRoom(new Rect(3, 3, 8, 8), w, h);
            Rect b = clampRoom(new Rect(w - 12, h - 12, 8, 8), w, h);

            carveRoom(d, a);
            carveRoom(d, b);
            carveCorridor(d, a.cx(), a.cy(), b.cx(), b.cy());

            rooms.clear();
            rooms.add(a);
            rooms.add(b);
        }

        Rect first = rooms.get(0);
        Rect last  = rooms.get(rooms.size() - 1);

        d.setStart(first.cx(), first.cy());
        d.setStairsDown(last.cx(), last.cy());

        return d;
    }

    // Ensures the room fits inside [1..w-2] / [1..h-2]
    private static Rect clampRoom(Rect r, int w, int h) {
        int minX = 1;
        int minY = 1;
        int maxX = (w - 2) - r.w;
        int maxY = (h - 2) - r.h;

        int cx = clamp(r.x, minX, Math.max(minX, maxX));
        int cy = clamp(r.y, minY, Math.max(minY, maxY));

        return new Rect(cx, cy, r.w, r.h);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    private static Rect expand(Rect r, int pad) {
        return new Rect(r.x - pad, r.y - pad, r.w + pad * 2, r.h + pad * 2);
    }

    private void carveRoom(Dungeon d, Rect r) {
        for (int x = r.x; x < r.x + r.w; x++) {
            for (int y = r.y; y < r.y + r.h; y++) {
                d.setTile(x, y, Tile.FLOOR);
            }
        }
    }

    private void carveCorridor(Dungeon d, int x1, int y1, int x2, int y2) {
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

    public int[] findRandomFloor(Dungeon d) {
        while (true) {
            int x = rng.range(1, d.w() - 2);
            int y = rng.range(1, d.h() - 2);
            if (d.tile(x, y) == Tile.FLOOR) return new int[]{x, y};
        }
    }
}