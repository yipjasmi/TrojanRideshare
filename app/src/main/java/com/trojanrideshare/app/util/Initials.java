package com.trojanrideshare.app.util;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The letters on a person's avatar: "Kyle Macasilli-Tan" is "KM", "Jasmine" is "J". */
public final class Initials {
    public static final String UNKNOWN = "?";

    private Initials() {}

    /** First and last word of the full name, or of the nickname when the full name is blank. */
    public static String of(String fullName, String nickname) {
        List<String> words = words(fullName);
        if (words.isEmpty()) {
            words = words(nickname);
        }
        if (words.isEmpty()) {
            return UNKNOWN;
        }
        String first = firstCharacter(words.get(0));
        if (words.size() == 1) {
            return first;
        }
        return first + firstCharacter(words.get(words.size() - 1));
    }

    private static List<String> words(String name) {
        List<String> words = new ArrayList<>();
        if (name == null) {
            return words;
        }
        for (String word : name.split("[\\s\\p{Z}]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /** substring(0, 1) would cut an emoji or an accented letter in half. */
    private static String firstCharacter(String word) {
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(word);
        return word.substring(0, characters.next()).toUpperCase(Locale.ROOT);
    }
}
