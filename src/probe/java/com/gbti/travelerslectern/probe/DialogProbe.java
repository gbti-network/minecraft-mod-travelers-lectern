package com.gbti.travelerslectern.probe;

import com.gbti.travelerslectern.Lectern;
import com.gbti.travelerslectern.LecternStore;
import com.gbti.travelerslectern.Lecterns;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.gbti.travelerslectern.probe.LecternTools.NETHER;
import static com.gbti.travelerslectern.probe.LecternTools.OVERWORLD;
import static com.gbti.travelerslectern.probe.LecternTools.bookOn;
import static com.gbti.travelerslectern.probe.LecternTools.carries;
import static com.gbti.travelerslectern.probe.LecternTools.get;
import static com.gbti.travelerslectern.probe.LecternTools.hasBook;
import static com.gbti.travelerslectern.probe.LecternTools.title;
import static com.gbti.travelerslectern.probe.ProbeSupport.command;
import static com.gbti.travelerslectern.probe.ProbeSupport.oneLine;

/**
 * The lectern manager, on 1.21.6 and later only (the build leaves this file out of older jars).
 * A mock operator opens the dialogs by command and presses their buttons as a client does: each
 * press takes the action and additions of a button in the dialog the server sent, overlays every
 * input's value, and goes through the click packet's codec, vanilla's listener and the mod's mixin.
 * What the server sends back is read from the mock player's channel.
 */
final class DialogProbe {
    static final int CHECKS = 24;
    private static final BlockPos DESK = new BlockPos(-8, -60, -8);
    private static final BlockPos FAR = new BlockPos(20, 64, -20);

    private final ProbeSupport.Checks checks;
    private ServerPlayer carol;
    private Dialog shown;
    private boolean cleared;
    private String chat = "";

    DialogProbe(ProbeSupport.Checks checks) {
        this.checks = checks;
    }

