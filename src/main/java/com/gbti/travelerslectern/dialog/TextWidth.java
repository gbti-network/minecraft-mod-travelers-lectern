package com.gbti.travelerslectern.dialog;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * A line's width in the vanilla font, in GUI pixels, so an icon's caption box fits its text. The
 * table is Traveler's Journal's BookFont. A client with Force Unicode Font or a resource pack's own
 * font measures text a little differently.
 */
final class TextWidth {
    private TextWidth() {
    }

    static int of(Component text) {
        int[] widest = {0, 0};
        text.visit((style, part) -> {
            part.codePoints().forEach(codePoint -> {
                if (codePoint == '\n') {
                    widest[1] = 0;
                } else {
                    widest[1] += glyphWidth(codePoint) + 1 + (style.isBold() ? 1 : 0);
                    widest[0] = Math.max(widest[0], widest[1]);
                }
            });
            return Optional.empty();
        }, Style.EMPTY);
        return widest[0];
    }

    private static int glyphWidth(int c) {
        if (c >= 'A' && c <= 'Z') return c == 'I' ? 3 : 5;
        if (c >= 'a' && c <= 'z') {
            return switch (c) {
                case 'i', 'l' -> 1;
                case 'f', 'k', 't' -> 4;
                default -> 5;
            };
        }
        if (c >= '0' && c <= '9') return 5;
        return switch (c) {
            case ' ' -> 3;
            case '!', '.', ',', ':', ';', '\'', '|' -> 1;
            case '`' -> 2;
            case '"', '[', ']' -> 3;
            case '(', ')', '{', '}', '<', '>' -> 4;
            case '@', '~' -> 6;
            default -> c >= 0x2E80 ? 8 : 5;
        };
    }
}
