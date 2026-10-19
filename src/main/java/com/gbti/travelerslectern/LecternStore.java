package com.gbti.travelerslectern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Traveler's Lecterns on disk, in config/travelers-lectern/lecterns.json, keyed by dimension and
 * position.
 *
 * <p>Releases before 2.0.0 kept them in travelers_lecterns.json, keyed by position alone, with the
 * dimension written field by field and the book in 1.21.1's item format. That file is imported
 * once and never written, so a server that goes back to an older release finds it as it was. If it
 * is newer than lecterns.json, which happens when an older release ran in between, it is imported
 * again.</p>
 *
 * <p>A file that cannot be read is never overwritten: the store turns read-only until the server
 * restarts with a readable file, and saving says so. A lectern entry that cannot be read is kept
 * and written back as it was.</p>
 */
public final class LecternStore {
    static final Path ROOT = Path.of("config", "travelers-lectern");
    static final Path CONFIG = ROOT.resolve("travelers_lectern_config.txt");
    static final Path FILE = ROOT.resolve("lecterns.json");
    static final Path OLD_FILE = ROOT.resolve("travelers_lecterns.json");
    private static final int FORMAT = 2;
    private static final Logger LOGGER = LogManager.getLogger("TravelersLectern");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<String, Lectern> LECTERNS = new LinkedHashMap<>();
    private static final List<JsonElement> KEPT = new ArrayList<>();
    private static boolean readOnly;
    private static MinecraftServer server;

    private LecternStore() {
    }

    /** Reads debug_logging from the config file, writing the file first if it is missing. */
    static void readConfig() {
        try {
            Files.createDirectories(ROOT);
            if (!Files.exists(CONFIG)) {
                Files.writeString(CONFIG, "debug_logging=false", StandardCharsets.UTF_8);
                return;
            }
            for (String line : Files.readAllLines(CONFIG, StandardCharsets.UTF_8)) {
                if (line.startsWith("debug_logging=")) {
                    TravelersLectern.debugLoggingEnabled = line.substring(line.indexOf('=') + 1).trim().equals("true");
                }
            }
        } catch (IOException e) {
            LOGGER.error("[TL] Could not read {}: {}", CONFIG, e.toString());
        }
    }

