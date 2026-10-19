package com.gbti.travelerslectern.probe;

import com.gbti.travelerslectern.Lectern;
import com.gbti.travelerslectern.LecternStore;
import com.gbti.travelerslectern.Lecterns;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.gbti.travelerslectern.probe.LecternProbe.FILE;
import static com.gbti.travelerslectern.probe.LecternProbe.OLD_FILE;
import static com.gbti.travelerslectern.probe.LecternProbe.ROOT;

/**
 * The upgrade test for scripts/test-matrix.sh: a server that ran Traveler's Lectern 1.2.x must keep
 * its lecterns when the jar is replaced, and keep them if it goes back. The harness runs the
 * boots on one world.
 *
 * "upgrade-before" and "downgrade-old" run beside the OLD jar, so they use vanilla code, files and
 * reflection only: the old jar has none of this version's classes. "upgrade-after" runs beside the
 * new jar and checks that every file the old jar left is byte for byte the same and every lectern
 * in it was imported. "downgrade-new" runs the new jar, adds a lectern and keeps a copy of the old
 * file; "downgrade-old" then checks the old jar finds that file as it was and loads it.
 */
class UpgradeProbe {
    // What the old jar left, copied by the harness after the first boot
    private static final Path SNAPSHOT = Path.of("config-before/travelers-lectern");
    // The old file as the new jar found it, kept outside the mod's folder
    private static final Path OLD_COPY = Path.of("config/travelers-lectern-probe-old.json");
    private static final BlockPos ADDED = new BlockPos(16, -60, 4);

    private final String mode;
    private final ProbeSupport.Checks checks = new ProbeSupport.Checks();
    private int countdown = -1;

    UpgradeProbe(String mode) {
        this.mode = mode;
    }

    private int expected() {
        return switch (mode) {
            case "upgrade-before" -> 2;
            case "upgrade-after" -> 4;
            case "downgrade-new" -> 2;
            case "downgrade-old" -> 3;
            default -> 0;
        };
    }

    void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ProbeSupport.LOG.info("[TL-PROBE] START {} expect={}", mode, expected());
            ProbeSupport.command(server, server.createCommandSourceStack(), "forceload add -32 -32 31 31");
            countdown = LecternProbe.JOIN_TICKS + LecternProbe.SETTLE_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (countdown > 0 && --countdown == 0) {
                checks.guarded(() -> {
                    switch (mode) {
                        case "upgrade-before" -> before();
                        case "upgrade-after" -> after();
                        case "downgrade-new" -> downgradeNew(server);
                        case "downgrade-old" -> downgradeOld();
                        default -> checks.check("mode", false, "unknown probe mode " + mode);
                    }
                });
                checks.done();
            }
        });
    }

    private void before() {
        oldJarRunning();
        checks.check("old-config-written", Files.exists(ROOT.resolve("travelers_lectern_config.txt")), "no config file");
    }

    private void after() throws Exception {
        String changed = "";
        try (Stream<Path> files = Files.list(SNAPSHOT)) {
            for (Path file : files.toList()) {
                Path now = ROOT.resolve(file.getFileName().toString());
                if (!Files.exists(now) || !Arrays.equals(Files.readAllBytes(file), Files.readAllBytes(now))) {
                    changed += file.getFileName() + " ";
                }
            }
        }
        checks.check("old-files-untouched", changed.isEmpty(), "changed: " + changed);
        int before = oldEntries(SNAPSHOT.resolve(OLD_FILE.getFileName().toString()));
        List<Lectern> all = LecternStore.all();
        checks.check("imported-all", all.size() == before && (before == 0 || Files.exists(FILE)),
                all.size() + " imported of " + before);
        checks.check("books-readable", all.stream().allMatch(lectern -> lectern.book() != null),
                all.stream().filter(lectern -> lectern.book() == null).count() + " unreadable");
        checks.check("writable", LecternStore.writable(), "read-only after the upgrade");
    }

    private void downgradeNew(MinecraftServer server) throws Exception {
        if (Files.exists(OLD_FILE)) Files.copy(OLD_FILE, OLD_COPY);
        LecternTools.place(server.overworld(), ADDED, LecternTools.book("Added By 2.0", "Probe", "a"));
        checks.check("added", Lecterns.create(server.overworld(), ADDED, 30) != null && Files.exists(FILE), "not saved");
        checks.check("old-file-not-written", !Files.exists(OLD_COPY) || Arrays.equals(Files.readAllBytes(OLD_FILE), Files.readAllBytes(OLD_COPY)),
                "the old file changed");
    }

    private void downgradeOld() throws Exception {
        oldJarRunning();
        // A fresh 2.0 install never wrote the old file, so the old jar starts it empty
        boolean asLeft = Files.exists(OLD_COPY) ? Arrays.equals(Files.readAllBytes(OLD_FILE), Files.readAllBytes(OLD_COPY))
                : oldEntries(OLD_FILE) == 0;
        checks.check("old-file-as-left", asLeft, "the old file differs from the one 2.0 left");
        Map<?, ?> loaded = (Map<?, ?>) Class.forName("com.gbti.travelerslectern.TravelersLectern").getField("lecterns").get(null);
        int entries = oldEntries(OLD_FILE);
        checks.check("old-jar-loaded-file", loaded.size() == entries, loaded.size() + " loaded of " + entries);
    }

    private void oldJarRunning() {
        String version = FabricLoader.getInstance().getModContainer("travelers_lectern")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("none");
        checks.check("old-jar-running", version.startsWith("1."), "travelers_lectern " + version);
    }

    // Entries in a 1.2.x file; the old jar creates it empty when it has nothing to save
    private static int oldEntries(Path file) throws Exception {
        if (!Files.exists(file)) return 0;
        JsonElement json = JsonParser.parseString(Files.readString(file));
        return json.isJsonObject() ? json.getAsJsonObject().size() : 0;
    }
}
