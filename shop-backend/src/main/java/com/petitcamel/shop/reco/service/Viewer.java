package com.petitcamel.shop.reco.service;

/**
 * Who is asking for recommendations. Only the requester's own member id (from the login cookie) or random
 * guest session id (rec_sid cookie) is ever used, so one customer can never see another customer's data.
 */
public record Viewer(Long memberId, String sessionKey, boolean personalizationAllowed) {

    public boolean member() {
        return memberId != null;
    }

    public boolean anonymous() {
        return memberId == null && sessionKey == null;
    }

    /** Stable cache key; never contains anything but the ids above. */
    public String cacheKey() {
        if (memberId != null) {
            return personalizationAllowed ? "m" + memberId : "m" + memberId + ":off";
        }
        return sessionKey == null ? "anon" : "s" + sessionKey;
    }
}
