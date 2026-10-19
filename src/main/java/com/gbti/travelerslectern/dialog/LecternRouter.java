package com.gbti.travelerslectern.dialog;

import com.gbti.travelerslectern.Durations;
import com.gbti.travelerslectern.Lectern;
import com.gbti.travelerslectern.LecternCommands;
import com.gbti.travelerslectern.LecternStore;
import com.gbti.travelerslectern.Lecterns;
import com.gbti.travelerslectern.TravelersLectern;
import com.gbti.travelerslectern.dialog.LecternScreens.Status;
import com.gbti.travelerslectern.dialog.LecternScreens.Target;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Handles a click on a lectern dialog button. Nothing the client sends is trusted: the player's
 * level is checked again on every click, the lectern is looked up again, and the minutes are
 * checked before anything is saved. Runs on the server thread (see CustomClickActionMixin).
 */
public final class LecternRouter {
    public static final String NAMESPACE = "travelers_lectern";
    private static final String GONE = "That lectern no longer exists.";
    private static final String NO_BOOK = "Hold a written book or a book and quill, then try again.";

    private LecternRouter() {
    }

    public static void handle(ServerPlayer player, String action, Optional<Tag> payload) {
        CompoundTag tag = payload.isPresent() && payload.get() instanceof CompoundTag compound ? compound : new CompoundTag();
        if (!LecternCommands.allowed(player.createCommandSourceStack())) {
            player.sendSystemMessage(Component.literal("Only operators can manage lecterns.").withStyle(ChatFormatting.RED));
            Widgets.close(player);
            return;
        }
        switch (action) {
            case "close" -> Widgets.close(player);
            case "list" -> LecternScreens.list(player, null);
            case "add" -> add(player);
            case "create" -> create(player, tag);
            default -> {
                Target target = Target.read(tag);
                Lectern lectern = target == null ? null : target.lectern();
                if (lectern == null || lectern.book() == null) {
                    LecternScreens.list(player, Status.refused(GONE, null));
                } else {
                    onLectern(player, action, lectern, tag);
                }
            }
        }
    }

    private static void onLectern(ServerPlayer player, String action, Lectern lectern, CompoundTag tag) {
        String minutes = tag.getStringOr(LecternScreens.MINUTES, LecternScreens.minutesOf(lectern));
        switch (action) {
            case "edit" -> LecternScreens.editor(player, lectern, null, null);
            case "save" -> save(player, lectern, minutes, tag.getStringOr(LecternScreens.SHOWN, ""));
            case "give" -> {
                Lecterns.give(player, lectern.book());
                LecternScreens.editor(player, lectern, minutes, Status.done("A copy is in your inventory."));
            }
            case "teleport" -> {
                if (Teleports.toLectern(player, lectern)) {
                    Widgets.close(player);
                } else {
                    LecternScreens.list(player, Status.refused(GONE, null));
                }
            }
            case "plain_ask" -> LecternScreens.confirmPlain(player, lectern);
            default -> {
                if (!LecternStore.writable()) {
                    LecternScreens.editor(player, lectern, minutes, Status.refused(Lecterns.READ_ONLY, null));
                    return;
                }
                change(player, action, lectern, minutes);
            }
        }
    }

    private static void change(ServerPlayer player, String action, Lectern lectern, String minutes) {
        switch (action) {
            case "put_back" -> {
                Lecterns.putBack(lectern);
                LecternScreens.editor(player, lectern, minutes, null);
            }
            case "use_hand" -> {
                if (Lecterns.useBookInHand(player, lectern)) {
                    LecternScreens.editor(player, lectern, minutes, saved(lectern));
                } else {
                    LecternScreens.editor(player, lectern, minutes, Status.refused(NO_BOOK, null));
                }
            }
            case "plain" -> {
                Lecterns.makePlain(lectern);
                LecternScreens.list(player, null);
            }
            default -> TravelersLectern.logDebug("Ignored lectern dialog action {}", action);
        }
    }

    private static void add(ServerPlayer player) {
        ServerLevel level = Lecterns.levelOf(player);
        BlockPos pos = Lecterns.facing(player);
        Lectern lectern = Lecterns.at(level, pos);
        if (lectern != null && lectern.book() != null) {
            LecternScreens.editor(player, lectern, null, null);
        } else if (Lecterns.canCreate(level, pos)) {
            LecternScreens.make(player, level, pos, null, null);
        } else {
            LecternScreens.list(player, Status.refused("Look at a lectern that holds a book, up to 20 blocks away, and try again.", null));
        }
    }

    private static void create(ServerPlayer player, CompoundTag tag) {
        Target target = Target.read(tag);
        ServerLevel level = Lecterns.levelOf(player);
        if (target == null || !target.dimension().equals(Lecterns.dimensionId(level.dimension()))) {
            LecternScreens.list(player, Status.refused(GONE, null));
            return;
        }
        Lectern existing = target.lectern();
        if (existing != null && existing.book() != null) {
            // Created already, by a second click or another operator
            LecternScreens.editor(player, existing, null, null);
            return;
        }
        String minutes = tag.getStringOr(LecternScreens.MINUTES, "");
        if (!Lecterns.canCreate(level, target.pos())) {
            LecternScreens.list(player, Status.refused("Look at a lectern that holds a book, up to 20 blocks away, and try again.", null));
            return;
        }
        Integer seconds = seconds(minutes);
        if (seconds == null) {
            LecternScreens.make(player, level, target.pos(), minutes, badMinutes());
            return;
        }
        if (!LecternStore.writable()) {
            LecternScreens.make(player, level, target.pos(), minutes, Status.refused(Lecterns.READ_ONLY, null));
            return;
        }
        Lectern created = Lecterns.create(level, target.pos(), seconds);
        if (created == null) {
            LecternScreens.list(player, Status.refused(GONE, null));
        } else {
            LecternScreens.editor(player, created, null, saved(created));
        }
    }

    private static void save(ServerPlayer player, Lectern lectern, String minutes, String shown) {
        // A box left as it was keeps the saved cooldown, which may be odd seconds the box rounds up
        if (!minutes.trim().equals(shown)) {
            Integer seconds = seconds(minutes);
            if (seconds == null) {
                LecternScreens.editor(player, lectern, minutes, badMinutes());
                return;
            }
            if (!LecternStore.writable()) {
                LecternScreens.editor(player, lectern, minutes, Status.refused(Lecterns.READ_ONLY, null));
                return;
            }
            Lecterns.setCooldown(lectern, seconds);
        }
        LecternScreens.editor(player, lectern, null, saved(lectern));
    }

    // Whole minutes, 1 or more; the box holds six digits, checked again here because a client can send anything
    private static Integer seconds(String minutes) {
        String value = minutes.trim();
        if (value.isEmpty() || value.length() > 6 || !value.chars().allMatch(Character::isDigit)) {
            return null;
        }
        int parsed = Integer.parseInt(value);
        return parsed < 1 ? null : parsed * 60;
    }

    private static Status badMinutes() {
        return Status.refused("Respawn after takes a whole number of minutes, 1 or more. Nothing was saved.", LecternScreens.MINUTES);
    }

    private static Status saved(Lectern lectern) {
        return Status.done("Saved. A new copy appears " + Durations.of(lectern.cooldown()) + " after one is taken.");
    }
}
