package com.gbti.travelerslectern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if >=26.3 {
import net.minecraft.util.Prediction;
//?}

/**
 * What players and operators do with a Traveler's Lectern. Anyone may take its book, by sneaking
 * with an empty hand or with the lectern screen's Take Book button, and the book comes back one
 * cooldown after it left. Reading never resets that. An empty Traveler's Lectern takes no other
 * book and says when its own returns.
 *
 * <p>The player's level is never read from the player: Entity.level() changed its intermediary name
 * in 1.21.9, inside the 1.21.6 jar's range. It comes from the event, or the player's command
 * source.</p>
 */
public final class Lecterns {
    /** How far a player can be from the lectern they face, as in releases before 2.0.0. */
    public static final double REACH = 20.0;
    public static final String READ_ONLY = "The lectern file could not be read, so nothing was saved. See the server log.";

    private Lecterns() {
    }

    /** Handles a click on a Traveler's Lectern and returns true; returns false for any other lectern. */
    public static boolean use(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof LecternBlockEntity entity)) {
            return false;
        }
        Lectern lectern = LecternStore.get(dimensionId(level.dimension()), pos);
        if (lectern == null || lectern.book() == null) {
            // A plain lectern, or one whose saved book this server cannot read: vanilla handles it
            return false;
        }
        long now = level.getGameTime();
        if (!state.getValue(LecternBlock.HAS_BOOK)) {
            if (!lectern.bookOut() && LecternStore.writable()) {
                lectern.taken(now);
                LecternStore.save();
            }
            serverPlayer.sendSystemMessage(Component.literal("The book returns in " + Durations.left(lectern.secondsLeft(now)) + "."), true);
            return true;
        }
        ItemStack book = lectern.book();
        if (!ItemStack.matches(entity.getBook(), book)) {
            entity.setBook(book.copy());
        }
        if (player.isSecondaryUseActive()) {
            give(serverPlayer, book);
            entity.setBook(ItemStack.EMPTY);
            LecternBlock.resetBookState(player, level, pos, state, false);
            if (LecternStore.writable()) {
                lectern.taken(now);
                LecternStore.save();
            }
            return true;
        }
        serverPlayer.openMenu(entity);
        return true;
    }

    /**
     * Once a second: notes a book taken with the lectern screen's button, and puts back each book
     * whose cooldown has passed. Lecterns in unloaded chunks wait until someone is near.
     */
    static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 20 != 0 || !LecternStore.writable()) {
            return;
        }
        boolean changed = false;
        for (Lectern lectern : LecternStore.all()) {
            ServerLevel level = level(server, lectern);
            if (level == null || lectern.book() == null || !level.isLoaded(lectern.pos())) {
                continue;
            }
            BlockState state = level.getBlockState(lectern.pos());
            if (!state.is(Blocks.LECTERN) || !(level.getBlockEntity(lectern.pos()) instanceof LecternBlockEntity entity)) {
                continue;
            }
            long now = level.getGameTime();
            boolean hasBook = state.getValue(LecternBlock.HAS_BOOK);
            if (hasBook && lectern.bookOut()) {
                lectern.returned();
                changed = true;
            } else if (!hasBook && !lectern.bookOut()) {
                lectern.taken(now);
                changed = true;
            } else if (!hasBook && lectern.due(now)) {
                TravelersLectern.logDebug("Putting the book back on the lectern at {}", lectern.pos());
                placeBook(level, entity, state, lectern);
                changed = true;
            }
        }
        if (changed) {
            LecternStore.save();
        }
    }

    /** The block a player faces within {@link #REACH}, or null. */
    public static BlockPos facing(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 1.0F, false);
        return hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block ? block.getBlockPos() : null;
    }

    /** The level a player is in, read through the command source (see the class comment). */
    public static ServerLevel levelOf(ServerPlayer player) {
        return player.createCommandSourceStack().getLevel();
    }

    /** The Traveler's Lectern at a position, or null. */
    public static Lectern at(ServerLevel level, BlockPos pos) {
        return pos == null ? null : LecternStore.get(dimensionId(level.dimension()), pos);
    }

    /** A plain lectern that holds a book, at a position: one that can be made a Traveler's Lectern. */
    public static boolean canCreate(ServerLevel level, BlockPos pos) {
        return pos != null && at(level, pos) == null && level.getBlockState(pos).is(Blocks.LECTERN)
                && level.getBlockEntity(pos) instanceof LecternBlockEntity entity && entity.hasBook();
    }

    /** Makes the lectern a Traveler's Lectern with a copy of its book, or returns null if it cannot be one. */
    public static Lectern create(ServerLevel level, BlockPos pos, int cooldown) {
        if (!canCreate(level, pos) || !(level.getBlockEntity(pos) instanceof LecternBlockEntity entity)) {
            return null;
        }
        return LecternStore.add(dimensionId(level.dimension()), pos, cooldown, entity.getBook());
    }

    public static void setCooldown(Lectern lectern, int seconds) {
        lectern.setCooldown(seconds);
        LecternStore.save();
    }

    /** Puts the book back now, loading the lectern's chunk if needed. False if the lectern is gone. */
    public static boolean putBack(Lectern lectern) {
        ServerLevel level = level(LecternStore.server(), lectern);
        if (level == null || lectern.book() == null) {
            return false;
        }
        level.getChunk(lectern.pos());
        BlockState state = level.getBlockState(lectern.pos());
        if (!state.is(Blocks.LECTERN) || !(level.getBlockEntity(lectern.pos()) instanceof LecternBlockEntity entity)) {
            return false;
        }
        if (!state.getValue(LecternBlock.HAS_BOOK)) {
            placeBook(level, entity, state, lectern);
            LecternStore.save();
        }
        return true;
    }

    /** Turns it back into a plain lectern, putting its book back first so the lectern is never left empty. */
    public static void makePlain(Lectern lectern) {
        putBack(lectern);
        LecternStore.remove(lectern);
    }

    /**
     * Makes the book in the player's main hand the lectern's book, and lays it on the lectern if the
     * lectern holds its book now. False if the player holds no book.
     */
    public static boolean useBookInHand(ServerPlayer player, Lectern lectern) {
        ItemStack held = player.getMainHandItem();
        if (!LecternBooks.isLecternBook(held)) {
            return false;
        }
        LecternStore.setBook(lectern, held);
        ServerLevel level = level(LecternStore.server(), lectern);
        if (level != null && level.isLoaded(lectern.pos()) && level.getBlockEntity(lectern.pos()) instanceof LecternBlockEntity entity
                && entity.hasBook()) {
            entity.setBook(lectern.book().copy());
        }
        LecternStore.save();
        return true;
    }

    /** Like /give: into the inventory, or dropped at the player's feet when it is full. */
    public static void give(ServerPlayer player, ItemStack book) {
        //? if >=26.3 {
        player.getInventory().placeItemBackInInventory(book.copy(), Prediction.SERVER_ONLY);
        //?} else {
        /*player.getInventory().placeItemBackInInventory(book.copy());
        *///?}
    }

    /** "Overworld at 12, 64, -40". */
    public static String place(Lectern lectern) {
        return place(lectern.dimension(), lectern.pos());
    }

    public static String place(String dimension, BlockPos pos) {
        return dimensionName(dimension) + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public static String dimensionName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "The Nether";
            case "minecraft:the_end" -> "The End";
            default -> id;
        };
    }

    public static String dimensionId(ResourceKey<Level> key) {
        //? if >=1.21.11 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();
        *///?}
    }

    /** The level a lectern stands in, or null when the server has no such dimension. */
    public static ServerLevel level(MinecraftServer server, Lectern lectern) {
        //? if >=1.21.11 {
        Identifier id = Identifier.tryParse(lectern.dimension());
        //?} else {
        /*ResourceLocation id = ResourceLocation.tryParse(lectern.dimension());
        *///?}
        return server == null || id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static void placeBook(ServerLevel level, LecternBlockEntity entity, BlockState state, Lectern lectern) {
        entity.setBook(lectern.book().copy());
        LecternBlock.resetBookState(null, level, lectern.pos(), state, true);
        lectern.returned();
    }
}
