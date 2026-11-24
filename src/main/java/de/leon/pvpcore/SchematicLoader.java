package de.leon.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Minimal Sponge Schematic v2 reader. It uses only the JDK and Paper API,
 * so PvPCore does not require WorldEdit or FAWE to paste its bundled arenas.
 */
final class SchematicLoader {
    private static final Map<String, Schematic> CACHE = new ConcurrentHashMap<>();
    private static final int LEGACY_ARENA_RADIUS = 48;
    private static final int MAX_DIMENSION = 512;
    private static final int MAX_BLOCKS = 30_000_000;

    private SchematicLoader() {
    }

    static void clearFootprint(JavaPlugin plugin, World world, String arena,
                               int centerX, int baseY, int centerZ) throws IOException {
        Schematic schematic = load(plugin, arena);
        clearSchematicBlocks(world, schematic, centerX - schematic.width / 2,
                baseY, centerZ - schematic.length / 2);
    }

    private static void clearSchematicBlocks(World world, Schematic schematic,
                                             int startX, int baseY, int startZ) {
        int index = 0;
        for (int y = 0; y < schematic.height; y++) {
            int worldY = baseY + y;
            boolean validY = worldY >= world.getMinHeight() && worldY < world.getMaxHeight();
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    BlockData state = schematic.palette[schematic.blockIndices[index++] & 0xFFFF];
                    if (!validY || state == null || state.getMaterial().isAir()) continue;
                    var block = world.getBlockAt(startX + x, worldY, startZ + z);
                    if (!block.getType().isAir()) block.setType(Material.AIR, false);
                }
            }
        }
    }

    private static void clearLegacyGeneratedRoom(World world, int centerX, int baseY, int centerZ) {
        int minY = Math.max(world.getMinHeight(), baseY);
        int maxY = Math.min(world.getMaxHeight(), baseY + 128);
        for (int y = minY; y < maxY; y++) {
            for (int z = centerZ - LEGACY_ARENA_RADIUS; z <= centerZ + LEGACY_ARENA_RADIUS; z++) {
                for (int x = centerX - LEGACY_ARENA_RADIUS; x <= centerX + LEGACY_ARENA_RADIUS; x++) {
                    var block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) block.setType(Material.AIR, false);
                }
            }
        }
    }

    static void paste(JavaPlugin plugin, World world, String arena, String previousArena,
                      int centerX, int baseY, int centerZ) throws IOException {
        if (world == null) throw new IOException("Practice world is not available.");

        Schematic schematic = load(plugin, arena);
        Schematic previous = null;
        boolean clearLegacyGeneratedArena = false;
        if (previousArena != null && !previousArena.isBlank()) {
            try {
                // Load even when the selected map has not changed. This is needed to
                // rebuild a missing room after a world-folder reset or stale config flag.
                previous = load(plugin, previousArena);
            } catch (IOException ignored) {
                clearLegacyGeneratedArena = true;
            }
        }

        int schematicX = centerX - schematic.width / 2;
        int schematicZ = centerZ - schematic.length / 2;
        if (previous != null) {
            int previousX = centerX - previous.width / 2;
            int previousZ = centerZ - previous.length / 2;
            clearSchematicBlocks(world, previous, previousX, baseY, previousZ);
        } else if (clearLegacyGeneratedArena) {
            clearLegacyGeneratedRoom(world, centerX, baseY, centerZ);
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
                    BlockData state = schematic.palette[schematic.blockIndices[index++] & 0xFFFF];
                    if (state == null || state.getMaterial().isAir()) continue;
                    // Arena 3 gets its only barrier from the generated circular perimeter.
                    // Ignore barrier blocks stored inside the schematic so no inner pillars remain.
                    if ("ARENA_3".equals(arena) && state.getMaterial() == Material.BARRIER) continue;
                    if (state instanceof Leaves leaves) leaves.setPersistent(true);
                    world.getBlockAt(schematicX + x, worldY, schematicZ + z).setBlockData(state, false);
                }
            }
        }

        int importedBlockEntities = applyBlockEntities(world, schematic, schematicX, baseY, schematicZ);
        if (!schematic.blockEntities.isEmpty()) {
            plugin.getLogger().info("Imported " + importedBlockEntities + "/" + schematic.blockEntities.size()
                    + " schematic block entities for " + arena + ".");
        }
    }

    private static Schematic load(JavaPlugin plugin, String arena) throws IOException {
        try {
            return CACHE.computeIfAbsent(arena, key -> {
                try {
                    return read(plugin, key);
                } catch (IOException exception) {
                    throw new SchematicLoadFailure(exception);
                }
            });
        } catch (SchematicLoadFailure failure) {
            throw failure.ioException;
        }
    }

    private static Schematic read(JavaPlugin plugin, String arena) throws IOException {
        String path = switch (arena) {
            case "ARENA_1" -> "schematics/arena-1.schem";
            case "ARENA_3" -> "schematics/arena-3.schem";
            case "HIGH_CROWN_SPAWN" -> "schematics/high-crown-medieval-spawn.schem";
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
                short[] blockIndices = decodeVarInts(encodedData, total, palette.length);

                List<Map<String, Object>> blockEntities = new ArrayList<>();
                Object rawBlockEntities = schematic.get("BlockEntities");
                if (rawBlockEntities instanceof List<?> list) {
                    for (Object value : list) {
                        Map<String, Object> entry = compound(value);
                        if (entry != null) blockEntities.add(entry);
                    }
                }
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

    private static short[] decodeVarInts(byte[] data, int expectedCount, int paletteSize) throws IOException {
        short[] result = new short[expectedCount];
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
            result[i] = (short) value;
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

    private static int applyBlockEntities(World world, Schematic schematic,
                                           int schematicX, int baseY, int schematicZ) {
        int imported = 0;
        for (Map<String, Object> tag : schematic.blockEntities) {
            Object rawPos = tag.get("Pos");
            if (!(rawPos instanceof List<?> pos) || pos.size() < 3) continue;
            int x = number(pos.get(0), Integer.MIN_VALUE);
            int y = number(pos.get(1), Integer.MIN_VALUE);
            int z = number(pos.get(2), Integer.MIN_VALUE);
            if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) continue;

            int worldY = baseY + y;
            if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) continue;
            BlockState state = world.getBlockAt(schematicX + x, worldY, schematicZ + z).getState();

            if (state instanceof Sign sign) {
                for (int line = 0; line < 4; line++) {
                    sign.setLine(line, legacySignText(tag.get("Text" + (line + 1))));
                }
            }
            if (state instanceof Skull skull) applySkullTexture(skull, tag);

            // Chests, trapped chests and hoppers in this schematic have empty Items lists;
            // their block states and empty inventories are restored by the block palette.
            if (state.update(true, false)) imported++;
        }
        return imported;
    }

    private static String legacySignText(Object raw) {
        if (!(raw instanceof String json) || json.isBlank()) return "";
        int key = json.indexOf("text");
        if (key < 0) return "";
        int colon = json.indexOf(':', key);
        int start = colon < 0 ? -1 : json.indexOf('"', colon + 1);
        if (start < 0) return "";
        int end = json.indexOf('"', start + 1);
        if (end < 0) return "";
        return json.substring(start + 1, end).replace("\\n", " ")
                .replace("\\r", "").replace("\\\"", "\"");
    }

    private static void applySkullTexture(Skull skull, Map<String, Object> tag) {
        try {
            Map<String, Object> owner = compound(tag.get("SkullOwner"));
            Map<String, Object> properties = owner == null ? null : compound(owner.get("Properties"));
            Object rawTextures = properties == null ? null : properties.get("textures");
            if (!(rawTextures instanceof List<?> textures) || textures.isEmpty()) return;
            Map<String, Object> texture = compound(textures.get(0));
            if (texture == null || !(texture.get("Value") instanceof String encoded)) return;

            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int urlKey = json.indexOf("url");
            int colon = urlKey < 0 ? -1 : json.indexOf(':', urlKey);
            int start = colon < 0 ? -1 : json.indexOf('"', colon + 1);
            int end = start < 0 ? -1 : json.indexOf('"', start + 1);
            if (start < 0 || end < 0) return;
            URI skin = URI.create(json.substring(start + 1, end));

            PlayerProfile profile = Bukkit.createPlayerProfile(
                    UUID.nameUUIDFromBytes(encoded.getBytes(StandardCharsets.UTF_8)));
            profile.getTextures().setSkin(skin.toURL());
            try {
                skull.getClass().getMethod("setPlayerProfile", PlayerProfile.class).invoke(skull, profile);
            } catch (ReflectiveOperationException ignored) {
                skull.getClass().getMethod("setOwnerProfile", PlayerProfile.class).invoke(skull, profile);
            }
        } catch (Exception ignored) {
            // A malformed/unsupported custom head must not prevent the rest of the hub from pasting.
        }
    }

    private record Schematic(int width, int height, int length, BlockData[] palette,
                             short[] blockIndices, List<Map<String, Object>> blockEntities) {
    }

    private static final class SchematicLoadFailure extends RuntimeException {
        private final IOException ioException;

        private SchematicLoadFailure(IOException cause) {
            super(cause);
            this.ioException = cause;
        }
    }

    private static final class NbtReader {
        private static final int MAX_ARRAY_SIZE = 32_000_000;
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
