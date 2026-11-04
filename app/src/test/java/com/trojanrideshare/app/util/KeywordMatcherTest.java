package com.trojanrideshare.app.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.trojanrideshare.app.model.PlaceInfo;

import org.junit.Test;

public class KeywordMatcherTest {
    private static PlaceInfo hoover() {
        PlaceInfo place = new PlaceInfo();
        place.address = "3201 S Hoover St, Los Angeles, CA 90089, USA";
        return place;
    }

    @Test
    public void matchesAddressWordsInAnyOrder() {
        assertTrue(KeywordMatcher.matches(hoover(), "Hoover"));
        assertTrue(KeywordMatcher.matches(hoover(), "90089 hoover"));
        assertTrue(KeywordMatcher.matches(hoover(), "  los angeles, ca "));
        assertFalse(KeywordMatcher.matches(hoover(), "Westwood"));
    }

    @Test
    public void matchesTypedQueryAndPlaceName() {
        PlaceInfo typed = hoover();
        typed.searchQuery = "USC";
        assertTrue(KeywordMatcher.matches(typed, "usc"));
        // Alias: the typed "USC" also matches its full name.
        assertTrue(KeywordMatcher.matches(typed, "Southern California"));

        PlaceInfo named = new PlaceInfo();
        named.address = "Los Angeles, CA 90007, USA";
        named.name = "University of Southern California";
        assertTrue(KeywordMatcher.matches(named, "USC"));
    }

    @Test
    public void emptyKeywordMatchesEverythingAndNullPlaceNothing() {
        assertTrue(KeywordMatcher.matches(hoover(), " "));
        assertFalse(KeywordMatcher.matches(null, "usc"));
    }
}
