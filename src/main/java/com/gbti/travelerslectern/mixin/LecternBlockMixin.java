package com.gbti.travelerslectern.mixin;

import com.gbti.travelerslectern.FileManager;
import com.gbti.travelerslectern.TravelersLectern;
import com.gbti.travelerslectern.utils.LecternObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import static net.minecraft.block.LecternBlock.HAS_BOOK;
import static net.minecraft.block.LecternBlock.putBookIfAbsent;

@Mixin(LecternBlock.class)
public class LecternBlockMixin {

    /**
     * Overrides the onUseWithItem method to handle custom lectern behavior.
     * This method is called when a player interacts with a lectern using an item.
     *
     * @param stack The ItemStack being used
     * @param state The current BlockState
     * @param world The World instance
     * @param pos The BlockPos of the lectern
     * @param player The PlayerEntity interacting with the lectern
     * @param hand The Hand being used
     * @param hit The BlockHitResult of the interaction
     * @return ItemActionResult indicating the result of the interaction
     */
    /**
     * @author gbti-network
     * @reason Handle custom behavior for using items with lecterns
     */
    @Overwrite
    public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if(world.getBlockEntity(pos) instanceof LecternBlockEntity be && !TravelersLectern.lecterns.isEmpty() && TravelersLectern.lecterns.containsKey(pos.asLong())) {
            if(state.get(HAS_BOOK)) {
                boolean isAdmin = player instanceof ServerPlayerEntity && ((ServerPlayerEntity) player).hasPermissionLevel(4);
                LecternObject lectern = TravelersLectern.lecterns.get(pos.asLong());
                long timeSinceLastUse = world.getTime() - lectern.getLastTimeUsed();
                long cooldownTicks = lectern.getCooldown() * 20L;
                boolean cooldownElapsed = timeSinceLastUse >= cooldownTicks;

                TravelersLectern.logDebug("Lectern interaction at {}: Book present, Admin: {}, Time since last use: {}s, Cooldown: {}s, Elapsed: {}", 
                    pos, isAdmin, timeSinceLastUse/20, cooldownTicks/20, cooldownElapsed);

                // Start cooldown as soon as player interacts with lectern
                if (cooldownElapsed) {
                    lectern.setLastTimeUsed(world.getTime());
                    FileManager.saveLecterns();
                    TravelersLectern.logDebug("Starting new cooldown for lectern at {}", pos);
                }

                // First, ensure the lectern has the correct book content
                try {
                    NbtCompound storedNBT = StringNbtReader.parse(lectern.getItem().asString());
                    Optional<ItemStack> storedBookOpt = ItemStack.fromNbt(world.getRegistryManager(), storedNBT);
                    
                    if (storedBookOpt.isPresent()) {
                        ItemStack storedBook = storedBookOpt.get();
                        
                        // Update the lectern's book if it doesn't match
                        if (!ItemStack.areEqual(be.getBook(), storedBook)) {
                            TravelersLectern.logDebug("Updating lectern book content at {}", pos);
                            be.setBook(storedBook.copy());
                        }

                        // If player is sneaking or clicks Take Book button, attempt to take the book
                        if (player.isSneaking()) {
                            if (isAdmin || cooldownElapsed) {
                                TravelersLectern.logDebug("Player taking book from lectern at {}", pos);
                                player.giveItemStack(storedBook.copy());
                                be.setBook(ItemStack.EMPTY);
                                LecternBlock.setHasBook(player, world, pos, state, false);
                                return ItemActionResult.success(world.isClient);
                            } else {
                                if (player instanceof ServerPlayerEntity) {
                                    ((ServerPlayerEntity) player).sendMessage(Text.literal("This book will be available again in " + 
                                        ((cooldownTicks - timeSinceLastUse) / 20) + " seconds."), true);
                                }
                                return ItemActionResult.success(world.isClient);
                            }
                        }
                        
                        // If not sneaking, open the lectern GUI (vanilla behavior)
                        if (!player.isSneaking()) {
                            TravelersLectern.logDebug("Opening lectern GUI at {}", pos);
                            player.openHandledScreen(be);
                            return ItemActionResult.success(world.isClient);
                        }
                    } else {
                        TravelersLectern.logError("Failed to create ItemStack from NBT at {}", pos);
                    }
                } catch (CommandSyntaxException e) {
                    TravelersLectern.logError("Error parsing NBT data for lectern at {}: {}", pos, e.getMessage());
                }
            }
            return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        } else if(state.get(HAS_BOOK)) {
            TravelersLectern.logDebug("Non-travelers lectern interaction at {}", pos);
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else if (stack.isIn(ItemTags.LECTERN_BOOKS)) {
            return putBookIfAbsent(player, world, pos, state, stack) ? ItemActionResult.success(world.isClient) : ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        } else {
            return stack.isEmpty() && hand == Hand.MAIN_HAND ? ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }

}
