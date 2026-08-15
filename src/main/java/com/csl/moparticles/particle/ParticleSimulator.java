package com.csl.moparticles.particle;

import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.molang.MolangEngine.Scope;
import com.google.gson.JsonObject;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Walks a parsed particle effect through time and produces baked frames.
 */
public final class ParticleSimulator {

    private final MolangEngine molang;
    private final Map<String, Curve> curves;
    private final JsonObject components;
    final ParticleDefinition.Builder out;
    private final Random rng = new Random();
    private final Map<String, Double> emitterVariables = new HashMap<>();

    public ParticleSimulator(MolangEngine molang, Map<String, Curve> curves,
                             JsonObject components, ParticleDefinition.Builder out) {
        this.molang = molang;
        this.curves = curves;
        this.components = components;
        this.out = out;
    }

    public void bake() {
        runEmitterInitialization();

        double emitterLifetime = 2.0;
        if (components.has("minecraft:emitter_lifetime_once")) {
            JsonObject o = components.getAsJsonObject("minecraft:emitter_lifetime_once");
            emitterLifetime = ParticleParser.optDouble(o, "active_time", emitterLifetime);
        } else if (components.has("minecraft:emitter_lifetime_looping")) {
            JsonObject o = components.getAsJsonObject("minecraft:emitter_lifetime_looping");
            emitterLifetime = ParticleParser.optDouble(o, "active_time", emitterLifetime);
        } else if (components.has("minecraft:emitter_lifetime_expression")) {
            emitterLifetime = 2.0;
        }

        double spawnRate = 0;
        int maxParticles = 200;
        boolean instant = false;
        int instantNum = 0;
        if (components.has("minecraft:emitter_rate_steady")) {
            JsonObject o = components.getAsJsonObject("minecraft:emitter_rate_steady");
            spawnRate = optMolangDouble(o, "spawn_rate", 0, molang, null);
            maxParticles = ParticleParser.optInt(o, "max_particles", 200);
        } else if (components.has("minecraft:emitter_rate_instant")) {
            JsonObject o = components.getAsJsonObject("minecraft:emitter_rate_instant");
            instant = true;
            instantNum = (int) optMolangDouble(o, "num_particles", 0, molang, null);
        } else if (!components.has("minecraft:emitter_rate_manual")) {
            instant = true;
            instantNum = 1;
        }

        EmitterShape shape = EmitterShape.parse(components, molang);
        ParticleInitialSpeed initialSpeed = ParticleInitialSpeed.parse(components, molang);
        ParticleMotion motion = ParticleMotion.parse(components, molang);
        ParticleAppearance appearance = ParticleAppearance.parse(components, molang);
        ParticleLifetime lifetime = ParticleLifetime.parse(components, molang);

        double fps = 20.0;
        double totalSeconds = Math.max(0.05, emitterLifetime);
        out.duration = totalSeconds;
        out.fps = fps;

        appearance.resolveJavaParticles(out);

        List<LiveParticle> live = new ArrayList<>();
        int nextParticleId = 0;
        double spawnAccumulator = 0.0;
        int maxFramesHard = (int) Math.ceil((totalSeconds + 10.0) * fps);

        for (int frame = 0; frame <= maxFramesHard; frame++) {
            double t = frame / fps;
            boolean emitterActive = t <= totalSeconds + 1.0e-9;

            Scope emitterScope = baseScope(t, totalSeconds, null, null);
            applyCurves(emitterScope);

            if (emitterActive) {
                if (instant && frame == 0) {
                    for (int i = 0; i < instantNum; i++) {
                        LiveParticle p = shape.spawn(emitterScope, initialSpeed, lifetime, motion, appearance, molang, nextParticleId++);
                        if (p != null) live.add(p);
                    }
                } else if (spawnRate > 0) {
                    double sr = components.has("minecraft:emitter_rate_steady")
                            ? optMolangDouble(components.getAsJsonObject("minecraft:emitter_rate_steady"),
                            "spawn_rate", spawnRate, molang, emitterScope)
                            : spawnRate;
                    if (sr <= 0) sr = spawnRate;
                    spawnAccumulator += sr / fps;
                    int spawnCount = (int) Math.floor(spawnAccumulator);
                    spawnAccumulator -= spawnCount;
                    for (int i = 0; i < spawnCount && live.size() < maxParticles; i++) {
                        LiveParticle p = shape.spawn(emitterScope, initialSpeed, lifetime, motion, appearance, molang, nextParticleId++);
                        if (p != null) live.add(p);
                    }
                }
            }

            List<LiveParticle> next = new ArrayList<>();
            for (LiveParticle p : live) {
                if (p.step(t, fps, motion, appearance, molang, this)) {
                    next.add(p);
                }
            }
            live = next;

            List<ParticleDefinition.BakedParticle> snap = new ArrayList<>(live.size());
            for (LiveParticle p : live) {
                snap.add(p.snapshot());
            }
            out.frames.add(new ParticleDefinition.BakedFrame(t, snap));

            if (!emitterActive && live.isEmpty()) {
                break;
            }
        }
        out.totalFrames = out.frames.size();
        if (!out.frames.isEmpty()) {
            out.duration = out.frames.get(out.frames.size() - 1).time;
        }
    }

