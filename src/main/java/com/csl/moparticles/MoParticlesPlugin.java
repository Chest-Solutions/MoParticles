package com.csl.moparticles;

import com.csl.moparticles.animation.AnimationPlayer;
import com.csl.moparticles.api.MoParticleAPI;
import com.csl.moparticles.command.MoParticlesCommand;
import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.particle.ParticleDefinition;
import com.csl.moparticles.particle.ParticleParser;
import com.csl.moparticles.resourcepack.ResourcePackGenerator;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
        // The spigot module normally sets up PacketEvents through its own Bukkit
        // plugin; since we shade + relocate it, we must do it ourselves:
        // build the API instance for THIS plugin, then load it early so it can
        // inject into player connections before they are established.
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();

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

        refreshDefaultTexture("circle.png");
        refreshDefaultTexture("cube.png");

        loadParticles();

        this.resourcePack = new ResourcePackGenerator(this);
        resourcePack.generate();

        this.player = new AnimationPlayer(this);
        MoParticleAPI.init(this);

        this.command = new MoParticlesCommand(this);
        command.register();

        getLogger().info("MoParticles v" + getPluginMeta().getVersion()
                + " enabled (packets via PacketEvents " + PacketEvents.getAPI().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        if (player != null) {
            player.shutdown();
        }
        if (command != null) {
            command.unregister();
        }
        PacketEvents.getAPI().terminate();
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

    /**
     * The item-definition location the resource pack assigns to this effect.
     * This is the {@code minecraft:item_model} component value and must match
     * {@code assets/<namespace>/items/particles/<name>.json} in the generated
     * pack (see ResourcePackGenerator#generate). The client resolves it
     * relative to the {@code items/} directory.
     */
    public String itemDefinitionPath(String effectId) {
        ParticleDefinition def = resolveParticle(effectId);
        if (def != null) {
            return def.namespace() + ":particles/" + def.shortName();
        }
        String id = effectId == null ? NAMESPACE : effectId;
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

    public void reloadParticles() {
        loadParticles();
        resourcePack.generate();
    }

    /** (Re)parses every effect in {@code plugins/MoParticles/particles}. */
    private void loadParticles() {
        particles.clear();
        File particlesDir = new File(getDataFolder(), "particles");
        if (!particlesDir.exists()) {
            particlesDir.mkdirs();
        }
        ParticleParser parser = new ParticleParser(molang, getLogger());
        int loaded = parser.parseDirectory(particlesDir, particles);
        getLogger().info("Loaded " + loaded + " particle effect(s).");
    }

    public static boolean isDebugMode() {
        return instance != null && instance.getConfig().getBoolean("debug", false);
    }

    private void saveBundledParticleIfMissing(String fileName) {
        extractBundled("particles/" + fileName, false);
    }

    /**
     * Default textures are plugin assets (crisp pixel art) and are refreshed
     * on every enable so texture fixes always propagate to existing installs.
     */
    private void refreshDefaultTexture(String fileName) {
        extractBundled("textures/particle/" + fileName, true);
    }

    /**
     * Copies a jar resource into the data folder. When {@code overwrite} is
     * false this only runs for missing files. A missing bundled resource is
     * logged instead of thrown so a packaging mistake can never prevent the
     * plugin from enabling.
     */
    private void extractBundled(String resourcePath, boolean overwrite) {
        File out = new File(getDataFolder(), resourcePath);
        if (!overwrite && out.isFile()) {
            return;
        }
        if (getResource(resourcePath) == null) {
            getLogger().warning("bundled resource '" + resourcePath + "' is missing from the jar.");
            return;
        }
        try {
            out.getParentFile().mkdirs();
            saveResource(resourcePath, overwrite);
        } catch (IllegalArgumentException ex) {
            getLogger().warning("could not save bundled resource '" + resourcePath + "': " + ex.getMessage());
        }
    }
}

