package com.nextgenware.FurnitureVisualizer.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LayoutSerializer {

    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    // Wrapper file format: { schemaVersion: 1, layout: { ... } }
    private static class LayoutFileDTO {
        int schemaVersion;
        LayoutModel layout;
    }

    public static void save(LayoutModel model, Path file) throws IOException {
        validateOrThrow(model);

        LayoutFileDTO dto = new LayoutFileDTO();
        dto.schemaVersion = SCHEMA_VERSION;
        dto.layout = model;

        String json = GSON.toJson(dto);
        Files.writeString(file, json);
    }

    public static LayoutModel load(Path file) throws IOException {
        String json = Files.readString(file);

        // Parse root to detect version / old format
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        LayoutModel loaded;

        // New format: has schemaVersion + layout
        if (root.has("schemaVersion") && root.has("layout")) {
            int v = root.get("schemaVersion").getAsInt();

            if (v != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported file version: " + v + ". Expected: " + SCHEMA_VERSION);
            }

            LayoutFileDTO dto = GSON.fromJson(root, LayoutFileDTO.class);
            loaded = dto.layout;

        } else {
            // Old format: directly LayoutModel JSON
            loaded = GSON.fromJson(root, LayoutModel.class);
        }

        validateOrThrow(loaded);
        return loaded;
    }

    // ---------------- Validation ----------------

    private static void validateOrThrow(LayoutModel model) {
        
        if (model == null) throw new IllegalArgumentException("File is empty or invalid JSON.");

        if (model.room == null) throw new IllegalArgumentException("Room data is missing (room is null).");

        if (model.room.shape == null) throw new IllegalArgumentException("Room shape is missing.");

        if (model.room.width <= 0 || model.room.depth <= 0 || model.room.height <= 0) {
            throw new IllegalArgumentException("Room dimensions must be > 0 (width/depth/height).");
        }

        if (model.items == null) throw new IllegalArgumentException("Items list is missing (items is null).");

        for (int i = 0; i < model.items.size(); i++) {
            FurnitureItem it = model.items.get(i);
            if (it == null) throw new IllegalArgumentException("Item #" + (i + 1) + " is null.");

            if (it.id == null || it.id.trim().isEmpty())
                throw new IllegalArgumentException("Item #" + (i + 1) + " has missing id.");

            if (it.type == null || it.type.trim().isEmpty())
                throw new IllegalArgumentException("Item #" + (i + 1) + " has missing type.");

            if (it.width <= 0 || it.depth <= 0 || it.height <= 0)
                throw new IllegalArgumentException("Item '" + it.id + "' has invalid size (must be > 0).");

            // Keep inside room (soft validation - allow small tolerance)
            if (it.x < 0 || it.x > model.room.width || it.z < 0 || it.z > model.room.depth) {
                throw new IllegalArgumentException(
                        "Item '" + it.id + "' position is outside room bounds (x/z out of range)."
                );
            }
        }
    }
}
