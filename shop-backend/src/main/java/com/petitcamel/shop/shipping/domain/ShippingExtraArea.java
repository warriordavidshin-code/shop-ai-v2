package com.petitcamel.shop.shipping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shipping_extra_area")
public class ShippingExtraArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "area_id")
    private Long areaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_type", nullable = false, length = 16)
    private ExtraAreaType areaType;

    @Column(name = "postcode_from", nullable = false, length = 5)
    private String postcodeFrom;

    @Column(name = "postcode_to", nullable = false, length = 5)
    private String postcodeTo;

    @Column(name = "note", length = 100)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean contains(String postcode) {
        return postcode != null
                && postcode.compareTo(postcodeFrom) >= 0
                && postcode.compareTo(postcodeTo) <= 0;
    }

    public Long getAreaId() {
        return areaId;
    }

    public void setAreaId(Long areaId) {
        this.areaId = areaId;
    }

    public ExtraAreaType getAreaType() {
        return areaType;
    }

    public void setAreaType(ExtraAreaType areaType) {
        this.areaType = areaType;
    }

    public String getPostcodeFrom() {
        return postcodeFrom;
    }

    public void setPostcodeFrom(String postcodeFrom) {
        this.postcodeFrom = postcodeFrom;
    }

    public String getPostcodeTo() {
        return postcodeTo;
    }

    public void setPostcodeTo(String postcodeTo) {
        this.postcodeTo = postcodeTo;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