    public static void load(MinecraftServer started) {
        server = started;
        LECTERNS.clear();
        KEPT.clear();
        readOnly = false;
        Path reading = FILE;
        try {
            boolean current = Files.exists(FILE);
            boolean old = Files.exists(OLD_FILE);
            if (old && (!current || Files.getLastModifiedTime(OLD_FILE).compareTo(Files.getLastModifiedTime(FILE)) > 0)) {
                reading = OLD_FILE;
                int imported = importOld(JsonParser.parseString(Files.readString(OLD_FILE, StandardCharsets.UTF_8)));
                LOGGER.info("[TL] Imported {} lecterns from {}, which is left as it was", imported, OLD_FILE);
                save();
            } else if (current) {
                readCurrent(JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)));
            }
            TravelersLectern.logDebug("Loaded {} lecterns", LECTERNS.size());
        } catch (Exception e) {
            readOnly = true;
            LECTERNS.clear();
            KEPT.clear();
            LOGGER.error("[TL] Could not read {}: {}. Nothing is saved until the file can be read and the server restarts.", reading, e.toString());
        }
    }

    /** Whether changes can be saved: false before the server starts, and after a failed load. */
    public static boolean writable() {
        return !readOnly && server != null;
    }

    public static boolean save() {
        if (!writable()) {
            return false;
        }
        JsonArray list = new JsonArray();
        for (Lectern lectern : LECTERNS.values()) {
            list.add(toJson(lectern));
        }
        KEPT.forEach(list::add);
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.add("lecterns", list);
        try {
            Files.createDirectories(ROOT);
            replace(FILE, GSON.toJson(root) + "\n");
            return true;
        } catch (IOException e) {
            LOGGER.error("[TL] Could not write {}: {}", FILE, e.toString());
            return false;
        }
    }

    public static Lectern get(String dimension, BlockPos pos) {
        return LECTERNS.get(Lectern.key(dimension, pos));
    }

    /** Every lectern, in the order they were made. */
    public static List<Lectern> all() {
        return new ArrayList<>(LECTERNS.values());
    }

    public static MinecraftServer server() {
        return server;
    }

    /** Adds a lectern holding a copy of the book, and saves. The caller checks {@link #writable()}. */
    static Lectern add(String dimension, BlockPos pos, int cooldown, ItemStack book) {
        Lectern lectern = new Lectern(dimension, pos, cooldown, false, 0L, null, 0, null);
        setBook(lectern, book);
        LECTERNS.put(lectern.key(), lectern);
        save();
        return lectern;
    }

    static void remove(Lectern lectern) {
        LECTERNS.remove(lectern.key());
        save();
    }

    /** Gives the lectern a copy of the book, saved at this server's data version. */
    static void setBook(Lectern lectern, ItemStack book) {
        ItemStack copy = book.copyWithCount(1);
        lectern.setBook(copy, LecternBooks.encode(copy, server.registryAccess()), LecternBooks.dataVersion());
    }

    private static void readCurrent(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        int format = root.get("format").getAsInt();
        if (format > FORMAT) {
            throw new IllegalStateException("a newer release wrote it (format " + format + ")");
        }
        for (JsonElement entry : root.getAsJsonArray("lecterns")) {
            try {
                JsonObject object = entry.getAsJsonObject();
                JsonArray pos = object.getAsJsonArray("pos");
                put(object.get("dimension").getAsString(),
                        new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()),
                        object.get("cooldown").getAsInt(),
                        object.get("bookOut").getAsBoolean(),
                        object.get("takenAt").getAsLong(),
                        object.get("book").getAsString(),
                        object.get("dataVersion").getAsInt());
            } catch (RuntimeException e) {
                LOGGER.error("[TL] A lectern in {} could not be read and is kept as it is: {}", FILE, e.toString());
                KEPT.add(entry);
            }
        }
    }

    // The old file: {"<BlockPos.asLong()>": {"lastTimeUsed": ticks, "cooldown": seconds,
    // "worldKey": {...}, "item": "<SNBT>"}}. Its lastTimeUsed is when a book last left or came
    // back, so a book that is out when the server upgrades returns when the old release would
    // have returned it. Whether the book is out is seen on the first tick.
    private static int importOld(JsonElement json) {
        // Older releases created the file empty when there was nothing to save
        if (json.isJsonNull()) {
            return 0;
        }
        int imported = 0;
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            try {
                JsonObject object = entry.getValue().getAsJsonObject();
                put(oldDimension(object.get("worldKey")), BlockPos.of(Long.parseLong(entry.getKey())),
                        object.get("cooldown").getAsInt(), true, object.get("lastTimeUsed").getAsLong(),
                        object.get("item").getAsString(), LecternBooks.BEFORE_2_0);
                imported++;
            } catch (RuntimeException e) {
                LOGGER.error("[TL] The lectern at {} in {} could not be imported: {}", entry.getKey(), OLD_FILE, e.toString());
            }
        }
        return imported;
    }

    // Gson wrote the dimension's RegistryKey field by field, under intermediary names: the registry
    // (field_25137) and the dimension itself (field_25138), each a namespace and a path
    private static String oldDimension(JsonElement worldKey) {
        if (worldKey.isJsonPrimitive()) {
            return worldKey.getAsString();
        }
        JsonObject location = worldKey.getAsJsonObject().getAsJsonObject("field_25138");
        return location.get("field_13353").getAsString() + ":" + location.get("field_13355").getAsString();
    }

    private static void put(String dimension, BlockPos pos, int cooldown, boolean bookOut, long takenAt, String text, int version) {
        ItemStack book = null;
        try {
            book = LecternBooks.decode(text, version, server.registryAccess());
        } catch (Exception e) {
            LOGGER.error("[TL] The book of the lectern at {} in {} could not be read, so it stays as saved: {}", pos.toShortString(), dimension, e.toString());
        }
        Lectern lectern = new Lectern(dimension, pos, cooldown, bookOut, takenAt, text, version, book);
        LECTERNS.put(lectern.key(), lectern);
    }

    private static JsonObject toJson(Lectern lectern) {
        JsonObject object = new JsonObject();
        object.addProperty("dimension", lectern.dimension());
        JsonArray pos = new JsonArray();
        pos.add(lectern.pos().getX());
        pos.add(lectern.pos().getY());
        pos.add(lectern.pos().getZ());
        object.add("pos", pos);
        object.addProperty("cooldown", lectern.cooldown());
        object.addProperty("bookOut", lectern.bookOut());
        object.addProperty("takenAt", lectern.takenAt());
        object.addProperty("dataVersion", lectern.bookVersion());
        object.addProperty("book", lectern.bookText());
        return object;
    }

    // A temporary file moved into place, with the previous version kept as .bak, so a failed write
    // never leaves a half-written file (Traveler's Journal's JournalFiles.replace)
    private static void replace(Path file, String content) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        if (Files.exists(file)) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
