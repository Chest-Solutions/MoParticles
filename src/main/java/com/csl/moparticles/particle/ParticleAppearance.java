package com.csl.moparticles.particle;

import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.molang.MolangEngine.Scope;
import com.csl.moparticles.particle.ParticleDefinition.BakedParticle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads appearance components and provides size / colour / uv state.
 *
 * Supports both:
 *  minecraft:particle_appearance_billboard.color
 *  minecraft:particle_appearance_tinting.color, including gradients.
 */
public final class ParticleAppearance {

    private enum TintMode { EXPRESSIONS, FIXED, GRADIENT }

    private record GradientStop(double position, Color color) {}

    public final String sizeX, sizeY;
    public final String facingMode;
    public final String rotation;
    public final String rotationRate;
    public final JsonObject raw;
    public final String material;
    public final String texture;
    public final int textureW, textureH;
    public final int uvX, uvY, uvW, uvH;
    public final boolean flipbook;
    public final int flipMax;
    public final double flipFps;
    public final boolean flipLoop;
    public final boolean flipStretch;
    public final int flipStepX, flipStepY;

    private final TintMode tintMode;
    private final String colorR, colorG, colorB, colorA;
    private final Color fixedColor;
    private final String gradientInterpolant;
    private final List<GradientStop> gradientStops;

    private ParticleAppearance(
            JsonObject raw,
            String material,
            String texture,
            String sizeX,
            String sizeY,
            String facingMode,
            String rotation,
            String rotationRate,
            int textureW,
            int textureH,
            int uvX,
            int uvY,
            int uvW,
            int uvH,
            boolean flipbook,
            int flipMax,
            double flipFps,
            boolean flipLoop,
            boolean flipStretch,
            int flipStepX,
            int flipStepY,
            TintMode tintMode,
            String colorR,
            String colorG,
            String colorB,
            String colorA,
            Color fixedColor,
            String gradientInterpolant,
            List<GradientStop> gradientStops
    ) {
        this.raw = raw;
        this.material = material;
        this.texture = texture;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.facingMode = facingMode;
        this.rotation = rotation;
        this.rotationRate = rotationRate;
        this.textureW = textureW;
        this.textureH = textureH;
        this.uvX = uvX;
        this.uvY = uvY;
        this.uvW = uvW;
        this.uvH = uvH;
        this.flipbook = flipbook;
        this.flipMax = flipMax;
        this.flipFps = flipFps;
        this.flipLoop = flipLoop;
        this.flipStretch = flipStretch;
        this.flipStepX = flipStepX;
        this.flipStepY = flipStepY;
        this.tintMode = tintMode;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.colorA = colorA;
        this.fixedColor = fixedColor;
        this.gradientInterpolant = gradientInterpolant;
        this.gradientStops = gradientStops;
    }