    void run(MinecraftServer server) throws Exception {
        ServerLevel overworld = server.overworld();
        carol = ProbeSupport.join(server, "ProbeCarol");
        command(server, server.createCommandSourceStack(), "op ProbeCarol");

        // Facing a plain lectern that holds a book, /lecterns offers to make it one
        LecternTools.place(overworld, DESK, LecternTools.book("Dialog Book", "Probe", "Desk page"));
        LecternTools.stand(carol, DESK);
        as(server, "lecterns");
        checks.check("dialog-opens-make", heading().equals("New Traveler's Lectern") && "30".equals(initial("minutes"))
                && labels().contains("Create") && body().contains("Dialog Book by Probe, 1 page")
                && body().contains("Its book comes back after a player takes it. Only operators can break it."), describe());
        press("Create", Map.of("minutes", "abc"));
        checks.check("dialog-create-bad-minutes", heading().equals("New Traveler's Lectern") && get(OVERWORLD, DESK) == null && redLabel("minutes")
                && body().contains("Respawn after takes a whole number of minutes, 1 or more. Nothing was saved."), describe());
        press("Create", Map.of("minutes", "5"));
        Lectern desk = get(OVERWORLD, DESK);
        checks.check("dialog-create", desk != null && desk.cooldown() == 300 && heading().equals("Dialog Book")
                && body().contains("Saved. A new copy appears 5 minutes after one is taken.") && body().contains("The book is on the lectern.")
                && labels().containsAll(List.of("Save", "Give me a copy", "Use the book in my hand", "Teleport there", "Turn into a plain lectern"))
                && !labels().contains("Put the book back now"), describe());

        // Facing a Traveler's Lectern, /lecterns opens its editor; facing nothing, the list
        as(server, "lecterns");
        checks.check("dialog-opens-editor", heading().equals("Dialog Book") && "5".equals(initial("minutes")), describe());
        LecternTools.lookUp(carol);
        as(server, "lecterns");
        long readable = LecternStore.all().stream().filter(lectern -> lectern.book() != null).count();
        checks.check("dialog-opens-list", heading().equals("Traveler's Lecterns") && body().contains(readable + " lecterns.")
                && labels().contains("Dialog Book") && labels().contains("Add the lectern I'm facing") && exit().equals("Done")
                && yellow("Dialog Book"), describe());
        press("Add the lectern I'm facing", Map.of());
        checks.check("dialog-add-nothing", heading().equals("Traveler's Lecterns")
                && body().contains("Look at a lectern that holds a book, up to 20 blocks away, and try again."), describe());

        // A Save that leaves the box alone keeps a cooldown of odd seconds the box rounds up
        Lecterns.setCooldown(desk, 15);
        press("Dialog Book", Map.of());
        boolean shownAsOne = "1".equals(initial("minutes"));
        press("Save", Map.of());
        checks.check("dialog-save-keeps-odd", shownAsOne && desk.cooldown() == 15
                && body().contains("Saved. A new copy appears 15 seconds after one is taken."), "shown as one " + shownAsOne + ", " + describe());
        press("Save", Map.of("minutes", "2"));
        checks.check("dialog-save-minutes", desk.cooldown() == 120 && body().contains("Saved. A new copy appears 2 minutes after one is taken."),
                describe());
        press("Save", Map.of("minutes", "0"));
        checks.check("dialog-save-bad", desk.cooldown() == 120 && redLabel("minutes")
                && body().contains("Respawn after takes a whole number of minutes, 1 or more. Nothing was saved."), describe());

        press("Give me a copy", Map.of());
        checks.check("dialog-give-copy", carries(carol, "Dialog Book") == 1 && body().contains("A copy is in your inventory."), describe());
        carol.getInventory().clearContent();
        press("Use the book in my hand", Map.of());
        checks.check("dialog-use-hand-empty", heading().equals("Dialog Book")
                && body().contains("Hold a written book or a book and quill, then try again."), describe());
        carol.setItemInHand(InteractionHand.MAIN_HAND, LecternTools.book("Swapped Book", "Carol", "Swapped page"));
        press("Use the book in my hand", Map.of());
        checks.check("dialog-use-hand", heading().equals("Swapped Book") && title(get(OVERWORLD, DESK)).equals("Swapped Book")
                && title(bookOn(overworld, DESK)).equals("Swapped Book") && body().contains("Saved."), describe());
        carol.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // With the book out, the editor says when it returns and offers to put it back
        LecternTools.click(carol, overworld, DESK, true);
        LecternTools.stand(carol, DESK);
        as(server, "lecterns");
        checks.check("dialog-book-out", body().contains("Taken ") && body().contains(" ago. Back in 2 minutes.")
                && labels().contains("Put the book back now"), describe());
        press("Back", Map.of());
        String tooltip = tooltip("Swapped Book");
        checks.check("dialog-list-tooltip", tooltip.contains("Overworld at -8, -60, -8") && tooltip.contains("Book taken, back in 2 minutes"),
                oneLine(tooltip));
        press("Swapped Book", Map.of());
        press("Put the book back now", Map.of());
        checks.check("dialog-put-back", hasBook(overworld, DESK) && !get(OVERWORLD, DESK).bookOut()
                && body().contains("The book is on the lectern."), describe());

        // Teleport there lands where a reader stands, in front of the lectern, in its dimension
        carol.setPos(0.5, -60, 0.5);
        press("Teleport there", Map.of());
        Vec3 reader = Vec3.atBottomCenterOf(DESK.north());
        checks.check("dialog-teleport", cleared && at(carol).distanceTo(reader) < 0.01, "at " + at(carol) + ", cleared " + cleared);
        ServerLevel nether = server.getLevel(Level.NETHER);
        LecternTools.place(nether, FAR, LecternTools.book("Far Book", "Probe", "Far page"));
        Lecterns.create(nether, FAR, 60);
        LecternTools.lookUp(carol);
        as(server, "lecterns");
        press("Far Book", Map.of());
        press("Teleport there", Map.of());
        ServerLevel landed = carol.createCommandSourceStack().getLevel();
        checks.check("dialog-teleport-nether", landed.dimension().equals(Level.NETHER) && at(carol).distanceTo(Vec3.atBottomCenterOf(FAR.north())) < 0.01,
                "in " + Lecterns.dimensionId(landed.dimension()) + " at " + at(carol));
        carol.teleportTo(overworld, 0.5, -60, 0.5, Set.of(), 0.0F, 0.0F, false);

        // Turning one plain asks first, and puts the book back when it is out
        LecternTools.click(carol, overworld, DESK, true);
        LecternTools.stand(carol, DESK);
        as(server, "lecterns");
        press("Turn into a plain lectern", Map.of());
        checks.check("dialog-plain-ask", shown instanceof ConfirmationDialog && heading().equals("Turn Swapped Book into a plain lectern?")
                && labels().containsAll(List.of("Make it plain", "Keep it")), describe());
        press("Keep it", Map.of());
        checks.check("dialog-keep-it", heading().equals("Swapped Book") && get(OVERWORLD, DESK) != null, describe());
        press("Turn into a plain lectern", Map.of());
        press("Make it plain", Map.of());
        checks.check("dialog-make-plain", get(OVERWORLD, DESK) == null && hasBook(overworld, DESK) && heading().equals("Traveler's Lecterns"),
                "book on it " + hasBook(overworld, DESK) + ", " + describe());

        // Nothing the client sends is trusted: the level is checked on every click, the lectern looked up again
        command(server, server.createCommandSourceStack(), "deop ProbeCarol");
        press("Done", Map.of());
        checks.check("dialog-refuses-non-op", cleared && chat.contains("Only operators can manage lecterns."), "chat " + oneLine(chat));
        command(server, server.createCommandSourceStack(), "op ProbeCarol");
        CompoundTag forged = new CompoundTag();
        forged.putString("dimension", OVERWORLD);
        forged.putInt("x", 999);
        forged.putInt("y", 0);
        forged.putInt("z", 999);
        send(new ServerboundCustomClickActionPacket(id("edit"), Optional.of(forged)));
        checks.check("dialog-missing-lectern", heading().equals("Traveler's Lecterns") && body().contains("That lectern no longer exists."), describe());
        press("Done", Map.of());
        checks.check("dialog-done-closes", cleared && shown == null, "cleared " + cleared + ", " + describe());

        // With every lectern made plain, the list says how to add one
        for (Lectern lectern : LecternStore.all()) {
            Lecterns.makePlain(lectern);
        }
        LecternTools.lookUp(carol);
        as(server, "lecterns");
        checks.check("dialog-empty-list", heading().equals("Traveler's Lecterns") && body().contains("Face a lectern that holds a book, then add it here.")
                && labels().equals(List.of("Add the lectern I'm facing")), describe());
        server.getPlayerList().remove(carol);
    }

