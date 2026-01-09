package org.example.ui;

import org.example.entity.Enemy;
import org.example.game.Game;
import org.example.game.GameConfig;
import org.example.world.Dungeon;
import org.example.world.Tile;
import org.example.entity.Chest;
import org.example.world.WorldMap;

import java.util.EnumMap;
import java.util.Map;
import java.io.InputStream;
import java.awt.*;
import java.util.List;

public final class Renderer {

    private final java.awt.image.BufferedImage playerBattle = Sprites.load("/sprites/playerknight.png");
    private final Map<Enemy.Type, java.awt.image.BufferedImage> enemyBattleSprites =
            new EnumMap<>(Enemy.Type.class);
    // ----------------------------
    // Battle layout tuning knobs
    // ----------------------------

    // Sprite BOX sizes (player bigger than enemy)
    private static final double ENEMY_SPRITE_BOX_BOOST  = 2.15; // slightly bigger
    private static final double PLAYER_SPRITE_BOX_BOOST = 2.45; // bigger than enemy (closer)

    // Sprite scale inside the box (optional, but helps the "closer" feel)
    private static final double ENEMY_SPRITE_SCALE_BOOST  = 2.45;
    private static final double PLAYER_SPRITE_SCALE_BOOST = 2.65;

    // Make info boxes less wide (more compact)
    private static final int ENEMY_INFO_W_MAX_TILES  = 22; // was ~30 in your version
    private static final int PLAYER_INFO_W_MAX_TILES = 22; // was ~32 in your version

    // Force sprite boxes to be at least this big (in tiles)
    private static final int ENEMY_SPRITE_BOX_MIN_TILES  = 16;
    private static final int PLAYER_SPRITE_BOX_MIN_TILES = 20;

    // Hard upper bounds (in tiles) to prevent ridiculous sizes
    private static final int ENEMY_SPRITE_BOX_MAX_TILES  = 24;
    private static final int PLAYER_SPRITE_BOX_MAX_TILES = 28;

    // Negative = move UP, positive = move DOWN (pixels)
    private static final int ENEMY_SPRITE_Y_OFFSET_PX = -16; // try -8, -12, -16

    // Keep them from getting too tiny on small windows
    private static final int INFO_W_MIN_TILES = 16;

    // ----------------------------
// Pixel font (loaded from resources)
// ----------------------------
    private static final Font PIXEL_BASE = loadFont("/fonts/PressStart2P-Regular.ttf");

    // Use these wherever you currently do new Font(Font.MONOSPACED, ...)
    private static Font pixel(float size) {
        int sz = Math.round(size);
        if (PIXEL_BASE == null) return new Font(Font.MONOSPACED, Font.PLAIN, sz);
        return PIXEL_BASE.deriveFont(Font.PLAIN, (float) sz);
    }

    private static Font pixelBold(float size) {
        int sz = Math.round(size);
        if (PIXEL_BASE == null) return new Font(Font.MONOSPACED, Font.BOLD, sz);
        // PressStart2P has no bold; keep plain but same size
        return PIXEL_BASE.deriveFont(Font.PLAIN, (float) sz);
    }