    public static ParticleAppearance parse(JsonObject components, MolangEngine molang) {
        JsonObject billboard = components.has("minecraft:particle_appearance_billboard")
                ? components.getAsJsonObject("minecraft:particle_appearance_billboard")
                : new JsonObject();
        JsonObject tinting = components.has("minecraft:particle_appearance_tinting")
                ? components.getAsJsonObject("minecraft:particle_appearance_tinting")
                : null;

        String mat = "particles_alpha";
        String tex = "textures/particle/moparticles/atlas";
        String sizeX = optArrayMolang(billboard, "size", 0, "0.1");
        String sizeY = optArrayMolang(billboard, "size", 1, sizeX);
        String facingMode = optMolang(billboard, "facing_camera_mode", "lookat_xyz");
        String rotation = optMolang(billboard, "rotation", "0");
        String rotationRate = optMolang(billboard, "rotation_rate", "0");

        int texW = 1, texH = 1, uvX = 0, uvY = 0, uvW = 1, uvH = 1;
        boolean flipbook = false;
        int flipMax = 1;
        double flipFps = 1;
        boolean flipLoop = true;
        boolean flipStretch = false;
        int flipStepX = 0, flipStepY = 0;

        if (billboard.has("uv") && billboard.get("uv").isJsonObject()) {
            JsonObject uv = billboard.getAsJsonObject("uv");
            texW = ParticleParser.optInt(uv, "texture_width", 1);
            texH = ParticleParser.optInt(uv, "texture_height", 1);
            if (uv.has("uv") && uv.get("uv").isJsonArray()) {
                JsonArray a = uv.getAsJsonArray("uv");
                if (a.size() >= 2) {
                    uvX = (int) parseDouble(a.get(0));
                    uvY = (int) parseDouble(a.get(1));
                }
            }
            if (uv.has("uv_size") && uv.get("uv_size").isJsonArray()) {
                JsonArray a = uv.getAsJsonArray("uv_size");
                if (a.size() >= 2) {
                    uvW = (int) parseDouble(a.get(0));
                    uvH = (int) parseDouble(a.get(1));
                }
            }
            if (uv.has("flipbook") && uv.get("flipbook").isJsonObject()) {
                JsonObject f = uv.getAsJsonObject("flipbook");
                flipbook = true;
                flipMax = ParticleParser.optInt(f, "max_frame", 1);
                flipFps = ParticleParser.optDouble(f, "frames_per_second", 1);
                flipLoop = ParticleParser.optBoolean(f, "loop", true);
                flipStretch = ParticleParser.optBoolean(f, "stretch_to_lifetime", false);
                if (f.has("base_UV") && f.get("base_UV").isJsonArray()) {
                    JsonArray b = f.getAsJsonArray("base_UV");
                    if (b.size() >= 2) {
                        uvX = (int) parseDouble(b.get(0));
                        uvY = (int) parseDouble(b.get(1));
                    }
                }
                if (f.has("size_UV") && f.get("size_UV").isJsonArray()) {
                    JsonArray b = f.getAsJsonArray("size_UV");
                    if (b.size() >= 2) {
                        uvW = (int) parseDouble(b.get(0));
                        uvH = (int) parseDouble(b.get(1));
                    }
                }
                if (f.has("step_UV") && f.get("step_UV").isJsonArray()) {
                    JsonArray b = f.getAsJsonArray("step_UV");
                    if (b.size() >= 2) {
                        flipStepX = (int) parseDouble(b.get(0));
                        flipStepY = (int) parseDouble(b.get(1));
                    }
                }
            }
        }

        TintData tintData = parseTintData(billboard, tinting);
        return new ParticleAppearance(
                billboard, mat, tex,
                sizeX, sizeY, facingMode, rotation, rotationRate,
                texW, texH, uvX, uvY, uvW, uvH,
                flipbook, flipMax, flipFps, flipLoop, flipStretch, flipStepX, flipStepY,
                tintData.mode, tintData.colorR, tintData.colorG, tintData.colorB, tintData.colorA,
                tintData.fixedColor, tintData.gradientInterpolant, tintData.gradientStops
        );
    }

    public void resolveJavaParticles(ParticleDefinition.Builder out) {
        out.javaParticles.add(Particle.DUST);
        out.javaParticles.add(Particle.ENTITY_EFFECT);
        out.javaParticles.add(Particle.EFFECT);
        out.hasCustomTexture = true;
    }

    public Color evalColour(Scope s, MolangEngine m) {
        return switch (tintMode) {
            case FIXED -> fixedColor == null ? Color.WHITE : fixedColor;
            case GRADIENT -> evalGradientColour(s, m);
            case EXPRESSIONS -> evalExpressionColour(s, m);
        };
    }

    public double evalSizeX(Scope s, MolangEngine m) {
        return m.eval(sizeX, s).value;
    }

    public double evalSizeY(Scope s, MolangEngine m) {
        return m.eval(sizeY, s).value;
    }

    public void apply(ParticleSimulator.LiveParticle p, Scope s, MolangEngine m, double t) {
        p.colour = evalColour(s, m);
        double sx = Math.max(0.01, m.eval(sizeX, s).value);
        double sy = Math.max(0.01, m.eval(sizeY, s).value);
        p.sizeX = (float) sx;
        p.sizeY = (float) sy;
        try {
            p.rotation = m.eval(rotation, s).value;
            p.rotationRate = m.eval(rotationRate, s).value;
        } catch (Exception ignored) { }
    }

    public BakedParticle snapshot(ParticleSimulator.LiveParticle p) {
        return new BakedParticle(p.id, p.x, p.y, p.z, p.vx, p.vy, p.vz,
                p.colour, p.sizeX, p.sizeY, 0, 0, 1, 1,
                true, false, false, Material.STONE, null);
    }

    private Color evalExpressionColour(Scope s, MolangEngine m) {
        double r = clamp01(m.eval(colorR, s).value);
        double g = clamp01(m.eval(colorG, s).value);
        double b = clamp01(m.eval(colorB, s).value);
        double a = clamp01(m.eval(colorA, s).value);
        return Color.fromARGB((int) (a * 255), (int) (r * 255), (int) (g * 255), (int) (b * 255));
    }