    // Where the player stands, read through the command source: Entity.position() changed its
    // intermediary name in 1.21.9, so the probe built for 1.21.6 could not call it there
    private static Vec3 at(ServerPlayer player) {
        return player.createCommandSourceStack().getPosition();
    }

    // Presses a button of the dialog on screen as a client does: the button's own action and
    // additions, then every input's value over them, typed here or as the dialog shows it
    private void press(String label, Map<String, String> typed) {
        ActionButton button = buttons().stream().filter(candidate -> candidate.button().label().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no button '" + label + "' on " + describe()));
        Action action = button.action().orElseThrow(() -> new IllegalStateException("button '" + label + "' does nothing"));
        if (!(action instanceof CustomAll custom)) throw new IllegalStateException("button '" + label + "' sends " + action);
        CompoundTag payload = custom.additions().map(CompoundTag::copy).orElseGet(CompoundTag::new);
        for (Input input : common().inputs()) {
            payload.putString(input.key(), typed.containsKey(input.key()) ? typed.get(input.key()) : initial(input.key()));
        }
        send(new ServerboundCustomClickActionPacket(custom.id(), Optional.of(payload)));
    }

    // Through the click packet's codec, as it crosses the network, then vanilla's listener
    private void send(ServerboundCustomClickActionPacket packet) {
        drain();
        ByteBuf buffer = Unpooled.buffer();
        ServerboundCustomClickActionPacket received;
        try {
            ServerboundCustomClickActionPacket.STREAM_CODEC.encode(buffer, packet);
            received = ServerboundCustomClickActionPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
        carol.connection.handleCustomClickAction(received);
        read();
    }

    private void as(MinecraftServer server, String commandLine) {
        drain();
        command(server, carol.createCommandSourceStack(), commandLine);
        read();
    }

    private void drain() {
        EmbeddedChannel channel = ProbeSupport.channel(carol);
        channel.flushOutbound();
        while (channel.readOutbound() != null) {
            // everything sent before this step is not about it
        }
        shown = null;
        cleared = false;
        chat = "";
    }

    private void read() {
        EmbeddedChannel channel = ProbeSupport.channel(carol);
        channel.flushOutbound();
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            if (message instanceof ClientboundShowDialogPacket packet) shown = packet.dialog().value();
            if (message instanceof ClientboundClearDialogPacket) cleared = true;
            if (message instanceof ClientboundSystemChatPacket packet) chat += packet.content().getString() + "\n";
        }
    }

    private List<ActionButton> buttons() {
        List<ActionButton> all = new ArrayList<>();
        if (shown instanceof MultiActionDialog dialog) {
            all.addAll(dialog.actions());
            dialog.exitAction().ifPresent(all::add);
        }
        if (shown instanceof ConfirmationDialog dialog) {
            all.add(dialog.yesButton());
            all.add(dialog.noButton());
        }
        return all;
    }

    // The buttons above the footer
    private List<String> labels() {
        if (shown instanceof MultiActionDialog dialog) return dialog.actions().stream().map(button -> button.button().label().getString()).toList();
        return buttons().stream().map(button -> button.button().label().getString()).toList();
    }

    private String exit() {
        return shown instanceof MultiActionDialog dialog ? dialog.exitAction().map(button -> button.button().label().getString()).orElse("") : "";
    }

    private String tooltip(String label) {
        return buttons().stream().filter(button -> button.button().label().getString().equals(label)).findFirst()
                .flatMap(button -> button.button().tooltip()).map(Component::getString).orElse("");
    }

    private boolean yellow(String label) {
        return buttons().stream().filter(button -> button.button().label().getString().equals(label))
                .anyMatch(button -> Objects.equals(button.button().label().getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.YELLOW)));
    }

