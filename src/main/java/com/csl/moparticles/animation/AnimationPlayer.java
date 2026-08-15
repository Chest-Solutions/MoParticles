package com.csl.moparticles.animation;

import com.csl.moparticles.MoParticlesPlugin;
import com.csl.moparticles.particle.ParticleDefinition;
import com.csl.moparticles.particle.ParticleDefinition.BakedFrame;
import com.csl.moparticles.particle.ParticleDefinition.BakedParticle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Plays baked MoParticles frames
 * For now it plays everything on the server and lets it handle the packets, Which is pretty laggy but I have no idea how to make it work with packetevents.
*/
public final class AnimationPlayer {

    private static final Material DISPLAY_ITEM = Material.PAPER;
    private static final byte BILLBOARD_CENTER = 3;
    private static final byte ITEM_DISPLAY_NONE = 0;
    private static final int FULL_BRIGHT = (15 << 4) | (15 << 20);
    private static final float BEDROCK_SIZE_MULTIPLIER = 5.0f;

    private final MoParticlesPlugin plugin;
    private final ConcurrentHashMap<UUID, ActiveAnimation> active = new ConcurrentHashMap<>();
    private final Set<UUID> stopping = ConcurrentHashMap.newKeySet();

    public AnimationPlayer(MoParticlesPlugin plugin) {
        this.plugin = plugin;
    }
    public boolean isPlaying(UUID id) { return active.containsKey(id); }

    public void stopAnimation(UUID id) {
        if (id == null) return;
        stopping.add(id);
        ActiveAnimation a = active.remove(id);
        if (a != null && a.task != null) a.task.cancel();
        if (a != null) a.cleanup();
    }

    public void stopAll() {
        for (UUID id : new ArrayList<>(active.keySet())) stopAnimation(id);
    }

    public void shutdown() {
        stopAll();
    }

    public UUID play(String effectId, Location location, double radius) {
        return play(effectId, location, radius, null);
    }

