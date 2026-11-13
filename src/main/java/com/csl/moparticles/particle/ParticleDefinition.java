package com.csl.moparticles.particle;

import org.bukkit.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed Bedrock particle effect plus its baked runtime frames.
 *
 * <p>The simulator bakes the effect once at load time; the animation player
 * afterwards only replays the immutable frames, so playing hundreds of
 * particles costs no MoLang evaluation at all.</p>
 */
public final class ParticleDefinition {

    private final String id;
    private final String namespace;
    private final String material;
    private final String texture;
    private final double bakedDuration;
    private final double bakedFps;
    private final int totalFrames;
    private final List<BakedFrame> frames;

    public ParticleDefinition(String id, String namespace, String material, String texture,
                              double duration, double fps, int totalFrames, List<BakedFrame> frames) {
        this.id = id;
        this.namespace = namespace;
        this.material = material;
        this.texture = texture;
        this.bakedDuration = duration;
        this.bakedFps = fps;
        this.totalFrames = totalFrames;
        this.frames = frames;
    }

    public String id() { return id; }
    public String namespace() { return namespace; }

    /** The id without its namespace prefix (e.g. {@code fire}). */
    public String shortName() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    public String material() { return material; }
    public String texture() { return texture; }
    public double duration() { return bakedDuration; }
    public double fps() { return bakedFps; }
    public int totalFrames() { return totalFrames; }
    public List<BakedFrame> frames() { return frames; }

    /** One baked timestep (usually 1/20 s) of the effect. */
    public static final class BakedFrame {
        public final double time;
        public final List<BakedParticle> particles;

        public BakedFrame(double time, List<BakedParticle> particles) {
            this.time = time;
            this.particles = particles;
        }
    }

    /**
     * A single particle at a single timestep, relative to the emitter origin.
     * Kept intentionally small: hundreds of these are held per effect.
     */
    public static final class BakedParticle {
        public final int id;
        public final double dx, dy, dz;
        public final Color colour;
        public final float scaleX;
        public final float scaleY;

        public BakedParticle(int id,
                             double dx, double dy, double dz,
                             Color colour, float scaleX, float scaleY) {
            this.id = id;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.colour = colour;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }

    /** Mutable staging area the simulator writes into while baking. */
    public static final class Builder {
        public String id = "moparticles:unnamed";
        public String material = "particles_alpha";
        public String texture = "textures/particle/moparticles/atlas";
        public double duration = 2.0;
        public double fps = 20.0;
        public int totalFrames;
        public final List<BakedFrame> frames = new ArrayList<>();

        public String namespace() {
            int colon = id.indexOf(':');
            return colon < 0 ? "moparticles" : id.substring(0, colon);
        }
    }
}
