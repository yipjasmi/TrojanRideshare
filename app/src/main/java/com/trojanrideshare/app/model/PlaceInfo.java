package com.trojanrideshare.app.model;

public class PlaceInfo {
    public String address;
    public String placeId;
    public double lat;
    public double lng;
    /** Google's name for the place, e.g. "University of Southern California". May be null. */
    public String name;
    /** What the user typed before picking it, e.g. "USC". May be null. */
    public String searchQuery;

    public PlaceInfo() {}
}