    // An input's value as the dialog shows it before anything is typed
    private String initial(String key) {
        if (common() == null) return null;
        for (Input input : common().inputs()) {
            if (input.key().equals(key) && input.control() instanceof TextInput box) return box.initial();
        }
        return null;
    }

    private CommonDialogData common() {
        if (shown instanceof MultiActionDialog dialog) return dialog.common();
        if (shown instanceof ConfirmationDialog dialog) return dialog.common();
        return null;
    }

    private String heading() {
        return common() == null ? "" : common().title().getString();
    }

    private String body() {
        if (common() == null) return "";
        StringBuilder text = new StringBuilder();
        for (DialogBody part : common().body()) {
            if (part instanceof PlainMessage message) text.append(message.contents().getString()).append('\n');
            if (part instanceof ItemBody item) item.description().ifPresent(caption -> text.append(caption.contents().getString()).append('\n'));
        }
        return text.toString();
    }

    private boolean redLabel(String key) {
        if (common() == null) return false;
        return common().inputs().stream().anyMatch(input -> input.key().equals(key) && input.control() instanceof TextInput box
                && Objects.equals(box.label().getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.RED)));
    }

    private String describe() {
        return shown == null ? "nothing shown" : shown.getClass().getSimpleName() + " '" + heading() + "': " + oneLine(body()) + " buttons " + labels();
    }

    //? if >=1.21.11 {
    private static Identifier id(String action) {
        return Identifier.fromNamespaceAndPath("travelers_lectern", action);
    }
    //?} else {
    /*private static ResourceLocation id(String action) {
        return ResourceLocation.fromNamespaceAndPath("travelers_lectern", action);
    }
    *///?}
}
