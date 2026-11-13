package com.csl.moparticles.animation;

import com.csl.moparticles.MoParticlesPlugin;
import com.csl.moparticles.display.DisplayEntityPackets;
import com.csl.moparticles.particle.ParticleDefinition;
import com.csl.moparticles.particle.ParticleDefinition.BakedFrame;
import com.csl.moparticles.particle.ParticleDefinition.BakedParticle;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays baked MoLang particle animations as purely packet-based,
 * client-side Item Display entities.
 *
 * <p>Instead of spawning real (server-side) entities - which is heavy and
 * forces the world to keep them around - every particle is rendered with
 * fake entities that only exist inside the client's packet stream:</p>
 *
 * <ol>
 *   <li><b>Spawn Entity</b> + <b>Set Entity Data</b> when a particle appears</li>
 *   <li><b>Teleport Entity</b> every tick (the client smooths it over the
 *       position/rotation interpolation duration)</li>
 *   <li><b>Set Entity Data</b> only when the scale or tint actually changes</li>
 *   <li><b>Remove Entities</b> (batched) when a particle disappears</li>
 * </ol>
 *
 * <p>All packets of one viewer are written to their channel and flushed in a
 * single batch per tick, and each viewer's PacketEvents {@link User} is
 * resolved once and cached, so animating hundreds of particles stays cheap.
 * PacketEvents encodes each packet for the viewer's exact client version, so
 * the same animation works on 1.21.x and 26.x clients at the same time.</p>
 */
public final class AnimationPlayer {

    private static final Material DISPLAY_ITEM = Material.PAPER;
    /** Bedrock particle sizes are much smaller than a Java block. */
    private static final float BEDROCK_SIZE_MULTIPLIER = 5.0f;
    private static final float MIN_SCALE = 0.03f;
    private static final float MAX_SCALE = 4.0f;
    /** Render distance multiplier applied to the client's entity view distance. */
    private static final float VIEW_RANGE = 2.0f;
    /** Ticks over which scale changes interpolate (smooth growth/shrink). */
    private static final int TRANSFORM_INTERPOLATION_TICKS = 2;
    /** Ticks over which teleports interpolate (must match the 20 tps bake rate). */
    private static final int POSITION_INTERPOLATION_TICKS = 1;
    private static final double SCALE_EPSILON = 1e-4;
    /** Oldest client version whose display entity metadata layout this plugin speaks. */
    private static final ClientVersion MIN_CLIENT_VERSION = ClientVersion.V_1_21_5;

    private final MoParticlesPlugin plugin;
    private final ConcurrentHashMap<UUID, ActiveAnimation> active = new ConcurrentHashMap<>();

    public AnimationPlayer(MoParticlesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPlaying(UUID id) {
        return id != null && active.containsKey(id);
    }

    public int activeCount() {
        return active.size();
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
            plugin.getLogger().warning("play(): unknown effect '" + effectId + "'");
            return null;
        }
        if (def.frames().isEmpty()) {
            plugin.getLogger().warning("play('" + effectId + "') ignored - effect has no baked frames.");
            return null;
        }
        if (radius <= 0) {
            radius = 64.0;
        }

        ActiveAnimation animation = new ActiveAnimation(UUID.randomUUID(), def, location.clone(), radius, onlyFor);
        active.put(animation.id, animation);
        animation.start();
        return animation.id;
    }

    public UUID playForPlayer(String effectId, Player player, Location location) {
        if (player == null) {
            return play(effectId, location, 0);
        }
        return play(effectId, location, 0, player);
    }

    public void stopAnimation(UUID id) {
        if (id == null) {
            return;
        }
        ActiveAnimation animation = active.remove(id);
        if (animation != null) {
            animation.finish();
        }
    }

