package com.trojanrideshare.app.model;

public class SearchCriteria {
    public final String originKeyword;
    public final String destinationKeyword;

    public SearchCriteria(String originKeyword, String destinationKeyword) {
        this.originKeyword = clean(originKeyword);
        this.destinationKeyword = clean(destinationKeyword);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