    private static Font loadFont(String path) {
        try (InputStream in = Renderer.class.getResourceAsStream(path)) {
            if (in == null) return null;
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            // Important: register so Swing can measure it reliably
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    public Renderer() {
        enemyBattleSprites.put(Enemy.Type.GOBLIN,   Sprites.load("/sprites/goblinsprite2.png"));
        enemyBattleSprites.put(Enemy.Type.SKELETON, Sprites.load("/sprites/skeleton.PNG"));
        enemyBattleSprites.put(Enemy.Type.CULTIST,  Sprites.load("/sprites/cultist.PNG"));
        enemyBattleSprites.put(Enemy.Type.SLIME,    Sprites.load("/sprites/slime.PNG"));
        enemyBattleSprites.put(Enemy.Type.ZOMBIE,   Sprites.load("/sprites/zombie.PNG"));
    }

    public void draw(Graphics2D g, Game game, int panelW, int panelH) {

        Object[] oldHints = pushTextHints(g);
        applyPixelTextHints(g);

        try {
            if (game.state() == Game.State.MAIN_MENU) {
                drawMainMenu(g, panelW, panelH);

                if (game.isDeathWipeActive()) drawDeathWipeOverlay(g, panelW, panelH, game.deathWipeProgress());
                else drawFadeOverlay(g, panelW, panelH, game.fadeAlpha());

                return;
            }

            if (game.state() == Game.State.BATTLE) {
                drawBattle(g, game, panelW, panelH);

                if (game.isDeathWipeActive()) drawDeathWipeOverlay(g, panelW, panelH, game.deathWipeProgress());
                else drawFadeOverlay(g, panelW, panelH, game.fadeAlpha());

                return;
            }

            if (game.state() == Game.State.INVENTORY) {
                drawInventory(g, game, panelW, panelH);

                if (game.isDeathWipeActive()) drawDeathWipeOverlay(g, panelW, panelH, game.deathWipeProgress());
                else drawFadeOverlay(g, panelW, panelH, game.fadeAlpha());

                return;
            }

            if (game.state() == Game.State.GAME_OVER) {
                drawGameOver(g, game, panelW, panelH);

                if (game.isDeathWipeActive()) drawDeathWipeOverlay(g, panelW, panelH, game.deathWipeProgress());
                else drawFadeOverlay(g, panelW, panelH, game.fadeAlpha());

                return;
            }

            WorldMap map = game.activeMap();
            if (map == null) return;

        int tile = GameConfig.TILE_SIZE;
        int uiTiles = GameConfig.UI_H_TILES;

        int viewWpx = panelW;
        int viewHpx = panelH - (uiTiles * tile);
        int uiY = viewHpx;

        int worldWpx = map.w() * tile;
        int worldHpx = map.h() * tile;

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
        Shape oldClip = g.getClip();
        java.awt.geom.AffineTransform oldTx = g.getTransform();

        g.setClip(0, 0, viewWpx, viewHpx);
        g.translate(-camX, -camY);

        int minTx = Math.max(0, camX / tile);
        int minTy = Math.max(0, camY / tile);
        int maxTx = Math.min(map.w() - 1, (camX + viewWpx) / tile + 1);
        int maxTy = Math.min(map.h() - 1, (camY + viewHpx) / tile + 1);

        for (int y = minTy; y <= maxTy; y++) {
            for (int x = minTx; x <= maxTx; x++) {
                boolean vis = map.isVisibleNow(x, y);
                boolean seen = map.wasSeenEver(x, y);
                if (!seen) continue;

                Tile t = map.tile(x, y);
                drawTile(g, x, y, t, vis);
            }
        }


            if (game.zone() == Game.Zone.DUNGEON) {   // you may need to add a zone() getter
                for (Enemy e : game.enemies()) {
                    if (map.isVisibleNow(e.x, e.y)) drawEnemy(g, e.x, e.y);
                }

                for (Chest c : game.chests()) {
                    if (!c.opened && map.isVisibleNow(c.x, c.y)) drawChest(g, c.x, c.y);
                }

                for (var gi : game.groundItems()) {
                    if (map.isVisibleNow(gi.x, gi.y)) drawGroundItem(g, gi.x, gi.y, gi.type);
                }
            }

        drawPlayer(g, game.player().x, game.player().y);

        g.setTransform(oldTx);
        g.setClip(oldClip);

        // UI bar
        g.setColor(Palette.GB1);
        g.fillRect(0, uiY, panelW, uiTiles * tile);

        String line1 = "LV " + game.player().level +
                "  HP " + game.player().hp + "/" + game.player().maxHp +
                "  MP " + game.player().mp + "/" + game.player().maxMp +
                "  EXP " + game.player().exp + "/" + game.player().expToNext +
                "  Keys " + game.player().keyCount() +
                "  Floor " + game.floor();

            int leftPad = 10;
            int maxTextW = panelW - leftPad * 2;

            g.setColor(Palette.GB3);

            g.setColor(Palette.GB3);

// ---- Top padding inside UI bar (gap from top of bar to line 1) ----
            int topPad = 6;     // try 6..10
            int rowGap = 3;     // gap between line 1 and line 2

// --- Line 1 ---
            g.setFont(pixelBold(8f));
            FontMetrics fm1 = g.getFontMetrics();
            int y1 = uiY + topPad + fm1.getAscent();

            String line1Fit = ellipsize(g, line1, maxTextW);
            g.drawString(line1Fit, leftPad, y1);

// --- Line 2 ---
            g.setFont(pixel(8f));
            FontMetrics fm2 = g.getFontMetrics();
            int y2 = y1 + fm2.getAscent() + rowGap;

// clamp so it never draws outside the UI bar
            int uiHpx = uiTiles * tile;
            int maxY = uiY + uiHpx - 2;
            y2 = Math.min(y2, maxY);

            String raw = game.lastLog();
            if (raw != null && !raw.isBlank()) {
                String logFit = ellipsize(g, raw, maxTextW);
                g.drawString(logFit, leftPad, y2);
            }
            // LEVEL UP overlay (modal) drawn on top of dungeon UI
            if (game.state() == Game.State.LEVEL_UP) {
                drawLevelUpOverlay(g, game, panelW, panelH);
            }

            if (game.isDeathWipeActive()) {
                drawDeathWipeOverlay(g, panelW, panelH, game.deathWipeProgress());
            } else {
                drawFadeOverlay(g, panelW, panelH, game.fadeAlpha());
            }
    } finally {
        popTextHints(g, oldHints);
        game.onFramePresented();
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

        } else if (t == Tile.LOCKED_DOOR) {
            // Simple door: dark fill + bright border "planks"
            g.setColor(Palette.GB0);
            g.fillRect(x, y, s, s);

            g.setColor(accent);
            g.drawRect(x + 1, y + 1, s - 3, s - 3);

            // vertical slats
            g.fillRect(x + 3, y + 2, 1, s - 4);
            g.fillRect(x + 5, y + 2, 1, s - 4);

            // keyhole
            g.setColor(base);
            g.fillRect(x + 4, y + 4, 1, 2);

        }

        else if (t == Tile.DOOR) {
            // Simple doorway marker (bright frame + dark opening)
            g.setColor(Palette.GB0);
            g.fillRect(x, y, s, s);

            g.setColor(accent);
            g.drawRect(x + 1, y + 1, s - 3, s - 3);

            // opening
            g.setColor(Palette.GB1);
            g.fillRect(x + 3, y + 3, s - 6, s - 5);

            // lintel
            g.setColor(Palette.GB3);
            g.fillRect(x + 2, y + 2, s - 4, 1);
        }
        else if (t == Tile.CRYPT_DOOR) {
            // A slightly “spookier” door marker (thicker + symbol)
            g.setColor(Palette.GB0);
            g.fillRect(x, y, s, s);

            g.setColor(Palette.GB3);
            g.drawRect(x + 1, y + 1, s - 3, s - 3);

            g.setColor(Palette.GB1);
            g.fillRect(x + 2, y + 3, s - 4, s - 5);

            // little “rune”
            g.setColor(Palette.GB3);
            g.fillRect(x + 4, y + 4, 1, 1);
            g.fillRect(x + 3, y + 5, 3, 1);
        }
        else if (t == Tile.TOWN_PORTAL) {
            // Portal: dark base + bright ring + glowing center
            g.setColor(Palette.GB0);
            g.fillRect(x, y, s, s);

            // ring
            g.setColor(accent);
            g.fillRect(x + 2, y + 1, s - 4, 1);
            g.fillRect(x + 2, y + s - 2, s - 4, 1);
            g.fillRect(x + 1, y + 2, 1, s - 4);
            g.fillRect(x + s - 2, y + 2, 1, s - 4);

            // inner glow
            g.setColor(base);
            g.fillRect(x + 3, y + 3, s - 6, s - 6);

            // core sparkle
            g.setColor(accent);
            g.fillRect(x + s/2, y + s/2, 1, 1);
        }
         else if (t == Tile.GRASS) {
        // Grass: slightly brighter base when lit, with scattered blades
        g.setColor(lit ? Palette.GB1 : Palette.GB0);
        g.fillRect(x, y, s, s);

        g.setColor(lit ? Palette.GB2 : Palette.GB1);
        // simple "blade" specks (works well at 8x8 or 16x16 tiles)
        g.fillRect(x + 1, y + 2, 1, 1);
        g.fillRect(x + 4, y + 1, 1, 1);
        g.fillRect(x + 6, y + 3, 1, 1);
        g.fillRect(x + 2, y + 5, 1, 1);
        g.fillRect(x + 5, y + 6, 1, 1);

         } else if (t == Tile.PATH) {
            // Path: dirt/stone look, with a border + a couple pebbles
            g.setColor(lit ? Palette.GB1 : Palette.GB0);
            g.fillRect(x, y, s, s);

            // inner path fill
            g.setColor(lit ? Palette.GB2 : Palette.GB1);
            g.fillRect(x + 1, y + 1, s - 2, s - 2);

            // subtle edge + pebbles
            g.setColor(lit ? Palette.GB3 : Palette.GB2);
            g.fillRect(x + 1, y + 1, s - 2, 1);         // top edge
            g.fillRect(x + 1, y + s - 2, s - 2, 1);     // bottom edge
            g.fillRect(x + 2, y + 4, 1, 1);
            g.fillRect(x + 5, y + 5, 1, 1);

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

    private void drawChest(Graphics2D g, int tx, int ty) {
        int s = GameConfig.TILE_SIZE;
        int x = tx * s;
        int y = ty * s;

        g.setColor(Palette.GB3);
        g.fillRect(x + 2, y + 3, 4, 3);
        g.setColor(Palette.GB1);
        g.fillRect(x + 2, y + 2, 4, 1); // lid
        g.setColor(Palette.GB0);
        g.fillRect(x + 3, y + 4, 1, 1); // latch
    }

    private void drawGroundItem(Graphics2D g, int tx, int ty, org.example.item.ItemType type) {
        int s = GameConfig.TILE_SIZE;
        int x = tx * s;
        int y = ty * s;

        switch (type) {
            case HP_POTION -> {
                g.setColor(Palette.GB3);
                g.fillRect(x + 3, y + 3, 2, 3);
                g.setColor(Palette.GB1);
                g.fillRect(x + 3, y + 2, 2, 1);
            }
            case MP_POTION -> {
                g.setColor(Palette.GB2);
                g.fillRect(x + 3, y + 3, 2, 3);
                g.setColor(Palette.GB1);
                g.fillRect(x + 3, y + 2, 2, 1);
            }
            case KEY -> {
                // same icon you used for Tile.KEY
                g.setColor(Palette.GB3);
                // head
                g.fillRect(x + 2, y + 3, 2, 2);
                // shaft
                g.fillRect(x + 4, y + 4, 2, 1);
                // teeth
                g.fillRect(x + 5, y + 5, 1, 1);
                g.fillRect(x + 6, y + 5, 1, 1);
            }
            default -> {
                g.setColor(Palette.GB3);
                g.fillRect(x + 3, y + 3, 1, 1);
            }
        }
    }

    private void drawBattle(Graphics2D g, Game game, int panelW, int panelH) {
        int tile = GameConfig.TILE_SIZE;

        var b = game.battle();
        if (b == null) return;

        g.setColor(Palette.GB0);
        g.fillRect(0, 0, panelW, panelH);

        int pad = Math.max(2, tile * 2);

        int minArena = tile * 12;
        int cmdH = Math.max(tile * 10, panelH / 3);
        cmdH = Math.min(cmdH, panelH - minArena);
        cmdH = Math.max(tile * 8, cmdH);

        int cmdY = panelH - cmdH;

        int arenaH = panelH - cmdH;
        int topH = arenaH / 2;
        int botY = topH;
        int botH = arenaH - topH;

        // --- Top arena (enemy) ---
        g.setColor(Palette.GB1);
        g.fillRect(0, 0, panelW, topH);

        // Enemy sprite box (make it actually bigger, with a guaranteed minimum)
        int esMin = tile * ENEMY_SPRITE_BOX_MIN_TILES;
        int esMax = Math.min(tile * ENEMY_SPRITE_BOX_MAX_TILES, topH - pad * 2);

        int esBase = Math.min(tile * 12, esMax);  // base target (bigger than before)
        int es = (int) Math.round(esBase * ENEMY_SPRITE_BOX_BOOST);

        // clamp
        es = Math.max(esMin, Math.min(es, esMax));

        int ex = panelW - es - pad;
        int ey = pad;

        // Enemy info box (LESS WIDE)
        int infoW = Math.min(tile * ENEMY_INFO_W_MAX_TILES, panelW - (pad * 3) - es);
        infoW = Math.max(tile * INFO_W_MIN_TILES, infoW);

        int infoH = Math.min(tile * 7, topH - pad * 2);
        infoH = Math.max(tile * 6, infoH);

        int ix = pad;
        int iy = pad;

        g.setColor(Palette.GB0);
        g.fillRect(ix, iy, infoW, infoH);
        g.setColor(Palette.GB3);
        g.drawRect(ix, iy, infoW, infoH);

        int enemyAtkX = attackShakeX(b.enemyAtkFrames, 2);
        int enemyHitY = hitBobY(b.enemyHitFrames, 2);

        var enemyImg = enemyBattleSprites.getOrDefault(b.foe.type, enemyBattleSprites.get(Enemy.Type.GOBLIN));

        if (b.foe.hp <= 0) {
            // If dead: play the split/fade while timer is active, otherwise draw nothing.
            if (b.enemyDefeatFrames > 0) {
                drawEnemyDefeatSplit(
                        g,
                        enemyImg,
                        ex, ey + ENEMY_SPRITE_Y_OFFSET_PX,
                        es, es,
                        ENEMY_SPRITE_SCALE_BOOST,
                        b.enemyDefeatFrames,
                        org.example.game.Battle.ENEMY_DEFEAT_FRAMES_MAX
                );
            }
            // else: draw nothing (keeps it gone permanently)
        } else {
            // Alive: normal enemy draw
            drawSpriteCentered(
                    g,
                    enemyImg,
                    ex + enemyAtkX - 2,
                    ey + ENEMY_SPRITE_Y_OFFSET_PX + enemyHitY,
                    es, es,
                    ENEMY_SPRITE_SCALE_BOOST
            );
        }

        g.setFont(pixelBold(8f));
        int nameY = iy + 16;

        if (nameY < iy + infoH - 2) {
            g.drawString(b.foe.name, ix + 6, nameY);

            String foeStatus = "";
            if (b.foeFrozenTurns > 0) foeStatus = "FROZEN";
            else if (b.foeSlowTurns > 0) foeStatus = "SLOW";



            if (!foeStatus.isEmpty()) {
                FontMetrics fm = g.getFontMetrics(); // bold metrics now
                int sw = fm.stringWidth(foeStatus);
                int sx = ix + infoW - 6 - sw;
                g.drawString(foeStatus, sx, nameY);
            }
        }

        // --- ENEMY HP BAR (ADD THIS) ---
        g.setColor(Palette.GB3); // make sure text/bar uses visible color

        int eBarX = ix + 6;
        int eBarW = infoW - 12;

// place bar below name line, but clamp so it stays inside the box
        int eBarY = Math.min(iy + 28, iy + infoH - 32);
        eBarY = Math.max(iy + 22, eBarY);

        int eBarH = 6;

// bar background
        g.setColor(Palette.GB1);
        g.fillRect(eBarX, eBarY, eBarW, eBarH);

// bar fill
        double eFrac = (b.foe.maxHp <= 0) ? 0.0 : (b.foe.hp / (double) b.foe.maxHp);
        int eFill = (int) Math.round(eBarW * eFrac);
        eFill = Math.max(0, Math.min(eBarW, eFill));

        g.setColor(Palette.GB3);
        g.fillRect(eBarX, eBarY, eFill, eBarH);

// OPTIONAL: HP text under bar (only if there’s room)
        g.setFont(pixel(8f));
        int eHpTextY = Math.min(eBarY + 18, iy + infoH - 4);
        if (eHpTextY > eBarY + 6) {
            g.drawString("HP " + b.foe.hp + "/" + b.foe.maxHp, ix + 6, eHpTextY);
        }

// then switch to normal font for HP text
        g.setFont(pixel(8f));

        // --- Bottom arena (player) ---
        g.setColor(Palette.GB0);
        g.fillRect(0, botY, panelW, botH);

        // Player sprite box (bigger than enemy, guaranteed minimum)
        int psMin = tile * PLAYER_SPRITE_BOX_MIN_TILES;
        int psMax = Math.min(tile * PLAYER_SPRITE_BOX_MAX_TILES, botH - pad * 2);

        int psBase = Math.min(tile * 14, psMax);  // base target (bigger than enemy)
        int ps = (int) Math.round(psBase * PLAYER_SPRITE_BOX_BOOST);

// clamp
        ps = Math.max(psMin, Math.min(ps, psMax));

        int px = pad;
        int py = botY + pad;

        // Player info box (LESS WIDE)
        int pInfoW = Math.min(tile * PLAYER_INFO_W_MAX_TILES, panelW - (pad * 3) - ps);
        pInfoW = Math.max(tile * INFO_W_MIN_TILES, pInfoW);

        int pInfoH = Math.min(tile * 8, botH - pad * 2);
        pInfoH = Math.max(tile * 6, pInfoH);

        int pIx = panelW - pInfoW - pad;
        int pIy = botY + pad;

        int playerAtkX = attackShakeX(b.playerAtkFrames, 2); // side-to-side when attacking
        int playerHitY = hitBobY(b.playerHitFrames, 2);      // bob up/down when hit

        // Low HP effect (<= 5): slow flicker + gentle bob
        boolean lowHp = game.player().hp > 0 && game.player().hp <= 5;

        long now = System.currentTimeMillis();

// slow bob (about 2px up/down)
        int lowHpBob = 0;
        if (lowHp) {
            lowHpBob = (int) Math.round(Math.sin(now / 250.0) * 2.0);
        }

// slow flicker (toggle every ~200ms)
        boolean flickerOn = true;
        float flickerAlpha = 1f;
        if (lowHp) {
            flickerOn = ((now / 200) % 2) == 0;
            // Instead of totally disappearing, dim it on "off" frames
            flickerAlpha = flickerOn ? 1f : 0.35f;
        }

        Composite oldComp = g.getComposite();
        if (flickerAlpha < 1f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flickerAlpha));
        }

        drawSpriteCentered(
                g,
                playerBattle,
                px + playerAtkX + 2,
                py + playerHitY + lowHpBob,
                ps, ps,
                PLAYER_SPRITE_SCALE_BOOST
        );

        g.setComposite(oldComp);

        g.setColor(Palette.GB0);
        g.fillRect(pIx, pIy, pInfoW, pInfoH);
        g.setColor(Palette.GB3);
        g.drawRect(pIx, pIy, pInfoW, pInfoH);

        g.setFont(pixelBold(8f));
        int youY = pIy + 16;
        if (youY < pIy + pInfoH - 2) g.drawString("YOU", pIx + 6, youY);

        String youStatus = "";
        if (b.playerSlowTurns > 0) youStatus += "SLOW";
        if (b.fireSwordActive) {
            if (!youStatus.isEmpty()) youStatus += " ";
            youStatus += "FIRE";
        }

        if (!youStatus.isEmpty()) {
            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(youStatus);
            int sx = pIx + pInfoW - 6 - sw;
            g.drawString(youStatus, sx, youY);
        }

        int pBarX = pIx + 6;
        int pBarW = pInfoW - 12;

        int pBarY = Math.min(pIy + 28, pIy + pInfoH - 32);
        pBarY = Math.max(pIy + 22, pBarY);

        int barH = 6; // try 6 or 7 (PressStart2P tends to need compact UI)

        g.setColor(Palette.GB1);
        g.fillRect(pBarX, pBarY, pBarW, barH);

        int pFill = (int) Math.round(pBarW * (game.player().hp / (double) game.player().maxHp));
        pFill = Math.max(0, Math.min(pBarW, pFill));

        g.setColor(Palette.GB3);
        g.fillRect(pBarX, pBarY, pFill, barH);

        g.setFont(pixel(8f));
        int hpY = Math.min(pBarY + 18, pIy + pInfoH - 18);
        int mpY = Math.min(hpY + 14, pIy + pInfoH - 4);

        g.drawString("HP " + game.player().hp + "/" + game.player().maxHp, pIx + 6, hpY);

        if (mpY > hpY + 2) {
            g.drawString("MP " + game.player().mp + "/" + game.player().maxMp, pIx + 6, mpY);
        } else {
            g.drawString(" MP " + game.player().mp + "/" + game.player().maxMp, pIx + pInfoW / 2, hpY);
        }
        // Weapon line (try to fit below MP)
        int wepY = mpY + 14;
        wepY = Math.min(wepY, pIy + pInfoH - 4);

        String wep = game.player().getWeaponName() +
                " (+" + game.player().getWeaponBonusMin() + "/+" + game.player().getWeaponBonusMax() + ")";

// Only draw if there's room below MP
        if (wepY > mpY + 2 && wepY < pIy + pInfoH) {
            g.setFont(pixel(7f)); // slightly smaller so it fits nicely
            g.drawString(ellipsize(g, wep, pInfoW - 12), pIx + 6, wepY);
            g.setFont(pixel(8f)); // restore
        }

        // --- Command area outer box ---
        g.setColor(Palette.GB1);
        g.fillRect(0, cmdY, panelW, cmdH);
        g.setColor(Palette.GB3);
        g.drawRect(0, cmdY, panelW - 1, cmdH - 1);

        // --- Split into MESSAGE (top) + MENU (bottom) frames ---
        int innerPad = 6;

        int msgBoxX = innerPad;
        int msgBoxY = cmdY + innerPad;

        // message height: enough for 1 line in your pixel font
        int msgBoxH = 26; // tune 24..32 if you want taller

        int msgBoxW = panelW - innerPad * 2;

        int menuBoxX = innerPad;
        int menuBoxY = msgBoxY + msgBoxH + 6;
        int menuBoxW = msgBoxW;
        int menuBoxH = (cmdY + cmdH) - innerPad - menuBoxY;

        // Draw message frame
        g.setColor(Palette.GB0);
        g.fillRect(msgBoxX, msgBoxY, msgBoxW, msgBoxH);
        g.setColor(Palette.GB3);
        g.drawRect(msgBoxX, msgBoxY, msgBoxW, msgBoxH);

        g.setFont(pixel(8f));
        FontMetrics msgFm = g.getFontMetrics();
        int msgTextY = msgBoxY + 6 + msgFm.getAscent();

        if (b.log != null && !b.log.isBlank()) {
            String msgFit = ellipsize(g, b.log, msgBoxW - 12);
            g.drawString(msgFit, msgBoxX + 6, msgTextY);
        }

        // Draw menu frame (always drawn, but we only render options when appropriate)
        g.setColor(Palette.GB0);
        g.fillRect(menuBoxX, menuBoxY, menuBoxW, menuBoxH);
        g.setColor(Palette.GB3);
        g.drawRect(menuBoxX, menuBoxY, menuBoxW, menuBoxH);

        // ---- What we draw inside the MENU frame depends on phase ----
        if (b.phase == org.example.game.Battle.Phase.PLAYER_MENU) {
            String[] opts = {"FIGHT", "SPELL", "ITEM", "RUN"};
            int lineH = 14;

            int baseY = menuBoxY + 18;

            // clamp so the options fit inside menuBox
            int maxBase = menuBoxY + menuBoxH - (opts.length * lineH) - 6;
            baseY = Math.min(baseY, maxBase);
            baseY = Math.max(baseY, menuBoxY + 14);

            for (int i = 0; i < opts.length; i++) {
                String prefix = (i == b.menuIndex) ? "> " : "  ";
                g.drawString(prefix + opts[i], tile, baseY + i * lineH);
            }

        } else if (b.phase == org.example.game.Battle.Phase.WON) {
            g.drawString("Returning...", tile, menuBoxY + Math.min(18, menuBoxH - 6));

        } else if (b.phase == org.example.game.Battle.Phase.LOST) {
            g.drawString("You were defeated...", tile, menuBoxY + Math.min(18, menuBoxH - 6));

        } else if (b.phase == org.example.game.Battle.Phase.ENEMY_DELAY) {
            g.drawString("(enemy prepares...)", tile, menuBoxY + Math.min(18, menuBoxH - 6));

        } else if (b.phase == org.example.game.Battle.Phase.ENEMY_MESSAGE ||
                b.phase == org.example.game.Battle.Phase.ENEMY_ACT) {
            // Intentionally blank: message is in the top frame.
            // (You could draw "..." here if you want.)
        } else if (b.phase == org.example.game.Battle.Phase.ITEM_MENU) {

            java.util.List<org.example.item.ItemType> items = game.getUsableBattleItems();
            int itemCount = items.size();

            int lineH = 14;
            int baseY = Math.max(menuBoxY + 14, menuBoxY + 18);

            if (itemCount == 0) {
                g.drawString("No usable items.", tile, baseY);
                g.setFont(pixel(8f));
                g.drawString("ESC back", tile, menuBoxY + Math.min(menuBoxH - 6, baseY + 20));
                return;
            }

            int sel = b.itemIndex;
            if (sel < 0) sel = 0;
            if (sel >= itemCount) sel = itemCount - 1;

            for (int i = 0; i < itemCount; i++) {
                var it = items.get(i);
                String label = game.itemLabel(it);
                String prefix = (i == sel) ? "> " : "  ";
                g.drawString(prefix + label, tile, baseY + i * lineH);
            }

            g.setFont(pixel(8f));
            g.drawString("←/→ select   ENTER use   ESC back", tile,
                    menuBoxY + Math.min(menuBoxH - 6, baseY + itemCount * lineH + 10));

        } else if (b.phase == org.example.game.Battle.Phase.SPELL_MENU) {

            var p = game.player();
            java.util.List<org.example.entity.Player.SpellType> spells = p.knownSpellsInOrder();
            int n = spells.size();

            int lineH = 14;

            // Inside-menu padding + footer reservation
            int listX = menuBoxX + 6;              // left text margin inside box
            int listTopY = menuBoxY + 18;          // first row baseline
            int footerY = menuBoxY + menuBoxH - 6; // ALWAYS bottom of menu box

            int footerReserve = 18; // keep space so list never hits footer
            int availH = (menuBoxY + menuBoxH - footerReserve) - listTopY;
            int visibleRows = Math.max(1, availH / lineH);

            if (n == 0) {
                g.drawString("No spells known.", listX, listTopY);
                g.setFont(pixel(8f));
                g.drawString("ESC back", listX, footerY);
                return;
            }

            // Clamp selection
            int sel = b.spellIndex;
            sel = Math.max(0, Math.min(sel, n - 1));

            // Compute a scroll window so the selected row stays visible
            int maxScroll = Math.max(0, n - visibleRows);
            int scroll = sel - (visibleRows / 2);
            scroll = Math.max(0, Math.min(scroll, maxScroll));

            // Clip to menu box interior so text can NEVER draw outside
            Shape oldClip = g.getClip();
            g.setClip(menuBoxX + 2, menuBoxY + 2, menuBoxW - 4, menuBoxH - 4);

            // Tiny scroll indicators (GB-style)
            int indX = menuBoxX + menuBoxW - 14;
            int indTopY = listTopY - 6;
            int indBotY = listTopY + (visibleRows - 1) * lineH + 6;

            boolean hasAbove = scroll > 0;
            boolean hasBelow = (scroll + visibleRows) < n;

            g.setFont(pixelBold(8f));
            g.setColor(Palette.GB3);
            if (hasAbove) g.drawString("^", indX, indTopY);
            if (hasBelow) g.drawString("v", indX, indBotY);

            // Draw only visible rows
            g.setFont(pixel(8f));
            for (int row = 0; row < visibleRows; row++) {
                int i = scroll + row;
                if (i >= n) break;

                var sp = spells.get(i);

                // ✅ Use the same label logic as Inventory (includes dmg range)
                String label = game.spellLabel(sp);

                String prefix = (i == sel) ? "> " : "  ";

                // Keep text inside the menu box (leave room for scroll indicator on the right)
                int maxW = menuBoxW - 28; // tweak 24..32 if you want more/less margin
                g.drawString(ellipsize(g, prefix + label, maxW), listX, listTopY + row * lineH);
            }

            // Restore clip
            g.setClip(oldClip);

            // Footer: anchored to bottom of menu box (never overlaps list)
            g.setFont(pixel(8f));
            g.drawString("←/→ select   ENTER cast   ESC back", listX, footerY);
        }

    }

    private void drawMainMenu(Graphics2D g, int panelW, int panelH) {
        g.setColor(Palette.GB0);
        g.fillRect(0, 0, panelW, panelH);

        g.setColor(Palette.GB3);

        Font title = pixelBold(16f); // PressStart2P runs large; 18-20 is usually plenty
        Font body  = pixel(8f);

        g.setFont(title);
        String t1 = "DUNGEON ROGUELIKE";
        int tw = g.getFontMetrics().stringWidth(t1);
        g.drawString(t1, (panelW - tw) / 2, panelH / 2 - 20);

        g.setFont(body);
        String t2 = "Press ENTER to Start";
        int bw = g.getFontMetrics().stringWidth(t2);
        g.drawString(t2, (panelW - bw) / 2, panelH / 2 + 10);

        String t3 = "R = Restart any time";
        int bw2 = g.getFontMetrics().stringWidth(t3);
        g.drawString(t3, (panelW - bw2) / 2, panelH / 2 + 30);
    }

    private void drawInventory(Graphics2D g, Game game, int panelW, int panelH) {

        g.setColor(Palette.GB0);
        g.fillRect(0, 0, panelW, panelH);

        var p = game.player();
        int page = game.invPage(); // 0=ITEMS, 1=SPELLS, 2=STATS

        // =========================
        // HEADER + TABS (EVEN)
        // =========================
        g.setColor(Palette.GB3);
        g.setFont(pixelBold(8f));

        int headerX = 20;
        int headerY = 32;

        String left = "INVENTORY";
        String[] tabs = new String[] {
                (page == 0) ? "[ITEMS]"  : "ITEMS",
                (page == 1) ? "[SPELLS]" : "SPELLS",
                (page == 2) ? "[STATS]"  : "STATS"
        };

        FontMetrics hfm = g.getFontMetrics();

        g.drawString(left, headerX, headerY);

        int xTabs = headerX + hfm.stringWidth(left) + hfm.stringWidth("  ");
        int gapW = hfm.stringWidth("  "); // two spaces

        int[] tabX = new int[3];
        int[] tabW = new int[3];

        int x = xTabs;
        for (int i = 0; i < 3; i++) {
            tabX[i] = x;
            tabW[i] = hfm.stringWidth(tabs[i]);
            g.drawString(tabs[i], x, headerY);
            x += tabW[i] + gapW;
        }

        int underlineH = 2;
        int underlineY = headerY + 3;
        g.fillRect(tabX[page], underlineY, tabW[page], underlineH);

        // =========================
        // COMMON LAYOUT CONSTANTS
        // =========================
        int leftX = 20;
        int topY = 50;

        int boxW = (panelW - 60) / 2;
        int leftBoxX = leftX;
        int rightBoxX = leftX + boxW + 20;

        // Footer reserved space so content never overlaps controls
        int footerH = 40;
        int footerPad = 10;

        // =========================
        // STATS PAGE: 2 COLUMNS ONLY
        // =========================
        if (page == 2) {
            int contentH = panelH - topY - footerH - footerPad;
            contentH = Math.max(160, contentH);

            // Two tall columns
            drawInvBox(g, leftBoxX, topY, boxW, contentH);
            drawInvBox(g, rightBoxX, topY, boxW, contentH);

            // Titles
            g.setFont(pixelBold(8f));
            g.setColor(Palette.GB3);
            g.drawString("SUMMARY", leftBoxX + 8, topY + 18);
            g.drawString("PLAYER STATS", rightBoxX + 8, topY + 18);

            g.setFont(pixel(8f));
            int lineH = 16;

            // LEFT COLUMN: valuable summary
            int lx = leftBoxX + 8;
            int ly = topY + 38;

            g.drawString("LV " + p.level + "  FLOOR " + game.floor(), lx, ly);
            ly += lineH;
            g.drawString("EXP " + p.exp + "/" + p.expToNext, lx, ly);
            ly += lineH;
            g.drawString("Keys: " + p.keyCount(), lx, ly);
            ly += lineH;

            ly += lineH / 2;

            // Extra useful combat info

            String baseAtk = p.getBaseAtkMin() + "-" + p.getBaseAtkMax();
            g.drawString("Base ATK: " + baseAtk, lx, ly);
            ly += lineH;

            String totalAtk = p.atkMin + "-" + p.atkMax;
            g.drawString("Total ATK: " + totalAtk, lx, ly);
            ly += lineH;

            // RIGHT COLUMN: full current stats
            int rx = rightBoxX + 8;
            int ry = topY + 38;

            g.drawString("HP  " + p.hp + "/" + p.maxHp, rx, ry);
            ry += lineH;
            g.drawString("MP  " + p.mp + "/" + p.maxMp, rx, ry);
            ry += lineH;
            g.drawString("ATK " + p.atkMin + "-" + p.atkMax, rx, ry);
            ry += lineH;

            ry += lineH / 2;

            g.drawString("SPD " + p.speed(), rx, ry);
            ry += lineH;
            g.drawString("WIL " + p.will(), rx, ry);
            ry += lineH;
            g.drawString("INT " + p.intelligence(), rx, ry);
            ry += lineH;

            // Footer (no list box on STATS)
            g.setFont(pixel(8f));
            int footerY = topY + contentH + footerPad + 16;
            footerY = Math.min(footerY, panelH - 12);
            g.drawString("←/→ tabs   ESC close", leftX + 8, footerY);

            return; // IMPORTANT: prevents the ITEMS/SPELLS list UI from drawing
        }

        // =========================
        // ITEMS/SPELLS PAGES (EXISTING STRUCTURE)
        // =========================
        int topBoxH = 90;
        int listBoxY = topY + topBoxH + 16;

        int listBoxH = panelH - listBoxY - footerH - footerPad;
        listBoxH = Math.max(120, listBoxH);

        // Draw top boxes
        drawInvBox(g, leftBoxX, topY, boxW, topBoxH);
        drawInvBox(g, rightBoxX, topY, boxW, topBoxH);

        // TOP TITLES
        g.setFont(pixelBold(8f));
        g.setColor(Palette.GB3);

        if (page == 0) g.drawString("EQUIPPED", leftBoxX + 8, topY + 18);
        else          g.drawString("KNOWN SPELLS", leftBoxX + 8, topY + 18);

        g.drawString("STATS", rightBoxX + 8, topY + 18);

        // Preview stats based on selection
        int previewAtkMin = p.atkMin;
        int previewAtkMax = p.atkMax;
        int previewHp = p.hp;
        int previewMp = p.mp;

        if (page == 0) {
            // Preview if selected item is a sword
            java.util.List<org.example.item.ItemType> items = p.inv.nonEmptyTypes();
            int n = items.size();
            if (n > 0) {
                int sel = game.invIndex();
                sel = Math.max(0, Math.min(sel, n - 1));

                var it = items.get(sel);

                int bMin = 0, bMax = 0;
                boolean isSword = true;

                switch (it) {
                    case SWORD_WORN   -> { bMin = 0; bMax = 1; }
                    case SWORD_BRONZE -> { bMin = 1; bMax = 1; }
                    case SWORD_IRON   -> { bMin = 1; bMax = 2; }
                    case SWORD_STEEL  -> { bMin = 2; bMax = 2; }
                    case SWORD_KNIGHT -> { bMin = 2; bMax = 3; }
                    default -> isSword = false;
                }

                if (isSword) {
                    previewAtkMin = p.getBaseAtkMin() + bMin;
                    previewAtkMax = p.getBaseAtkMax() + bMax;
                }
            }
        } else if (page == 1) {
            // Spell preview: show post-cast MP and post-heal HP for HEAL
            java.util.List<org.example.entity.Player.SpellType> spells = p.knownSpellsInOrder();
            int n = spells.size();
            if (n > 0) {
                int sel = game.spellIndex();
                sel = Math.max(0, Math.min(sel, n - 1));
                var sp = spells.get(sel);

                int cost = game.spellCost(sp);
                previewMp = Math.max(0, p.mp - cost);

                if (sp == org.example.entity.Player.SpellType.HEAL) {
                    previewHp = Math.min(p.maxHp, p.hp + 15);
                }

                if (sp == org.example.entity.Player.SpellType.FIRE_SWORD) {
                    previewAtkMin = p.atkMin + 6;
                    previewAtkMax = p.atkMax + 9;
                }
            }
        }

        // LEFT TOP CONTENT
        g.setFont(pixel(8f));
        g.setColor(Palette.GB3);

        if (page == 0) {
            String wep = "Weapon: " + p.getWeaponName();
            g.drawString(ellipsize(g, wep, boxW - 16), leftBoxX + 8, topY + 38);
            g.drawString("LV " + p.level + "  EXP " + p.exp + "/" + p.expToNext, leftBoxX + 8, topY + 56);
        } else {
            java.util.List<org.example.entity.Player.SpellType> spells = p.knownSpellsInOrder();
            int known = spells.size();
            g.drawString("Spells known: " + known, leftBoxX + 8, topY + 38);

            String usageLine = "Cast in battle only";
            if (known > 0) {
                int sel = game.spellIndex();
                sel = Math.max(0, Math.min(sel, known - 1));
                var selected = spells.get(sel);
                usageLine = game.spellUsageText(selected);
            }
            g.drawString(ellipsize(g, usageLine, boxW - 16), leftBoxX + 8, topY + 56);
        }

        // RIGHT TOP CONTENT (preview changes)
        String hpCur  = p.hp + "/" + p.maxHp;
        String hpPrev = previewHp + "/" + p.maxHp;

        String mpCur  = p.mp + "/" + p.maxMp;
        String mpPrev = previewMp + "/" + p.maxMp;

        String atkCur  = p.atkMin + "-" + p.atkMax;
        String atkPrev = previewAtkMin + "-" + previewAtkMax;

        boolean hpChanged  = (previewHp != p.hp);
        boolean mpChanged  = (previewMp != p.mp);
        boolean atkChanged = (previewAtkMin != p.atkMin || previewAtkMax != p.atkMax);

        int statMaxW = boxW - 16;
        int sx = rightBoxX + 8;
        int sy = topY + 38;
        int statLineH = 18;

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();

        if (hpChanged)  lines.add("HP  " + hpCur + " > " + hpPrev);
        if (mpChanged)  lines.add("MP  " + mpCur + " > " + mpPrev);
        if (atkChanged) lines.add("ATK " + atkCur + " > " + atkPrev);

        if (lines.isEmpty()) {
            lines.add("HP  " + hpCur);
            lines.add("MP  " + mpCur);
            lines.add("ATK " + atkCur);
        }

        for (String sLine : lines) {
            g.drawString(ellipsize(g, sLine, statMaxW), sx, sy);
            sy += statLineH;
        }

        // LIST AREA BOX (ITEMS / SPELLS)
        drawInvBox(g, leftX, listBoxY, panelW - 40, listBoxH);

        g.setFont(pixelBold(8f));
        g.drawString(page == 0 ? "ITEMS" : "SPELLS", leftX + 8, listBoxY + 18);

        int listX = leftX + 8;
        int listY = listBoxY + 38;
        int lineH = 16;

        int visible = game.invVisibleRows();

        // Clip list rendering to inside the list box
        Shape oldClip = g.getClip();
        int clipX = leftX + 2;
        int clipY = listBoxY + 22;
        int clipW = (panelW - 40) - 4;
        int clipH = listBoxH - 26;
        g.setClip(clipX, clipY, clipW, clipH);

        g.setFont(pixel(8f));

        if (page == 0) {
            java.util.List<org.example.item.ItemType> items = p.inv.nonEmptyTypes();
            int n = items.size();

            if (n == 0) {
                g.drawString("(none)", listX, listY);
            } else {
                int sel = Math.max(0, Math.min(game.invIndex(), n - 1));
                int scroll = Math.max(0, Math.min(game.invScroll(), Math.max(0, n - visible)));

                // scroll indicators
                int listBoxW = panelW - 40;
                int indX = leftX + listBoxW - 14;
                int indTopY = listY - 6;
                int indBotY = listY + (visible - 1) * lineH + 6;

                g.setFont(pixelBold(8f));
                g.setColor(Palette.GB3);

                boolean hasAbove = scroll > 0;
                boolean hasBelow = (scroll + visible) < n;

                if (hasAbove) g.drawString("^", indX, indTopY);
                if (hasBelow) g.drawString("v", indX, indBotY);

                g.setFont(pixel(8f));
                for (int row = 0; row < visible; row++) {
                    int i = scroll + row;
                    if (i >= n) break;

                    var it = items.get(i);
                    String label = game.itemLabel(it);
                    String prefix = (i == sel) ? "> " : "  ";
                    g.drawString(prefix + ellipsize(g, label, panelW - 80), listX, listY + row * lineH);
                }
            }

        } else { // page == 1
            java.util.List<org.example.entity.Player.SpellType> spells = p.knownSpellsInOrder();
            int n = spells.size();

            if (n == 0) {
                g.drawString("(no spells known)", listX, listY);
            } else {
                int sel = Math.max(0, Math.min(game.spellIndex(), n - 1));
                int scroll = Math.max(0, Math.min(game.spellScroll(), Math.max(0, n - visible)));

                // scroll indicators
                int listBoxW = panelW - 40;
                int indX = leftX + listBoxW - 14;
                int indTopY = listY - 6;
                int indBotY = listY + (visible - 1) * lineH + 6;

                g.setFont(pixelBold(8f));
                g.setColor(Palette.GB3);

                boolean hasAbove = scroll > 0;
                boolean hasBelow = (scroll + visible) < n;

                if (hasAbove) g.drawString("^", indX, indTopY);
                if (hasBelow) g.drawString("v", indX, indBotY);

                g.setFont(pixel(8f));
                for (int row = 0; row < visible; row++) {
                    int i = scroll + row;
                    if (i >= n) break;

                    var sp = spells.get(i);
                    String label = game.spellLabel(sp);
                    String prefix = (i == sel) ? "> " : "  ";
                    g.drawString(prefix + ellipsize(g, label, panelW - 80), listX, listY + row * lineH);
                }
            }
        }

        // restore clip for footer
        g.setClip(oldClip);

        // Footer
        g.setFont(pixel(8f));
        int footerY = listBoxY + listBoxH + footerPad + 16;
        footerY = Math.min(footerY, panelH - 12);

        g.drawString("←/→ tabs   ↑/↓ select   ENTER use/equip   ESC close", leftX + 8, footerY);
    }

    private void drawLevelUpOverlay(Graphics2D g, Game game, int panelW, int panelH) {

        var p = game.player();
        int tile = GameConfig.TILE_SIZE;

        // Dim background
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.70f));
        g.setColor(Palette.GB0);
        g.fillRect(0, 0, panelW, panelH);
        g.setComposite(old);

        // Modal box
        int boxW = Math.min(panelW - 40, tile * 60);
        int boxH = Math.min(panelH - 40, tile * 34);
        int x = (panelW - boxW) / 2;
        int y = (panelH - boxH) / 2;

        g.setColor(Palette.GB0);
        g.fillRect(x, y, boxW, boxH);
        g.setColor(Palette.GB3);
        g.drawRect(x, y, boxW, boxH);

        g.setFont(pixelBold(8f));
        int pad = 10;
        int cx = x + pad;
        int cy = y + 18;

        g.drawString("LEVEL UP", cx, cy);

        g.setFont(pixel(8f));
        cy += 16;

        // Basic info
        g.drawString("LV " + p.level + "   EXP " + p.exp + "/" + p.expToNext, cx, cy);
        cy += 14;

        // Compute preview based on current selection (only meaningful on stage 1)
        int stage = game.levelUpStage();
        int statSel = game.levelUpStatIndex(); // 0..5 now

        boolean previewHP    = (stage == 1 && statSel == 0);
        boolean previewMP    = (stage == 1 && statSel == 1);
        boolean previewATK   = (stage == 1 && statSel == 2);
        boolean previewSPD   = (stage == 1 && statSel == 3);
        boolean previewWILL  = (stage == 1 && statSel == 4);
        boolean previewINT   = (stage == 1 && statSel == 5);

        int maxHpPreview = p.maxHp + (previewHP ? 4 : 0);
        int maxMpPreview = p.maxMp + (previewMP ? 2 : 0);

        int baseMin = p.getBaseAtkMin() + (previewATK ? 1 : 0);
        int baseMax = p.getBaseAtkMax() + (previewATK ? 1 : 0);
        int atkMinPreview = baseMin + p.getWeaponBonusMin();
        int atkMaxPreview = baseMax + p.getWeaponBonusMax();

        int spdPreview  = p.speed() + (previewSPD ? 1 : 0);
        int willPreview = p.will() + (previewWILL ? 1 : 0);
        int intPreview  = p.intelligence() + (previewINT ? 1 : 0);

        // Show stats with live preview arrows
        String hpLine   = "HP MAX: " + p.maxHp + (previewHP ? " -> " + maxHpPreview : "");
        String mpLine   = "MP MAX: " + p.maxMp + (previewMP ? " -> " + maxMpPreview : "");
        String atkLine  = "ATK: " + p.atkMin + "-" + p.atkMax + (previewATK ? " -> " + atkMinPreview + "-" + atkMaxPreview : "");
        String spdLine  = "SPEED: " + p.speed() + (previewSPD ? " -> " + spdPreview : "");
        String willLine = "WILL:  " + p.will() + (previewWILL ? " -> " + willPreview : "");
        String intLine  = "INTEL: " + p.intelligence() + (previewINT ? " -> " + intPreview : "");

        g.drawString(hpLine, cx, cy);   cy += 14;
        g.drawString(mpLine, cx, cy);   cy += 14;
        g.drawString(atkLine, cx, cy);  cy += 14;
        g.drawString(spdLine, cx, cy);  cy += 14;
        g.drawString(willLine, cx, cy); cy += 14;
        g.drawString(intLine, cx, cy);  cy += 14;

        // Divider
        cy += 6;
        g.drawLine(x + 8, cy, x + boxW - 8, cy);
        cy += 14;

        // Stage 0: YES/NO confirm
        if (stage == 0) {
            g.setFont(pixel(8f));
            g.drawString("Level up now?", cx, cy);
            cy += 16;

            int yn = game.levelUpYesNoIndex();
            String yes = (yn == 0) ? "> YES" : "  YES";
            String no  = (yn == 1) ? "> NO"  : "  NO";

            g.drawString(yes, cx, cy); cy += 14;
            g.drawString(no,  cx, cy); cy += 18;

            g.drawString("↑/↓ choose   ENTER confirm", cx, y + boxH - 12);
            return;
        }

        // Stage 1: stat selection (6 choices)
        g.setFont(pixel(8f));
        g.drawString("Choose a stat:", cx, cy);
        cy += 16;

        String[] stats = {"HP", "MP", "ATK", "SPEED", "WILL", "INT"};
        for (int i = 0; i < stats.length; i++) {
            String prefix = (i == statSel) ? "> " : "  ";
            g.drawString(prefix + stats[i], cx, cy);
            cy += 14;
        }

        g.drawString("↑/↓ choose   ENTER apply   ESC back", cx, y + boxH - 12);
    }

