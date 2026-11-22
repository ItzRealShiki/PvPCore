package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Minimal Sponge Schematic v2 reader. It uses only the JDK and Paper API,
 * so PvPCore does not require WorldEdit or FAWE to paste its bundled arenas.
 */
final class SchematicLoader {
    private static final Map<String, Schematic> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_DIMENSION = 512;
    private static final int MAX_BLOCKS = 8_000_000;

    private SchematicLoader() {
    }

    static void paste(JavaPlugin plugin, World world, String arena, String previousArena,
                      int centerX, int baseY, int centerZ) throws IOException {
        if (world == null) throw new IOException("Practice world is not available.");

        Schematic schematic = load(plugin, arena);
        Schematic previous = null;
        if (previousArena != null && !previousArena.isBlank() && !previousArena.equals(arena)) {
            try {
                previous = load(plugin, previousArena);
            } catch (IOException ignored) {
                // Older plugin versions used generated themes rather than bundled schematics.
            }
        }

        int schematicX = centerX - schematic.width / 2;
        int schematicZ = centerZ - schematic.length / 2;

        // Clear the new footprint and, when known, the old footprint before the new paste.
        int clearMinX = schematicX;
        int clearMinZ = schematicZ;
        int clearMaxX = schematicX + schematic.width;
        int clearMaxZ = schematicZ + schematic.length;
        int clearHeight = schematic.height + 32;
        if (previous != null) {
            int previousX = centerX - previous.width / 2;
            int previousZ = centerZ - previous.length / 2;
            clearMinX = Math.min(clearMinX, previousX);
            clearMinZ = Math.min(clearMinZ, previousZ);
            clearMaxX = Math.max(clearMaxX, previousX + previous.width);
            clearMaxZ = Math.max(clearMaxZ, previousZ + previous.length);
            clearHeight = Math.max(clearHeight, previous.height + 32);
        }
        int maxY = Math.min(world.getMaxHeight(), baseY + clearHeight);
        for (int y = Math.max(world.getMinHeight(), baseY); y < maxY; y++) {
            for (int z = clearMinZ; z < clearMaxZ; z++) {
                for (int x = clearMinX; x < clearMaxX; x++) {
                    var block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) block.setType(Material.AIR, false);
                }
            }
        }

