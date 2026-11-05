package com.trojanrideshare.app.util;

/** Text another user controls, like a ride title or a name, made safe to put inside a sentence. */
public final class DisplayText {
    private DisplayText() {}

    /**
     * Drops control and invisible formatting characters (line breaks become one space), trims, and
     * cuts to {@code maxCodePoints} with an ellipsis. Null becomes empty.
     */
    public static String clean(String text, int maxCodePoints) {
        if (text == null) {
            return "";
        }
        StringBuilder visible = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (type != Character.CONTROL && type != Character.FORMAT) {
                visible.appendCodePoint(codePoint);
            } else if (Character.isWhitespace(codePoint)) {
                visible.append(' ');
            }
        });
        String cleaned = visible.toString().replaceAll("\\s+", " ").trim();
        if (cleaned.codePointCount(0, cleaned.length()) > maxCodePoints) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, maxCodePoints)).trim()
                    + "…";
        }
        return cleaned;
    }
}