    public void stopAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            stopAnimation(id);
        }
    }

    public void shutdown() {
        stopAll();
    }

    /**
     * Builds the tinted paper item the displays render. The resource pack
     * turns this into the effect's plane model: the {@code item_model}
     * component points at the effect's item definition, and the custom model
     * data color list carries the tint (custom_model_data tint, index 0).
     */
    private org.bukkit.inventory.ItemStack buildBukkitItem(ParticleDefinition def, Color colour) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(DISPLAY_ITEM, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(colour == null ? Color.WHITE : colour));
        meta.setCustomModelDataComponent(cmd);

        NamespacedKey itemModel = NamespacedKey.fromString(plugin.itemDefinitionPath(def.id()));
        if (itemModel != null) {
            meta.setItemModel(itemModel);
        }
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    private static float scaleOf(float bedrockSize) {
        float scaled = bedrockSize * BEDROCK_SIZE_MULTIPLIER;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scaled));
    }

    private static int argbOf(Color colour) {
        return colour == null ? Color.WHITE.asARGB() : colour.asARGB();
    }

    /** One running animation instance (a single baked effect at one origin). */
    private final class ActiveAnimation implements Runnable {
        final UUID id;
        final ParticleDefinition def;
        final Location origin;
        final double radius;
        final Player onlyFor;
        final List<BakedFrame> frames;
        final Map<UUID, ViewerState> viewers = new HashMap<>();
        final Set<UUID> unsupported = new HashSet<>();
        final Map<Integer, ItemStack> itemCache = new HashMap<>();
        /** Scratch collections reused every tick to avoid per-tick allocation. */
        final List<Player> viewerScratch = new ArrayList<>();
        final Set<Integer> aliveScratch = new HashSet<>();
        int frameIndex = -1;
        BukkitTask task;

        ActiveAnimation(UUID id, ParticleDefinition def, Location origin, double radius, Player onlyFor) {
            this.id = id;
            this.def = def;
            this.origin = origin;
            this.radius = radius;
            this.onlyFor = onlyFor;
            this.frames = def.frames();
        }

        void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 1L);
            if (MoParticlesPlugin.isDebugMode()) {
                plugin.getLogger().info("[MoParticles] started " + def.id() + " (" + def.totalFrames() + " frames)");
            }
        }

        @Override
        public void run() {
            frameIndex++;

            List<Player> currentViewers = collectViewers();
            if (frameIndex >= frames.size() || currentViewers.isEmpty()) {
                finish();
                return;
            }

            BakedFrame frame = frames.get(frameIndex);
            aliveScratch.clear();
            for (BakedParticle bp : frame.particles) {
                aliveScratch.add(bp.id);
            }
            for (Player viewer : currentViewers) {
                sendViewerFrame(viewer, frame, aliveScratch);
            }
            removeAbsentViewers(currentViewers);
        }

        private List<Player> collectViewers() {
            viewerScratch.clear();
            if (onlyFor != null) {
                if (onlyFor.isOnline()) {
                    viewerScratch.add(onlyFor);
                }
                return viewerScratch;
            }
            if (origin.getWorld() == null) {
                return viewerScratch;
            }
            double radiusSq = radius * radius;
            for (Player p : origin.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(origin) <= radiusSq) {
                    viewerScratch.add(p);
                }
            }
            return viewerScratch;
        }

        private void sendViewerFrame(Player viewer, BakedFrame frame, Set<Integer> aliveIds) {
            UUID viewerId = viewer.getUniqueId();
            if (unsupported.contains(viewerId)) {
                return;
            }

            ViewerState state = viewers.get(viewerId);
            if (state == null) {
                User user = DisplayEntityPackets.userOf(viewer);
                if (user == null) {
                    return; // player not injected (yet) - nothing we can do
                }
                if (user.getClientVersion().isOlderThan(MIN_CLIENT_VERSION)) {
                    unsupported.add(viewerId);
                    plugin.getLogger().warning("Client '" + viewer.getName() + "' runs "
                            + user.getClientVersion() + " (minimum " + MIN_CLIENT_VERSION
                            + "); effects are skipped for them.");
                    return;
                }
                state = new ViewerState(user);
                viewers.put(viewerId, state);
            }

            // Batch-despawn particles that are no longer part of this frame.
            int[] deadIds = state.retainAlive(aliveIds);
            if (deadIds.length > 0) {
                DisplayEntityPackets.destroy(state.user, deadIds);
            }

            for (BakedParticle bp : frame.particles) {
                double x = origin.getX() + bp.dx;
                double y = origin.getY() + bp.dy;
                double z = origin.getZ() + bp.dz;
                float sx = scaleOf(bp.scaleX);
                float sy = scaleOf(bp.scaleY);
                float thin = Math.max(0.0015f, Math.max(sx, sy) * 0.01f);
                int argb = argbOf(bp.colour);

                TrackedParticle tracked = state.entities.get(bp.id);
                if (tracked == null) {
                    int entityId = DisplayEntityPackets.allocateEntityId();
                    DisplayEntityPackets.spawnItemDisplay(state.user, entityId, x, y, z,
                            itemFor(bp.colour), sx, sy, thin,
                            TRANSFORM_INTERPOLATION_TICKS, POSITION_INTERPOLATION_TICKS, VIEW_RANGE);
                    state.entities.put(bp.id, new TrackedParticle(bp.id, entityId, argb, sx, sy, thin));
                } else {
                    // Teleport first: it resets interpolation and the client eases
                    // the move over POSITION_INTERPOLATION_TICKS ticks.
                    DisplayEntityPackets.teleport(state.user, tracked.entityId, x, y, z);
                    boolean scaleChanged = Math.abs(tracked.scaleX - sx) > SCALE_EPSILON
                            || Math.abs(tracked.scaleY - sy) > SCALE_EPSILON
                            || Math.abs(tracked.scaleZ - thin) > SCALE_EPSILON;
                    boolean itemChanged = tracked.colourArgb != argb;
                    if (scaleChanged || itemChanged) {
                        DisplayEntityPackets.update(state.user, tracked.entityId,
                                sx, sy, thin, scaleChanged,
                                itemChanged ? itemFor(bp.colour) : null, itemChanged);
                        if (scaleChanged) {
                            tracked.scaleX = sx;
                            tracked.scaleY = sy;
                            tracked.scaleZ = thin;
                        }
                        if (itemChanged) {
                            tracked.colourArgb = argb;
                        }
                    }
                }
            }

            // One flush per viewer per tick instead of one per packet.
            DisplayEntityPackets.flush(state.user);
        }

        private void removeAbsentViewers(List<Player> currentViewers) {
            for (Player p : currentViewers) {
                unsupported.remove(p.getUniqueId());
            }
            Iterator<Map.Entry<UUID, ViewerState>> it = viewers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, ViewerState> entry = it.next();
                boolean stillWatching = false;
                for (Player p : currentViewers) {
                    if (p.getUniqueId().equals(entry.getKey())) {
                        stillWatching = true;
                        break;
                    }
                }
                if (!stillWatching) {
                    // Fake entities never auto-despawn on the client - if the player
                    // leaves the radius (or disconnects) their view must be cleaned up.
                    ViewerState state = entry.getValue();
                    destroyAll(state);
                    it.remove();
                }
            }
        }

        private ItemStack itemFor(Color colour) {
            return itemCache.computeIfAbsent(argbOf(colour),
                    k -> DisplayEntityPackets.toPacketItemStack(buildBukkitItem(def, colour)));
        }

        /** Despawns every particle of one viewer and flushes once. */
        private void destroyAll(ViewerState state) {
            if (state.entities.isEmpty()) {
                return;
            }
            DisplayEntityPackets.destroy(state.user, entityIdsOf(state.entities));
            DisplayEntityPackets.flush(state.user);
            state.entities.clear();
        }

        private static int[] entityIdsOf(Map<Integer, TrackedParticle> entities) {
            int[] ids = new int[entities.size()];
            int i = 0;
            for (TrackedParticle tracked : entities.values()) {
                ids[i++] = tracked.entityId;
            }
            return ids;
        }

        void finish() {
            for (ViewerState state : viewers.values()) {
                destroyAll(state);
            }
            viewers.clear();
            itemCache.clear();
            if (task != null) {
                task.cancel();
            }
            active.remove(id);
            if (MoParticlesPlugin.isDebugMode()) {
                plugin.getLogger().info("[MoParticles] stopped " + def.id());
            }
        }
    }

    /** Client-side particle handles owned by a single viewer. */
    private static final class ViewerState {
        final User user;
        final Map<Integer, TrackedParticle> entities = new HashMap<>();

        ViewerState(User user) {
            this.user = user;
        }

        /**
         * Drops every tracked particle that is not in {@code aliveIds} and
         * returns the entity ids to destroy (empty array when nothing died).
         */
        int[] retainAlive(Set<Integer> aliveIds) {
            if (entities.isEmpty()) {
                return EMPTY_IDS;
            }
            int[] dead = new int[entities.size()];
            int count = 0;
            Iterator<TrackedParticle> it = entities.values().iterator();
            while (it.hasNext()) {
                TrackedParticle tracked = it.next();
                if (!aliveIds.contains(tracked.particleId)) {
                    dead[count++] = tracked.entityId;
                    it.remove();
                }
            }
            return count == dead.length ? dead : java.util.Arrays.copyOf(dead, count);
        }

        private static final int[] EMPTY_IDS = new int[0];
    }

    /** A fake display entity plus the last values pushed to the client. */
    private static final class TrackedParticle {
        final int particleId;
        final int entityId;
        int colourArgb;
        float scaleX, scaleY, scaleZ;

        TrackedParticle(int particleId, int entityId, int colourArgb,
                        float scaleX, float scaleY, float scaleZ) {
            this.particleId = particleId;
            this.entityId = entityId;
            this.colourArgb = colourArgb;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }
    }
}
