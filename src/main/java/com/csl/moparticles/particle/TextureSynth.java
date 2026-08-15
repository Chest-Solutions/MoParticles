package com.csl.moparticles.particle;

import com.csl.moparticles.particle.ParticleDefinition.TextureRow;

import java.util.Random;

/**
 * Builds simple coloured "particles" in memory.  The resulting
 * {@link TextureRow} holds a 32x32 RGBA byte array that the resource-pack
 * generator writes out as a real PNG.
 */
public final class TextureSynth {

    public static TextureRow generate(String id, int w, int h) {
        int W = Math.max(8, Math.min(128, w));
        int H = Math.max(8, Math.min(128, h));
        byte[] data = new byte[W * H * 4];
        // deterministic from id
        Random r = new Random(id.hashCode());
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int idx = (y * W + x) * 4;
                double dx = (x - W / 2.0) / (W / 2.0);
                double dy = (y - H / 2.0) / (H / 2.0);
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 1.0) {
                    int alpha = (int) ((1.0 - d) * 255);
                    data[idx + 0] = (byte) r.nextInt(255);
                    data[idx + 1] = (byte) r.nextInt(255);
                    data[idx + 2] = (byte) r.nextInt(255);
                    data[idx + 3] = (byte) alpha;
                } else {
                    data[idx + 0] = 0;
                    data[idx + 1] = 0;
                    data[idx + 2] = 0;
                    data[idx + 3] = 0;
                }
            }
        }
        return new TextureRow(id, W, H, data);
    }
}
