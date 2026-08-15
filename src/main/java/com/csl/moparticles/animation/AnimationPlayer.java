package com.csl.moparticles.animation;

import com.csl.moparticles.MoParticlesPlugin;
import com.csl.moparticles.particle.ParticleDefinition;
import com.csl.moparticles.particle.ParticleDefinition.BakedFrame;
import com.csl.moparticles.particle.ParticleDefinition.BakedParticle;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Quaternion4f IDENTITY_QUATERNION = new Quaternion4f(0f, 0f, 0f, 1f);

    private enum ClientItemMode {
        CUSTOM_MODEL_DATA,
        ITEM_MODEL
    }

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

    private User packetUser(Player viewer) {
        try {
            return PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ClientItemMode itemModeFor(Player viewer) {
        int protocol = detectProtocolVersion(viewer);
        if (protocol == 769) {
            return ClientItemMode.CUSTOM_MODEL_DATA;
        }
        if (protocol >= 774 || protocol == -1) {
            return ClientItemMode.ITEM_MODEL;
        }
        return ClientItemMode.ITEM_MODEL;
    }

    private int detectProtocolVersion(Player viewer) {
        if (viewer != null) {
            try {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                Method getAPI = viaClass.getMethod("getAPI");
                Object api = getAPI.invoke(null);
                Method getPlayerVersion = api.getClass().getMethod("getPlayerVersion", Player.class);
                Object raw = getPlayerVersion.invoke(api, viewer);
                if (raw instanceof Number number) {
                    return number.intValue();
                }
            } catch (Throwable ignored) {
                // Fallback to the server's own version
            }
        }

        String mc = Bukkit.getMinecraftVersion();
        if (mc != null) {
            if (mc.startsWith("1.21.4")) return 769;
            if (mc.startsWith("1.21.11")) return 774;
            if (mc.startsWith("26.1")) return 775;
        }
        return -1;
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

        if (itemModeFor(viewer) == ClientItemMode.ITEM_MODEL && itemModel != null) {
            meta.setItemModel(itemModel);
        } else {
            meta.setItemModel(null);
        }

        meta.setHideTooltip(true);
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return item;
    }

    private List<EntityData<?>> itemDisplayMetadata(ItemStack item, float scaleX, float scaleY) {
        float sx = Math.max(0.03f, Math.min(4.0f, scaleX * BEDROCK_SIZE_MULTIPLIER));
        float sy = Math.max(0.03f, Math.min(4.0f, scaleY * BEDROCK_SIZE_MULTIPLIER));
        float thin = Math.max(0.0015f, Math.max(sx, sy) * 0.01f);
        return List.of(
                new EntityData(5, EntityDataTypes.BOOLEAN, true),
                new EntityData(8, EntityDataTypes.INT, 0),
                new EntityData(9, EntityDataTypes.INT, 1),
                new EntityData(10, EntityDataTypes.INT, 1),
                new EntityData(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, 0f, 0f)),
                new EntityData(12, EntityDataTypes.VECTOR3F, new Vector3f(sx, sy, thin)),
                new EntityData(13, EntityDataTypes.QUATERNION, IDENTITY_QUATERNION),
                new EntityData(14, EntityDataTypes.QUATERNION, IDENTITY_QUATERNION),
                new EntityData(15, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData(16, EntityDataTypes.INT, FULL_BRIGHT),
                new EntityData(17, EntityDataTypes.FLOAT, 6.0f),
                new EntityData(18, EntityDataTypes.FLOAT, 0.0f),
                new EntityData(19, EntityDataTypes.FLOAT, 0.0f),
                new EntityData(20, EntityDataTypes.FLOAT, 0.0f),
                new EntityData(21, EntityDataTypes.FLOAT, 0.0f),
                new EntityData(23, EntityDataTypes.ITEMSTACK, item),
                new EntityData(24, EntityDataTypes.BYTE, ITEM_DISPLAY_NONE)
        );
    }

    private List<EntityData<?>> blockDisplayMetadata(int blockStateId, float scale) {
        float s = Math.max(0.03f, Math.min(4.0f, scale));
        return List.of(
                new EntityData(5, EntityDataTypes.BOOLEAN, true),
                new EntityData(8, EntityDataTypes.INT, 0),
                new EntityData(9, EntityDataTypes.INT, 1),
                new EntityData(10, EntityDataTypes.INT, 1),
                new EntityData(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, 0f, 0f)),
                new EntityData(12, EntityDataTypes.VECTOR3F, new Vector3f(s, s, s)),
                new EntityData(13, EntityDataTypes.QUATERNION, IDENTITY_QUATERNION),
                new EntityData(14, EntityDataTypes.QUATERNION, IDENTITY_QUATERNION),
                new EntityData(15, EntityDataTypes.BYTE, (byte) 0),
                new EntityData(16, EntityDataTypes.INT, FULL_BRIGHT),
                new EntityData(17, EntityDataTypes.FLOAT, 6.0f),
                new EntityData(18, EntityDataTypes.FLOAT, 0.0f),
                new EntityData(19, EntityDataTypes.FLOAT, 0.0f),
                new EntityData(20, EntityDataTypes.FLOAT, 0.0f),
                new EntityData(21, EntityDataTypes.FLOAT, 0.0f),
                new EntityData(23, EntityDataTypes.BLOCK_STATE, blockStateId)
        );
    }

    private void spawnPacketItemDisplay(Player viewer, int entityId, Location where,
                                        org.bukkit.inventory.ItemStack bukkitItem, float scaleX, float scaleY) {
        User user = packetUser(viewer);
        if (user == null) return;

        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(UUID.randomUUID()),
                EntityTypes.ITEM_DISPLAY,
                new Vector3d(where.getX(), where.getY(), where.getZ()),
                0f, 0f, 0f, 0, Optional.empty()
        );
        ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(entityId, itemDisplayMetadata(peItem, scaleX, scaleY));
        user.sendPacket(spawn);
        user.sendPacket(meta);
    }

    private void updatePacketItemDisplay(Player viewer, int entityId, Location where,
                                         org.bukkit.inventory.ItemStack bukkitItem, float scaleX, float scaleY) {
        User user = packetUser(viewer);
        if (user == null) return;

        user.sendPacket(new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(where.getX(), where.getY(), where.getZ()),
                0f, 0f,
                true
        ));

        ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
        float sx = Math.max(0.03f, Math.min(4.0f, scaleX * BEDROCK_SIZE_MULTIPLIER));
        float sy = Math.max(0.03f, Math.min(4.0f, scaleY * BEDROCK_SIZE_MULTIPLIER));
        float thin = Math.max(0.0015f, Math.max(sx, sy) * 0.01f);
        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(entityId, List.of(
                new EntityData(10, EntityDataTypes.INT, 1),
                new EntityData(12, EntityDataTypes.VECTOR3F,
                        new Vector3f(sx, sy, thin)),
                new EntityData(23, EntityDataTypes.ITEMSTACK, peItem)
        ));
        user.sendPacket(meta);
    }

    private void spawnPacketBlockDisplay(Player viewer, int entityId, Location where, int blockStateId, float scale) {
        User user = packetUser(viewer);
        if (user == null) return;

        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(UUID.randomUUID()),
                EntityTypes.BLOCK_DISPLAY,
                new Vector3d(where.getX(), where.getY(), where.getZ()),
                0f, 0f, 0f, 0, Optional.empty()
        );
        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(entityId, blockDisplayMetadata(blockStateId, scale));
        user.sendPacket(spawn);
        user.sendPacket(meta);
    }

    private void updatePacketBlockDisplay(Player viewer, int entityId, Location where) {
        User user = packetUser(viewer);
        if (user == null) return;
        user.sendPacket(new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(where.getX(), where.getY(), where.getZ()),
                0f, 0f,
                true
        ));
    }

    private void destroyPacketEntities(Player viewer, List<Integer> entityIds) {
        if (entityIds.isEmpty()) return;
        User user = packetUser(viewer);
        if (user == null) return;
        int[] ids = entityIds.stream().mapToInt(Integer::intValue).toArray();
        user.sendPacket(new WrapperPlayServerDestroyEntities(ids));
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

        final Map<Integer, Integer> packetParticleIds = new HashMap<>();
        final Map<Integer, Integer> packetDebugIds = new HashMap<>();
        final Set<UUID> packetViewers = new HashSet<>();
        final Map<Integer, ItemDisplay> nativeParticleDisplays = new HashMap<>();
        final Map<Integer, BlockDisplay> nativeDebugDisplays = new HashMap<>();

        final int debugStoneStateId = com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.STONE
                .createBlockState().getGlobalId();

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
            if (!packetViewers.isEmpty()) {
                List<Integer> allPacketIds = new ArrayList<>(packetParticleIds.values());
                allPacketIds.addAll(packetDebugIds.values());
                for (UUID uuid : new HashSet<>(packetViewers)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        destroyPacketEntities(p, allPacketIds);
                    }
                }
                packetViewers.clear();
                packetParticleIds.clear();
                packetDebugIds.clear();
            }

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
