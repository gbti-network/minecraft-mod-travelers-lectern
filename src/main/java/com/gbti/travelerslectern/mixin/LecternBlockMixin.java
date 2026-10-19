package com.gbti.travelerslectern.mixin;

import com.gbti.travelerslectern.Lecterns;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.InteractionResult;
//? if >=1.20.5 {
import net.minecraft.world.item.ItemStack;
//?}
//? if >=1.20.5 <1.21.2 {
/*import net.minecraft.world.ItemInteractionResult;
*///?}

/**
 * Hands a click on a Traveler's Lectern to {@link Lecterns#use}; every other lectern behaves as in
 * vanilla. The click is the lectern's use method, renamed useItemOn in 1.20.5, whose result type
 * became InteractionResult again in 1.21.2. useItemOn runs for an empty hand too, before
 * useWithoutItem, so one injection covers every click.
 */
@Mixin(LecternBlock.class)
public abstract class LecternBlockMixin {
    //? if >=1.21.2 {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void travelers_lectern$use(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (Lecterns.use(state, level, pos, player)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
    //?} else if >=1.20.5 {
    /*@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void travelers_lectern$use(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (Lecterns.use(state, level, pos, player)) {
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
        }
    }
    *///?} else {
    /*@Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void travelers_lectern$use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (Lecterns.use(state, level, pos, player)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
    *///?}
}
