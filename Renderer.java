package org.example.ui;

import org.example.entity.Enemy;
import org.example.game.Game;
import org.example.game.GameConfig;
import org.example.world.Dungeon;
import org.example.world.Tile;

import java.awt.*;
import java.util.List;

public final class Renderer {

    public void draw(Graphics2D g, Game game, int panelW, int panelH) {
        Dungeon d = game.dungeon();
        if (d == null) return;

        int tile = GameConfig.TILE_SIZE;
        int uiTiles = GameConfig.UI_H_TILES;

        // Viewport (map area) excludes the UI bar at the bottom
        int viewWpx = panelW;
        int viewHpx = panelH - (uiTiles * tile);
        int uiY = viewHpx;

        int worldWpx = d.w() * tile;
        int worldHpx = d.h() * tile;

        // Camera follows player (pixel coords), clamped to dungeon bounds
        int px = game.player().x * tile + tile / 2;
        int py = game.player().y * tile + tile / 2;

        int camX = px - viewWpx / 2;
        int camY = py - viewHpx / 2;

        if (worldWpx <= viewWpx) camX = 0;
        else camX = Math.max(0, Math.min(camX, worldWpx - viewWpx));

        if (worldHpx <= viewHpx) camY = 0;
        else camY = Math.max(0, Math.min(camY, worldHpx - viewHpx));

        // Background
        g.setColor(Palette.GB0);
        g.fillRect(0, 0, panelW, panelH);

        // --- Camera transform for map + entities (clipped to viewport) ---
        java.awt.Shape oldClip = g.getClip();
        java.awt.geom.AffineTransform oldTx = g.getTransform();

        // Only draw the world inside the map viewport (keep UI untouched)
        g.setClip(0, 0, viewWpx, viewHpx);
        g.translate(-camX, -camY);

        // Map (draw only tiles that could appear in the viewport)
        int minTx = Math.max(0, camX / tile);
        int minTy = Math.max(0, camY / tile);
        int maxTx = Math.min(d.w() - 1, (camX + viewWpx) / tile + 1);
        int maxTy = Math.min(d.h() - 1, (camY + viewHpx) / tile + 1);
        g.setColor(Color.RED);
        g.drawString("camX=" + camX + " camY=" + camY + " world=" + worldWpx + "x" + worldHpx + " view=" + viewWpx + "x" + viewHpx, 10, 14);

        for (int y = minTy; y <= maxTy; y++) {
            for (int x = minTx; x <= maxTx; x++) {
                boolean vis = d.isVisibleNow(x, y);
                boolean seen = d.wasSeenEver(x, y);
                if (!seen) continue;

                Tile t = d.tile(x, y);
                drawTile(g, x, y, t, vis);
            }
        }

        // Enemies (only if visible)
        List<Enemy> enemies = game.enemies();
        for (Enemy e : enemies) {
            if (d.isVisibleNow(e.x, e.y)) {
                drawEnemy(g, e.x, e.y);
            }
        }

        // Player
        drawPlayer(g, game.player().x, game.player().y);

        // Restore for UI drawing
        g.setTransform(oldTx);
        g.setClip(oldClip);

        // UI bar
        g.setColor(Palette.GB1);
        g.fillRect(0, uiY, panelW, uiTiles * tile);

        g.setColor(Palette.GB3);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));

        String line1 = "HP " + game.player().hp + "/" + game.player().maxHp
                + "   Floor " + game.floor()
                + "   Seed " + (game.seed() & 0xFFFF);

        g.drawString(line1, 10, uiY + 18);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        g.drawString(game.lastLog(), 10, uiY + 36);

        if (game.state() == Game.State.DEAD) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
            g.drawString("DEAD - Press R to restart", 10, uiY + 60);
        }
    }

    private void drawTile(Graphics2D g, int tx, int ty, Tile t, boolean lit) {
        int s = GameConfig.TILE_SIZE;
        int x = tx * s;
        int y = ty * s;

        Color base, accent;
        if (lit) {
            base = Palette.GB2;
            accent = Palette.GB3;
        } else {
            base = Palette.GB1;
            accent = Palette.GB2;
        }

        if (t == Tile.WALL) {
            g.setColor(base);
            g.fillRect(x, y, s, s);
            g.setColor(accent);
            g.fillRect(x + 1, y + 2, s - 2, 1);
            g.fillRect(x + 1, y + 5, s - 2, 1);
        } else if (t == Tile.FLOOR) {
            g.setColor(Palette.GB0);
            g.fillRect(x, y, s, s);
            g.setColor(base);
            g.fillRect(x + 2, y + 2, 1, 1);
            g.fillRect(x + 5, y + 4, 1, 1);
        } else if (t == Tile.STAIRS_DOWN) {
            g.setColor(Palette.GB0);
            g.fillRect(x, y, s, s);
            g.setColor(accent);
            g.fillRect(x + 2, y + 2, 1, 1);
            g.fillRect(x + 3, y + 3, 1, 1);
            g.fillRect(x + 4, y + 4, 1, 1);
            g.fillRect(x + 3, y + 5, 1, 1);
            g.fillRect(x + 2, y + 6, 1, 1);
        }
    }

    private void drawPlayer(Graphics2D g, int tx, int ty) {
        int s = GameConfig.TILE_SIZE;
        int x = tx * s;
        int y = ty * s;

        g.setColor(Palette.GB3);
        g.fillRect(x + 2, y + 2, 4, 4);
        g.setColor(Palette.GB1);
        g.fillRect(x + 3, y + 3, 2, 2);
    }

    private void drawEnemy(Graphics2D g, int tx, int ty) {
        int s = GameConfig.TILE_SIZE;
        int x = tx * s;
        int y = ty * s;

        g.setColor(Palette.GB2);
        g.fillRect(x + 2, y + 2, 4, 4);
        g.setColor(Palette.GB0);
        g.fillRect(x + 3, y + 3, 1, 1);
        g.fillRect(x + 4, y + 3, 1, 1);
    }
}