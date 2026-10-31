package com.trojanrideshare.app.service.payment;

public final class CostSplitter {
    private CostSplitter() {}

    // Leftover cents go to the first participants. The organizer is always first, so riders
    // are never the ones rounded up unless there is more than one cent left over.
    public static long[] even(long totalCents, int participants) {
        if (totalCents <= 0 || participants <= 0) {
            throw new IllegalArgumentException("Need a positive total and at least one participant");
        }
        long[] shares = new long[participants];
        long base = totalCents / participants;
        long leftover = totalCents % participants;
        for (int i = 0; i < participants; i++) {
            shares[i] = base + (i < leftover ? 1 : 0);
        }
        return shares;
    }
}
