package com.csl.moparticles.display;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Thin layer over PacketEvents that spawns, animates and removes purely
 * client-side Item Display entities using Spawn Entity, Set Entity Data,
 * Teleport Entity and Remove Entities packets (see the Minecraft wiki,
 * "Java Edition protocol"). PacketEvents encodes each packet for the viewer's
 * exact client version, so one code path serves 1.21.5 through 26.3.
 *
 * <p>All methods only <b>write</b> packets to the player's channel; the caller
 * must call {@link #flush} once per tick to actually send them. Batching
 * hundreds of writes into a single flush is dramatically cheaper than one
 * send per packet.</p>
 */
public final class DisplayEntityPackets {

    // ------------------------------------------------------------------
    // Display entity metadata indexes.
    //
    // Verified against the actual game code (net.minecraft.world.entity.Display
    // #defineSynchedData auto-assigns indexes sequentially after the 8 shared
    // Entity slots). The order below is identical in 1.21.11 and 26.3:
    //   8  pos/rot interpolation duration (teleport smoothing)
    //   9  transform interpolation start delta ticks ("delay")
    //   10 transform interpolation duration (translation/scale/rotation)
    //   11 translation, 12 scale, 13 right rotation, 14 left rotation,
    //   15 billboard, 16 brightness, 17 view range, 18 shadow radius,
    //   19 shadow strength, 20 width, 21 height, 22 glow color override,
    //   23 item stack - ITEM_STACK serializer. NOT "optional item stack":
    //      that serializer was removed in 1.21.5 and PacketEvents maps it to
    //      id -1, which hard-crashes clients ("Unknown serializer type -1")!
    //   24 item display mode
    // ------------------------------------------------------------------
    /** VarInt - how long position/rotation (teleport) changes interpolate. */
    public static final int DATA_POSITION_ROTATION_INTERPOLATION_DURATION = 8;
    /** VarInt - ticks to wait before starting transformation interpolation. */
    public static final int DATA_INTERPOLATION_DELAY = 9;
    /** VarInt - how long transformation (translation/scale/rotation) changes take. */
    public static final int DATA_TRANSFORM_INTERPOLATION_DURATION = 10;
    /** Vector3f - transformation translation. */
    public static final int DATA_TRANSLATION = 11;
    /** Vector3f - transformation scale. */
    public static final int DATA_SCALE = 12;
    /** Quaternion - transformation right rotation. */
    public static final int DATA_ROTATION_RIGHT = 13;
    /** Quaternion - transformation left rotation. */
    public static final int DATA_ROTATION_LEFT = 14;
    /** Byte - billboard constraints (0=FIXED, 1=VERTICAL, 2=HORIZONTAL, 3=CENTER). */
    public static final int DATA_BILLBOARD_CONSTRAINTS = 15;
    /** VarInt - brightness override (blockLight {@code << 4 | skyLight << 20}), -1 = none. */
    public static final int DATA_BRIGHTNESS_OVERRIDE = 16;
    /** Float - view range, multiplied by the client's entity render distance. */
    public static final int DATA_VIEW_RANGE = 17;
    /** Float - shadow radius. */
    public static final int DATA_SHADOW_RADIUS = 18;
    /** Float - shadow strength. */
    public static final int DATA_SHADOW_STRENGTH = 19;
    /** Float - culling box width (0 = no manual culling box). */
    public static final int DATA_WIDTH = 20;
    /** Float - culling box height. */
    public static final int DATA_HEIGHT = 21;
    /** VarInt - glow color override, -1 = none (1.21.2+). */
    public static final int DATA_GLOW_COLOR_OVERRIDE = 22;
    /** ItemStack - Item Display: the displayed item (plain ITEM_STACK serializer). */
    public static final int DATA_ITEM_DISPLAY_ITEM = 23;
    /** Byte - Item Display: the transform to render the item with (0 = NONE). */
    public static final int DATA_ITEM_DISPLAY_TYPE = 24;

    public static final byte BILLBOARD_CENTER = 3;
    public static final byte ITEM_DISPLAY_TRANSFORM_NONE = 0;
    /** {@code 15 << 4 | 15 << 20} - maximum block & sky light. */
    public static final int FULL_BRIGHTNESS = (15 << 4) | (15 << 20);

    private static final Quaternion4f IDENTITY = new Quaternion4f(0f, 0f, 0f, 1f);
    private static final Logger LOGGER = Logger.getLogger("MoParticles");

    private DisplayEntityPackets() {
    }

    /**
     * Allocates a unique entity id from the server's own entity id counter,
     * guaranteeing it will never collide with a real entity.
     */
    public static int allocateEntityId() {
        return SpigotReflectionUtil.generateEntityId();
    }

    /**
     * Spawns a client-side Item Display with full animation-ready metadata.
     *
     * @param user                 the viewer's PacketEvents user
     * @param transformInterpTicks ticks transformation changes interpolate over
     * @param positionInterpTicks  ticks teleports interpolate over
     */
    public static void spawnItemDisplay(User user, int entityId,
                                        double x, double y, double z,
                                        ItemStack item,
                                        float sx, float sy, float sz,
                                        int transformInterpTicks, int positionInterpTicks,
                                        float viewRange) {
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId, UUID.randomUUID(), EntityTypes.ITEM_DISPLAY,
                new Location(x, y, z, 0.0f, 0.0f), 0.0f, 0, null);
        user.writePacket(spawn);

        List<EntityData<?>> data = new ArrayList<>(16);
        add(data, user, DATA_POSITION_ROTATION_INTERPOLATION_DURATION, EntityDataTypes.INT, positionInterpTicks);
        add(data, user, DATA_INTERPOLATION_DELAY, EntityDataTypes.INT, 0);
        add(data, user, DATA_TRANSFORM_INTERPOLATION_DURATION, EntityDataTypes.INT, transformInterpTicks);
        add(data, user, DATA_TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f());
        add(data, user, DATA_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(sx, sy, sz));
        add(data, user, DATA_ROTATION_RIGHT, EntityDataTypes.QUATERNION, IDENTITY);
        add(data, user, DATA_ROTATION_LEFT, EntityDataTypes.QUATERNION, IDENTITY);
        add(data, user, DATA_BILLBOARD_CONSTRAINTS, EntityDataTypes.BYTE, BILLBOARD_CENTER);
        add(data, user, DATA_BRIGHTNESS_OVERRIDE, EntityDataTypes.INT, FULL_BRIGHTNESS);
        add(data, user, DATA_VIEW_RANGE, EntityDataTypes.FLOAT, viewRange);
        add(data, user, DATA_SHADOW_RADIUS, EntityDataTypes.FLOAT, 0.0f);
        add(data, user, DATA_SHADOW_STRENGTH, EntityDataTypes.FLOAT, 0.0f);
        add(data, user, DATA_WIDTH, EntityDataTypes.FLOAT, 0.0f);
        add(data, user, DATA_HEIGHT, EntityDataTypes.FLOAT, 0.0f);
        add(data, user, DATA_GLOW_COLOR_OVERRIDE, EntityDataTypes.INT, -1);
        add(data, user, DATA_ITEM_DISPLAY_ITEM, EntityDataTypes.ITEMSTACK, item);
        add(data, user, DATA_ITEM_DISPLAY_TYPE, EntityDataTypes.BYTE, ITEM_DISPLAY_TRANSFORM_NONE);
        writeMetadata(user, entityId, data);
    }

    /**
     * Moves the fake display. The client interpolates the move over the
     * "position/rotation interpolation duration" configured at spawn time,
     * which turns the 20 ticks-per-second animation into smooth motion.
     */
    public static void teleport(User user, int entityId, double x, double y, double z) {
        user.writePacket(new WrapperPlayServerEntityTeleport(entityId, new Location(x, y, z, 0.0f, 0.0f), true));
    }

    /**
     * Updates the animated properties of an already spawned display.
     * Only properties flagged as changed are included in the packet.
     */
    public static void update(User user, int entityId,
                              float sx, float sy, float sz, boolean scaleChanged,
                              ItemStack item, boolean itemChanged) {
        if (!scaleChanged && !itemChanged) return;
        List<EntityData<?>> data = new ArrayList<>(2);
        if (scaleChanged) {
            add(data, user, DATA_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(sx, sy, sz));
        }
        if (itemChanged) {
            add(data, user, DATA_ITEM_DISPLAY_ITEM, EntityDataTypes.ITEMSTACK, item);
        }
        writeMetadata(user, entityId, data);
    }

    /** Removes one or more fake entities with a single packet. */
    public static void destroy(User user, int... entityIds) {
        if (entityIds == null || entityIds.length == 0) return;
        user.writePacket(new WrapperPlayServerDestroyEntities(entityIds));
    }

    /** Sends everything written since the last flush in one go. */
    public static void flush(User user) {
        user.flushPackets();
    }

    /**
     * Converts a Bukkit ItemStack into the PacketEvents representation,
     * preserving every item component (item model, custom model data tint, ...).
     */
    public static ItemStack toPacketItemStack(org.bukkit.inventory.ItemStack bukkitItem) {
        return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
    }

    /**
     * Resolves a viewer's PacketEvents user once. Returns null when the
     * player is not (yet) injected - callers should skip such viewers.
     */
    public static User userOf(Player viewer) {
        return PacketEvents.getAPI().getPlayerManager().getUser(viewer);
    }

    /**
     * Appends a metadata entry only when PacketEvents can encode it for the
     * viewer's client version. PacketEvents resolves unmapped serializer types
     * to id -1, which the receiving client treats as a fatal protocol error
     * ("Unknown serializer type -1") and disconnects - so those entries are
     * dropped (and reported) instead of ever being written to the wire.
     */
    private static <T> void add(List<EntityData<?>> out, User user, int index, EntityDataType<T> type, T value) {
        if (type.getId(user.getClientVersion()) < 0) {
            LOGGER.warning("Skipping entity data " + type.getName() + " (index " + index
                    + "): no serializer id for client version " + user.getClientVersion());
            return;
        }
        out.add(new EntityData<>(index, type, value));
    }

    private static void writeMetadata(User user, int entityId, List<EntityData<?>> data) {
        if (data.isEmpty()) return;
        user.writePacket(new WrapperPlayServerEntityMetadata(entityId, data));
    }
}
