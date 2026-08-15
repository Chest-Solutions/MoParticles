package com.csl.moparticles.api;

import com.csl.moparticles.MoParticlesPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Public API for other plugins. */
public final class MoParticleAPI {

    private static MoParticleAPI instance;

    private final MoParticlesPlugin plugin;

    MoParticleAPI(MoParticlesPlugin plugin) {
        this.plugin = plugin;
    }

    public static void init(MoParticlesPlugin plugin) {
        instance = new MoParticleAPI(plugin);
    }

    public static MoParticleAPI get() {
        if (instance == null) {
            throw new IllegalStateException("MoParticles is not enabled");
        }
        return instance;
    }

    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    public List<String> listEffects() {
        return plugin.getParticles().keySet().stream().sorted().collect(java.util.stream.Collectors.toList());
    }

    public boolean hasEffect(String id) {
        return plugin.getParticles().containsKey(id);
    }

    public UUID play(String effect, Location where, double radius) {
        return plugin.getAnimationPlayer().play(effect, where, radius);
    }

    public void stop(UUID id) {
        plugin.getAnimationPlayer().stopAnimation(id);
    }

    public void stopAll() {
        plugin.getAnimationPlayer().stopAll();
    }

    public java.io.File getResourcePackFile() {
        return new java.io.File(plugin.getDataFolder(), "generated/resourcepack.zip");
    }
}