    public UUID play(String effectId, Location location, double radius, Player onlyFor) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("play('" + effectId + "') ignored - location is null.");
            return null;
        }

        String resolvedId = plugin.resolveEffectId(effectId);
        ParticleDefinition def = resolvedId == null ? null : plugin.getParticles().get(resolvedId);
        if (def == null) {
            plugin.getLogger().warning("play(): Unknown Effect '" + effectId + "'");
            return null;
        }
        if (def.frames().isEmpty()) {
            plugin.getLogger().warning("play('" + effectId + "') - Effect is empty.");
            return null;
        }

        if (radius <= 0) radius = 64.0;

        UUID id = UUID.randomUUID();
        ActiveAnimation a = new ActiveAnimation(id, def, location.clone(), radius, onlyFor);
        active.put(id, a);
        a.start();
        return id;
    }

    public UUID playForPlayer(String effectId, Player player, Location location) {
        if (player == null) return play(effectId, location, 0);
        return play(effectId, location, 0, player);
    }

    private org.bukkit.inventory.ItemStack buildParticleItem(Player viewer, ParticleDefinition def, Color colour) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(DISPLAY_ITEM, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Color tint = colour == null ? Color.WHITE : colour;
        int numericModelId = plugin.modelNumericId(def.id());
        NamespacedKey itemModel = NamespacedKey.fromString(plugin.modelPath(def.id()));

        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) numericModelId));
        cmd.setFlags(List.of());
        cmd.setStrings(List.of());
        cmd.setColors(List.of(tint));
        meta.setCustomModelDataComponent(cmd);

        if (itemModel != null) {
            meta.setItemModel(itemModel);
        }

        meta.setHideTooltip(true);
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return item;
    }

    private ItemDisplay spawnNativeItemDisplay(Location where) {
        return where.getWorld().spawn(where, ItemDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(1);
            e.setTeleportDuration(1);
            e.setViewRange(6.0f);
            e.setShadowRadius(0.0f);
            e.setShadowStrength(0.0f);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        });
    }

    private void updateNativeItemDisplay(ItemDisplay display, List<Player> viewers, Location where,
                                         org.bukkit.inventory.ItemStack item, float scaleX, float scaleY) {
        if (display == null || !display.isValid()) return;
        display.teleport(where);
        display.setItemStack(item);
        float sx = Math.max(0.03f, Math.min(4.0f, scaleX * BEDROCK_SIZE_MULTIPLIER));
        float sy = Math.max(0.03f, Math.min(4.0f, scaleY * BEDROCK_SIZE_MULTIPLIER));
        float thin = Math.max(0.0015f, Math.max(sx, sy) * 0.01f);
        display.setTransformation(new Transformation(
                new org.joml.Vector3f(),
                new AxisAngle4f(),
                new org.joml.Vector3f(sx, sy, thin),
                new AxisAngle4f()
        ));
        for (Player viewer : viewers) {
            if (viewer.isOnline()) viewer.showEntity(plugin, display);
        }
    }

    private BlockDisplay spawnNativeBlockDisplay(Location where) {
        return where.getWorld().spawn(where, BlockDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setBillboard(Display.Billboard.FIXED);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(1);
            e.setTeleportDuration(1);
            e.setViewRange(6.0f);
            e.setShadowRadius(0.0f);
            e.setShadowStrength(0.0f);
            e.setBlock(Bukkit.createBlockData(Material.STONE));
        });
    }

    private void updateNativeBlockDisplay(BlockDisplay display, List<Player> viewers, Location where, float scale) {
        if (display == null || !display.isValid()) return;
        display.teleport(where);
        float s = Math.max(0.03f, Math.min(4.0f, scale));
        display.setTransformation(new Transformation(
                new org.joml.Vector3f(),
                new AxisAngle4f(),
                new org.joml.Vector3f(s, s, s),
                new AxisAngle4f()
        ));
        for (Player viewer : viewers) {
            if (viewer.isOnline()) viewer.showEntity(plugin, display);
        }
    }

    private final class ActiveAnimation implements Runnable {
        final UUID id;
        final ParticleDefinition def;
        final Location origin;
        final double radius;
        final Player onlyFor;
        int frameIndex = -1;
        BukkitTask task;

        final Map<Integer, ItemDisplay> nativeParticleDisplays = new HashMap<>();
        final Map<Integer, BlockDisplay> nativeDebugDisplays = new HashMap<>();

        ActiveAnimation(UUID id, ParticleDefinition def, Location origin, double radius, Player onlyFor) {
            this.id = id;
            this.def = def;
            this.origin = origin;
            this.radius = radius;
            this.onlyFor = onlyFor;
        }

        void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 1L);
        }

        @Override
        public void run() {
            if (stopping.contains(id)) {
                cleanup();
                active.remove(id);
                stopping.remove(id);
                if (task != null) task.cancel();
                return;
            }
            frameIndex++;
            if (frameIndex >= def.frames().size()) {
                cleanup();
                active.remove(id);
                if (task != null) task.cancel();
                return;
            }
            BakedFrame frame = def.frames().get(frameIndex);
            sendFrame(frame);
        }

        void cleanup() {
            for (ItemDisplay display : nativeParticleDisplays.values()) {
                if (display != null && display.isValid()) display.remove();
            }
            nativeParticleDisplays.clear();

            for (BlockDisplay display : nativeDebugDisplays.values()) {
                if (display != null && display.isValid()) display.remove();
            }
            nativeDebugDisplays.clear();
        }

        void sendFrame(BakedFrame frame) {
            if (frame == null) return;

            List<Player> viewers = collectViewers();
            if (viewers.isEmpty()) {
                cleanup();
                return;
            }

            sendNativeFrame(viewers, frame);
        }

        private List<Player> collectViewers() {
            List<Player> viewers = new ArrayList<>();
            if (onlyFor != null) {
                if (onlyFor.isOnline()) viewers.add(onlyFor);
                return viewers;
            }
            if (origin.getWorld() == null) return viewers;
            double radiusSq = radius * radius;
            for (Player p : origin.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(origin) <= radiusSq) {
                    viewers.add(p);
                }
            }
            return viewers;
        }

        private void sendNativeFrame(List<Player> viewers, BakedFrame frame) {
            Set<Integer> desiredParticleIds = new HashSet<>();
            for (BakedParticle bp : frame.particles) desiredParticleIds.add(bp.id);

            for (Map.Entry<Integer, ItemDisplay> entry : new HashMap<>(nativeParticleDisplays).entrySet()) {
                if (!desiredParticleIds.contains(entry.getKey())) {
                    ItemDisplay removed = nativeParticleDisplays.remove(entry.getKey());
                    if (removed != null && removed.isValid()) removed.remove();
                }
            }

            for (BakedParticle bp : frame.particles) {
                Location where = new Location(origin.getWorld(),
                        origin.getX() + bp.dx,
                        origin.getY() + bp.dy,
                        origin.getZ() + bp.dz);
                ItemDisplay display = nativeParticleDisplays.computeIfAbsent(bp.id, k -> spawnNativeItemDisplay(origin));
                updateNativeItemDisplay(
                        display,
                        viewers,
                        where,
                        buildParticleItem(viewers.get(0), def, bp.colour),
                        bp.scaleX,
                        bp.scaleY
                );
            }

            boolean debug = MoParticlesPlugin.isDebugMode();
            if (!debug) {
                for (BlockDisplay display : nativeDebugDisplays.values()) {
                    if (display != null && display.isValid()) display.remove();
                }
                nativeDebugDisplays.clear();
            } else {
                for (Map.Entry<Integer, BlockDisplay> entry : new HashMap<>(nativeDebugDisplays).entrySet()) {
                    if (!desiredParticleIds.contains(entry.getKey())) {
                        BlockDisplay removed = nativeDebugDisplays.remove(entry.getKey());
                        if (removed != null && removed.isValid()) removed.remove();
                    }
                }
                for (BakedParticle bp : frame.particles) {
                    Location where = new Location(origin.getWorld(),
                            origin.getX() + bp.dx,
                            origin.getY() + bp.dy,
                            origin.getZ() + bp.dz);
                    BlockDisplay display = nativeDebugDisplays.computeIfAbsent(bp.id, k -> spawnNativeBlockDisplay(origin));
                    updateNativeBlockDisplay(display, viewers, where, 0.08f);
                }
            }
        }
    }
}
