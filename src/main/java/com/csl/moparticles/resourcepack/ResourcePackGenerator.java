package com.csl.moparticles.resourcepack;

import com.csl.moparticles.MoParticlesPlugin;
import com.csl.moparticles.particle.ParticleDefinition;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Generates a resourcepack based on the configs given
// Uses overlays to work with multiple versions
public final class ResourcePackGenerator {

    private static final int PACK_FORMAT_1194 = 13;
    private static final int PACK_FORMAT_1214 = 46;
    private static final int PACK_FORMAT_12111 = 75;
    private static final int PACK_FORMAT_2612 = 84;

    private static final String OVERLAY_DIR = "overlay_46_84";

    private final MoParticlesPlugin plugin;

    public ResourcePackGenerator(MoParticlesPlugin plugin) {
        this.plugin = plugin;
    }

    public File generate() {
        File generated = new File(plugin.getDataFolder(), "generated");
        File packDir = new File(generated, "resourcepack");
        File zipFile = new File(generated, "resourcepack.zip");
        generated.mkdirs();

        try {
            deleteRecursively(packDir.toPath());
            if (zipFile.exists()) zipFile.delete();
            Files.createDirectories(packDir.toPath());

            writeString(packDir.toPath().resolve("pack.mcmeta"), packMcmeta());
            writePng(packDir.toPath().resolve("pack.png"), brandLogo());

            List<EffectModelEntry> entries = new ArrayList<>();
            Path overlayDir = packDir.toPath().resolve(OVERLAY_DIR);

            for (ParticleDefinition def : plugin.getParticles().values()) {
                String id = def.id();
                int colon = id.indexOf(':');
                String namespace = colon < 0 ? MoParticlesPlugin.NAMESPACE : id.substring(0, colon);
                String name = colon < 0 ? id : id.substring(colon + 1);
                int modelId = plugin.modelNumericId(id);
                String modelPath = namespace + ":item/particles/" + name;

                BufferedImage tex = loadTexture(def.texture(), name);
                if (tex == null) tex = synthTexture(name);

                Path textureFile = packDir.toPath().resolve("assets/" + namespace + "/textures/item/particles/" + name + ".png");
                Path modelFile = packDir.toPath().resolve("assets/" + namespace + "/models/item/particles/" + name + ".json");
                Path overlayItemFile = overlayDir.resolve("assets/" + namespace + "/items/particles/" + name + ".json");

                writePng(textureFile, tex);
                writeString(modelFile, rawPlaneModel(namespace, name));
                writeString(overlayItemFile, directItemDefinition(modelPath));

                entries.add(new EffectModelEntry(modelId, modelPath));
            }

            entries.sort(Comparator.comparingInt(e -> e.numericId));
            writeString(packDir.toPath().resolve("assets/minecraft/models/item/paper.json"), legacyPaperModel(entries));
            writeString(overlayDir.resolve("assets/minecraft/items/paper.json"), paperDispatcher(entries));
            writeString(packDir.toPath().resolve("assets/" + MoParticlesPlugin.NAMESPACE + "/sounds.json"), "{}\n");
            zipDirectory(packDir.toPath(), zipFile.toPath());
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to write resource pack: " + ex);
            ex.printStackTrace();
        }

        return zipFile;
    }

    private String packMcmeta() {
        return "{\n" +
                "  \"pack\": {\n" +
                "    \"pack_format\": " + PACK_FORMAT_1194 + ",\n" +
                "    \"supported_formats\": {\n" +
                "      \"min_inclusive\": " + PACK_FORMAT_1194 + ",\n" +
                "      \"max_inclusive\": " + PACK_FORMAT_2612 + "\n" +
                "    },\n" +
                "    \"min_format\": " + PACK_FORMAT_1194 + ",\n" +
                "    \"max_format\": " + PACK_FORMAT_2612 + ",\n" +
                "    \"description\": \"MoParticles generated resource pack - RedSnicker / Chest Solutions\"\n" +
                "  },\n" +
                "  \"overlays\": {\n" +
                "    \"entries\": [\n" +
                "      {\n" +
                "        \"directory\": \"" + OVERLAY_DIR + "\",\n" +
                "        \"formats\": {\n" +
                "          \"min_inclusive\": " + PACK_FORMAT_1214 + ",\n" +
                "          \"max_inclusive\": " + PACK_FORMAT_2612 + "\n" +
                "        },\n" +
                "        \"min_format\": " + PACK_FORMAT_1214 + ",\n" +
                "        \"max_format\": " + PACK_FORMAT_2612 + "\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}\n";
    }

    private String rawPlaneModel(String namespace, String name) {
        return "{\n" +
                "  \"gui_light\": \"front\",\n" +
                "  \"textures\": {\n" +
                "    \"particle\": \"" + namespace + ":item/particles/" + name + "\"\n" +
                "  },\n" +
                "  \"elements\": [\n" +
                "    {\n" +
                "      \"from\": [0, 0, 7.98],\n" +
                "      \"to\": [16, 16, 8.02],\n" +
                "      \"faces\": {\n" +
                "        \"north\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#particle\", \"tintindex\": 0},\n" +
                "        \"south\": {\"uv\": [16, 0, 0, 16], \"texture\": \"#particle\", \"tintindex\": 0}\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";
    }

