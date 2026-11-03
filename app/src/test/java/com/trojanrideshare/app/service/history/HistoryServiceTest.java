package com.trojanrideshare.app.service.history;

import com.google.firebase.Timestamp;
import com.trojanrideshare.app.model.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class HistoryServiceTest {
    private static final String UID = "me";
    private Ride ride(String id, int year, int month, int day, String owner, String status) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(year, month - 1, day, 12, 0);
        Ride r = new Ride(); r.id = id; r.organizerId = owner;
        r.participantIds = new ArrayList<>(Arrays.asList(UID, "friend"));
        r.status = status; r.departureTime = new Timestamp(c.getTime());
        r.origin = place("USC Village", 34.025, -118.285);
        r.destination = place("LAX", 33.942, -118.408); return r;
    }
    private PlaceInfo place(String address, double lat, double lng) {
        PlaceInfo p = new PlaceInfo(); p.address = address; p.lat = lat; p.lng = lng; return p;
    }
    @Test public void newestFirstAndOldestFirst() {
        Ride a = ride("a",2026,10,1,UID,"completed"), b = ride("b",2026,10,2,UID,"completed");
        HistoryFilter f = new HistoryFilter();
        assertEquals(Arrays.asList(b,a), HistoryService.filter(Arrays.asList(a,b),UID,f,Long.MAX_VALUE));
        f.oldestFirst = true;
        assertEquals(Arrays.asList(a,b), HistoryService.filter(Arrays.asList(a,b),UID,f,Long.MAX_VALUE));
    }
    @Test public void excludesOtherUsersFutureActiveAndMissingDate() {
        Ride other = ride("o",2026,10,1,"other","completed"); other.participantIds.clear();
        Ride future = ride("f",2026,10,1,UID,"onTime");
        Ride missing = ride("m",2026,10,1,UID,"completed"); missing.departureTime = null;
        assertEquals(0,HistoryService.filter(Arrays.asList(other,future,missing),UID,new HistoryFilter(),0).size());
    }
    @Test public void organizerIncludedWithoutParticipantEntry() {
        Ride r = ride("a",2026,10,1,UID,"completed"); r.participantIds.clear();
        assertEquals(1,HistoryService.filter(Arrays.asList(r),UID,new HistoryFilter(),0).size());
    }
    @Test public void textStatusAndParticipantFiltersCombine() {
        Ride r = ride("a",2026,10,1,UID,"completed"); HistoryFilter f = new HistoryFilter();
        f.origin = " village "; f.destination = "lax"; f.status = "completed"; f.participantId = "friend";
        assertEquals(1,HistoryService.filter(Arrays.asList(r),UID,f,0).size());
        f.participantId = "stranger";
        assertEquals(0,HistoryService.filter(Arrays.asList(r),UID,f,0).size());
    }
    @Test public void radiusMatchesCoordinatesInsteadOfAddressLabel() {
        Ride r = ride("a",2026,10,1,UID,"completed"); HistoryFilter f = new HistoryFilter();
        f.origin = "Different name"; f.originCenter = place("Different name",34.025,-118.285); f.radiusMiles = 1.0;
        assertEquals(1,HistoryService.filter(Arrays.asList(r),UID,f,0).size());
        f.originCenter = r.destination;
        assertEquals(0,HistoryService.filter(Arrays.asList(r),UID,f,0).size());
        assertEquals(0.0,HistoryService.distanceMiles(r.origin,r.origin),0.000001);
    }
    @Test public void rejectsInvalidRadius() {
        for (double radius : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            HistoryFilter f = new HistoryFilter(); f.originCenter = place("USC",0,0); f.radiusMiles = radius;
            boolean rejected = false;
            try { HistoryService.filter(Collections.emptyList(),UID,f,0); }
            catch (IllegalArgumentException expected) { rejected = true; }
            assertTrue(rejected);
        }
    }
    @Test public void radiusRequiresSelectedLocation() {
        HistoryFilter f = new HistoryFilter(); f.radiusMiles = 2.0;
        boolean rejected = false;
        try { HistoryService.filter(Collections.emptyList(),UID,f,0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        assertTrue(rejected);
    }
    @Test public void summaryCountsOwnSharesOnlyAndDeduplicates() {
        Ride owned = ride("o",2026,10,1,UID,"completed"); owned.finalCost = 100.0;
        Ride joined = ride("j",2026,10,31,"other","completed");
        Ride cancelled = ride("c",2026,10,1,UID,"cancelled");
        Ride november = ride("n",2026,11,1,UID,"completed");
        Ride priorYear = ride("y",2025,10,1,UID,"completed");
        Map<String,Payment> payments = new HashMap<>();
        Payment a = new Payment(); a.userId = UID; a.amount = 3.34; payments.put("o",a);
        Payment b = new Payment(); b.userId = UID; b.amount = 6.67; payments.put("j",b);
        MonthlySummary s = HistoryService.summarize(Arrays.asList(owned,owned,joined,cancelled,november,priorYear),payments,UID,2026,10);
        assertEquals(2,s.completedRides); assertEquals(1,s.ownedRides); assertEquals(1,s.joinedRides);
        assertEquals(1001L,s.totalCostCents); assertEquals(0,s.unassignedShares);
    }
    @Test public void missingShareIsPendingAndOtherUsersShareIgnored() {
        Ride r = ride("a",2026,10,1,UID,"completed");
        Payment p = new Payment(); p.userId = "other"; p.amount = 500;
        MonthlySummary s = HistoryService.summarize(Arrays.asList(r),Collections.singletonMap("a",p),UID,2026,10);
        assertEquals(1,s.unassignedShares); assertEquals(0L,s.totalCostCents);
    }
    @Test public void emptyMonthAndDecemberBoundary() {
        Ride december = ride("d",2026,12,31,UID,"completed");
        assertEquals(1,HistoryService.summarize(Arrays.asList(december),Collections.emptyMap(),UID,2026,12).completedRides);
        assertEquals(0,HistoryService.summarize(Arrays.asList(december),Collections.emptyMap(),UID,2027,1).completedRides);
    }
}