    private void drawInvBox(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Palette.GB1);
        g.fillRect(x, y, w, h);
        g.setColor(Palette.GB3);
        g.drawRect(x, y, w, h);
    }

    private static String ellipsize(Graphics2D g, String s, int maxW) {
        if (s == null) return "";
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= maxW) return s;

        String ell = "...";
        int ellW = fm.stringWidth(ell);
        int n = s.length();

        while (n > 0 && fm.stringWidth(s.substring(0, n)) + ellW > maxW) n--;
        return (n <= 0) ? ell : s.substring(0, n) + ell;
    }

    private static void applyPixelTextHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

        // Optional but usually good for this style
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private static Object[] pushTextHints(Graphics2D g) {
        return new Object[] {
                g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING),
                g.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS),
                g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        };
    }

    private static void popTextHints(Graphics2D g, Object[] old) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, old[0]);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, old[1]);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old[2]);
    }

    private void drawGameOver(Graphics2D g, Game game, int panelW, int panelH) {
        g.setColor(Palette.GB0);
        g.fillRect(0, 0, panelW, panelH);

        g.setColor(Palette.GB3);

        Font title = pixelBold(24f);
        Font body  = pixel(16f);
        Font small = pixel(8f);

        g.setFont(title);
        String s1 = "GAME OVER";
        int w1 = g.getFontMetrics().stringWidth(s1);
        g.drawString(s1, (panelW - w1) / 2, panelH / 2 - 20);

        g.setFont(body);
        String s2 = "Play again?";
        int w2 = g.getFontMetrics().stringWidth(s2);
        g.drawString(s2, (panelW - w2) / 2, panelH / 2 + 10);

        g.setFont(small);
        String s3 = "(Y) Yes  /  (N) No  /  (R) Restart";
        int w3 = g.getFontMetrics().stringWidth(s3);
        g.drawString(s3, (panelW - w3) / 2, panelH / 2 + 30);

        g.setFont(small);
        String s4 = "Enter also restarts.";
        int w4 = g.getFontMetrics().stringWidth(s4);
        g.drawString(s4, (panelW - w4) / 2, panelH / 2 + 50);
    }

    private static int attackShakeX(int frames, int magnitudePx) {
        if (frames <= 0) return 0;
        // toggles every 2 frames: 0,0,1,1,0,0,1,1...
        int t = (frames / 2) % 2;
        return (t == 0) ? magnitudePx : -magnitudePx;
    }

    private static int hitBobY(int frames, int magnitudePx) {
        if (frames <= 0) return 0;
        // small bounce pattern
        int t = frames % 6;
        return switch (t) {
            case 5 -> 0;
            case 4 -> -magnitudePx;
            case 3 -> -magnitudePx * 2;
            case 2 -> -magnitudePx;
            default -> 0;
        };
    }

    private void drawSpriteCentered(Graphics2D g, java.awt.image.BufferedImage img,
                                    int boxX, int boxY, int boxW, int boxH,
                                    double boost) {
        if (img == null) return;

        int iw = img.getWidth();
        int ih = img.getHeight();

        double fit = Math.min(boxW / (double) iw, boxH / (double) ih);

        double baseScale;
        if (fit >= 1.0) {
            baseScale = Math.floor(fit);
            if (baseScale < 1.0) baseScale = 1.0;
        } else {
            baseScale = fit;
        }

        double scale = Math.min(fit, baseScale * boost);

        if (scale >= 1.0) {
            scale = Math.floor(scale * 2.0) / 2.0; // snap to 0.5 steps
            if (scale < 1.0) scale = 1.0;
            scale = Math.min(scale, fit);
        }

        int dw = Math.max(1, (int) Math.round(iw * scale));
        int dh = Math.max(1, (int) Math.round(ih * scale));

        int dx = boxX + (boxW - dw) / 2;
        int dy = boxY + (boxH - dh) / 2;

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g.drawImage(img, dx, dy, dw, dh, null);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
    }
    private void drawEnemyDefeatSplit(Graphics2D g, java.awt.image.BufferedImage img,
                                      int boxX, int boxY, int boxW, int boxH,
                                      double boost, int framesLeft, int framesMax) {
        if (img == null) return;

        // flicker: skip every other frame
        if ((framesLeft % 2) == 0) return;

        float t = 1f - (framesLeft / (float) framesMax); // 0 -> 1 over time
        float alpha = Math.max(0f, 1f - t);              // fade out

        // split horizontally (top half / bottom half)
        int iw = img.getWidth();
        int ih = img.getHeight();
        int halfH = ih / 2;

        java.awt.image.BufferedImage top = img.getSubimage(0, 0, iw, halfH);
        java.awt.image.BufferedImage bot = img.getSubimage(0, halfH, iw, ih - halfH);

        // compute the same scale you use in drawSpriteCentered
        double fit = Math.min(boxW / (double) iw, boxH / (double) ih);

        double baseScale;
        if (fit >= 1.0) {
            baseScale = Math.floor(fit);
            if (baseScale < 1.0) baseScale = 1.0;
        } else {
            baseScale = fit;
        }

        double scale = Math.min(fit, baseScale * boost);

        if (scale >= 1.0) {
            scale = Math.floor(scale * 2.0) / 2.0;
            if (scale < 1.0) scale = 1.0;
            scale = Math.min(scale, fit);
        }

        int dw = Math.max(1, (int) Math.round(iw * scale));
        int dh = Math.max(1, (int) Math.round(ih * scale));

        int dx = boxX + (boxW - dw) / 2;
        int dy = boxY + (boxH - dh) / 2;

        // fling apart: top goes up-left, bottom goes down-right
        int fling = (int) Math.round(t * 26); // pixels of travel
        int topDx = dx - fling;
        int topDy = dy - fling;

        int botDx = dx + fling;
        int botDy = dy + (dh / 2) + fling;

        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Composite oldComp = g.getComposite();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // draw top half
        g.drawImage(top, topDx, topDy, dw, dh / 2, null);
        // draw bottom half
        g.drawImage(bot, botDx, botDy, dw, dh - (dh / 2), null);

        g.setComposite(oldComp);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
    }
    private static void drawFadeOverlay(Graphics2D g, int w, int h, float alpha) {
        if (alpha <= 0f) return;

        // Clamp
        alpha = Math.max(0f, Math.min(1f, alpha));

        // True fade: draw the darkest green using alpha blending
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g.setColor(Palette.GB0); // darkest
        g.fillRect(0, 0, w, h);

        g.setComposite(old);
    }

    private static void drawDeathWipeOverlay(Graphics2D g, int w, int h, float t) {
        if (t <= 0f) return;
        t = Math.max(0f, Math.min(1f, t));

        // Draw a big pie-slice that grows counterclockwise from the top (12 o'clock).
        int size = (int) Math.ceil(Math.hypot(w, h)); // big enough to cover the screen
        int cx = w / 2;
        int cy = h / 2;

        int arcW = size * 2;
        int arcH = size * 2;
        int x = cx - size;
        int y = cy - size;

        int startAngle = 90;                 // 12 o'clock in Java2D
        int sweepAngle = (int) Math.round(t * 360.0); // grows CCW with positive angles

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver); // full dark wipe (not translucent)

        g.setColor(Palette.GB0);
        g.fillArc(x, y, arcW, arcH, startAngle, sweepAngle);

        g.setComposite(old);
    }
}