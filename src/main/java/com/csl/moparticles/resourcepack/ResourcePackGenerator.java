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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates the resource pack that renders every effect.
 *
 * <p>The client resolves the chain from the item's {@code item_model}
 * component (all paths inside {@code assets/<namespace>/}):</p>
 *
 * <pre>
 * snowstorm:particles/fire                  (item_model component)
 *   -&gt; items/particles/fire.json            (item definition)
 *     -&gt; models/item/particles/fire.json    (plane model)
 *       -&gt; textures/item/particles/fire.png
 * </pre>
 *
 * <p>Tints travel through the {@code minecraft:custom_model_data} color list
 * (index 0), applied by the {@code tints} entry of the item definition.</p>
 */
public final class ResourcePackGenerator {

    /**
     * Pack format 46 is 1.21.4, the version that introduced the
     * item-definition system this pack relies on. The supported range reaches
     * far into the future (kept an int so old-format parsers can read it).
     */
    private static final int PACK_FORMAT_MIN = 46;
    private static final int PACK_FORMAT_MAX = 999;

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

            for (ParticleDefinition def : plugin.getParticles().values()) {
                String root = "assets/" + def.namespace();
                writePng(packDir.toPath().resolve(root + "/textures/item/particles/" + def.shortName() + ".png"),
                        resolveTexture(def));
                writeString(packDir.toPath().resolve(root + "/models/item/particles/" + def.shortName() + ".json"),
                        planeModel(def.namespace() + ":item/particles/" + def.shortName()));
                writeString(packDir.toPath().resolve(root + "/items/particles/" + def.shortName() + ".json"),
                        itemDefinition(def.namespace() + ":item/particles/" + def.shortName()));
            }

            zipDirectory(packDir.toPath(), zipFile.toPath());
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to write resource pack: " + ex);
        }

        return zipFile;
    }

    // ------------------------------------------------------------------
    // Pack contents
    // ------------------------------------------------------------------

    private String packMcmeta() {
        return """
                {
                  "pack": {
                    "pack_format": %d,
                    "supported_formats": {
                      "min_inclusive": %d,
                      "max_inclusive": %d
                    },
                    "min_format": %d,
                    "max_format": %d,
                    "description": "MoParticles"
                  }
                }
                """.formatted(PACK_FORMAT_MIN, PACK_FORMAT_MIN, PACK_FORMAT_MAX,
                PACK_FORMAT_MIN, PACK_FORMAT_MAX);
    }

    private String planeModel(String textureRef) {
        return """
                {
                  "gui_light": "front",
                  "textures": {
                    "particle": "%s"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 7.98],
                      "to": [16, 16, 8.02],
                      "faces": {
                        "north": { "uv": [0, 0, 16, 16], "texture": "#particle", "tintindex": 0 },
                        "south": { "uv": [16, 0, 0, 16], "texture": "#particle", "tintindex": 0 }
                      }
                    }
                  ]
                }
                """.formatted(textureRef);
    }

    private String itemDefinition(String modelRef) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s",
                    "tints": [
                      {
                        "type": "minecraft:custom_model_data",
                        "index": 0,
                        "default": 16777215
                      }
                    ]
                  }
                }
                """.formatted(modelRef);
    }

    // ------------------------------------------------------------------
    // Texture resolution
    // ------------------------------------------------------------------

    /**
     * Finds the effect's texture on disk or in the bundled resources,
     * falling back to a generated circle. Candidate order:
     * <ol>
     *   <li>the declared path exactly</li>
     *   <li>{@code textures/particle/&lt;lastSegment&gt;.png} (so
     *       {@code textures/particle/moparticles/circle} finds our bundled
     *       {@code textures/particle/circle.png})</li>
     *   <li>{@code textures/particle/&lt;effectName&gt;.png}</li>
     * </ol>
     */
    private BufferedImage resolveTexture(ParticleDefinition def) {
        List<String> candidates = textureCandidates(def);
        for (String c : candidates) {
            File onDisk = new File(plugin.getDataFolder(), c);
            if (onDisk.isFile()) {
                BufferedImage read = tryRead(onDisk);
                if (read != null) return read;
            }
        }
        for (String c : candidates) {
            try (InputStream in = plugin.getResource(c)) {
                if (in != null) {
                    BufferedImage read = ImageIO.read(in);
                    if (read != null) return read;
                }
            } catch (IOException ignored) {
            }
        }
        return synthCircle();
    }

    private List<String> textureCandidates(ParticleDefinition def) {
        Set<String> candidates = new LinkedHashSet<>();
        String declared = def.texture();
        if (declared != null && !declared.isBlank()) {
            String normalized = declared.trim();
            if (normalized.startsWith("assets/")) {
                int idx = normalized.indexOf("/textures/");
                if (idx >= 0) normalized = normalized.substring(idx + 1);
            }
            if (!normalized.startsWith("textures/")) {
                normalized = "textures/" + normalized;
            }
            candidates.add(normalized + ".png");
            int slash = normalized.lastIndexOf('/');
            if (slash >= 0 && slash < normalized.length() - 1) {
                candidates.add("textures/particle/" + normalized.substring(slash + 1) + ".png");
            }
        }
        candidates.add("textures/particle/" + def.shortName() + ".png");
        return new ArrayList<>(candidates);
    }

    private BufferedImage tryRead(File file) {
        try {
            return ImageIO.read(file);
        } catch (IOException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Generated art (hard pixel edges only, no anti-aliasing)
    // ------------------------------------------------------------------

    /** Crisp circle: 1px darker outline ring around a white tintable core. */
    private BufferedImage synthCircle() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double d = Math.hypot(x + 0.5 - 8.0, y + 0.5 - 8.0);
                if (d <= 6.3) {
                    img.setRGB(x, y, 0xFFFFFFFF);
                } else if (d <= 7.4) {
                    img.setRGB(x, y, 0xFF9E9E9E);
                }
            }
        }
        return img;
    }

    private static void fill(BufferedImage img, int x, int y, int size, int argb) {
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int px = x + i, py = y + j;
                if (px >= 0 && px < img.getWidth() && py >= 0 && py < img.getHeight()) {
                    img.setRGB(px, py, argb);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // File helpers
    // ------------------------------------------------------------------

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
            List<Path> files;
            try (var walk = Files.walk(sourceDir)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path path : files) {
                String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream in = new FileInputStream(path.toFile())) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        List<Path> paths;
        try (var walk = Files.walk(path)) {
            paths = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
        }
        for (Path p : paths) {
            Files.deleteIfExists(p);
        }
    }
}
