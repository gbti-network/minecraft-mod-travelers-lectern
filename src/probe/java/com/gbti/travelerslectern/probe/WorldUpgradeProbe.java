package com.gbti.travelerslectern.probe;

import com.gbti.travelerslectern.Lectern;
import com.gbti.travelerslectern.Lecterns;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static com.gbti.travelerslectern.probe.LecternTools.OVERWORLD;
import static com.gbti.travelerslectern.probe.LecternTools.bookOn;
import static com.gbti.travelerslectern.probe.LecternTools.get;
import static com.gbti.travelerslectern.probe.LecternTools.hasBook;
import static com.gbti.travelerslectern.probe.LecternTools.title;

/**
 * The world move for scripts/test-matrix.sh: a server moving its world to a later Minecraft keeps
 * its Traveler's Lecterns. "world-before" runs on the older version and makes two, one whose book
 * stays on it and one whose book a player took. "world-after" opens the same world and config on
 * the later version: both books still read (Minecraft's own item upgrades, from NBT books before
 * 1.20.5 and across 1.21.5's change to stored text), the taken one is still out, and it comes back
 * once its cooldown has passed.
 */
class WorldUpgradeProbe {
    private static final BlockPos KEPT = new BlockPos(6, -60, 10);
    private static final BlockPos TAKEN = new BlockPos(10, -60, 10);

    private final boolean before;
    private final ProbeSupport.Checks checks = new ProbeSupport.Checks();
    private int countdown = -1;
    private int respawnCountdown = -1;

    WorldUpgradeProbe(boolean before) {
        this.before = before;
    }

    void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ProbeSupport.LOG.info("[TL-PROBE] START {} expect={}", before ? "world-before" : "world-after", before ? 2 : 4);
            ProbeSupport.command(server, server.createCommandSourceStack(), "forceload add -32 -32 31 31");
            countdown = LecternProbe.JOIN_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (countdown > 0 && --countdown == 0) {
                checks.guarded(() -> {
                    if (before) make(server);
                    else reopened(server);
                });
                if (before) checks.done();
                else respawnCountdown = LecternProbe.TICK_WAIT;
            } else if (respawnCountdown > 0 && --respawnCountdown == 0) {
                checks.guarded(() -> returned(server));
                checks.done();
            }
        });
    }

    private void make(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        LecternTools.place(overworld, KEPT, LecternTools.book("World Kept", "Probe", "Kept page"));
        LecternTools.place(overworld, TAKEN, LecternTools.book("World Taken", "Probe", "Taken page"));
        Lecterns.create(overworld, KEPT, 86400);
        Lecterns.create(overworld, TAKEN, 86400);
        checks.check("before-made", title(get(OVERWORLD, KEPT)).equals("World Kept") && title(get(OVERWORLD, TAKEN)).equals("World Taken"),
                LecternTools.place(get(OVERWORLD, KEPT)) + " / " + LecternTools.place(get(OVERWORLD, TAKEN)));
        ServerPlayer reader = LecternTools.joinEmpty(server, "LecternMover");
        LecternTools.click(reader, overworld, TAKEN, true);
        checks.check("before-taken", get(OVERWORLD, TAKEN).bookOut() && !hasBook(overworld, TAKEN), LecternTools.place(get(OVERWORLD, TAKEN)));
        server.getPlayerList().remove(reader);
    }

    private void reopened(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        Lectern kept = get(OVERWORLD, KEPT);
        Lectern taken = get(OVERWORLD, TAKEN);
        checks.check("after-books-read", title(kept).equals("World Kept") && title(taken).equals("World Taken")
                && LecternTools.pages(taken == null ? null : taken.book()).equals(List.of("Taken page")),
                LecternTools.place(kept) + " / " + LecternTools.place(taken));
        checks.check("after-book-on-lectern", hasBook(overworld, KEPT) && title(bookOn(overworld, KEPT)).equals("World Kept"),
                "on the lectern '" + title(bookOn(overworld, KEPT)) + "'");
        checks.check("after-still-out", taken != null && taken.bookOut() && !hasBook(overworld, TAKEN), LecternTools.place(taken));
        if (taken != null) Lecterns.setCooldown(taken, 0);
    }

    private void returned(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        checks.check("after-book-returns", hasBook(overworld, TAKEN) && title(bookOn(overworld, TAKEN)).equals("World Taken"),
                "on the lectern '" + title(bookOn(overworld, TAKEN)) + "', " + LecternTools.place(get(OVERWORLD, TAKEN)));
    }
}
