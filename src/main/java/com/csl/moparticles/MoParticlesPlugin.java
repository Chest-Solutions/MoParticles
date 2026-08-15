package com.csl.moparticles;

import com.csl.moparticles.animation.AnimationPlayer;
import com.csl.moparticles.api.MoParticleAPI;
import com.csl.moparticles.command.MoParticlesCommand;
import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.particle.ParticleDefinition;
import com.csl.moparticles.particle.ParticleParser;
import com.csl.moparticles.resourcepack.ResourcePackGenerator;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

public final class MoParticlesPlugin extends JavaPlugin {
    public static final String NAMESPACE = "moparticles";

    private static MoParticlesPlugin instance;

    private final Map<String, ParticleDefinition> particles = new HashMap<>();
    private MolangEngine molang;
    private AnimationPlayer player;
    private ResourcePackGenerator resourcePack;
    private MoParticlesCommand command;

    public static MoParticlesPlugin get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        this.molang = new MolangEngine();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        File particlesDir = new File(getDataFolder(), "particles");
        if (!particlesDir.exists()) {
            particlesDir.mkdirs();
        }
        saveBundledParticleIfMissing("rainbow.json");
        saveBundledParticleIfMissing("fire.json");
        saveBundledParticleIfMissing("loading.json");

        File texturesDir = new File(getDataFolder(), "textures/particle");
        if (!texturesDir.exists()) {
            texturesDir.mkdirs();
        }

        saveBundledTextureIfMissing("circle.png");
        saveBundledTextureIfMissing("cube.png");

        ParticleParser parser = new ParticleParser(molang);
        int loaded = parser.parseDirectory(particlesDir, particles);
        getLogger().info("[MoParticles] Loaded " + loaded + " particle effect(s).");

        this.resourcePack = new ResourcePackGenerator(this);
        resourcePack.generate();

        this.player = new AnimationPlayer(this);
        MoParticleAPI.init(this);

        this.command = new MoParticlesCommand(this);
        command.register();

        getLogger().info("MoParticles v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (player != null) {
            player.shutdown();
        }
        if (command != null) {
            command.unregister();
        }
    }

    public Map<String, ParticleDefinition> getParticles() {
        return particles;
    }

    public String resolveEffectId(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;

        if (particles.containsKey(trimmed)) {
            return trimmed;
        }

        String bare = trimmed;
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && colon + 1 < trimmed.length()) {
            bare = trimmed.substring(colon + 1);
        }

        String defaulted = NAMESPACE + ":" + bare;
        if (particles.containsKey(defaulted)) {
            return defaulted;
        }

        for (String id : particles.keySet()) {
            if (id.equalsIgnoreCase(trimmed) || id.endsWith(":" + bare)) {
                return id;
            }
        }
        return null;
    }

    public String resolveEffectId(NamespacedKey key) {
        return key == null ? null : resolveEffectId(key.toString());
    }

    public ParticleDefinition resolveParticle(String input) {
        String id = resolveEffectId(input);
        return id == null ? null : particles.get(id);
    }

    public ParticleDefinition resolveParticle(NamespacedKey key) {
        String id = resolveEffectId(key);
        return id == null ? null : particles.get(id);
    }

    public int modelNumericId(String effectId) {
        String resolved = resolveEffectId(effectId);
        String id = resolved == null ? effectId : resolved;
        CRC32 crc32 = new CRC32();
        crc32.update(id.getBytes(StandardCharsets.UTF_8));
        long value = crc32.getValue();
        return 1_000_000 + (int) (value % 9_000_000L);
    }

    public String modelPath(String effectId) {
        String resolved = resolveEffectId(effectId);
        String id = resolved == null ? effectId : resolved;
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? NAMESPACE : id.substring(0, colon);
        String name = colon < 0 ? id : id.substring(colon + 1);
        return namespace + ":particles/" + name;
    }

    public MolangEngine getMolang() {
        return molang;
    }

    public AnimationPlayer getAnimationPlayer() {
        return player;
    }

    public ResourcePackGenerator getResourcePack() {
        return resourcePack;
    }

    private void saveBundledParticleIfMissing(String fileName) {
        File out = new File(new File(getDataFolder(), "particles"), fileName);
        if (!out.exists()) {
            saveResource("particles/" + fileName, false);
        }
    }

    private void saveBundledTextureIfMissing(String fileName) {
        File out = new File(new File(getDataFolder(), "textures"), fileName);
        if (!out.exists()) {
            saveResource("textures/" + fileName, false);
        }
    }

    public void reloadParticles() {
        particles.clear();
        File particlesDir = new File(getDataFolder(), "particles");
        if (!particlesDir.exists()) {
            particlesDir.mkdirs();
        }
        ParticleParser parser = new ParticleParser(molang);
        int loaded = parser.parseDirectory(particlesDir, particles);
        getLogger().info("[MoParticles] Reloaded " + loaded + " particle effect(s).");
    }
}
