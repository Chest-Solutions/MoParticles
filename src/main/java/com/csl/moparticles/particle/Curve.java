package com.csl.moparticles.particle;

import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.molang.MolangEngine.Scope;
import com.google.gson.JsonElement;

/**
 * Interpolation curve (linear / bezier / catmull_rom).
 *
 * The output of the curve at a given input is written to a MoLang
 * variable of the same name as the curve.
 */
public final class Curve {

    public enum Type { LINEAR, BEZIER, CATMULL_ROM }

    public final String name;
    public final Type type;
    public final double[] nodes;
    public final MolangExpr input;
    public final MolangExpr horizontalRange;

    public Curve(String name, Type type, double[] nodes, MolangExpr input, MolangExpr horizontalRange) {
        this.name = name;
        this.type = type;
        this.nodes = nodes;
        this.input = input;
        this.horizontalRange = horizontalRange;
    }

    public static Curve parse(String name, com.google.gson.JsonObject o, MolangEngine molang) {
        String typeStr = ParticleParser.optString(o, "type", "linear").toLowerCase();
        Type t;
        switch (typeStr) {
            case "bezier":
                t = Type.BEZIER;
                break;
            case "catmull_rom":
                t = Type.CATMULL_ROM;
                break;
            default:
                t = Type.LINEAR;
                break;
        }

        double[] nodes = new double[0];
        if (o.has("nodes") && o.get("nodes").isJsonArray()) {
            com.google.gson.JsonArray a = o.getAsJsonArray("nodes");
            nodes = new double[a.size()];
            for (int i = 0; i < a.size(); i++) {
                JsonElement n = a.get(i);
                if (n.isJsonPrimitive() && n.getAsJsonPrimitive().isNumber()) {
                    nodes[i] = n.getAsDouble();
                } else {
                    nodes[i] = molang.evalLiteral(n.getAsString());
                }
            }
        }
        if (nodes.length == 0) {
            nodes = new double[] {0.0};
        }

        MolangExpr input = parseExpr(o.get("input"), molang, "0");
        MolangExpr horizontalRange = parseExpr(o.get("horizontal_range"), molang, "1");
        return new Curve(name, t, nodes, input, horizontalRange);
    }

    private static MolangExpr parseExpr(JsonElement e, MolangEngine molang, String def) {
        if (e == null || e.isJsonNull()) {
            return new MolangExpr(def, molang, true);
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return new MolangExpr(String.valueOf(e.getAsDouble()), molang, true);
        }
        if (e.isJsonPrimitive()) {
            return new MolangExpr(e.getAsString(), molang, false);
        }
        return new MolangExpr(def, molang, true);
    }

    /**
     * Compute the curve value in the given MoLang scope.
     */
    public double evaluate(Scope scope) {
        if (nodes == null || nodes.length == 0) return 0.0;

        double inputValue = input == null ? 0.0 : input.eval(scope);
        double hr = horizontalRange == null ? 1.0 : horizontalRange.eval(scope);
        if (Math.abs(hr) < 1.0e-9) hr = 1.0;
        double t = clamp01(inputValue / hr);

        switch (type) {
            case LINEAR:
                return linear(t, nodes);
            case BEZIER:
                return bezier(t, nodes);
            case CATMULL_ROM:
                return catmullRom(t, nodes);
            default:
                return 0.0;
        }
    }

    private static double linear(double t, double[] nodes) {
        if (nodes.length == 1) return nodes[0];
        double pos = t * (nodes.length - 1);
        int i = (int) Math.floor(pos);
        int j = Math.min(nodes.length - 1, i + 1);
        double f = pos - i;
        return nodes[i] + (nodes[j] - nodes[i]) * f;
    }

    private static double bezier(double t, double[] p) {
        double[] q = new double[4];
        int copy = Math.min(4, p.length);
        System.arraycopy(p, 0, q, 0, copy);
        for (int i = copy; i < 4; i++) q[i] = q[Math.max(0, i - 1)];
        double u = 1.0 - t;
        return u * u * u * q[0]
                + 3 * u * u * t * q[1]
                + 3 * u * t * t * q[2]
                + t * t * t * q[3];
    }

    private static double catmullRom(double t, double[] p) {
        if (p.length < 4) {
            return linear(t, p);
        }
        int segments = p.length - 3;
        double scaled = t * segments;
        int seg = Math.min(segments - 1, Math.max(0, (int) Math.floor(scaled)));
        double localT = scaled - seg;
        if (seg == segments - 1) localT = Math.min(1.0, localT);

        double p0 = p[seg];
        double p1 = p[seg + 1];
        double p2 = p[seg + 2];
        double p3 = p[seg + 3];
        double t2 = localT * localT;
        double t3 = t2 * localT;
        return 0.5 * ((2 * p1)
                + (-p0 + p2) * localT
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /**
     * Helper: wraps a MoLang string.
     */
    public static final class MolangExpr {
        public final String src;
        public final MolangEngine engine;
        public final boolean literal;

        public MolangExpr(String src, MolangEngine engine, boolean literal) {
            this.src = src;
            this.engine = engine;
            this.literal = literal;
        }

        public double eval(Scope s) {
            return literal ? engine.evalLiteral(src) : engine.eval(src, s).value;
        }
    }
}
