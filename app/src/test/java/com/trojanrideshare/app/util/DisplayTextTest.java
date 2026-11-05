package com.trojanrideshare.app.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DisplayTextTest {
    @Test
    public void clean_turnsNullIntoEmpty() {
        assertEquals("", DisplayText.clean(null, 10));
    }

    @Test
    public void clean_turnsLineBreaksIntoSpacesAndDropsDirectionMarks() {
        assertEquals("LAX run home", DisplayText.clean("LAX‮ run\r\nhome", 60));
    }

    @Test
    public void clean_dropsZeroWidthCharacters() {
        assertEquals("Kyle", DisplayText.clean("Ky​le", 60));
    }

    @Test
    public void clean_trimsTheEnds() {
        assertEquals("Ashley", DisplayText.clean("  Ashley \n", 60));
    }

    @Test
    public void clean_cutsALongTextWithAnEllipsis() {
        assertEquals("abcde…", DisplayText.clean("abcdefghij", 5));
    }

    @Test
    public void clean_countsAnEmojiAsOneCharacterWhenCutting() {
        assertEquals("🚗🚗…", DisplayText.clean("🚗🚗🚗🚗", 2));
    }

    @Test
    public void clean_leavesShortTextAlone() {
        assertEquals("Union Station", DisplayText.clean("Union Station", 60));
    }
}
