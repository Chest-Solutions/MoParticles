package com.csl.moparticles.particle;

import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.List;

/** Parsed effect plus baked runtime frames. */
public final class ParticleDefinition {

    private final String id;
    private final String namespace;
    private final String material;
    private final String texture;
    private final double bakedDuration;
    private final double bakedFps;
    private final int totalFrames;
    private final List<BakedFrame> frames;
    private final List<Particle> javaParticles;
    private final boolean hasCustomTexture;
    private final List<TextureRow> textureRows;

    public ParticleDefinition(String id, String namespace, String material, String texture,
                              double duration, double fps, int totalFrames,
                              List<BakedFrame> frames, List<Particle> javaParticles,
                              boolean hasCustomTexture, List<TextureRow> textureRows) {
        this.id = id;
        this.namespace = namespace;
        this.material = material;
        this.texture = texture;
        this.bakedDuration = duration;
        this.bakedFps = fps;
        this.totalFrames = totalFrames;
        this.frames = frames;
        this.javaParticles = javaParticles;
        this.hasCustomTexture = hasCustomTexture;
        this.textureRows = textureRows;
    }

    public String id() { return id; }
    public String namespace() { return namespace; }
    public String material() { return material; }
    public String texture() { return texture; }
    public double duration() { return bakedDuration; }
    public double fps() { return bakedFps; }
    public int totalFrames() { return totalFrames; }
    public List<BakedFrame> frames() { return frames; }
    public List<Particle> javaParticles() { return javaParticles; }
    public boolean hasCustomTexture() { return hasCustomTexture; }
    public List<TextureRow> textureRows() { return textureRows; }

    public static final class BakedFrame {
        public final double time;
        public final List<BakedParticle> particles;

        public BakedFrame(double time, List<BakedParticle> particles) {
            this.time = time;
            this.particles = particles;
        }
    }

    public static final class BakedParticle {
        public final int id;
        public final double dx, dy, dz;
        public final double vx, vy, vz;
        public final Color colour;
        public final float scaleX;
        public final float scaleY;
        public final float scale;
        public final int textureRow;
        public final int textureColumn;
        public final int atlasWidth;
        public final int atlasHeight;
        public final boolean dust;
        public final boolean block;
        public final boolean item;
        public final org.bukkit.Material blockMaterial;
        public final org.bukkit.inventory.ItemStack itemStack;

        public BakedParticle(int id,
                             double dx, double dy, double dz,
                             double vx, double vy, double vz,
                             Color colour, float scaleX, float scaleY,
                             int textureRow, int textureColumn,
                             int atlasWidth, int atlasHeight,
                             boolean dust, boolean block, boolean item,
                             org.bukkit.Material blockMaterial,
                             org.bukkit.inventory.ItemStack itemStack) {
            this.id = id;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.colour = colour;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scale = (scaleX + scaleY) * 0.5f;
            this.textureRow = textureRow;
            this.textureColumn = textureColumn;
            this.atlasWidth = atlasWidth;
            this.atlasHeight = atlasHeight;
            this.dust = dust;
            this.block = block;
            this.item = item;
            this.blockMaterial = blockMaterial;
            this.itemStack = itemStack;
        }
    }

    public static final class TextureRow {
        public final String name;
        public final int width;
        public final int height;
        public final byte[] rgba;

        public TextureRow(String name, int width, int height, byte[] rgba) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.rgba = rgba;
        }
    }

    public static final class Builder {
        public String id = "moparticles:unnamed";
        public String material = "particles_alpha";
        public String texture = "textures/particle/moparticles/atlas";
        public double duration = 2.0;
        public double fps = 20.0;
        public int totalFrames;
        public final List<BakedFrame> frames = new ArrayList<>();
        public final List<Particle> javaParticles = new ArrayList<>();
        public final List<TextureRow> textureRows = new ArrayList<>();
        public boolean hasCustomTexture;

        public String namespace() {
            int colon = id.indexOf(':');
            return colon < 0 ? "moparticles" : id.substring(0, colon);
        }
    }
}
