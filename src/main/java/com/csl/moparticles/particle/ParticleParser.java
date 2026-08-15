package com.csl.moparticles.particle;

import com.csl.moparticles.molang.MolangEngine;
import com.csl.moparticles.molang.MolangEngine.Scope;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Color;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Parses Bedrock particle JSON into a baked definition. */
public final class ParticleParser {

    private final MolangEngine molang;

    public ParticleParser(MolangEngine molang) {
        this.molang = molang;
    }

    public int parseDirectory(File dir, Map<String, ParticleDefinition> out) {
        if (!dir.isDirectory()) return 0;
        int loaded = 0;
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path path : files) {
                try (FileReader r = new FileReader(path.toFile())) {
                    JsonElement root = JsonParser.parseReader(r);
                    if (!root.isJsonObject()) continue;
                    ParticleDefinition def = parse(root.getAsJsonObject());
                    if (def != null) {
                        out.put(def.id(), def);
                        loaded++;
                    }
                } catch (Exception ex) {
                    System.err.println("[moparticles] failed to parse " + path + " : " + ex);
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            System.err.println("[moparticles] failed to walk particle directory " + dir + " : " + ex);
            ex.printStackTrace();
        }
        return loaded;
    }

    public ParticleDefinition parse(JsonObject root) {
        JsonObject effect = null;
        if (root.has("particle_effect") && root.get("particle_effect").isJsonObject()) {
            effect = root.getAsJsonObject("particle_effect");
        } else if (root.has("particles") && root.get("particles").isJsonObject()) {
            JsonObject p = root.getAsJsonObject("particles");
            for (Map.Entry<String, JsonElement> e : p.entrySet()) {
                if (e.getValue().isJsonObject()) {
                    effect = e.getValue().getAsJsonObject();
                    JsonObject desc = effect.has("description") && effect.get("description").isJsonObject()
                            ? effect.getAsJsonObject("description") : new JsonObject();
                    if (!desc.has("identifier")) desc.addProperty("identifier", e.getKey());
                    effect.add("description", desc);
                    break;
                }
            }
        }
        if (effect == null) return null;

        JsonObject description = effect.has("description") && effect.get("description").isJsonObject()
                ? effect.getAsJsonObject("description") : new JsonObject();

        String identifier = optString(description, "identifier", "moparticles:unnamed");
        String material = "particles_alpha";
        String texture = "textures/particle/moparticles/atlas";

        if (description.has("basic_render_parameters")
                && description.get("basic_render_parameters").isJsonObject()) {
            JsonObject brp = description.getAsJsonObject("basic_render_parameters");
            material = optString(brp, "material", material);
            texture = optString(brp, "texture", texture);
        }

        Map<String, Curve> curves = new HashMap<>();
        if (effect.has("curves") && effect.get("curves").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : effect.getAsJsonObject("curves").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                Curve c = Curve.parse(e.getKey(), e.getValue().getAsJsonObject(), molang);
                if (c != null) curves.put(e.getKey(), c);
            }
        }

        JsonObject components = effect.has("components") && effect.get("components").isJsonObject()
                ? effect.getAsJsonObject("components") : new JsonObject();

        ParticleDefinition.Builder b = new ParticleDefinition.Builder();
        b.id = identifier;
        b.material = material;
        b.texture = texture;

        ParticleSimulator sim = new ParticleSimulator(molang, curves, components, b);
        sim.bake();

        return new ParticleDefinition(b.id, b.namespace(), b.material, b.texture,
                b.duration, b.fps, b.totalFrames,
                b.frames, b.javaParticles, b.hasCustomTexture, b.textureRows);
    }

    static String optString(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive()) return e.getAsString();
        return def;
    }

    static double optDouble(JsonObject o, String key, double def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
        if (e.isJsonPrimitive()) {
            try { return Double.parseDouble(e.getAsString()); } catch (Exception ignored) { }
        }
        return def;
    }

    static int optInt(JsonObject o, String key, int def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
        if (e.isJsonPrimitive()) {
            try { return Integer.parseInt(e.getAsString()); } catch (Exception ignored) { }
        }
        return def;
    }

    static boolean optBoolean(JsonObject o, String key, boolean def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
            try { return Double.parseDouble(e.getAsString()) != 0; } catch (Exception ignored) { }
        }
        return def;
    }

    static List<Double> optVector(JsonObject o, String key, double defX, double defY, double defZ, Scope scope, MolangEngine molang) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            List<Double> v = new ArrayList<>();
            v.add(defX);
            v.add(defY);
            v.add(defZ);
            return v;
        }
        JsonElement e = o.get(key);
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            List<Double> out = new ArrayList<>();
            for (int i = 0; i < a.size(); i++) {
                JsonElement item = a.get(i);
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber()) {
                    out.add(item.getAsDouble());
                } else {
                    out.add(molang.eval(item.getAsString(), scope == null ? new Scope() : scope).value);
                }
            }
            while (out.size() < 3) out.add(0.0);
            return out;
        }
        if (e.isJsonPrimitive()) {
            return molang.evalVector(e.getAsString(), scope == null ? new Scope() : scope, 3);
        }
        List<Double> v = new ArrayList<>();
        v.add(defX);
        v.add(defY);
        v.add(defZ);
        return v;
    }

    static Color optColor(JsonObject o, String key, Color def) {
        if (o == null || !o.has(key) || !o.get(key).isJsonObject()) return def;
        JsonObject c = o.getAsJsonObject(key);
        double r = optDouble(c, "r", def.getRed());
        double g = optDouble(c, "g", def.getGreen());
        double b = optDouble(c, "b", def.getBlue());
        double a = optDouble(c, "a", def.getAlpha());
        return Color.fromARGB((int) a, (int) r, (int) g, (int) b);
    }
}
