package com.trojanrideshare.app.model;

/** Transient screen criteria; never written to Firestore. Radius is in miles. */
public class HistoryFilter {
    public String origin = "", destination = "", status = "", participantId = "";
    public PlaceInfo originCenter, destinationCenter;
    public Double radiusMiles;
    public boolean oldestFirst;
}
