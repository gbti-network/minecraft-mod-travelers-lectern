package com.gbti.travelerslectern.probe;

import com.gbti.travelerslectern.Lectern;
import com.gbti.travelerslectern.LecternStore;
import com.gbti.travelerslectern.Lecterns;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.gbti.travelerslectern.probe.LecternTools.NETHER;
import static com.gbti.travelerslectern.probe.LecternTools.OVERWORLD;
import static com.gbti.travelerslectern.probe.LecternTools.bookOn;
import static com.gbti.travelerslectern.probe.LecternTools.carries;
import static com.gbti.travelerslectern.probe.LecternTools.click;
import static com.gbti.travelerslectern.probe.LecternTools.get;
import static com.gbti.travelerslectern.probe.LecternTools.hasBook;
import static com.gbti.travelerslectern.probe.LecternTools.place;
import static com.gbti.travelerslectern.probe.LecternTools.stand;
import static com.gbti.travelerslectern.probe.LecternTools.title;
import static com.gbti.travelerslectern.probe.ProbeSupport.command;
import static com.gbti.travelerslectern.probe.ProbeSupport.join;
import static com.gbti.travelerslectern.probe.ProbeSupport.oneLine;
import static com.gbti.travelerslectern.probe.ProbeSupport.said;

/**
 * Test mod for the boot harness, never shipped. Loaded beside the packaged Traveler's Lectern jar
 * on a throwaway server, it joins mock players the way a real login does and checks what players
 * and operators get. It starts with "[TL-PROBE] START expect=N", logs "[TL-PROBE] PASS name" or
 * "[TL-PROBE] FAIL name: detail" for each check, and ends with "[TL-PROBE] DONE pass=N fail=M".
 *
 * Before the server starts it installs the lecterns a 1.2.x release left
 * (src/probe/fixtures/past-install), so the first checks are the import. The rest run in steps a
 * second or more apart, because the book comes back on the mod's once-a-second tick: the commands
 * and taking the book, then the respawn, then on 1.21.6 and later the manager (DialogProbe).
 */
public class LecternProbe implements ModInitializer {
    static final Path ROOT = Path.of("config/travelers-lectern");
    static final Path FILE = ROOT.resolve("lecterns.json");
    static final Path OLD_FILE = ROOT.resolve("travelers_lecterns.json");
    private static final Path MODE = Path.of("config/travelers-lectern-probe.txt");
    // The mock players join once the server is ticking and the force-loaded spawn area has loaded
    // (a join from inside SERVER_STARTED can block forever waiting for entities to load)
    static final int JOIN_TICKS = 40;
    static final int SETTLE_TICKS = 60;
    // More than a second, so the mod's tick has run in between
    static final int TICK_WAIT = 30;
    static final int CHECKS = 26;
    //? if >=1.21.6 {
    private static final int DIALOG_CHECKS = DialogProbe.CHECKS;
    //?} else {
    /*private static final int DIALOG_CHECKS = 0;
    *///?}

    // Lecterns the checks make, inside the force-loaded area of the flat world
    static final BlockPos MADE = new BlockPos(4, -60, 4);
    static final BlockPos PLAIN = new BlockPos(-4, -60, 4);
    static final BlockPos GUARDED = new BlockPos(8, -60, 4);
    static final BlockPos TWIN = new BlockPos(0, 100, 0);
    static final BlockPos UNMADE = new BlockPos(12, -60, 4);

    private final ProbeSupport.Checks checks = new ProbeSupport.Checks();
    private byte[] fixture = new byte[0];
    private int joinCountdown = -1;
    private int takeCountdown = -1;
    private int respawnCountdown = -1;
    private ServerPlayer op;
    private ServerPlayer alice;

