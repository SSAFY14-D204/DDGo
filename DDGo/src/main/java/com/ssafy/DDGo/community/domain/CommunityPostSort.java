package com.ssafy.DDGo.community.domain;

public enum CommunityPostSort {
    LATEST,
    POPULAR;

    public static CommunityPostSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return LATEST;
        }
        try {
            return CommunityPostSort.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LATEST;
        }
    }
}
