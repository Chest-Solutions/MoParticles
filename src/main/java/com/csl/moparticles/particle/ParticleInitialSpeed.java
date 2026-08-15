package com.csl.moparticles.particle;

import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.molang.MolangEngine.Scope;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the minecraft:particle_initial_speed component (or a number)
 * and produces a 3-vector of base speeds.
 */
public final class ParticleInitialSpeed {

    public final String src;          // raw molang or number
    public final boolean isMolang;

    public ParticleInitialSpeed(String src, boolean isMolang) {
        this.src = src;
        this.isMolang = isMolang;
    }

    public static ParticleInitialSpeed parse(JsonObject components, MolangEngine molang) {
        if (components.has("minecraft:particle_initial_speed")) {
            JsonElement e = components.get("minecraft:particle_initial_speed");
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                return new ParticleInitialSpeed(String.valueOf(e.getAsDouble()), false);
            }
            if (e.isJsonPrimitive()) {
                return new ParticleInitialSpeed(e.getAsString(), true);
            }
        }
        if (components.has("minecraft:particle_initial_velocity")) {
            return new ParticleInitialSpeed(components.get("minecraft:particle_initial_velocity").toString(), true);
        }
        return new ParticleInitialSpeed("0", false);
    }

    public List<Double> eval(Scope scope, MolangEngine molang) {
        if (src == null) {
            List<Double> v = new ArrayList<>(); v.add(0.0); v.add(0.0); v.add(0.0); return v;
        }
        String s = src.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            return molang.evalVector(s, scope, 3);
        }
        try {
            double d = Double.parseDouble(s);
            List<Double> v = new ArrayList<>(); v.add(d); v.add(d); v.add(d); return v;
        } catch (NumberFormatException ignored) { }
        return molang.evalVector(s, scope, 3);
    }
}
