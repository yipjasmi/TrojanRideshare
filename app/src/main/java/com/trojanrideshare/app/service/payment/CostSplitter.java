package com.trojanrideshare.app.service.payment;

public final class CostSplitter {
    private CostSplitter() {}

    public static long[] even(long totalCents, int participants) {
        if (totalCents <= 0 || participants <= 0) {
            throw new IllegalArgumentException(
                    "Need a positive total and at least one participant"
            );
        }
        long[] shares = new long[participants];
        long base = totalCents / participants;
        long leftover = totalCents % participants;
        for (int index = 0; index < participants; index++) {
            shares[index] = base + (index < leftover ? 1 : 0);
        }
        return shares;
    }
}
