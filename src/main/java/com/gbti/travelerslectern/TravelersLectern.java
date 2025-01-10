package com.gbti.travelerslectern;

import com.gbti.travelerslectern.utils.LecternObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TravelersLectern implements ModInitializer {
    public static final String MOD_ID = "travelers_lectern";
    public static Map<Long, LecternObject> lecterns = new HashMap<>();
    public static Map<UUID, Integer> playersBreaks;
    private static final Logger LOGGER = LogManager.getLogger("TravelersLectern");
    public static boolean debugLoggingEnabled = false;

    public static void logDebug(String message, Object... params) {
        if (debugLoggingEnabled) {
            LOGGER.info("[TL Debug] " + message, params);
        }
    }

    public static void logError(String message, Object... params) {
        LOGGER.error(message, params);
    }

    @Override
    public void onInitialize() {
        // First read the config files to set up debug logging
        FileManager.readFiles();
        
        // Now we can start logging
        LOGGER.info("[TL] Debug logging status: {}", debugLoggingEnabled);
        if (debugLoggingEnabled) {
            logDebug("[TL] Initializing Travelers Lectern Mod with debug logging enabled");
        }
        
        playersBreaks = new HashMap<>();

        // Handles TL items respawning
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(server.getOverworld().getTime() % 20 == 0) {  // Every second
                lecterns.forEach((pos, lecternObject) -> {
                    World world = server.getWorld(lecternObject.getWorldKey());
                    BlockPos blockPos = BlockPos.fromLong(pos);
                    
                    if(world != null) {
                        long timeSinceLastUse = world.getTime() - lecternObject.getLastTimeUsed();
                        long cooldownTicks = lecternObject.getCooldown() * 20L;
                        
                        logDebug("Checking lectern at {}: Time since last use: {}s, Cooldown: {}s", 
                            blockPos, timeSinceLastUse/20, cooldownTicks/20);
                        
                        if(timeSinceLastUse >= cooldownTicks) {
                            BlockState state = world.getBlockState(blockPos);

                            if (world.getBlockEntity(blockPos) instanceof LecternBlockEntity be && 
                                state.getBlock().equals(Blocks.LECTERN)) {
                                
                                // Only respawn if there's no book
                                if (!state.get(LecternBlock.HAS_BOOK)) {
                                    try {
                                        logDebug("Respawning book in lectern at {} after cooldown", blockPos);
                                        NbtCompound storedNBT = StringNbtReader.parse(lecternObject.getItem().asString());
                                        Optional<ItemStack> storedBookOpt = ItemStack.fromNbt(world.getRegistryManager(), storedNBT);
                                        
                                        if (storedBookOpt.isPresent()) {
                                            ItemStack storedBook = storedBookOpt.get();
                                            world.addBlockBreakParticles(blockPos, state);
                                            be.setBook(storedBook.copy());
                                            LecternBlock.setHasBook(null, world, blockPos, state, true);
                                            logDebug("Successfully respawned book in lectern at {}", blockPos);
                                            
                                            // Reset the lastTimeUsed to current time to prevent immediate re-spawning
                                            lecternObject.setLastTimeUsed(world.getTime());
                                            FileManager.saveLecterns();
                                        } else {
                                            logError("Failed to create ItemStack from NBT when respawning book at {}", blockPos);
                                        }
                                    } catch (CommandSyntaxException e) {
                                        logError("Error respawning book in lectern at {}: {}", blockPos, e.getMessage());
                                    }
                                } else {
                                    logDebug("Lectern at {} already has a book, skipping respawn", blockPos);
                                }
                            }
                        } else {
                            logDebug("Lectern at {} still on cooldown. {}s remaining", 
                                blockPos, (cooldownTicks - timeSinceLastUse)/20);
                        }
                    }
                });

            }

        });

        // Makes the TL and TC unbreakable by non-admin players
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
                if(state.getBlock().equals(Blocks.LECTERN) && blockEntity instanceof LecternBlockEntity && lecterns.containsKey(pos.asLong())) {
                    if(!player.hasPermissionLevel(4)) {
                        if(playersBreaks.getOrDefault(player.getUuid(), 1) >= 3) {
                            playersBreaks.put(player.getUuid(), 0);
                            player.sendMessage(Text.literal("This lectern is protected by a mysterious force."));
                        } else playersBreaks.put(player.getUuid(), playersBreaks.getOrDefault(player.getUuid(), 0) + 1);
                        return false;
                    } else {
                        lecterns.remove(pos.asLong());
                        FileManager.saveLecterns();
                    }
                }

                return true;
            }
        );

    
        // Load all configs and stored data
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FileManager.loadLecterns();
            TravelersLectern.lecterns.forEach((pos, lecternObject) -> {
                RegistryKey<World> worldKey = server.getWorldRegistryKeys().stream().filter(key -> key.toString().equals(lecternObject.getWorldKey().toString())).findFirst().orElse(null);
                if(worldKey != null) lecternObject.setWorldKey(worldKey);
            });
        });

        // TL and TC commands
        LecternObject.lecternCommand();
        
        logDebug("[TL] Initialization complete. Debug logging is {}", debugLoggingEnabled ? "enabled" : "disabled");
    }

    public static BlockHitResult getBlockPlayerIsLooking(ServerPlayerEntity player) {
        double maxDistance = 20.0D; // range of the player

        Vec3d eyePosition = player.getCameraPosVec(1.0F);
        Vec3d lookDirection = player.getRotationVec(1.0F).multiply(maxDistance);
        Vec3d targetPosition = eyePosition.add(lookDirection);

        // send raycast to determine what block the player is looking
        return player.getWorld().raycast(new RaycastContext(eyePosition, targetPosition, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, player
        ));
    }
}