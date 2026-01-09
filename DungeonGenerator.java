package org.example.world;

import org.example.game.GameConfig;
import org.example.game.util.RNG;

import java.util.ArrayList;
import java.util.List;

public final class DungeonGenerator {
    private final RNG rng;

    // Minimum Manhattan distance (center-to-center) between start room and stairs room.
    // You can tune this. It scales a bit with dungeon size so larger dungeons spread more.
    private static int minStartToStairsDist(int w, int h) {
        // Example: ensure at least 12, and scale up modestly with size.
        return Math.max(12, (w + h) / 8);
    }

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
            d.addRoom(r);

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

        // -----------------------------
        // START + STAIRS placement rule
        // -----------------------------
        Rect startRoom = rooms.get(0);

        // Pick the room farthest from startRoom, preferring those that satisfy a minimum distance
        int minDist = minStartToStairsDist(w, h);
        Rect stairsRoom = pickStairsRoomFarFromStart(rooms, startRoom, minDist);

        // If something went weird (shouldn't), fall back safely
        if (stairsRoom == null) stairsRoom = rooms.get(rooms.size() - 1);
        if (stairsRoom == startRoom && rooms.size() > 1) stairsRoom = rooms.get(1);

        d.setStart(startRoom.cx(), startRoom.cy());
        d.setStairsDown(stairsRoom.cx(), stairsRoom.cy());

        // store bounds for Game (so key chest can avoid stairs room)
        d.setStairsRoomBounds(stairsRoom.x, stairsRoom.y, stairsRoom.w, stairsRoom.h);

        // Make stairs room have ONE entrance, and lock it.
        // IMPORTANT: previously you assumed stairsRoom was "last" and used rooms.size()-2 as prev.
        // Now stairsRoom can be any room, so pick the closest *other* room as the "approach" reference.
        Rect approach = findClosestOtherRoom(rooms, stairsRoom);
        int fromX = (approach != null) ? approach.cx() : startRoom.cx();
        int fromY = (approach != null) ? approach.cy() : startRoom.cy();

        ensureAllRoomsConnected(d, rooms);              // ✅ new: hard connectivity guarantee
        placeLockedDoorsForAllStairsEntrances(d, stairsRoom);  // ✅ new: cannot softlock

// Put an exit door on the boundary of the start room (bottom edge here)
        int exitDoorX = startRoom.cx();
        int exitDoorY = startRoom.y2();
        d.setTile(exitDoorX, exitDoorY, Tile.DOOR);

// Ensure the tile just inside the door is walkable and make that the start
        d.setTile(exitDoorX, exitDoorY - 1, Tile.FLOOR);
        d.setStart(exitDoorX, exitDoorY - 1);

