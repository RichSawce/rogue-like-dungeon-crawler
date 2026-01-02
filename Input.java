package org.example.game;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public final class Input {
    private final Set<Integer> pressed = new HashSet<>();
    private final Set<Integer> tapped = new HashSet<>();

    public void onKeyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (!pressed.contains(k)) tapped.add(k);
        pressed.add(k);
    }

    public void onKeyReleased(KeyEvent e) {
        pressed.remove(e.getKeyCode());
    }

    public boolean isDown(int keyCode) {
        return pressed.contains(keyCode);
    }

    public boolean wasTapped(int keyCode) {
        return tapped.contains(keyCode);
    }

    public void endFrame() {
        tapped.clear();
    }
}