    private void runEmitterInitialization() {
        if (!components.has("minecraft:emitter_initialization")
                || !components.get("minecraft:emitter_initialization").isJsonObject()) {
            return;
        }
        JsonObject init = components.getAsJsonObject("minecraft:emitter_initialization");
        String expr = ParticleParser.optString(init, "creation_expression", null);
        if (expr == null || expr.isBlank()) return;
        Scope scope = new Scope();
        scope.variable.putAll(emitterVariables);
        molang.eval(expr, scope);
        emitterVariables.clear();
        emitterVariables.putAll(scope.variable);
    }

    private static double optMolangDouble(JsonObject o, String key, double def,
                                          MolangEngine molang, Scope scope) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        com.google.gson.JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
        if (e.isJsonPrimitive()) {
            try { return Double.parseDouble(e.getAsString()); } catch (Exception ignored) {}
            Scope use = scope == null ? new Scope() : scope;
            return molang.eval(e.getAsString(), use).value;
        }
        return def;
    }

    public Scope baseScope(double emitterAge, double emitterLifetime,
                           LiveParticle particle, Double particleAge) {
        Scope s = new Scope();
        s.variable.putAll(emitterVariables);
        s.query.put("anim_time", emitterAge);
        s.query.put("emitter_age", emitterAge);
        s.query.put("emitter_lifetime", emitterLifetime);
        s.query.put("emitter_random_1", rng.nextDouble());
        s.query.put("emitter_random_2", rng.nextDouble());
        s.query.put("emitter_random_3", rng.nextDouble());
        s.query.put("emitter_random_4", rng.nextDouble());

        s.variable.put("anim_time", emitterAge);
        s.variable.put("emitter_age", emitterAge);
        s.variable.put("emitter_lifetime", emitterLifetime);
        s.variable.put("emitter_random_1", s.query.get("emitter_random_1"));
        s.variable.put("emitter_random_2", s.query.get("emitter_random_2"));
        s.variable.put("emitter_random_3", s.query.get("emitter_random_3"));
        s.variable.put("emitter_random_4", s.query.get("emitter_random_4"));

        if (particle != null) {
            s.query.put("particle_age", particleAge == null ? 0.0 : particleAge);
            s.query.put("particle_lifetime", particle.maxLifetime);
            s.query.put("particle_random_1", particle.random1);
            s.query.put("particle_random_2", particle.random2);
            s.query.put("particle_random_3", particle.random3);
            s.query.put("particle_random_4", particle.random4);
            s.variable.put("ParticleAge", s.query.get("particle_age"));
            s.variable.put("ParticleLifetime", s.query.get("particle_lifetime"));
            s.variable.put("ParticleRandom1", s.query.get("particle_random_1"));
            s.variable.put("ParticleRandom2", s.query.get("particle_random_2"));
            s.variable.put("ParticleRandom3", s.query.get("particle_random_3"));
            s.variable.put("ParticleRandom4", s.query.get("particle_random_4"));
            s.variable.put("particle_age", s.query.get("particle_age"));
            s.variable.put("particle_lifetime", s.query.get("particle_lifetime"));
            s.variable.put("particle_random_1", s.query.get("particle_random_1"));
            s.variable.put("particle_random_2", s.query.get("particle_random_2"));
            s.variable.put("particle_random_3", s.query.get("particle_random_3"));
            s.variable.put("particle_random_4", s.query.get("particle_random_4"));
        }
        return s;
    }

    public void applyCurves(Scope s) {
        for (Map.Entry<String, Curve> e : curves.entrySet()) {
            Curve c = e.getValue();
            double value = c.evaluate(s);
            String key = e.getKey();
            String normalized = key.startsWith("variable.") ? key.substring("variable.".length()) : key;
            s.variable.put(normalized, value);
            s.variable.put(key, value);
        }
    }

    public static final class LiveParticle {
        public final int id;
        public final double maxLifetime;
        public double age;
        public double x, y, z;
        public double vx, vy, vz;
        public double rotation;
        public double rotationRate;
        public Color colour = Color.WHITE;
        public float sizeX = 0.1f;
        public float sizeY = 0.1f;
        public final double random1, random2, random3, random4;

        public LiveParticle(int id, double x, double y, double z,
                            double vx, double vy, double vz,
                            double maxLifetime,
                            double r1, double r2, double r3, double r4) {
            this.id = id;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.maxLifetime = maxLifetime;
            this.random1 = r1; this.random2 = r2; this.random3 = r3; this.random4 = r4;
        }

        public boolean step(double t, double fps,
                            ParticleMotion motion, ParticleAppearance appearance,
                            MolangEngine molang, ParticleSimulator sim) {
            double dt = 1.0 / fps;
            age += dt;
            if (age >= maxLifetime) return false;

            Scope s = sim.baseScope(t, sim.out.duration, this, age);
            sim.applyCurves(s);

            double[] accel = motion.acceleration(s, molang);
            double[] drag = motion.drag(s);
            vx += accel[0] * dt; vy += accel[1] * dt; vz += accel[2] * dt;
            double dragF = drag[0];
            vx *= (1.0 - dragF * dt);
            vy *= (1.0 - dragF * dt);
            vz *= (1.0 - dragF * dt);
            x += vx * dt; y += vy * dt; z += vz * dt;

            appearance.apply(this, s, molang, t);
            return true;
        }

        public ParticleDefinition.BakedParticle snapshot() {
            return new ParticleDefinition.BakedParticle(
                    id,
                    x, y, z, vx, vy, vz,
                    colour, sizeX, sizeY,
                    0, 0, 1, 1,
                    false, false, false,
                    Material.STONE, null
            );
        }
    }
}
