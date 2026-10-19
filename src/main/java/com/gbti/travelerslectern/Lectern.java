package com.gbti.travelerslectern;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * One Traveler's Lectern: where it stands, its cooldown, whether its book is out and since when,
 * and the book it gives. Times are game ticks, which every dimension shares.
 *
 * <p>The book is kept as the text it was saved as (SNBT) and the data version it was saved at, and
 * written back that way, so a book this server cannot read is never lost. {@link #book()} is the
 * same book decoded for this server, or null when it could not be.</p>
 */
public final class Lectern {
    private final String dimension;
    private final BlockPos pos;
    private int cooldown;
    private boolean bookOut;
    private long takenAt;
    private String bookText;
    private int bookVersion;
    private ItemStack book;

    Lectern(String dimension, BlockPos pos, int cooldown, boolean bookOut, long takenAt, String bookText, int bookVersion, ItemStack book) {
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.cooldown = cooldown;
        this.bookOut = bookOut;
        this.takenAt = takenAt;
        this.bookText = bookText;
        this.bookVersion = bookVersion;
        this.book = book;
    }

    static String key(String dimension, BlockPos pos) {
        return dimension + " " + pos.asLong();
    }

    String key() {
        return key(dimension, pos);
    }

    /** The dimension's id, for example minecraft:overworld. */
    public String dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Seconds between the book leaving the lectern and coming back. */
    public int cooldown() {
        return cooldown;
    }

    public boolean bookOut() {
        return bookOut;
    }

    public long takenAt() {
        return takenAt;
    }

    /** The book this lectern gives, decoded for this server, or null when it could not be read. */
    public ItemStack book() {
        return book;
    }

    String bookText() {
        return bookText;
    }

    int bookVersion() {
        return bookVersion;
    }

    /** Seconds until the book comes back, measured from when it left. */
    public long secondsLeft(long now) {
        return Math.max(0, (cooldown * 20L - (now - takenAt)) / 20);
    }

    public long secondsSinceTaken(long now) {
        return Math.max(0, (now - takenAt) / 20);
    }

    boolean due(long now) {
        return now - takenAt >= cooldown * 20L;
    }

    void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    void taken(long now) {
        bookOut = true;
        takenAt = now;
    }

    void returned() {
        bookOut = false;
    }

    void setBook(ItemStack book, String text, int version) {
        this.book = book;
        this.bookText = text;
        this.bookVersion = version;
    }
}
