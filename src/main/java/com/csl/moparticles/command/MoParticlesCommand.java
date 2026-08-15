package com.csl.moparticles.command;

import com.csl.moparticles.MoParticlesPlugin;
import com.csl.moparticles.animation.AnimationPlayer;
import com.csl.moparticles.particle.ParticleDefinition;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.bukkit.annotation.specifier.DefaultNamespace;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Command registration and handlers. */
public final class MoParticlesCommand {

    private final MoParticlesPlugin plugin;
    private LegacyPaperCommandManager<CommandSender> commandManager;
    private AnnotationParser<CommandSender> annotationParser;

    public MoParticlesCommand(MoParticlesPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            this.commandManager = LegacyPaperCommandManager.createNative(
                    plugin,
                    ExecutionCoordinator.simpleCoordinator());

            try {
                if (commandManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)
                        || commandManager.hasCapability(CloudBukkitCapabilities.BRIGADIER)) {
                    commandManager.registerBrigadier();
                    plugin.getLogger().info("[MoParticles] Cloud command manager registered with Brigadier support.");
                } else {
                    plugin.getLogger().info("[MoParticles] Cloud command manager registered");
                }
            } catch (Throwable brigEx) {
                plugin.getLogger().warning("[MoParticles] Brigadier registration failed, continuing without it): " + brigEx.getMessage());
            }

            this.annotationParser = new AnnotationParser<>(commandManager, CommandSender.class);
            annotationParser.parse(this);
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to register /moparticles command: " + ex);
            ex.printStackTrace();
        }
    }

    public void unregister() {

    }

    @Command("moparticles list")
    @CommandDescription("List all loaded particle effects")
    @Permission("moparticles.use")
    public void listCommand(final CommandSender sender) {
        var all = plugin.getParticles();
        sender.sendMessage("§6[MoParticles] §7Loaded effects: §e" + all.size());
        StringBuilder sb = new StringBuilder();
        for (String id : all.keySet().stream().sorted().toList()) {
            if (sb.length() > 0) sb.append("§7, §e");
            sb.append(id);
        }
        sender.sendMessage("§7" + sb);
    }

    @Command("moparticles play <effect> <location> [radius]")
    @CommandDescription("Play a particle effect at the given location for players within [radius] blocks")
    @Permission("moparticles.play")
    public void playCommand(
            final CommandSender sender,
            @Argument(value = "effect", suggestions = "effect-suggestions")
            @DefaultNamespace(MoParticlesPlugin.NAMESPACE) final NamespacedKey effect,
            @Argument("location") final Location location,
            @Argument("radius") @Default("64.0") final Double radius
    ) {
        double r = radius == null ? 64.0 : radius;
        String resolved = plugin.resolveEffectId(effect);
        UUID id = plugin.getAnimationPlayer().play(resolved, location, r);
        if (id == null) {
            sender.sendMessage("§c[MoParticles] unknown effect: §f" + effect);
        } else {
            sender.sendMessage("§a[MoParticles] playing §e" + resolved + " §a(id=" + id + ")");
        }
    }

    @Command("moparticles playhere <effect> [radius]")
    @CommandDescription("Play a particle effect at your position")
    @Permission("moparticles.play")
    public void playhereCommand(
            final Player player,
            @Argument(value = "effect", suggestions = "effect-suggestions")
            @DefaultNamespace(MoParticlesPlugin.NAMESPACE) final NamespacedKey effect,
            @Argument("radius") @Default("64.0") final Double radius
    ) {
        double r = radius == null ? 64.0 : radius;
        String resolved = plugin.resolveEffectId(effect);
        UUID id = plugin.getAnimationPlayer().play(resolved, player.getLocation(), r);
        if (id == null) {
            player.sendMessage("§c[MoParticles] unknown effect: §f" + effect);
        } else {
            player.sendMessage("§a[MoParticles] playing §e" + resolved + " §a(id=" + id + ")");
        }
    }

    @Command("moparticles stop <id>")
    @CommandDescription("Stop a playing particle animation by its id")
    @Permission("moparticles.play")
    public void stopCommand(final CommandSender sender, @Argument("id") final String idStr) {
        try {
            UUID id = UUID.fromString(idStr);
            plugin.getAnimationPlayer().stopAnimation(id);
            sender.sendMessage("§a[MoParticles] stopped " + id);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c[MoParticles] invalid uuid: " + idStr);
        }
    }

    @Command("moparticles stopall")
    @CommandDescription("Stop every running particle animation")
    @Permission("moparticles.play")
    public void stopallCommand(final CommandSender sender) {
        plugin.getAnimationPlayer().stopAll();
        sender.sendMessage("§a[MoParticles] all animations stopped");
    }

    @Command("moparticles reload")
    @CommandDescription("Reload particles and resource pack from disk")
    @Permission("moparticles.admin")
    public void reloadCommand(final CommandSender sender) {
        plugin.reloadParticles();
        plugin.getResourcePack().generate();
        sender.sendMessage("§a[MoParticles] reloaded");
    }

    @Command("moparticles info <effect>")
    @CommandDescription("Show effect metadata")
    @Permission("moparticles.use")
    public void infoCommand(
            final CommandSender sender,
            @Argument(value = "effect", suggestions = "effect-suggestions")
            @DefaultNamespace(MoParticlesPlugin.NAMESPACE) final NamespacedKey effect
    ) {
        ParticleDefinition def = plugin.resolveParticle(effect);
        if (def == null) {
            sender.sendMessage("§c[MoParticles] unknown effect: §f" + effect);
            return;
        }
        sender.sendMessage("§6[MoParticles] info §e" + def.id());
        sender.sendMessage("§7  duration: §f" + def.duration() + "s, fps: " + def.fps());
        sender.sendMessage("§7  frames: §f" + def.totalFrames());
        sender.sendMessage("§7  material: §f" + def.material());
        sender.sendMessage("§7  texture: §f" + def.texture());
    }

    @Suggestions("effect-suggestions")
    public @NonNull Stream<@NonNull String> suggestEffects(
            final @NonNull CommandContext<CommandSender> ctx,
            final @NonNull String input
    ) {
        List<String> all = plugin.getParticles().keySet().stream()
                .map(id -> {
                    int colon = id.indexOf(':');
                    return colon < 0 ? id : id.substring(colon + 1);
                })
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (input != null && !input.isEmpty()) {
            String lo = input.toLowerCase();
            all.removeIf(s -> !s.toLowerCase().contains(lo));
        }
        return all.stream();
    }
}
