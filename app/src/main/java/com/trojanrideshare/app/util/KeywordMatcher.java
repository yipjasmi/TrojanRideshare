package com.trojanrideshare.app.util;

import com.trojanrideshare.app.model.PlaceInfo;

import java.util.Locale;

/**
 * Keyword matching for locations. A keyword matches when every word in it appears in the
 * place's address, its Google place name, or the text typed when it was picked, in any order
 * and ignoring case and punctuation. So "USC", "Hoover", "hoover 90089" and
 * "Southern California" can all find a ride picked as "3201 S Hoover St" after typing "USC".
 */
public final class KeywordMatcher {
    // Common campus/airport short names and their full names, matched both ways.
    private static final String[][] ALIASES = {
            {"usc", "university of southern california"},
            {"ucla", "university of california los angeles"},
            {"lax", "los angeles international airport"},
            {"lmu", "loyola marymount university"},
            {"dtla", "downtown los angeles"},
    };

    private KeywordMatcher() {}

    public static boolean matches(PlaceInfo place, String keyword) {
        String query = normalize(keyword);
        if (query.isEmpty()) {
            return true;
        }
        if (place == null) {
            return false;
        }
        String haystack = " " + normalize(join(place.address, place.name, place.searchQuery)) + " ";
        for (String[] alias : ALIASES) {
            if (haystack.contains(" " + alias[0] + " ")) haystack += alias[1] + " ";
            if (haystack.contains(" " + alias[1] + " ")) haystack += alias[0] + " ";
        }
        for (String word : query.split(" ")) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part != null) builder.append(part).append(' ');
        }
        return builder.toString();
    }
}
