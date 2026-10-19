package com.gbti.travelerslectern.dialog;

import com.gbti.travelerslectern.Durations;
import com.gbti.travelerslectern.Lectern;
import com.gbti.travelerslectern.LecternBooks;
import com.gbti.travelerslectern.LecternStore;
import com.gbti.travelerslectern.Lecterns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The lectern manager's screens, as picked in sow-027's design round (options A and B together):
 * the list of every Traveler's Lectern, the screen that makes one, its editor and the confirmation
 * that turns one plain. /lecterns opens the lectern the operator faces, or the list. A status line
 * under the title is green for something done and red for something refused.
 */
public final class LecternScreens {
    private static final int BUTTON = 200;
    private static final int HALF = 150;
    static final String MINUTES = "minutes";
    // The box's initial text, so a Save that leaves it alone keeps a cooldown of odd seconds
    static final String SHOWN = "shown";
    private static final int MINUTES_MAX = 6;

    private LecternScreens() {
    }

    /** A line under a screen's title: green for something done, red for something refused. */
    record Status(Component line, String field) {
        static Status done(String text) {
            return new Status(Component.literal(text).withStyle(ChatFormatting.GREEN), null);
        }

        static Status refused(String text, String field) {
            return new Status(Component.literal(text).withStyle(ChatFormatting.RED), field);
        }
    }

    /** The lectern a button is about, carried in the button's additions as its dimension and position. */
    record Target(String dimension, BlockPos pos) {
        static Target of(Lectern lectern) {
            return new Target(lectern.dimension(), lectern.pos());
        }

