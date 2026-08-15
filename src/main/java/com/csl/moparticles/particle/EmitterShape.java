package com.csl.moparticles.particle;

import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.molang.MolangEngine.Scope;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Reads the emitter shape component (point, sphere, box, disc, custom...)
 * and produces a new {@link ParticleSimulator.LiveParticle} at the
 * calculated spawn position with the calculated initial velocity.
 */
public final class EmitterShape {

    public enum Kind { POINT, SPHERE, BOX, DISC, CUSTOM, ENTITY_AABB }

    public final Kind kind;
    public final JsonObject data;

    public EmitterShape(Kind kind, JsonObject data) {
        this.kind = kind;
        this.data = data;
    }

    public static EmitterShape parse(JsonObject components, MolangEngine molang) {
        if (components.has("minecraft:emitter_shape_sphere")) {
            return new EmitterShape(Kind.SPHERE, components.getAsJsonObject("minecraft:emitter_shape_sphere"));
        }
        if (components.has("minecraft:emitter_shape_box")) {
            return new EmitterShape(Kind.BOX, components.getAsJsonObject("minecraft:emitter_shape_box"));
        }
        if (components.has("minecraft:emitter_shape_disc")) {
            return new EmitterShape(Kind.DISC, components.getAsJsonObject("minecraft:emitter_shape_disc"));
        }
        if (components.has("minecraft:emitter_shape_point")) {
            return new EmitterShape(Kind.POINT, components.getAsJsonObject("minecraft:emitter_shape_point"));
        }
        if (components.has("minecraft:emitter_shape_custom")) {
            return new EmitterShape(Kind.CUSTOM, components.getAsJsonObject("minecraft:emitter_shape_custom"));
        }
        if (components.has("minecraft:emitter_shape_entity_aabb")) {
            return new EmitterShape(Kind.ENTITY_AABB, components.getAsJsonObject("minecraft:emitter_shape_entity_aabb"));
        }
        return new EmitterShape(Kind.POINT, new JsonObject());
    }

    public ParticleSimulator.LiveParticle spawn(Scope scope,
                                                ParticleInitialSpeed initialSpeed,
                                                ParticleLifetime lifetime,
                                                ParticleMotion motion,
                                                ParticleAppearance appearance,
                                                MolangEngine molang,
                                                int id) {
        double x = 0, y = 0, z = 0;
        double vx = 0, vy = 0, vz = 0;

        double r1 = Math.random();
        double r2 = Math.random();
        double r3 = Math.random();
        double r4 = Math.random();
        Scope spawnScope = scope == null ? new Scope() : scope.copy();
        spawnScope.query.put("particle_age", 0.0);
        spawnScope.query.put("particle_random_1", r1);
        spawnScope.query.put("particle_random_2", r2);
        spawnScope.query.put("particle_random_3", r3);
        spawnScope.query.put("particle_random_4", r4);
        spawnScope.variable.put("ParticleAge", 0.0);
        spawnScope.variable.put("ParticleRandom1", r1);
        spawnScope.variable.put("ParticleRandom2", r2);
        spawnScope.variable.put("ParticleRandom3", r3);
        spawnScope.variable.put("ParticleRandom4", r4);
        spawnScope.variable.put("particle_age", 0.0);
        spawnScope.variable.put("particle_random_1", r1);
        spawnScope.variable.put("particle_random_2", r2);
        spawnScope.variable.put("particle_random_3", r3);
        spawnScope.variable.put("particle_random_4", r4);

        switch (kind) {
            case POINT: {
                if (data.has("offset")) {
                    List<Double> off = ParticleParser.optVector(data, "offset", 0, 0, 0, spawnScope, molang);
                    x = off.get(0);
                    y = off.get(1);
                    z = off.get(2);
                }
                break;
            }
            case SPHERE: {
                double radius = ParticleParser.optDouble(data, "radius", 0.5);
                // uniform direction on a unit sphere
                double u = Math.random() * 2 - 1;
                double theta = Math.random() * Math.PI * 2;
                double r = Math.sqrt(1 - u * u);
                double dx = r * Math.cos(theta) * radius;
                double dy = u * radius;
                double dz = r * Math.sin(theta) * radius;
                x = dx; y = dy; z = dz;
                break;
            }
            case BOX: {
                JsonArray h = data.has("half_dimensions") ? data.getAsJsonArray("half_dimensions") : null;
                if (h != null && h.size() == 3) {
                    x = (Math.random() * 2 - 1) * h.get(0).getAsDouble();
                    y = (Math.random() * 2 - 1) * h.get(1).getAsDouble();
                    z = (Math.random() * 2 - 1) * h.get(2).getAsDouble();
                }
                break;
            }
            case DISC: {
                double r = ParticleParser.optDouble(data, "radius", 0.5);
                double theta = Math.random() * Math.PI * 2;
                x = Math.cos(theta) * r;
                z = Math.sin(theta) * r;
                // offset along plane normal
                JsonArray n = data.has("plane_normal") ? data.getAsJsonArray("plane_normal") : null;
                if (n != null && n.size() == 3) {
                    x *= n.get(0).getAsDouble();
                    z *= n.get(2).getAsDouble();
                }
                break;
            }
            case ENTITY_AABB: {
                x = (Math.random() * 2 - 1) * 0.5;
                y = (Math.random() * 2 - 1) * 0.5;
                z = (Math.random() * 2 - 1) * 0.5;
                break;
            }
            case CUSTOM: {
                if (data.has("offset") && data.get("offset").isJsonArray()) {
                    List<Double> off = ParticleParser.optVector(data, "offset", 0, 0, 0, spawnScope, molang);
                    x = off.get(0); y = off.get(1); z = off.get(2);
                }
                if (data.has("direction") && data.get("direction").isJsonArray()) {
                    List<Double> dir = ParticleParser.optVector(data, "direction", 0, 0, 0, spawnScope, molang);
                    vx = dir.get(0); vy = dir.get(1); vz = dir.get(2);
                }
                break;
            }
        }

        // initial speed (molang evaluated)
        List<Double> speed = initialSpeed.eval(spawnScope, molang);
        // direction override from shape
        List<Double> dir = ParticleParser.optVector(data, "direction", 0, 1, 0, spawnScope, molang);
        vx = dir.get(0) * speed.get(0);
        vy = dir.get(1) * speed.get(1);
        vz = dir.get(2) * speed.get(2);

        double life = lifetime.eval(spawnScope, molang);
        ParticleSimulator.LiveParticle p = new ParticleSimulator.LiveParticle(
                id, x, y, z, vx, vy, vz, life,
                r1, r2, r3, r4
        );
        p.colour = appearance.evalColour(spawnScope, molang);
        p.sizeX = (float) Math.max(0.01, appearance.evalSizeX(spawnScope, molang));
        p.sizeY = (float) Math.max(0.01, appearance.evalSizeY(spawnScope, molang));
        return p;
    }
}
