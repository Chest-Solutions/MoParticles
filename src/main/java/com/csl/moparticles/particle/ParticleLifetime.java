package com.csl.moparticles.particle;

import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.molang.MolangEngine.Scope;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reads the particle lifetime component (minecraft:particle_lifetime_expression
 * or minecraft:particle_lifetime_once) and returns the max lifetime in
 * seconds.
 */
public final class ParticleLifetime {

    public final String src;
    public final boolean isMolang;

    public ParticleLifetime(String src, boolean isMolang) {
        this.src = src;
        this.isMolang = isMolang;
    }

    public static ParticleLifetime parse(JsonObject components, MolangEngine molang) {
        if (components.has("minecraft:particle_lifetime_expression")) {
            JsonObject o = components.getAsJsonObject("minecraft:particle_lifetime_expression");
            JsonElement m = o.get("max_lifetime");
            if (m != null) {
                if (m.isJsonPrimitive() && m.getAsJsonPrimitive().isNumber()) {
                    return new ParticleLifetime(String.valueOf(m.getAsDouble()), false);
                }
                return new ParticleLifetime(m.getAsString(), true);
            }
        }
        if (components.has("minecraft:particle_lifetime_once")) {
            JsonObject o = components.getAsJsonObject("minecraft:particle_lifetime_once");
            double t = ParticleParser.optDouble(o, "active_time", 1.0);
            return new ParticleLifetime(String.valueOf(t), false);
        }
        return new ParticleLifetime("1.0", false);
    }

    public double eval(Scope scope, MolangEngine molang) {
        if (!isMolang) {
            try { return Double.parseDouble(src); } catch (NumberFormatException e) { return 1.0; }
        }
        double v = molang.eval(src, scope).value;
        return v <= 0 ? 0.05 : v;
    }
}
