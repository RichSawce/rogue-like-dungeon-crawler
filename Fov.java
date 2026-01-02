package org.example.world;

public final class Fov {
    private boolean[][] vis;

    public void compute(Dungeon d, int ox, int oy, int radius) {
        int w = d.w(), h = d.h();
        vis = new boolean[w][h];

        // Simple ray-cast to each tile in a square around player.
        // Not the most advanced FOV, but solid for a first playable and easy to upgrade later.
        for (int x = ox - radius; x <= ox + radius; x++) {
            for (int y = oy - radius; y <= oy + radius; y++) {
                if (!d.inBounds(x, y)) continue;
                int dx = x - ox, dy = y - oy;
                if (dx*dx + dy*dy > radius*radius) continue;

                if (hasLineOfSight(d, ox, oy, x, y)) {
                    vis[x][y] = true;
                }
            }
        }

        // Always see your own tile
        if (d.inBounds(ox, oy)) vis[ox][oy] = true;
    }

    public boolean visible(int x, int y) {
        if (vis == null) return false;
        if (x < 0 || y < 0 || x >= vis.length || y >= vis[0].length) return false;
        return vis[x][y];
    }

    private boolean hasLineOfSight(Dungeon d, int x0, int y0, int x1, int y1) {
        // Bresenham line; stop when hitting a wall (but allow the target tile itself to be visible if it is a wall edge).
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int x = x0, y = y0;

        while (true) {
            if (x == x1 && y == y1) return true;

            // step
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx)  { err += dx; y += sy; }

            // if the ray entered a wall tile, block further sight
            if (d.blocksSight(x, y)) {
                // you can "see" the first blocking tile (wall face)
                return (x == x1 && y == y1);
            }
        }
    }
}
