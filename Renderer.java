package org.example.ui;

import org.example.entity.Enemy;
import org.example.game.Game;
import org.example.game.GameConfig;
import org.example.world.Dungeon;
import org.example.world.Tile;
import org.example.entity.Chest;

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
                return;
            }
            if (game.state() == Game.State.BATTLE) {
                drawBattle(g, game, panelW, panelH);
                return;
            }
            if (game.state() == Game.State.INVENTORY) {
                drawInventory(g, game, panelW, panelH);
                return;
            }
            if (game.state() == Game.State.GAME_OVER) {
                drawGameOver(g, game, panelW, panelH);
                return;
            }

        Dungeon d = game.dungeon();
        if (d == null) return;

        int tile = GameConfig.TILE_SIZE;
        int uiTiles = GameConfig.UI_H_TILES;

        int viewWpx = panelW;
        int viewHpx = panelH - (uiTiles * tile);
        int uiY = viewHpx;

        int worldWpx = d.w() * tile;
        int worldHpx = d.h() * tile;

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
        int maxTx = Math.min(d.w() - 1, (camX + viewWpx) / tile + 1);
        int maxTy = Math.min(d.h() - 1, (camY + viewHpx) / tile + 1);

        for (int y = minTy; y <= maxTy; y++) {
            for (int x = minTx; x <= maxTx; x++) {
                boolean vis = d.isVisibleNow(x, y);
                boolean seen = d.wasSeenEver(x, y);
                if (!seen) continue;

                Tile t = d.tile(x, y);
                drawTile(g, x, y, t, vis);
            }
        }

        List<Enemy> enemies = game.enemies();
        for (Enemy e : enemies) {
            if (d.isVisibleNow(e.x, e.y)) {
                drawEnemy(g, e.x, e.y);
            }
        }

        for (Chest c : game.chests()) {
            if (!c.opened && d.isVisibleNow(c.x, c.y)) {
                drawChest(g, c.x, c.y);
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

            String logFit = ellipsize(g, game.lastLog(), maxTextW);
            g.drawString(logFit, leftPad, y2);


    } finally {
        popTextHints(g, oldHints);
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
        if (nameY < iy + infoH - 2) g.drawString(b.foe.name, ix + 6, nameY);

        int barX = ix + 6;
        int barW = infoW - 12;
        int barH = 6;

        int barY = Math.min(iy + 28, iy + infoH - 18);
        barY = Math.max(iy + 22, barY);

        g.setColor(Palette.GB1);
        g.fillRect(barX, barY, barW, barH);

        int eFill = (int) Math.round(barW * (b.foe.hp / (double) b.foe.maxHp));
        eFill = Math.max(0, Math.min(barW, eFill));
        g.setColor(Palette.GB3);
        g.fillRect(barX, barY, eFill, barH);

        g.setFont(pixel(8f));
        int eTextY = Math.min(barY + 18, iy + infoH - 4);
        g.drawString("HP " + b.foe.hp + "/" + b.foe.maxHp, ix + 6, eTextY);

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

        drawSpriteCentered(
                g,
                playerBattle,
                px + playerAtkX + 2, // nudge toward enemy (right) when attacking
                py + playerHitY,
                ps, ps,
                PLAYER_SPRITE_SCALE_BOOST
        );

        g.setColor(Palette.GB0);
        g.fillRect(pIx, pIy, pInfoW, pInfoH);
        g.setColor(Palette.GB3);
        g.drawRect(pIx, pIy, pInfoW, pInfoH);

        g.setFont(pixelBold(8f));
        int youY = pIy + 16;
        if (youY < pIy + pInfoH - 2) g.drawString("YOU", pIx + 6, youY);

        int pBarX = pIx + 6;
        int pBarW = pInfoW - 12;

        int pBarY = Math.min(pIy + 28, pIy + pInfoH - 32);
        pBarY = Math.max(pIy + 22, pBarY);

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

        // --- Command / log box ---
        g.setColor(Palette.GB1);
        g.fillRect(0, cmdY, panelW, cmdH);
        g.setColor(Palette.GB3);
        g.drawRect(0, cmdY, panelW - 1, cmdH - 1);

        g.setFont(pixel(8f)); // you can try 10f or 11f if PressStart2P feels too big
        int logY = cmdY + 16;
        if (b.log != null && !b.log.isBlank()) {
            g.drawString(b.log, tile, logY);
        }

        if (b.phase == org.example.game.Battle.Phase.PLAYER_MENU) {
            String[] opts = {"FIGHT", "SPELL", "ITEM", "RUN"};
            int lineH = 14;
            int baseY = cmdY + 34;

            int maxBase = cmdY + cmdH - (opts.length * lineH) - 4;
            baseY = Math.min(baseY, maxBase);
            baseY = Math.max(baseY, cmdY + 24);

            for (int i = 0; i < opts.length; i++) {
                String prefix = (i == b.menuIndex) ? "> " : "  ";
                g.drawString(prefix + opts[i], tile, baseY + i * lineH);
            }
        } else if (b.phase == org.example.game.Battle.Phase.WON) {
            g.drawString("Press Enter to return.", tile, cmdY + Math.min(56, cmdH - 6));
        }else if (b.phase == org.example.game.Battle.Phase.LOST) {
            g.drawString("You were defeated...", tile, cmdY + Math.min(48, cmdH - 6));
        } else if (b.phase == org.example.game.Battle.Phase.ENEMY_DELAY) {
            g.drawString("(enemy prepares...)", tile, cmdY + Math.min(48, cmdH - 6));
        } else if (b.phase == org.example.game.Battle.Phase.ITEM_MENU) {

                var p = game.player();

            java.util.List<org.example.item.ItemType> items = p.inv.nonEmptyTypes();

                int itemCount = items.size();

                int lineH = 14;
                int baseY = cmdY + 34;
                baseY = Math.max(baseY, cmdY + 24);

                if (itemCount == 0) {
                    g.drawString("No usable items.", tile, baseY);
                    g.setFont(pixel(8f));
                    g.drawString("ESC back", tile, cmdY + Math.min(cmdH - 6, baseY + 24));
                    return;
                }

                // Clamp selection in case inventory changed
                int sel = b.itemIndex;
                if (sel < 0) sel = 0;
                if (sel >= itemCount) sel = itemCount - 1;

                for (int i = 0; i < itemCount; i++) {
                    var it = items.get(i);
                    int c = p.inv.count(it);

                    String label = switch (it) {
                        case HP_POTION -> "HP Potion  x" + c + "  (+8 HP)";
                        case MP_POTION -> "MP Potion  x" + c + "  (+6 MP)";
                        default -> it.name() + "  x" + c;
                    };

                    String prefix = (i == sel) ? "> " : "  ";
                    g.drawString(prefix + label, tile, baseY + i * lineH);
                }

                g.setFont(pixel(8f));
                g.drawString("←/→ select   ENTER use   ESC back", tile,
                        cmdY + Math.min(cmdH - 6, baseY + itemCount * lineH + 10));
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

        g.setColor(Palette.GB3);
        g.setFont(pixelBold(8f));
        g.drawString("INVENTORY / EQUIPMENT", 20, 40);

        var p = game.player();

        g.setFont(pixel(8f));
        g.drawString("LV " + p.level + "   EXP " + p.exp + "/" + p.expToNext, 20, 70);
        g.drawString("HP " + p.hp + "/" + p.maxHp + "   MP " + p.mp + "/" + p.maxMp, 20, 90);
        g.drawString("ATK " + p.atkMin + "-" + p.atkMax, 20, 110);

        int y = 150;
        g.setFont(pixelBold(8f));
        g.drawString("Items:", 20, y);

        y += 24;

        java.util.List<org.example.item.ItemType> items = p.inv.nonEmptyTypes();
        int n = items.size();

        if (n == 0) {
            g.setFont(pixelBold(8f));
            g.drawString("  (none)", 20, y);
        } else {
            int sel = game.invIndex();
            if (sel < 0) sel = 0;
            if (sel >= n) sel = n - 1;

            g.setFont(pixelBold(8f));

            for (int i = 0; i < n; i++) {
                var it = items.get(i);
                int c = p.inv.count(it);

                String label = switch (it) {
                    case HP_POTION -> "HP Potion  x" + c + "   (+8 HP)";
                    case MP_POTION -> "MP Potion  x" + c + "   (+6 MP)";
                    default -> it.name() + "  x" + c;
                };

                String prefix = (i == sel) ? "> " : "  ";
                g.drawString(prefix + label, 20, y + i * 20);
            }
        }

        g.setFont(pixel(8f));
        g.drawString("ENTER = use (consumes a turn)   ESC/I = close", 20, panelH - 30);
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
        String s2 = "Play again?  (Y) Yes  /  (N) No";
        int w2 = g.getFontMetrics().stringWidth(s2);
        g.drawString(s2, (panelW - w2) / 2, panelH / 2 + 10);

        g.setFont(small);
        String s3 = "Enter also restarts.";
        int w3 = g.getFontMetrics().stringWidth(s3);
        g.drawString(s3, (panelW - w3) / 2, panelH / 2 + 30);
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
}