        return d;
    }

    private Rect pickStairsRoomFarFromStart(List<Rect> rooms, Rect startRoom, int minDist) {
        Rect bestMeetingMin = null;
        int bestMeetingMinDist = -1;

        Rect bestOverall = null;
        int bestOverallDist = -1;

        int sx = startRoom.cx();
        int sy = startRoom.cy();

        for (Rect r : rooms) {
            if (r == startRoom) continue;

            int dist = manhattan(sx, sy, r.cx(), r.cy());

            if (dist > bestOverallDist) {
                bestOverallDist = dist;
                bestOverall = r;
            }

            if (dist >= minDist && dist > bestMeetingMinDist) {
                bestMeetingMinDist = dist;
                bestMeetingMin = r;
            }
        }

        // Prefer any room that meets the minimum distance; otherwise use the farthest overall.
        return (bestMeetingMin != null) ? bestMeetingMin : bestOverall;
    }

    private Rect findClosestOtherRoom(List<Rect> rooms, Rect target) {
        Rect best = null;
        int bestDist = Integer.MAX_VALUE;

        int tx = target.cx();
        int ty = target.cy();

        for (Rect r : rooms) {
            if (r == target) continue;
            int dist = manhattan(tx, ty, r.cx(), r.cy());
            if (dist < bestDist) {
                bestDist = dist;
                best = r;
            }
        }
        return best;
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
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

    // (Keeping your existing door helpers unchanged)

    private void placeLockedDoorsForAllStairsEntrances(Dungeon d, Rect stairsRoom) {
        java.util.ArrayList<int[]> candidates = new java.util.ArrayList<>();

        // Collect all boundary tiles that touch outside FLOOR
        for (int x = stairsRoom.x; x <= stairsRoom.x2(); x++) {
            collectIfConnects(d, stairsRoom, x, stairsRoom.y,  candidates);
            collectIfConnects(d, stairsRoom, x, stairsRoom.y2(), candidates);
        }
        for (int y = stairsRoom.y; y <= stairsRoom.y2(); y++) {
            collectIfConnects(d, stairsRoom, stairsRoom.x,  y, candidates);
            collectIfConnects(d, stairsRoom, stairsRoom.x2(), y, candidates);
        }

        // Safety: if no entrances found, force-create one by carving a corridor from room center outward
        if (candidates.isEmpty()) {
            // Try to punch an exit from the room center (very small, controlled carve)
            int cx = stairsRoom.cx();
            int cy = stairsRoom.cy();

            // Try each direction to find a wall to punch through to a non-room tile
            int[][] dirs = { {1,0}, {-1,0}, {0,1}, {0,-1} };
            for (int[] dir : dirs) {
                int x = cx, y = cy;
                for (int steps = 0; steps < 12; steps++) { // short poke
                    x += dir[0];
                    y += dir[1];
                    if (!d.inBounds(x, y)) break;

                    // once we're outside the room bounds, carve a tiny hallway
                    boolean inside =
                            x >= stairsRoom.x && x <= stairsRoom.x2() &&
                                    y >= stairsRoom.y && y <= stairsRoom.y2();

                    d.setTile(x, y, Tile.FLOOR);

                    // stop after we exit the room and have at least 1 outside tile carved
                    if (!inside) break;
                }
            }

            // Re-collect after forcing
            candidates.clear();
            for (int x = stairsRoom.x; x <= stairsRoom.x2(); x++) {
                collectIfConnects(d, stairsRoom, x, stairsRoom.y,  candidates);
                collectIfConnects(d, stairsRoom, x, stairsRoom.y2(), candidates);
            }
            for (int y = stairsRoom.y; y <= stairsRoom.y2(); y++) {
                collectIfConnects(d, stairsRoom, stairsRoom.x,  y, candidates);
                collectIfConnects(d, stairsRoom, stairsRoom.x2(), y, candidates);
            }
        }

        // Lock every connecting entrance tile (no bypass, no sealing-by-wall)
        for (int[] p : candidates) {
            int x = p[0], y = p[1];
            if (d.isStairsDown(x, y)) continue;
            d.setLockedDoor(x, y);
        }
    }

    private void ensureAllRoomsConnected(Dungeon d, java.util.List<Rect> rooms) {
        if (rooms == null || rooms.isEmpty()) return;

        // Start flood fill from the first room center (start room)
        Rect startRoom = rooms.get(0);
        boolean[][] reachable = floodFill(d, startRoom.cx(), startRoom.cy(), false);

        // If any room center isn't reachable, connect it.
        for (Rect r : rooms) {
            int cx = r.cx();
            int cy = r.cy();
            if (!inBoundsVis(reachable, cx, cy) || !reachable[cx][cy]) {
                // Carve corridor from nearest reachable tile to this room center
                int[] anchor = findNearestReachableTile(reachable, cx, cy);
                if (anchor != null) {
                    carveCorridor(d, anchor[0], anchor[1], cx, cy);
                    // Recompute reachable after each fix (keeps it simple + safe)
                    reachable = floodFill(d, startRoom.cx(), startRoom.cy(), false);
                } else {
                    // Worst-case: just connect from start room
                    carveCorridor(d, startRoom.cx(), startRoom.cy(), cx, cy);
                    reachable = floodFill(d, startRoom.cx(), startRoom.cy(), false);
                }
            }
        }
    }

    private boolean[][] floodFill(Dungeon d, int sx, int sy, boolean treatLockedDoorAsFloor) {
        int w = d.w(), h = d.h();
        boolean[][] vis = new boolean[w][h];

        if (!d.inBounds(sx, sy)) return vis;

        // Start must be on floor-ish; if not, find nearby floor
        if (!isPassableForConnectivity(d, sx, sy, treatLockedDoorAsFloor)) {
            int[] p = findAnyNearbyPassable(d, sx, sy, treatLockedDoorAsFloor);
            if (p == null) return vis;
            sx = p[0]; sy = p[1];
        }

        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        vis[sx][sy] = true;
        q.add(new int[]{sx, sy});

        int[][] dirs = { {1,0}, {-1,0}, {0,1}, {0,-1} };

        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int x = cur[0], y = cur[1];

            for (int[] dir : dirs) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                if (!d.inBounds(nx, ny)) continue;
                if (vis[nx][ny]) continue;
                if (!isPassableForConnectivity(d, nx, ny, treatLockedDoorAsFloor)) continue;

                vis[nx][ny] = true;
                q.addLast(new int[]{nx, ny});
            }
        }

        return vis;
    }

    private boolean isPassableForConnectivity(Dungeon d, int x, int y, boolean treatLockedDoorAsFloor) {
        Tile t = d.tile(x, y);
        if (t == Tile.WALL) return false;
        if (t == Tile.LOCKED_DOOR) return treatLockedDoorAsFloor; // optional mode
        return true; // FLOOR, STAIRS_DOWN, KEY etc.
    }

    private int[] findNearestReachableTile(boolean[][] reachable, int tx, int ty) {
        // Simple expanding ring search (fast enough for your sizes)
        int w = reachable.length;
        int h = reachable[0].length;

        int maxR = Math.max(w, h);

        for (int r = 0; r <= maxR; r++) {
            for (int dx = -r; dx <= r; dx++) {
                int x1 = tx + dx;
                int y1 = ty - r;
                int y2 = ty + r;

                if (inBoundsVis(reachable, x1, y1) && reachable[x1][y1]) return new int[]{x1, y1};
                if (inBoundsVis(reachable, x1, y2) && reachable[x1][y2]) return new int[]{x1, y2};
            }
            for (int dy = -r + 1; dy <= r - 1; dy++) {
                int y1 = ty + dy;
                int x1 = tx - r;
                int x2 = tx + r;

                if (inBoundsVis(reachable, x1, y1) && reachable[x1][y1]) return new int[]{x1, y1};
                if (inBoundsVis(reachable, x2, y1) && reachable[x2][y1]) return new int[]{x2, y1};
            }
        }

        return null;
    }

    private boolean inBoundsVis(boolean[][] a, int x, int y) {
        return x >= 0 && y >= 0 && x < a.length && y < a[0].length;
    }

    private int[] findAnyNearbyPassable(Dungeon d, int sx, int sy, boolean treatLockedDoorAsFloor) {
        for (int r = 0; r <= 6; r++) {
            for (int x = sx - r; x <= sx + r; x++) {
                for (int y = sy - r; y <= sy + r; y++) {
                    if (!d.inBounds(x, y)) continue;
                    if (isPassableForConnectivity(d, x, y, treatLockedDoorAsFloor)) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private boolean tryDoorAt(Dungeon d, Rect room, int x, int y) {
        if (d.tile(x, y) != Tile.FLOOR) return false;
        if (d.isStairsDown(x, y)) return false;
        if (connectsOutsideRoomToFloor(d, room, x, y)) {
            d.setLockedDoor(x, y);
            return true;
        }
        return false;
    }

    private boolean connectsOutsideRoomToFloor(Dungeon d, Rect room, int x, int y) {
        int[][] dirs = { {1,0}, {-1,0}, {0,1}, {0,-1} };
        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int ny = y + dir[1];

            boolean insideRoom =
                    nx >= room.x && nx <= room.x2() &&
                            ny >= room.y && ny <= room.y2();

            if (!insideRoom && d.inBounds(nx, ny) && d.tile(nx, ny) == Tile.FLOOR) {
                return true;
            }
        }
        return false;
    }

    private void placeSingleLockedEntranceForStairsRoom(Dungeon d, Rect stairsRoom, int fromX, int fromY) {
        java.util.ArrayList<int[]> candidates = new java.util.ArrayList<>();

        for (int x = stairsRoom.x; x <= stairsRoom.x2(); x++) {
            collectIfConnects(d, stairsRoom, x, stairsRoom.y,  candidates);
            collectIfConnects(d, stairsRoom, x, stairsRoom.y2(), candidates);
        }
        for (int y = stairsRoom.y; y <= stairsRoom.y2(); y++) {
            collectIfConnects(d, stairsRoom, stairsRoom.x,  y, candidates);
            collectIfConnects(d, stairsRoom, stairsRoom.x2(), y, candidates);
        }

        if (candidates.isEmpty()) return;

        int bestIdx = 0;
        int bestDist = Integer.MAX_VALUE;

        for (int i = 0; i < candidates.size(); i++) {
            int[] p = candidates.get(i);
            int dist = Math.abs(p[0] - fromX) + Math.abs(p[1] - fromY);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }

        int[] entrance = candidates.get(bestIdx);
        int ex = entrance[0], ey = entrance[1];

        for (int i = 0; i < candidates.size(); i++) {
            if (i == bestIdx) continue;
            int[] p = candidates.get(i);
            if (d.isStairsDown(p[0], p[1])) continue;
            d.setTile(p[0], p[1], Tile.WALL);
        }

        if (!d.isStairsDown(ex, ey) && d.tile(ex, ey) == Tile.FLOOR) {
            d.setLockedDoor(ex, ey);
        }
    }

    private void collectIfConnects(Dungeon d, Rect room, int x, int y, java.util.List<int[]> out) {
        if (d.tile(x, y) != Tile.FLOOR) return;
        if (d.isStairsDown(x, y)) return;

        if (connectsOutsideRoomToFloor(d, room, x, y)) {
            out.add(new int[]{x, y});
        }
    }

    public int[] findRandomFloor(Dungeon d) {
        while (true) {
            int x = rng.range(1, d.w() - 2);
            int y = rng.range(1, d.h() - 2);
            if (d.tile(x, y) == Tile.FLOOR) return new int[]{x, y};
        }
    }
}