    @Override
    public void onInitialize() {
        // test-matrix.sh writes this file to run the halves of an upgrade or world test instead
        String mode = mode();
        if (mode.startsWith("world-")) {
            new WorldUpgradeProbe(mode.equals("world-before")).register();
            return;
        }
        if (mode.startsWith("upgrade-") || mode.startsWith("downgrade-")) {
            new UpgradeProbe(mode).register();
            return;
        }
        installFixture();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ProbeSupport.LOG.info("[TL-PROBE] START expect={}", CHECKS + DIALOG_CHECKS);
            command(server, server.createCommandSourceStack(), "forceload add -32 -32 31 31");
            joinCountdown = JOIN_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (joinCountdown > 0 && --joinCountdown == 0) {
                checks.guarded(() -> imported(server));
                checks.guarded(() -> commandsAndTaking(server));
                takeCountdown = TICK_WAIT;
            } else if (takeCountdown > 0 && --takeCountdown == 0) {
                checks.guarded(() -> afterTick(server));
                respawnCountdown = TICK_WAIT;
            } else if (respawnCountdown > 0 && --respawnCountdown == 0) {
                checks.guarded(() -> readOnly(server));
                //? if >=1.21.6 {
                checks.guarded(() -> new DialogProbe(checks).run(server));
                //?}
                // Last, so it covers every packet the checks above made the server send
                checks.check("packets-encode", PacketCheck.FAILURES.isEmpty() && PacketCheck.encoded > 0, PacketCheck.summary());
                checks.done();
            }
        });
    }

    private static String mode() {
        try {
            return Files.exists(MODE) ? Files.readString(MODE).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    // The lecterns Traveler's Lectern 1.2.x left on a 1.21.1 server, in its file and format
    private void installFixture() {
        try (InputStream in = LecternProbe.class.getResourceAsStream("/past-install/travelers_lecterns.json")) {
            fixture = in.readAllBytes();
            if (!Files.exists(FILE) && !Files.exists(OLD_FILE)) {
                Files.createDirectories(ROOT);
                Files.write(OLD_FILE, fixture);
            }
        } catch (IOException e) {
            throw new IllegalStateException("probe fixture missing", e);
        }
    }

    private void imported(MinecraftServer server) throws Exception {
        List<Lectern> all = LecternStore.all();
        checks.check("import-old-file", all.size() == 3 && Files.exists(FILE), all.size() + " lecterns, new file " + Files.exists(FILE));
        long overworld = all.stream().filter(lectern -> lectern.dimension().equals(OVERWORLD)).count();
        long nether = all.stream().filter(lectern -> lectern.dimension().equals(NETHER)).count();
        checks.check("import-dimensions", overworld == 2 && nether == 1, overworld + " in the overworld, " + nether + " in the Nether");
        Set<Integer> cooldowns = all.stream().map(Lectern::cooldown).collect(Collectors.toCollection(TreeSet::new));
        checks.check("import-cooldowns", cooldowns.equals(Set.of(10, 15, 86400)), "cooldowns " + cooldowns);
        Lectern quests = get(OVERWORLD, new BlockPos(24, -60, 20));
        //? if >=1.21 {
        // Minecraft's own item upgrades bring the 1.21.1 book to this version, styled pages and all
        List<String> pages = LecternTools.pages(quests == null ? null : quests.book());
        Set<String> titles = all.stream().map(LecternTools::title).collect(Collectors.toCollection(TreeSet::new));
        checks.check("import-books", titles.equals(Set.of("Probe Rules", "Probe Quests", "Probe Nether Notes"))
                && pages.equals(List.of("Hello Traveler\n", "Second page")), "titles " + titles + ", pages " + pages);
        //?} else {
        /*// A 1.21.1 book cannot be read by 1.20: it is kept as saved, and the new file carries it unchanged
        boolean kept = all.stream().allMatch(lectern -> lectern.book() == null)
                && Files.readString(FILE).contains("Second page");
        checks.check("import-books", kept, "unread " + all.stream().filter(lectern -> lectern.book() == null).count());
        *///?}
        checks.check("old-file-untouched", Arrays.equals(Files.readAllBytes(OLD_FILE), fixture), "the 1.2.x file changed");

        // The new file reads back the same
        LecternStore.load(server);
        Set<Integer> reloaded = LecternStore.all().stream().map(Lectern::cooldown).collect(Collectors.toCollection(TreeSet::new));
        checks.check("store-round-trip", LecternStore.all().size() == 3 && reloaded.equals(cooldowns)
                && title(get(OVERWORLD, new BlockPos(24, -60, 20))).equals(title(quests)), "after reload " + LecternStore.all().size()
                + " lecterns, cooldowns " + reloaded);
    }

    private void commandsAndTaking(MinecraftServer server) throws Exception {
        ServerLevel overworld = server.overworld();
        op = join(server, "ProbeOp");
        command(server, server.createCommandSourceStack(), "op ProbeOp");
        alice = join(server, "ProbeAlice");

        // An operator faces a lectern holding a book and makes it a Traveler's Lectern
        place(overworld, MADE, LecternTools.book("Made Book", "Probe", "Made page"));
        stand(op, MADE);
        String created = said(server, op.createCommandSourceStack(), "travelers_lectern create 60");
        Lectern made = get(OVERWORLD, MADE);
        checks.check("command-create", created.contains("Successfully created travelers lectern") && made != null
                && made.cooldown() == 60 && title(made).equals("Made Book"), oneLine(created) + " " + LecternTools.place(made));
        String edited = said(server, op.createCommandSourceStack(), "travelers_lectern edit 120");
        checks.check("command-edit", edited.contains("Successfully edited travelers lectern") && made.cooldown() == 120, oneLine(edited));
        place(overworld, UNMADE, LecternTools.book("Unmade Book", "Probe", "u"));
        stand(alice, UNMADE);
        String refused = said(server, alice.createCommandSourceStack(), "travelers_lectern create");
        checks.check("command-needs-operator", get(OVERWORLD, UNMADE) == null && !refused.contains("Successfully"), oneLine(refused));
        //? if >=1.21.6 {
        String manager = said(server, "lecterns");
        checks.check("lecterns-command", manager.contains("A player is required to run this command here"), oneLine(manager));
        //?} else {
        /*String manager = said(server, op.createCommandSourceStack(), "lecterns");
        checks.check("lecterns-command", manager.contains("Dialogs need Minecraft 1.21.6 or newer. Face the lectern and use "
                + "/travelers_lectern create, edit or destroy."), oneLine(manager));
        *///?}

        // Alice sneaks with an empty hand and takes the book
        long before = overworld.getGameTime();
        click(alice, overworld, MADE, true);
        checks.check("sneak-takes-book", carries(alice, "Made Book") == 1 && !hasBook(overworld, MADE) && made.bookOut()
                && made.takenAt() >= before, "carries " + carries(alice, "Made Book") + ", " + LecternTools.place(made));
        // The empty Traveler's Lectern says when its book returns, and takes no other book
        LecternTools.drain(alice);
        alice.setItemInHand(InteractionHand.MAIN_HAND, LecternTools.book("Other Book", "Alice", "o"));
        click(alice, overworld, MADE, false);
        String bar = LecternTools.actionBar(alice);
        checks.check("empty-says-when", bar.contains("The book returns in 2 minutes."), oneLine(bar));
        checks.check("empty-refuses-other-book", !hasBook(overworld, MADE) && title(alice.getMainHandItem()).equals("Other Book"),
                "on the lectern " + title(bookOn(overworld, MADE)));
        alice.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // A cooldown of 0 seconds brings it back on the next tick
        said(server, op.createCommandSourceStack(), "travelers_lectern edit 0");

        // Reading opens vanilla's screen and leaves the timer alone; its Take Book button takes the book
        place(overworld, TWIN, LecternTools.book("Overworld Twin", "Probe", "t"));
        Lecterns.create(overworld, TWIN, 30);
        Lectern twin = get(OVERWORLD, TWIN);
        long takenAt = twin.takenAt();
        click(alice, overworld, TWIN, false);
        checks.check("reading-keeps-timer", alice.containerMenu instanceof LecternMenu && !twin.bookOut() && twin.takenAt() == takenAt,
                "menu " + alice.containerMenu.getClass().getSimpleName() + ", " + LecternTools.place(twin));
        alice.containerMenu.clickMenuButton(alice, 3);
        alice.closeContainer();
        checks.check("button-takes-book", carries(alice, "Overworld Twin") == 1 && !hasBook(overworld, TWIN),
                "carries " + carries(alice, "Overworld Twin"));

        // A plain lectern stays vanilla: a book goes on, the screen opens, its button takes the book
        place(overworld, PLAIN, null);
        alice.setItemInHand(InteractionHand.MAIN_HAND, LecternTools.book("Plain Book", "Alice", "p"));
        click(alice, overworld, PLAIN, false);
        boolean placed = hasBook(overworld, PLAIN) && alice.getMainHandItem().isEmpty();
        click(alice, overworld, PLAIN, false);
        boolean opened = alice.containerMenu instanceof LecternMenu;
        alice.containerMenu.clickMenuButton(alice, 3);
        alice.closeContainer();
        checks.check("plain-lectern-vanilla", placed && opened && !hasBook(overworld, PLAIN) && carries(alice, "Plain Book") == 1
                && get(OVERWORLD, PLAIN) == null, "placed " + placed + ", opened " + opened + ", carries " + carries(alice, "Plain Book"));

        // Only operators can break a Traveler's Lectern
        place(overworld, GUARDED, LecternTools.book("Guarded Book", "Probe", "g"));
        Lecterns.create(overworld, GUARDED, 30);
        alice.gameMode.destroyBlock(GUARDED);
        checks.check("break-refused", overworld.getBlockState(GUARDED).is(Blocks.LECTERN) && get(OVERWORLD, GUARDED) != null, "broken");
        op.gameMode.destroyBlock(GUARDED);
        checks.check("break-by-operator", !overworld.getBlockState(GUARDED).is(Blocks.LECTERN) && get(OVERWORLD, GUARDED) == null,
                "still " + LecternTools.place(get(OVERWORLD, GUARDED)));

        // The same x, y, z in two dimensions are two lecterns
        ServerLevel nether = server.getLevel(Level.NETHER);
        place(nether, TWIN, LecternTools.book("Nether Twin", "Probe", "n"));
        Lecterns.create(nether, TWIN, 30);
        LecternStore.load(server);
        checks.check("same-place-two-dimensions", title(get(OVERWORLD, TWIN)).equals("Overworld Twin")
                && title(get(NETHER, TWIN)).equals("Nether Twin"), LecternTools.place(get(OVERWORLD, TWIN)) + " / "
                + LecternTools.place(get(NETHER, TWIN)));
    }

    private void afterTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        Lectern made = get(OVERWORLD, MADE);
        checks.check("respawn-after-cooldown", hasBook(overworld, MADE) && !made.bookOut()
                && title(bookOn(overworld, MADE)).equals("Made Book"), LecternTools.place(made));
        Lectern twin = get(OVERWORLD, TWIN);
        checks.check("button-take-noticed", twin.bookOut() && twin.takenAt() > 0, LecternTools.place(twin));
        // Anyone may take it again at once: the wait starts when it leaves, not when it returns.
        // Empty hands first, since vanilla skips a sneaking click with anything in hand.
        alice.getInventory().clearContent();
        click(alice, overworld, MADE, true);
        checks.check("take-right-after-respawn", carries(alice, "Made Book") == 1 && !hasBook(overworld, MADE),
                "carries " + carries(alice, "Made Book"));
        // destroy while the book is out puts it back first, so the lectern is never left empty
        stand(op, MADE);
        String destroyed = said(server, op.createCommandSourceStack(), "travelers_lectern destroy");
        checks.check("destroy-puts-book-back", destroyed.contains("Successfully destroyed travelers lectern") && get(OVERWORLD, MADE) == null
                && hasBook(overworld, MADE), oneLine(destroyed) + ", book on it " + hasBook(overworld, MADE));
    }

    // A file the store cannot read is never written over: saving says so until it can be read again
    private void readOnly(MinecraftServer server) throws Exception {
        byte[] good = Files.readAllBytes(FILE);
        int count = LecternStore.all().size();
        Files.writeString(FILE, "{ not json");
        LecternStore.load(server);
        place(server.overworld(), UNMADE, LecternTools.book("Unmade Book", "Probe", "u"));
        stand(op, UNMADE);
        String reply = said(server, op.createCommandSourceStack(), "travelers_lectern create");
        checks.check("broken-file-read-only", !LecternStore.writable() && Files.readString(FILE).equals("{ not json")
                && reply.contains("The lectern file could not be read, so nothing was saved. See the server log."), oneLine(reply));
        Files.write(FILE, good);
        LecternStore.load(server);
        checks.check("readable-again", LecternStore.writable() && LecternStore.all().size() == count,
                LecternStore.all().size() + " of " + count);
    }
}
