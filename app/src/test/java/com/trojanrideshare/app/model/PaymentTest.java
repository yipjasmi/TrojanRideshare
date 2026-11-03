package com.trojanrideshare.app.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class PaymentTest {
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    @Test
    public void isOverdue_startsExactlySevenDaysAfterTheSplit() {
        Payment share = assignedAtEpoch();

        assertFalse(share.isOverdue(7 * DAY - 1));
        assertTrue(share.isOverdue(7 * DAY));
        assertTrue(share.isOverdue(30 * DAY));
    }

    @Test
    public void isOverdue_neverAppliesToAPaidShare() {
        Payment share = assignedAtEpoch();
        share.paid = true;

        assertFalse(share.isOverdue(30 * DAY));
    }

    @Test
    public void isOverdue_waitsForTheAssignedTime() {
        assertFalse(new Payment().isOverdue(30 * DAY));
    }

    @Test
    public void hasRiderMarkedPaid_needsAPaidAtTime() {
        Payment settled = new Payment();
        settled.paid = true;
        Payment marked = new Payment();
        marked.paid = true;
        marked.paidAt = new Timestamp(new Date());

        assertFalse(Payment.hasRiderMarkedPaid(Collections.emptyList()));
        assertFalse(Payment.hasRiderMarkedPaid(Collections.singletonList(settled)));
        assertTrue(Payment.hasRiderMarkedPaid(Arrays.asList(settled, marked)));
    }

    private static Payment assignedAtEpoch() {
        Payment share = new Payment();
        share.assignedAt = new Timestamp(new Date(0));
        return share;
    }
}
