package com.gbti.travelerslectern.probe;

import com.gbti.travelerslectern.Lectern;
import com.gbti.travelerslectern.LecternStore;
import com.gbti.travelerslectern.Lecterns;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
*///?}

import java.util.ArrayList;
import java.util.List;

/** Lecterns, books and a mock player's hands for the probes, the same way on every version. */
final class LecternTools {
    static final String OVERWORLD = "minecraft:overworld";
    static final String NETHER = "minecraft:the_nether";

    private LecternTools() {
    }

    static ItemStack book(String title, String author, String... pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        //? if >=1.20.5 {
        List<Filterable<Component>> list = new ArrayList<>();
        for (String page : pages) list.add(Filterable.passThrough(Component.literal(page)));
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, list, true));
        //?} else {
        /*CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putInt("generation", 0);
        tag.putBoolean("resolved", true);
        ListTag list = new ListTag();
        for (String page : pages) list.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        tag.put("pages", list);
        *///?}
        return stack;
    }

    /** The text of a written book's pages, one per entry. */
    static List<String> pages(ItemStack book) {
        List<String> pages = new ArrayList<>();
        if (book == null) return pages;
        //? if >=1.20.5 {
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content != null) {
            for (Filterable<Component> page : content.pages()) pages.add(page.raw().getString());
        }
        //?} else {
        /*CompoundTag tag = book.getTag();
        if (tag != null) {
            ListTag list = tag.getList("pages", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                Component page = Component.Serializer.fromJson(list.getString(i));
                pages.add(page == null ? "" : page.getString());
            }
        }
        *///?}
        return pages;
    }

    static String title(ItemStack book) {
        return book == null || book.isEmpty() ? "" : book.getHoverName().getString();
    }

    static String title(Lectern lectern) {
        return lectern == null ? "" : title(lectern.book());
    }

    static Lectern get(String dimension, BlockPos pos) {
        return LecternStore.get(dimension, pos);
    }

    /** A lectern facing north at the position, holding a copy of the book unless it is null. */
    static void place(ServerLevel level, BlockPos pos, ItemStack book) {
        level.getChunk(pos);
        level.setBlockAndUpdate(pos, Blocks.LECTERN.defaultBlockState());
        // Clear the reader's spot and the space above, for the Nether and Teleport there
        level.setBlockAndUpdate(pos.north(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.north().above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        if (book != null) {
            LecternBlock.tryPlaceBook(null, level, pos, level.getBlockState(pos), book.copy());
        }
    }

    static boolean hasBook(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.LECTERN) && state.getValue(LecternBlock.HAS_BOOK);
    }

    static ItemStack bookOn(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LecternBlockEntity entity ? entity.getBook() : ItemStack.EMPTY;
    }

    /** Stands the player where a reader stands, on the lectern's north side, looking at it. */
    static void stand(ServerPlayer player, BlockPos lectern) {
        BlockPos spot = lectern.relative(Direction.NORTH);
        player.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        face(player, lectern);
    }

    static void face(ServerPlayer player, BlockPos target) {
        Vec3 eye = player.getEyePosition();
        Vec3 aim = Vec3.atCenterOf(target);
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    /** Looks straight up, so the player faces no block. */
    static void lookUp(ServerPlayer player) {
        player.setXRot(-90.0F);
    }

    /** A right-click on the lectern, sneaking or not, through vanilla's game mode as a client's click arrives. */
    static void click(ServerPlayer player, ServerLevel level, BlockPos pos, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(false);
    }

    /** Written books with this title the player carries, counting books, not slots. */
    static int carries(ServerPlayer player, String title) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.WRITTEN_BOOK) && title.equals(title(stack))) count += stack.getCount();
        }
        return count;
    }

    /** Drops what the server has sent the player so far. */
    static void drain(ServerPlayer player) {
        EmbeddedChannel channel = ProbeSupport.channel(player);
        channel.flushOutbound();
        while (channel.readOutbound() != null) {
            // not about the next step
        }
    }

    /** The action bar lines the server sent the player since the last drain. */
    static String actionBar(ServerPlayer player) {
        EmbeddedChannel channel = ProbeSupport.channel(player);
        channel.flushOutbound();
        StringBuilder text = new StringBuilder();
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            if (message instanceof ClientboundSystemChatPacket packet && packet.overlay()) {
                text.append(packet.content().getString()).append('\n');
            }
        }
        return text.toString();
    }

    static String place(Lectern lectern) {
        return lectern == null ? "none" : Lecterns.place(lectern) + " cooldown " + lectern.cooldown() + " out " + lectern.bookOut()
                + " book '" + title(lectern) + "'";
    }
}