    private Color evalGradientColour(Scope s, MolangEngine m) {
        if (gradientStops == null || gradientStops.isEmpty()) return Color.WHITE;
        double at = gradientInterpolant == null ? 0.0 : m.eval(gradientInterpolant, s).value;
        if (at <= gradientStops.get(0).position) return gradientStops.get(0).color;
        if (at >= gradientStops.get(gradientStops.size() - 1).position) return gradientStops.get(gradientStops.size() - 1).color;
        for (int i = 0; i < gradientStops.size() - 1; i++) {
            GradientStop a = gradientStops.get(i);
            GradientStop b = gradientStops.get(i + 1);
            if (at >= a.position && at <= b.position) {
                double span = Math.max(1.0e-9, b.position - a.position);
                double f = (at - a.position) / span;
                return lerp(a.color, b.color, f);
            }
        }
        return gradientStops.get(gradientStops.size() - 1).color;
    }

    private static TintData parseTintData(JsonObject billboard, JsonObject tinting) {
        if (tinting != null && tinting.has("color")) {
            JsonElement color = tinting.get("color");
            if (color.isJsonObject()) {
                JsonObject obj = color.getAsJsonObject();
                if (obj.has("gradient")) {
                    List<GradientStop> stops = parseGradient(obj.get("gradient"));
                    String interpolant = optMolang(obj, "interpolant", "0");
                    return TintData.gradient(interpolant, stops);
                }
                if (obj.has("r") || obj.has("g") || obj.has("b") || obj.has("a")) {
                    return TintData.expressions(
                            optMolang(obj, "r", "1"),
                            optMolang(obj, "g", "1"),
                            optMolang(obj, "b", "1"),
                            optMolang(obj, "a", "1")
                    );
                }
            }
            if (color.isJsonArray()) {
                JsonArray arr = color.getAsJsonArray();
                if (arr.size() == 3 || arr.size() == 4) {
                    boolean expr = false;
                    for (JsonElement e : arr) {
                        if (!(e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber())) {
                            expr = true;
                            break;
                        }
                    }
                    if (expr) {
                        return TintData.expressions(
                                arr.size() > 0 ? arr.get(0).getAsString() : "1",
                                arr.size() > 1 ? arr.get(1).getAsString() : "1",
                                arr.size() > 2 ? arr.get(2).getAsString() : "1",
                                arr.size() > 3 ? arr.get(3).getAsString() : "1"
                        );
                    }
                    return TintData.fixed(parseColorArray(arr));
                }
            }
            if (color.isJsonPrimitive()) {
                return TintData.fixed(parseColorLiteral(color.getAsString()));
            }
        }

        if (billboard.has("color") && billboard.get("color").isJsonObject()) {
            JsonObject c = billboard.getAsJsonObject("color");
            return TintData.expressions(
                    optMolang(c, "r", "1"),
                    optMolang(c, "g", "1"),
                    optMolang(c, "b", "1"),
                    optMolang(c, "a", "1")
            );
        }

        return TintData.fixed(Color.WHITE);
    }

    private static List<GradientStop> parseGradient(JsonElement gradient) {
        List<GradientStop> out = new ArrayList<>();
        if (gradient == null || gradient.isJsonNull()) return out;
        if (gradient.isJsonObject()) {
            for (var entry : gradient.getAsJsonObject().entrySet()) {
                try {
                    out.add(new GradientStop(Double.parseDouble(entry.getKey()), parseColorElement(entry.getValue())));
                } catch (Exception ignored) { }
            }
        } else if (gradient.isJsonArray()) {
            JsonArray arr = gradient.getAsJsonArray();
            if (arr.size() == 1) {
                out.add(new GradientStop(0.0, parseColorElement(arr.get(0))));
            } else {
                for (int i = 0; i < arr.size(); i++) {
                    double pos = (double) i / (double) (arr.size() - 1);
                    out.add(new GradientStop(pos, parseColorElement(arr.get(i))));
                }
            }
        }
        out.sort(Comparator.comparingDouble(GradientStop::position));
        if (out.isEmpty()) out.add(new GradientStop(0.0, Color.WHITE));
        return out;
    }

