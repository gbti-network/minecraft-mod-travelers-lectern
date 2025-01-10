package com.gbti.travelerslectern.utils;

import static com.gbti.travelerslectern.TravelersLectern.getBlockPlayerIsLooking;
import static com.gbti.travelerslectern.TravelersLectern.lecterns;

import com.gbti.travelerslectern.FileManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class LecternObject {

    private long lastTimeUsed;
    private int cooldown;
    private RegistryKey<World> worldKey;
    private NbtElement item;

    public LecternObject(long lastTimeUsed, int cooldown, RegistryKey<World> worldKey, NbtElement item) {
        this.lastTimeUsed = lastTimeUsed;
        this.cooldown = cooldown;
        this.worldKey = worldKey;
        this.item = item;
    }

    public long getLastTimeUsed() {
        return lastTimeUsed;
    }

    public void setLastTimeUsed(long lastTimeUsed) {
        this.lastTimeUsed = lastTimeUsed;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public RegistryKey<World> getWorldKey() {
        return worldKey;
    }

    public void setWorldKey(RegistryKey<World> worldKey) {
        this.worldKey = worldKey;
    }

    public NbtElement getItem() {
        return item;
    }

    public void setItem(NbtElement item) {
        this.item = item;
    }

    public static void lecternCommand() {

        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("travelers_lectern").requires(source -> {
            if(source instanceof ServerCommandSource serverSource) return serverSource.hasPermissionLevel(4);
            else return false;
        }).then(CommandManager.literal("create").then(CommandManager.argument("time", IntegerArgumentType.integer(0)).executes(ctx -> {
            if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                if(createTravelersLectern(player, IntegerArgumentType.getInteger(ctx, "time"))) {
                    ctx.getSource().sendFeedback(() -> Text.literal("Successfully created travelers lectern"), false);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Make sure the block you are facing is a lectern and has a book"), false);
                }
            } else ctx.getSource().sendFeedback(() -> Text.literal("A player is required to run this command here"), false);
            return 1;
        })).executes(ctx -> {
            if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                if(createTravelersLectern(player, 1800)) {
                    ctx.getSource().sendFeedback(() -> Text.literal("Successfully created travelers lectern"), false);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Make sure the block you are facing is a lectern and has a book"), false);
                }
            } else ctx.getSource().sendFeedback(() -> Text.literal("A player is required to run this command here"), false);
            return 1;
        })).then(CommandManager.literal("edit").then(CommandManager.argument("time", IntegerArgumentType.integer(0)).executes(ctx -> {
            if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                if(editTravelersLectern(player, IntegerArgumentType.getInteger(ctx, "time"))) {
                    ctx.getSource().sendFeedback(() -> Text.literal("Successfully edited travelers lectern"), false);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Make sure the block you are facing is a travelers lectern"), false);
                }
            } else ctx.getSource().sendFeedback(() -> Text.literal("A player is required to run this command here"), false);
            return 1;
        }))).then(CommandManager.literal("destroy").executes(ctx -> {
            if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                if(destroyTravelersLectern(player)) {
                    ctx.getSource().sendFeedback(() -> Text.literal("Successfully destroyed travelers lectern"), false);
                } else {
                    ctx.getSource().sendFeedback(() -> Text.literal("Make sure the block you are facing is a travelers lectern"), false);
                }
            } else ctx.getSource().sendFeedback(() -> Text.literal("A player is required to run this command here"), false);
            return 1;
        })))));
    }


    public static boolean createTravelersLectern(ServerPlayerEntity player, int time) {
        BlockHitResult hitResult = getBlockPlayerIsLooking(player);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockState block = player.getWorld().getBlockState(hitResult.getBlockPos());

            if(!lecterns.containsKey(hitResult.getBlockPos().asLong()) && block.getBlock() instanceof LecternBlock && player.getWorld().getBlockEntity(hitResult.getBlockPos()) instanceof LecternBlockEntity be) {
                if(be.hasBook()) {
                    lecterns.put(hitResult.getBlockPos().asLong(), new LecternObject(player.getWorld().getTime(), time, player.getWorld().getRegistryKey(),
                            be.getBook().encode(player.getWorld().getRegistryManager()))); // we encode the item as nbt element
                    FileManager.saveLecterns();
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean editTravelersLectern(ServerPlayerEntity player, int time) {
        BlockHitResult hitResult = getBlockPlayerIsLooking(player);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockState block = player.getWorld().getBlockState(hitResult.getBlockPos());

            if(lecterns.containsKey(hitResult.getBlockPos().asLong()) && block.getBlock() instanceof LecternBlock && player.getWorld().getBlockEntity(hitResult.getBlockPos()) instanceof LecternBlockEntity be) {
                lecterns.get(hitResult.getBlockPos().asLong()).setCooldown(time);
                FileManager.saveLecterns();
                return true;
            }
        }
        return false;
    }

    public static boolean destroyTravelersLectern(ServerPlayerEntity player) {
        BlockHitResult hitResult = getBlockPlayerIsLooking(player);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockState block = player.getWorld().getBlockState(hitResult.getBlockPos());

            if(lecterns.containsKey(hitResult.getBlockPos().asLong()) && block.getBlock() instanceof LecternBlock && player.getWorld().getBlockEntity(hitResult.getBlockPos()) instanceof LecternBlockEntity be) {
                lecterns.remove(hitResult.getBlockPos().asLong());
                FileManager.saveLecterns();
                return true;
            }
        }
        return false;
    }

}