        int index = 0;
        for (int y = 0; y < schematic.height; y++) {
            int worldY = baseY + y;
            if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) {
                index += schematic.width * schematic.length;
                continue;
            }
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    BlockData state = schematic.palette[schematic.blockIndices[index++]];
                    if (state == null || state.getMaterial().isAir()) continue;
                    world.getBlockAt(schematicX + x, worldY, schematicZ + z).setBlockData(state, false);
                }
            }
        }

        if (schematic.blockEntityCount > 0) {
            plugin.getLogger().warning("Arena " + arena + " contains " + schematic.blockEntityCount
                    + " block entities; decorative/container data is not imported.");
        }
    }

    private static Schematic load(JavaPlugin plugin, String arena) throws IOException {
        return CACHE.computeIfAbsent(arena, key -> {
            try {
                return read(plugin, key);
            } catch (IOException exception) {
                throw new SchematicLoadFailure(exception);
            }
        });
    }

    private static Schematic read(JavaPlugin plugin, String arena) throws IOException {
        String path = switch (arena) {
            case "ARENA_1" -> "schematics/arena-1.schem";
            case "ARENA_3" -> "schematics/arena-3.schem";
            default -> throw new IOException("Unknown arena key: " + arena);
        };

        try (InputStream resource = plugin.getResource(path)) {
            if (resource == null) throw new IOException("Missing plugin resource: " + path);
            BufferedInputStream buffered = new BufferedInputStream(resource);
            buffered.mark(2);
            int first = buffered.read();
            int second = buffered.read();
            buffered.reset();
            InputStream decoded = first == 0x1f && second == 0x8b
                    ? new GZIPInputStream(buffered) : buffered;

            try (DataInputStream input = new DataInputStream(decoded)) {
                NbtReader reader = new NbtReader(input);
                Map<String, Object> root = reader.readRootCompound();
                Map<String, Object> schematic = compound(root.get("Schematic"));
                if (schematic == null) schematic = root;

                Map<String, Object> blocks = compound(schematic.get("Blocks"));
                if (blocks == null) blocks = schematic;

                int width = number(schematic.get("Width"), number(blocks.get("Width"), -1));
                int height = number(schematic.get("Height"), number(blocks.get("Height"), -1));
                int length = number(schematic.get("Length"), number(blocks.get("Length"), -1));
                if (width < 1 || height < 1 || length < 1
                        || width > MAX_DIMENSION || height > MAX_DIMENSION || length > MAX_DIMENSION) {
                    throw new IOException("Invalid schematic dimensions: " + width + "x" + height + "x" + length);
                }

                long totalLong = (long) width * height * length;
                if (totalLong > MAX_BLOCKS) throw new IOException("Schematic exceeds the block safety limit.");
                int total = (int) totalLong;

                Map<String, Object> paletteTags = compound(blocks.get("Palette"));
                if (paletteTags == null) paletteTags = compound(schematic.get("Palette"));
                if (paletteTags == null || paletteTags.isEmpty()) throw new IOException("Schematic has no block palette.");

                int maximumPaletteId = -1;
                for (Object value : paletteTags.values()) {
                    if (value instanceof Number number) maximumPaletteId = Math.max(maximumPaletteId, number.intValue());
                }
                if (maximumPaletteId < 0 || maximumPaletteId > 65535) throw new IOException("Invalid block palette.");

                BlockData[] palette = new BlockData[maximumPaletteId + 1];
                for (Map.Entry<String, Object> entry : paletteTags.entrySet()) {
                    if (!(entry.getValue() instanceof Number number)) continue;
                    int paletteId = number.intValue();
                    if (paletteId < 0 || paletteId >= palette.length) continue;
                    palette[paletteId] = parseBlockData(entry.getKey());
                }

                Object rawData = blocks.get("Data");
                if (rawData == null) rawData = schematic.get("BlockData");
                if (rawData == null) rawData = schematic.get("Data");
                if (!(rawData instanceof byte[] encodedData)) {
                    throw new IOException("Unsupported schematic block-data format.");
                }
                int[] blockIndices = decodeVarInts(encodedData, total, palette.length);

                int blockEntities = 0;
                Object rawBlockEntities = schematic.get("BlockEntities");
                if (rawBlockEntities instanceof List<?> list) blockEntities = list.size();
                return new Schematic(width, height, length, palette, blockIndices, blockEntities);
            }
        } catch (SchematicLoadFailure failure) {
            throw failure.ioException;
        }
    }

    private static BlockData parseBlockData(String state) {
        try {
            return Bukkit.createBlockData(state);
        } catch (IllegalArgumentException exception) {
            int properties = state.indexOf('[');
            String materialName = properties >= 0 ? state.substring(0, properties) : state;
            Material material = Material.matchMaterial(materialName);
            return material == null ? null : Bukkit.createBlockData(material);
        }
    }

    private static int[] decodeVarInts(byte[] data, int expectedCount, int paletteSize) throws IOException {
        int[] result = new int[expectedCount];
        int offset = 0;
        for (int i = 0; i < expectedCount; i++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (offset >= data.length || shift > 28) {
                    throw new IOException("Schematic block data ended before all blocks were decoded.");
                }
                int next = data[offset++] & 0xff;
                value |= (next & 0x7f) << shift;
                if ((next & 0x80) == 0) break;
                shift += 7;
            }
            if (value < 0 || value >= paletteSize) {
                throw new IOException("Schematic contains an invalid palette index: " + value);
            }
            result[i] = value;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private record Schematic(int width, int height, int length, BlockData[] palette,
                             int[] blockIndices, int blockEntityCount) {
    }

    private static final class SchematicLoadFailure extends RuntimeException {
        private final IOException ioException;

        private SchematicLoadFailure(IOException cause) {
            super(cause);
            this.ioException = cause;
        }
    }

    private static final class NbtReader {
        private static final int MAX_ARRAY_SIZE = 16_000_000;
        private final DataInputStream input;

        private NbtReader(DataInputStream input) {
            this.input = input;
        }

        private Map<String, Object> readRootCompound() throws IOException {
            int type = input.readUnsignedByte();
            if (type != 10) throw new IOException("Schematic root tag is not an NBT compound.");
            readString(); // Root name
            Object root = readPayload(type, 0);
            Map<String, Object> compound = compound(root);
            if (compound == null) throw new IOException("Invalid schematic root compound.");
            return compound;
        }

        private Object readPayload(int type, int depth) throws IOException {
            if (depth > 64) throw new IOException("NBT nesting is too deep.");
            return switch (type) {
                case 1 -> input.readByte();
                case 2 -> input.readShort();
                case 3 -> input.readInt();
                case 4 -> input.readLong();
                case 5 -> input.readFloat();
                case 6 -> input.readDouble();
                case 7 -> readByteArray();
                case 8 -> readString();
                case 9 -> readList(depth + 1);
                case 10 -> readCompound(depth + 1);
                case 11 -> readIntArray();
                case 12 -> readLongArray();
                default -> throw new IOException("Unsupported NBT tag type: " + type);
            };
        }

        private byte[] readByteArray() throws IOException {
            int length = checkedLength(input.readInt());
            byte[] result = new byte[length];
            input.readFully(result);
            return result;
        }

        private int[] readIntArray() throws IOException {
            int length = checkedLength(input.readInt());
            int[] result = new int[length];
            for (int i = 0; i < length; i++) result[i] = input.readInt();
            return result;
        }

        private long[] readLongArray() throws IOException {
            int length = checkedLength(input.readInt());
            long[] result = new long[length];
            for (int i = 0; i < length; i++) result[i] = input.readLong();
            return result;
        }

        private int checkedLength(int length) throws IOException {
            if (length < 0 || length > MAX_ARRAY_SIZE) throw new IOException("Invalid NBT array/list length.");
            return length;
        }

        private List<Object> readList(int depth) throws IOException {
            int elementType = input.readUnsignedByte();
            int length = checkedLength(input.readInt());
            List<Object> result = new ArrayList<>(Math.min(length, 100_000));
            for (int i = 0; i < length; i++) result.add(readPayload(elementType, depth));
            return result;
        }

        private Map<String, Object> readCompound(int depth) throws IOException {
            Map<String, Object> result = new LinkedHashMap<>();
            while (true) {
                int type = input.readUnsignedByte();
                if (type == 0) return result;
                String name = readString();
                result.put(name, readPayload(type, depth));
            }
        }

        private String readString() throws IOException {
            int length = input.readUnsignedShort();
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
