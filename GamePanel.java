package org.example.ui;



import org.example.game.Game;
import org.example.game.GameConfig;
import org.example.game.Input;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public final class GamePanel extends JPanel {
    private final Game game = new Game();
    private final Input input = new Input();
    private final Renderer renderer = new Renderer();

    private Timer timer;

    public GamePanel() {
        int w = GameConfig.MAP_W * GameConfig.TILE_SIZE * GameConfig.SCALE;
        int h = (GameConfig.MAP_H + GameConfig.UI_H_TILES) * GameConfig.TILE_SIZE * GameConfig.SCALE;

        setPreferredSize(new Dimension(w, h));
        setFocusable(true);
        setDoubleBuffered(true);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { input.onKeyPressed(e); }
            @Override public void keyReleased(KeyEvent e) { input.onKeyReleased(e); }
        });

        // start a run
        long seed = System.currentTimeMillis();
        game.newRun(seed);
    }

    public void start() {
        requestFocusInWindow();

        // 60fps “render/update”; turn-based logic only advances on taps.
        timer = new Timer(1000 / 60, e -> tick());
        timer.start();
    }

    private void tick() {
        // Restart (works even if dead)
        if (input.wasTapped(KeyEvent.VK_R)) {
            long seed = System.currentTimeMillis();
            game.newRun(seed);
        }

        // Update game
        game.update(input);

        // Consume taps
        input.endFrame();

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);

        // Pixel-perfect scaling: draw to a small buffer then scale up
        int logicalW = GameConfig.MAP_W * GameConfig.TILE_SIZE;
        int logicalH = (GameConfig.MAP_H + GameConfig.UI_H_TILES) * GameConfig.TILE_SIZE;

        Image img = createImage(logicalW, logicalH);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        renderer.draw(g, game, logicalW, logicalH);
        g.dispose();

        Graphics2D g2 = (Graphics2D) g0;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(img, 0, 0, logicalW * GameConfig.SCALE, logicalH * GameConfig.SCALE, null);
    }
}