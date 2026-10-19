package com.gbti.travelerslectern;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
//?} else {
/*import net.minecraft.world.item.Items;
*///?}

/**
 * A lectern's book as text and back, on every supported version. The text is the item's SNBT, saved
 * with the data version it was written at; reading it runs Minecraft's own item upgrades from that
 * version to this server's, the same ones a world gets when it moves to a newer Minecraft.
 */
public final class LecternBooks {
    /** 1.21.1's data version. Releases before 2.0.0 ran on 1.21.1 only and saved no version. */
    static final int BEFORE_2_0 = 3955;

    private LecternBooks() {
    }

    public static int dataVersion() {
        //? if >=1.21.6 {
        return SharedConstants.getCurrentVersion().dataVersion().version();
        //?} else {
        /*return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        *///?}
    }

    public static boolean isLecternBook(ItemStack stack) {
        return stack.is(ItemTags.LECTERN_BOOKS);
    }

    static String encode(ItemStack stack, HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        Tag tag = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).result().orElseThrow();
        //?} else {
        /*Tag tag = stack.save(new CompoundTag());
        *///?}
        return tag.toString();
    }

    /** The book a saved text describes, upgraded from its data version; throws when it is no book. */
    static ItemStack decode(String text, int version, HolderLookup.Provider registries) throws CommandSyntaxException {
        Tag tag = parse(text);
        int now = dataVersion();
        if (version < now) {
            tag = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, tag), version, now).getValue();
        }
        //? if >=1.20.5 {
        ItemStack stack = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
        //?} else {
        /*ItemStack stack = tag instanceof CompoundTag compound ? ItemStack.of(compound) : ItemStack.EMPTY;
        *///?}
        if (!isLecternBook(stack)) {
            throw new IllegalArgumentException("the saved item is not a book this lectern can hold");
        }
        return stack;
    }

    private static CompoundTag parse(String text) throws CommandSyntaxException {
        //? if >=1.21.5 {
        return TagParser.parseCompoundFully(text);
        //?} else {
        /*return TagParser.parseTag(text);
        *///?}
    }

    /** The book's title, or the item's name for a book and quill. */
    public static String title(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    /** "Server Rules by Admins, 3 pages"; a book and quill has no author, so "Book and Quill, 2 pages". */
    public static String caption(ItemStack stack) {
        String author = author(stack);
        int pages = pages(stack);
        return title(stack) + (author.isEmpty() ? "" : " by " + author) + ", " + (pages == 1 ? "1 page" : pages + " pages");
    }

    private static String author(ItemStack stack) {
        //? if >=1.20.5 {
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return written == null ? "" : written.author();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag == null || !stack.is(Items.WRITTEN_BOOK) ? "" : tag.getString("author");
        *///?}
    }

    private static int pages(ItemStack stack) {
        //? if >=1.20.5 {
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) return written.pages().size();
        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        return writable == null ? 0 : writable.pages().size();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getList("pages", Tag.TAG_STRING).size();
        *///?}
    }
}
