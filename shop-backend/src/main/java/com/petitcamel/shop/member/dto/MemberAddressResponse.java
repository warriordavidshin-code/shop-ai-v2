package com.petitcamel.shop.member.dto;

import java.time.Instant;

public record MemberAddressResponse(
        Long addressId,
        String label,
        String receiverName,
        String receiverPhone,
        String postcode,
        String address1,
        String address2,
        boolean defaultAddress,
        Instant updatedAt
) {
}
