package com.trojanrideshare.app.payment;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.trojanrideshare.app.service.payment.CostSplitter;

import org.junit.Test;

public class CostSplitterTest {

    @Test
    public void tenDollarsAcrossThreeGivesTheLeftoverCentToTheOrganizer() {
        assertArrayEquals(new long[] {334, 333, 333}, CostSplitter.even(1000, 3));
    }

    @Test
    public void twoLeftoverCentsGoToTheFirstTwoPeople() {
        assertArrayEquals(new long[] {334, 334, 333}, CostSplitter.even(1001, 3));
    }

    @Test
    public void exactDivisionHasNoLeftover() {
        assertArrayEquals(new long[] {1200, 1200}, CostSplitter.even(2400, 2));
    }

    @Test
    public void totalSmallerThanPeopleStillAddsUp() {
        assertArrayEquals(new long[] {1, 1, 0}, CostSplitter.even(2, 3));
    }

    @Test
    public void onePersonPaysEverything() {
        assertArrayEquals(new long[] {4801}, CostSplitter.even(4801, 1));
    }

    @Test
    public void everyTotalAddsUpExactlyAndStaysWithinACent() {
        for (long total = 1; total <= 50_000; total++) {
            for (int people = 1; people <= 7; people++) {
                long[] shares = CostSplitter.even(total, people);
                long sum = 0;
                long min = Long.MAX_VALUE;
                long max = Long.MIN_VALUE;
                for (long share : shares) {
                    sum += share;
                    min = Math.min(min, share);
                    max = Math.max(max, share);
                }
                assertEquals(total, sum);
                assertTrue(max - min <= 1);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroPeople() {
        CostSplitter.even(1000, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroTotal() {
        CostSplitter.even(0, 3);
    }
}
