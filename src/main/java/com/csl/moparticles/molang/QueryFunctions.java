package com.csl.moparticles.molang;

import java.util.List;

/**
 * Built-in MoLang query.* functions used by the particle engine.
 *
 * These are the read-only runtime queries that particle components refer to.
 * Values are populated by the animation player and read back
 * during expression evaluation.
 *
 */
public final class QueryFunctions {

    public static double call(String name, List<Double> args, MolangEngine.Scope scope) {
        switch (name) {
            // ----- Time -----
            case "anim_time":      return scope.query.getOrDefault("anim_time", 0.0);
            case "life_time":      return scope.query.getOrDefault("life_time", 0.0);
            case "particle_age":   return scope.query.getOrDefault("particle_age", 0.0);
            case "particle_lifetime": return scope.query.getOrDefault("particle_lifetime", 1.0);
            case "emitter_age":    return scope.query.getOrDefault("emitter_age", 0.0);
            case "emitter_lifetime": return scope.query.getOrDefault("emitter_lifetime", 1.0);
            case "emitter_random_1": return scope.query.getOrDefault("emitter_random_1", 0.0);
            case "emitter_random_2": return scope.query.getOrDefault("emitter_random_2", 0.0);
            case "emitter_random_3": return scope.query.getOrDefault("emitter_random_3", 0.0);
            case "emitter_random_4": return scope.query.getOrDefault("emitter_random_4", 0.0);

            // ----- States -----
            case "particle_random_1": return scope.query.getOrDefault("particle_random_1", 0.0);
            case "particle_random_2": return scope.query.getOrDefault("particle_random_2", 0.0);
            case "particle_random_3": return scope.query.getOrDefault("particle_random_3", 0.0);
            case "particle_random_4": return scope.query.getOrDefault("particle_random_4", 0.0);

            // ----- Environmental -----
            case "is_in_water":     return scope.query.getOrDefault("is_in_water", 0.0);
            case "is_on_ground":    return scope.query.getOrDefault("is_on_ground", 0.0);
            case "is_alive":        return scope.query.getOrDefault("is_alive", 1.0);
            case "yaw":             return scope.query.getOrDefault("yaw", 0.0);
            case "pitch":           return scope.query.getOrDefault("pitch", 0.0);
            case "position_x":      return scope.query.getOrDefault("position_x", 0.0);
            case "position_y":      return scope.query.getOrDefault("position_y", 0.0);
            case "position_z":      return scope.query.getOrDefault("position_z", 0.0);

            // ----- Alises (Old) -----
            case "ParticleRandom1": return scope.query.getOrDefault("particle_random_1", 0.0);
            case "ParticleRandom2": return scope.query.getOrDefault("particle_random_2", 0.0);
            case "ParticleRandom3": return scope.query.getOrDefault("particle_random_3", 0.0);
            case "ParticleRandom4": return scope.query.getOrDefault("particle_random_4", 0.0);
            case "ParticleAge":     return scope.query.getOrDefault("particle_age", 0.0);
            case "ParticleLifetime": return scope.query.getOrDefault("particle_lifetime", 1.0);
            case "EmitterAge":      return scope.query.getOrDefault("emitter_age", 0.0);
            case "EmitterLifetime": return scope.query.getOrDefault("emitter_lifetime", 1.0);

            default: return 0.0;
        }
    }
}
