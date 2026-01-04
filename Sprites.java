package org.example.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

public final class Sprites {
    private Sprites() {}

    public static BufferedImage load(String path) {
        try {
            var in = Sprites.class.getResourceAsStream(path);
            if (in == null) throw new IllegalArgumentException("Missing resource: " + path);
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load sprite: " + path, e);
        }
    }
}