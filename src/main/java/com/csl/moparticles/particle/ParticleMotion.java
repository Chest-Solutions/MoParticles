package com.csl.moparticles.particle;

import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.molang.MolangEngine.Scope;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reads minecraft:particle_motion_dynamic / particle_motion_parametric
 * and provides the per-tick acceleration and drag for integration.
 */
public final class ParticleMotion {

    public final String accelX, accelY, accelZ;
    public final String drag;
    public final MolangEngine molang;

    public ParticleMotion(MolangEngine molang, String accelX, String accelY, String accelZ, String drag) {
        this.molang = molang;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.drag = drag;
    }

    public static ParticleMotion parse(JsonObject components, MolangEngine molang) {
        String ax = "0", ay = "0", az = "0", d = "0";
        if (components.has("minecraft:particle_motion_dynamic")) {
            JsonObject o = components.getAsJsonObject("minecraft:particle_motion_dynamic");
            JsonElement accel = o.get("linear_acceleration");
            if (accel != null && !accel.isJsonNull()) {
                if (accel.isJsonArray()) {
                    JsonArray a = accel.getAsJsonArray();
                    ax = arrayExpr(a, 0, "0");
                    ay = arrayExpr(a, 1, "0");
                    az = arrayExpr(a, 2, "0");
                } else if (accel.isJsonPrimitive() && accel.getAsJsonPrimitive().isNumber()) {
                    ay = String.valueOf(accel.getAsDouble());
                } else if (accel.isJsonPrimitive()) {
                    String raw = accel.getAsString();
                    if (raw.startsWith("[") && raw.endsWith("]")) {
                        try {
                            var v = molang.evalVector(raw, new Scope(), 3);
                            ax = String.valueOf(v.get(0));
                            ay = String.valueOf(v.get(1));
                            az = String.valueOf(v.get(2));
                        } catch (Exception ignored) {
                        }
                    } else {
                        ay = raw;
                    }
                }
            }
            d = expr(o.get("linear_drag_coefficient"), "0");
        } else if (components.has("minecraft:particle_motion_parametric")) {
            d = "0";
        }
        return new ParticleMotion(molang, ax, ay, az, d);
    }

    public double[] acceleration(Scope s, MolangEngine m) {
        return new double[] { m.eval(accelX, s).value, m.eval(accelY, s).value, m.eval(accelZ, s).value };
    }

    public double[] drag(Scope s) {
        double v = 0;
        try { v = Double.parseDouble(drag); }
        catch (Exception ignored) {
            try { v = molang.eval(drag, s).value; } catch (Exception ignored2) { v = 0; }
        }
        return new double[] { v };
    }

    private static String expr(JsonElement e, String def) {
        if (e == null || e.isJsonNull()) return def;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return String.valueOf(e.getAsDouble());
        if (e.isJsonPrimitive()) return e.getAsString();
        return def;
    }

    private static String arrayExpr(JsonArray a, int index, String def) {
        if (a == null || index >= a.size()) return def;
        JsonElement e = a.get(index);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return String.valueOf(e.getAsDouble());
        if (e.isJsonPrimitive()) return e.getAsString();
        return def;
    }
}