    private String legacyPaperModel(List<EffectModelEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"parent\": \"minecraft:item/generated\",\n");
        sb.append("  \"textures\": {\n");
        sb.append("    \"layer0\": \"minecraft:item/paper\"\n");
        sb.append("  },\n");
        sb.append("  \"overrides\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            EffectModelEntry entry = entries.get(i);
            sb.append("    {\"predicate\": {\"custom_model_data\": ").append(entry.numericId).append("}, \"model\": \"").append(entry.rawModelPath).append("\"}");
            if (i + 1 < entries.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String directItemDefinition(String rawModelPath) {
        return "{\n" +
                "  \"model\": {\n" +
                "    \"type\": \"minecraft:model\",\n" +
                "    \"model\": \"" + rawModelPath + "\",\n" +
                "    \"tints\": [\n" +
                "      {\n" +
                "        \"type\": \"minecraft:custom_model_data\",\n" +
                "        \"index\": 0,\n" +
                "        \"default\": 16777215\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}\n";
    }

    private String paperDispatcher(List<EffectModelEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"model\": {\n");
        sb.append("    \"type\": \"minecraft:range_dispatch\",\n");
        sb.append("    \"property\": \"minecraft:custom_model_data\",\n");
        sb.append("    \"fallback\": {\n");
        sb.append("      \"type\": \"minecraft:model\",\n");
        sb.append("      \"model\": \"minecraft:item/paper\"\n");
        sb.append("    },\n");
        sb.append("    \"entries\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            EffectModelEntry entry = entries.get(i);
            sb.append("      {\n");
            sb.append("        \"threshold\": ").append(entry.numericId).append(",\n");
            sb.append("        \"model\": {\n");
            sb.append("          \"type\": \"minecraft:model\",\n");
            sb.append("          \"model\": \"").append(entry.rawModelPath).append("\",\n");
            sb.append("          \"tints\": [\n");
            sb.append("            {\n");
            sb.append("              \"type\": \"minecraft:custom_model_data\",\n");
            sb.append("              \"index\": 0,\n");
            sb.append("              \"default\": 16777215\n");
            sb.append("            }\n");
            sb.append("          ]\n");
            sb.append("        }\n");
            sb.append("      }");
            if (i + 1 < entries.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("    ]\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private BufferedImage loadTexture(String declaredTexture, String fallbackName) {
        List<String> candidates = new ArrayList<>();
        if (declaredTexture != null && !declaredTexture.isBlank()) {
            String normalized = declaredTexture;
            if (normalized.startsWith("assets/")) {
                int idx = normalized.indexOf("/textures/");
                if (idx >= 0) normalized = normalized.substring(idx + 1);
            }
            if (normalized.startsWith("textures/")) {
                candidates.add(normalized + ".png");
            }
        }
        candidates.add("textures/particle/" + fallbackName + ".png");

        for (String candidate : candidates) {
            File dataTexture = new File(plugin.getDataFolder(), candidate);
            if (dataTexture.isFile()) {
                try {
                    return ImageIO.read(dataTexture);
                } catch (IOException ignored) {
                }
            }
        }

        for (String candidate : candidates) {
            try (InputStream in = plugin.getResource(candidate)) {
                if (in != null) {
                    return ImageIO.read(in);
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private BufferedImage synthTexture(String name) {
        int w = 16;
        int h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int hue = (name.hashCode() & 0x7FFFFFFF) % 360;
        float[] hsv = {hue / 360.0f, 0.0f, 1.0f};
        int rgb = java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - w / 2.0) / (w / 2.0);
                double dy = (y - h / 2.0) / (h / 2.0);
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 1.0) {
                    int a = (int) ((1.0 - d) * 255);
                    img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                } else {
                    img.setRGB(x, y, 0);
                }
            }
        }
        return img;
    }

    private BufferedImage brandLogo() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                if (x >= 6 && x < 26 && y >= 6 && y < 26) {
                    img.setRGB(x, y, 0xFF35A1C8);
                } else {
                    img.setRGB(x, y, 0xFF121218);
                }
            }
        }
        return img;
    }

    private void writeString(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }

    private void writePng(Path path, BufferedImage img) throws IOException {
        Files.createDirectories(path.getParent());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        Files.write(path, baos.toByteArray());
    }

    private void zipDirectory(Path sourceDir, Path outputZip) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputZip.toFile());
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> {
                        String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                        try {
                            zip.putNextEntry(new ZipEntry(entryName));
                            try (FileInputStream in = new FileInputStream(path.toFile())) {
                                in.transferTo(zip);
                            }
                            zip.closeEntry();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) throw io;
            throw ex;
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) throw io;
            throw ex;
        }
    }

    private record EffectModelEntry(int numericId, String rawModelPath) {
    }
}
