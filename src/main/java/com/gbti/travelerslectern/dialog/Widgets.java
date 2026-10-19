package com.gbti.travelerslectern.dialog;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
import net.minecraft.world.item.ItemStackTemplate;
//?}
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.List;
import java.util.Optional;

/**
 * Building blocks for the lectern dialogs, after Traveler's Journal's (and Jockey Radio's
 * DialogWidgets). Every dialog keeps the game running (pause false) and stays open after a click
 * (NONE), so the server's reply replaces it; Escape closes it.
 */
final class Widgets {
    static final int WIDE = 300;
    private static final int ICON = 24;
    private static final int CAPTION_MAX = 260;

    private Widgets() {
    }

    static CommonDialogData common(Component title, List<DialogBody> body, List<Input> inputs) {
        return new CommonDialogData(title, Optional.empty(), true, false, DialogAction.NONE, body, inputs);
    }

    static DialogBody line(Component text) {
        return new PlainMessage(text, WIDE);
    }

    // An item drawn in the dialog with its tooltip on hover when asked for. The caption's box is
    // sized to its text (plus the widget's 4 px padding a side), so the icon and caption sit centred.
    static DialogBody icon(ItemStack item, Component caption, boolean tooltip) {
        Optional<PlainMessage> description = caption == null ? Optional.empty()
                : Optional.of(new PlainMessage(caption, Math.min(CAPTION_MAX, TextWidth.of(caption) + 9)));
        //? if >=26.1 {
        return new ItemBody(ItemStackTemplate.fromNonEmptyStack(item), description, true, tooltip, ICON, ICON);
        //?} else {
        /*return new ItemBody(item, description, true, tooltip, ICON, ICON);
        *///?}
    }

    static ActionButton button(String label, int width, String action, CompoundTag additions) {
        return new ActionButton(new CommonButtonData(Component.literal(label), width), Optional.of(custom(action, additions)));
    }

    static ActionButton button(Component label, Component tooltip, int width, String action, CompoundTag additions) {
        return new ActionButton(new CommonButtonData(label, Optional.ofNullable(tooltip), width), Optional.of(custom(action, additions)));
    }

    // Sends every input with the button's additions; an input overwrites an addition of the same key
    private static Action custom(String action, CompoundTag additions) {
        //? if >=1.21.11 {
        return new CustomAll(Identifier.fromNamespaceAndPath(LecternRouter.NAMESPACE, action), Optional.of(additions.copy()));
        //?} else {
        /*return new CustomAll(ResourceLocation.fromNamespaceAndPath(LecternRouter.NAMESPACE, action), Optional.of(additions.copy()));
        *///?}
    }

    // The codec refuses an initial value longer than the box allows, so it is shortened first
    static Input text(String key, Component label, String initial, int maxLength, int width) {
        String value = initial == null ? "" : initial.length() > maxLength ? initial.substring(0, maxLength) : initial;
        return new Input(key, new TextInput(width, label, true, value, maxLength, Optional.empty()));
    }

    static void open(ServerPlayer player, Dialog dialog) {
        player.openDialog(Holder.direct(dialog));
    }

    static void close(ServerPlayer player) {
        player.connection.send(ClientboundClearDialogPacket.INSTANCE);
    }
}