        CompoundTag tag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension);
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            return tag;
        }

        static Target read(CompoundTag tag) {
            String dimension = tag.getStringOr("dimension", "");
            if (dimension.isEmpty() || !tag.contains("x") || !tag.contains("y") || !tag.contains("z")) {
                return null;
            }
            return new Target(dimension, new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0)));
        }

        Lectern lectern() {
            return LecternStore.get(dimension, pos);
        }
    }

    /** The lectern the operator faces: its editor, or the screen that makes it one. Otherwise the list. */
    public static void open(ServerPlayer player) {
        ServerLevel level = Lecterns.levelOf(player);
        BlockPos pos = Lecterns.facing(player);
        Lectern lectern = Lecterns.at(level, pos);
        if (lectern != null && lectern.book() != null) {
            editor(player, lectern, null, null);
        } else if (Lecterns.canCreate(level, pos)) {
            make(player, level, pos, null, null);
        } else {
            list(player, null);
        }
    }

    static void list(ServerPlayer player, Status status) {
        // A lectern whose saved book this server cannot read is left out; the log names it
        List<Lectern> lecterns = new ArrayList<>();
        for (Lectern lectern : LecternStore.all()) {
            if (lectern.book() != null) lecterns.add(lectern);
        }
        List<DialogBody> body = new ArrayList<>();
        if (status != null) body.add(Widgets.line(status.line()));
        String count = lecterns.isEmpty() ? "Face a lectern that holds a book, then add it here."
                : lecterns.size() == 1 ? "1 lectern." : lecterns.size() + " lecterns.";
        body.add(Widgets.icon(new ItemStack(Items.LECTERN), Component.literal(count), false));

        long now = player.createCommandSourceStack().getLevel().getGameTime();
        List<ActionButton> buttons = new ArrayList<>();
        for (Lectern lectern : lecterns) {
            String tooltip = Lecterns.place(lectern);
            if (lectern.bookOut()) tooltip += "\nBook taken, back in " + Durations.left(lectern.secondsLeft(now));
            buttons.add(Widgets.button(Component.literal(LecternBooks.title(lectern.book())).withStyle(ChatFormatting.YELLOW),
                    Component.literal(tooltip), BUTTON, "edit", Target.of(lectern).tag()));
        }
        buttons.add(Widgets.button("Add the lectern I'm facing", BUTTON, "add", new CompoundTag()));
        Widgets.open(player, new MultiActionDialog(Widgets.common(Component.literal("Traveler's Lecterns"), body, List.of()),
                buttons, Optional.of(Widgets.button("Done", BUTTON, "close", new CompoundTag())), 1));
    }

    static void make(ServerPlayer player, ServerLevel level, BlockPos pos, String minutes, Status status) {
        if (!(level.getBlockEntity(pos) instanceof LecternBlockEntity entity) || !entity.hasBook()) {
            list(player, null);
            return;
        }
        Target target = new Target(Lecterns.dimensionId(level.dimension()), pos);
        List<DialogBody> body = new ArrayList<>();
        if (status != null) body.add(Widgets.line(status.line()));
        body.add(Widgets.icon(entity.getBook(), Component.literal(LecternBooks.caption(entity.getBook())), true));
        body.add(Widgets.line(grey(Lecterns.place(target.dimension(), pos))));
        body.add(Widgets.line(Component.literal("Its book comes back after a player takes it. Only operators can break it.")));
        List<Input> inputs = List.of(minutesBox(minutes == null ? "30" : minutes, status));
        Widgets.open(player, new MultiActionDialog(Widgets.common(Component.literal("New Traveler's Lectern"), body, inputs),
                List.of(Widgets.button("Create", BUTTON, "create", target.tag())),
                Optional.of(Widgets.button("Back", BUTTON, "list", new CompoundTag())), 1));
    }

    static void editor(ServerPlayer player, Lectern lectern, String minutes, Status status) {
        ItemStack book = lectern.book();
        long now = player.createCommandSourceStack().getLevel().getGameTime();
        List<DialogBody> body = new ArrayList<>();
        if (status != null) body.add(Widgets.line(status.line()));
        body.add(Widgets.icon(book, Component.literal(LecternBooks.caption(book)), true));
        body.add(Widgets.line(grey(Lecterns.place(lectern))));
        body.add(Widgets.line(lectern.bookOut()
                ? Component.literal("Taken " + Durations.ago(lectern.secondsSinceTaken(now)) + " ago. Back in "
                        + Durations.left(lectern.secondsLeft(now)) + ".").withStyle(ChatFormatting.YELLOW)
                : Component.literal("The book is on the lectern.")));
        List<Input> inputs = List.of(minutesBox(minutes == null ? minutesOf(lectern) : minutes, status));

        CompoundTag target = Target.of(lectern).tag();
        CompoundTag save = target.copy();
        save.putString(SHOWN, minutesOf(lectern));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Widgets.button("Save", HALF, "save", save));
        buttons.add(Widgets.button("Give me a copy", HALF, "give", target));
        if (lectern.bookOut()) buttons.add(Widgets.button("Put the book back now", HALF, "put_back", target));
        buttons.add(Widgets.button("Use the book in my hand", HALF, "use_hand", target));
        buttons.add(Widgets.button("Teleport there", HALF, "teleport", target));
        buttons.add(Widgets.button("Turn into a plain lectern", HALF, "plain_ask", target));
        Widgets.open(player, new MultiActionDialog(Widgets.common(Component.literal(LecternBooks.title(book)), body, inputs),
                buttons, Optional.of(Widgets.button("Back", BUTTON, "list", new CompoundTag())), 2));
    }

    static void confirmPlain(ServerPlayer player, Lectern lectern) {
        ItemStack book = lectern.book();
        List<DialogBody> body = List.of(
                Widgets.icon(book, Component.literal(LecternBooks.caption(book)), true),
                Widgets.line(Component.literal("The book stays on the lectern. Once a player takes it, it is gone for good, "
                        + "and anyone can break the lectern.")),
                Widgets.line(grey(Lecterns.place(lectern))));
        CompoundTag target = Target.of(lectern).tag();
        Widgets.open(player, new ConfirmationDialog(
                Widgets.common(Component.literal("Turn " + LecternBooks.title(book) + " into a plain lectern?"), body, List.of()),
                Widgets.button("Make it plain", HALF, "plain", target), Widgets.button("Keep it", HALF, "edit", target)));
    }

    /** The cooldown as the box shows it: whole minutes, rounded up. */
    static String minutesOf(Lectern lectern) {
        return Long.toString((lectern.cooldown() + 59L) / 60);
    }

    private static Input minutesBox(String value, Status status) {
        Component label = Component.literal("Respawn after, in minutes");
        if (status != null && MINUTES.equals(status.field())) label = label.copy().withStyle(ChatFormatting.RED);
        return Widgets.text(MINUTES, label, value, MINUTES_MAX, BUTTON);
    }

    private static Component grey(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }
}