    private static Color parseColorElement(JsonElement e) {
        if (e == null || e.isJsonNull()) return Color.WHITE;
        if (e.isJsonPrimitive()) return parseColorLiteral(e.getAsString());
        if (e.isJsonArray()) return parseColorArray(e.getAsJsonArray());
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            double r = parseFlexibleNumber(o.get("r"), 1.0);
            double g = parseFlexibleNumber(o.get("g"), 1.0);
            double b = parseFlexibleNumber(o.get("b"), 1.0);
            double a = parseFlexibleNumber(o.get("a"), 1.0);
            return fromUnitOr255(r, g, b, a);
        }
        return Color.WHITE;
    }

    private static Color parseColorArray(JsonArray arr) {
        double r = arr.size() > 0 ? parseFlexibleNumber(arr.get(0), 1.0) : 1.0;
        double g = arr.size() > 1 ? parseFlexibleNumber(arr.get(1), 1.0) : 1.0;
        double b = arr.size() > 2 ? parseFlexibleNumber(arr.get(2), 1.0) : 1.0;
        double a = arr.size() > 3 ? parseFlexibleNumber(arr.get(3), 1.0) : 1.0;
        return fromUnitOr255(r, g, b, a);
    }

    private static Color parseColorLiteral(String s) {
        if (s == null || s.isBlank()) return Color.WHITE;
        String v = s.trim();
        if (v.startsWith("#")) v = v.substring(1);
        try {
            if (v.length() == 6) {
                int rgb = Integer.parseUnsignedInt(v, 16);
                return Color.fromRGB(rgb);
            }
            if (v.length() == 8) {
                int argb = (int) Long.parseLong(v, 16);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                return Color.fromARGB(a, r, g, b);
            }
            double n = Double.parseDouble(v);
            int gray = (int) Math.max(0, Math.min(255, Math.round(n <= 1.0 ? n * 255.0 : n)));
            return Color.fromRGB(gray, gray, gray);
        } catch (Exception ignored) {
            return Color.WHITE;
        }
    }

    private static Color fromUnitOr255(double r, double g, double b, double a) {
        boolean unit = r <= 1.0 && g <= 1.0 && b <= 1.0 && a <= 1.0;
        int rr = clamp255(unit ? r * 255.0 : r);
        int gg = clamp255(unit ? g * 255.0 : g);
        int bb = clamp255(unit ? b * 255.0 : b);
        int aa = clamp255(unit ? a * 255.0 : a);
        return Color.fromARGB(aa, rr, gg, bb);
    }

    private static int clamp255(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static Color lerp(Color a, Color b, double t) {
        int rr = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int gg = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bb = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int aa = (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return Color.fromARGB(aa, rr, gg, bb);
    }

    private static double parseDouble(JsonElement e) {
        if (e == null) return 0;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
        try { return Double.parseDouble(e.getAsString()); } catch (Exception ex) { return 0; }
    }

    private static double parseFlexibleNumber(JsonElement e, double def) {
        if (e == null || e.isJsonNull()) return def;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
        try { return Double.parseDouble(e.getAsString()); } catch (Exception ex) { return def; }
    }

    private static String optMolang(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return String.valueOf(e.getAsDouble());
        }
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        return def;
    }

    private static String optArrayMolang(JsonObject o, String key, int index, String def) {
        if (o == null || !o.has(key)) return def;
        JsonElement e = o.get(key);
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (index < a.size()) {
                JsonElement item = a.get(index);
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber()) {
                    return String.valueOf(item.getAsDouble());
                }
                if (item.isJsonPrimitive()) return item.getAsString();
            }
        } else if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        return def;
    }

    private static final class TintData {
        final TintMode mode;
        final String colorR, colorG, colorB, colorA;
        final Color fixedColor;
        final String gradientInterpolant;
        final List<GradientStop> gradientStops;

        private TintData(TintMode mode, String colorR, String colorG, String colorB, String colorA,
                         Color fixedColor, String gradientInterpolant, List<GradientStop> gradientStops) {
            this.mode = mode;
            this.colorR = colorR;
            this.colorG = colorG;
            this.colorB = colorB;
            this.colorA = colorA;
            this.fixedColor = fixedColor;
            this.gradientInterpolant = gradientInterpolant;
            this.gradientStops = gradientStops;
        }

        static TintData expressions(String r, String g, String b, String a) {
            return new TintData(TintMode.EXPRESSIONS, r, g, b, a, null, null, List.of());
        }

        static TintData fixed(Color color) {
            return new TintData(TintMode.FIXED, "1", "1", "1", "1", color, null, List.of());
        }

        static TintData gradient(String interpolant, List<GradientStop> stops) {
            return new TintData(TintMode.GRADIENT, "1", "1", "1", "1", null, interpolant, stops);
        }
    }
}
