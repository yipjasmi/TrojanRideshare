package com.trojanrideshare.app.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InitialsTest {
    @Test
    public void of_takesTheFirstAndLastWord() {
        assertEquals("KM", Initials.of("Kyle Macasilli-Tan", "Kyle"));
        assertEquals("YZ", Initials.of("Yucheng Middle Zhao", null));
    }

    @Test
    public void of_givesOneLetterForOneWord() {
        assertEquals("J", Initials.of("Jasmine", null));
    }

    @Test
    public void of_uppercases() {
        assertEquals("JY", Initials.of("jasmine yip", null));
    }

    @Test
    public void of_keepsANonLatinCharacterWhole() {
        assertEquals("李", Initials.of("李明", null));
    }

    @Test
    public void of_keepsAnEmojiWhole() {
        assertEquals("👩🏽‍💻L", Initials.of("👩🏽‍💻 Ada Lovelace", null));
    }

    @Test
    public void of_keepsAnAccentWithItsLetter() {
        assertEquals("ÉZ", Initials.of("émile Zola", null));
    }

    @Test
    public void of_ignoresExtraSpaces() {
        assertEquals("KM", Initials.of("  Kyle   Macasilli-Tan  ", null));
        assertEquals("KM", Initials.of(" Kyle　Macasilli-Tan\t", null));
    }

    @Test
    public void of_fallsBackToTheNickname() {
        assertEquals("K", Initials.of(null, "Kyle"));
        assertEquals("KT", Initials.of("   ", "Kyle Tan"));
    }

    @Test
    public void of_isAQuestionMarkWithNoName() {
        assertEquals("?", Initials.of(null, null));
        assertEquals("?", Initials.of("", " "));
        assertEquals("?", Initials.of(" \t ", " "));
    }
}
