package com.gbti.travelerslectern;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.6 {
import com.gbti.travelerslectern.dialog.LecternScreens;
//?}
//? if >=1.21.11 {
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionLevel;
//?}

/**
 * /travelers_lectern create, edit and destroy act on the lectern the operator faces, with the
 * arguments and replies of releases before 2.0.0; the cooldown is in seconds. /lecterns opens the
 * lectern manager on 1.21.6 and later.
 */
public final class LecternCommands {
    /** The operator level the commands, the manager and breaking a Traveler's Lectern need. */
    static final int LEVEL = 4;
    private static final String PLAYER_ONLY = "A player is required to run this command here";
    private static final String NOT_ONE = "Make sure the block you are facing is a travelers lectern";

    private LecternCommands() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("travelers_lectern")
                .requires(LecternCommands::allowed)
                .then(Commands.literal("create")
                    .executes(context -> create(context, 1800))
                    .then(Commands.argument("time", IntegerArgumentType.integer(0))
                        .executes(context -> create(context, IntegerArgumentType.getInteger(context, "time")))))
                .then(Commands.literal("edit")
                    .then(Commands.argument("time", IntegerArgumentType.integer(0))
                        .executes(context -> edit(context, IntegerArgumentType.getInteger(context, "time")))))
                .then(Commands.literal("destroy").executes(LecternCommands::destroy)));
            dispatcher.register(Commands.literal("lecterns")
                .requires(LecternCommands::allowed)
                .executes(LecternCommands::manager));
        });
    }

    /** Whether a command source may use the commands and the manager. */
    public static boolean allowed(CommandSourceStack source) {
        //? if >=1.21.11 {
        return new PermissionCheck.Require(new Permission.HasCommandLevel(PermissionLevel.byId(LEVEL))).check(source.permissions());
        //?} else {
        /*return source.hasPermission(LEVEL);
        *///?}
    }

    private static int create(CommandContext<CommandSourceStack> context, int seconds) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return reply(context, PLAYER_ONLY);
        }
        if (!LecternStore.writable()) {
            return refuse(context, Lecterns.READ_ONLY);
        }
        ServerLevel level = context.getSource().getLevel();
        return reply(context, Lecterns.create(level, Lecterns.facing(player), seconds) != null
                ? "Successfully created travelers lectern" : "Make sure the block you are facing is a lectern and has a book");
    }

    private static int edit(CommandContext<CommandSourceStack> context, int seconds) {
        Lectern lectern = faced(context);
        if (lectern == null) {
            return context.getSource().getPlayer() == null ? reply(context, PLAYER_ONLY) : reply(context, NOT_ONE);
        }
        if (!LecternStore.writable()) {
            return refuse(context, Lecterns.READ_ONLY);
        }
        Lecterns.setCooldown(lectern, seconds);
        return reply(context, "Successfully edited travelers lectern");
    }

    private static int destroy(CommandContext<CommandSourceStack> context) {
        Lectern lectern = faced(context);
        if (lectern == null) {
            return context.getSource().getPlayer() == null ? reply(context, PLAYER_ONLY) : reply(context, NOT_ONE);
        }
        if (!LecternStore.writable()) {
            return refuse(context, Lecterns.READ_ONLY);
        }
        Lecterns.makePlain(lectern);
        return reply(context, "Successfully destroyed travelers lectern");
    }

    private static int manager(CommandContext<CommandSourceStack> context) {
        //? if >=1.21.6 {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return reply(context, PLAYER_ONLY);
        }
        LecternScreens.open(player);
        return 1;
        //?} else {
        /*return reply(context, "Dialogs need Minecraft 1.21.6 or newer. Face the lectern and use /travelers_lectern create, edit or destroy.");
        *///?}
    }

    private static Lectern faced(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        return player == null ? null : Lecterns.at(context.getSource().getLevel(), Lecterns.facing(player));
    }

    private static int reply(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int refuse(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.RED), false);
        return 1;
    }
}
