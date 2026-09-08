package com.petitcamel.shop.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "product_measurement")
public class ProductMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "measurement_id")
    private Long measurementId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "size", nullable = false, length = 30)
    private String size;

    @Column(name = "shoulder", precision = 6, scale = 1)
    private BigDecimal shoulder;

    @Column(name = "chest", precision = 6, scale = 1)
    private BigDecimal chest;

    @Column(name = "waist", precision = 6, scale = 1)
    private BigDecimal waist;

    @Column(name = "hip", precision = 6, scale = 1)
    private BigDecimal hip;

    @Column(name = "sleeve", precision = 6, scale = 1)
    private BigDecimal sleeve;

    @Column(name = "total_length", precision = 6, scale = 1)
    private BigDecimal totalLength;

    @Column(name = "rise", precision = 6, scale = 1)
    private BigDecimal rise;

    @Column(name = "thigh", precision = 6, scale = 1)
    private BigDecimal thigh;

    @Column(name = "hem", precision = 6, scale = 1)
    private BigDecimal hem;

    public Long getMeasurementId() {
        return measurementId;
    }

    public void setMeasurementId(Long measurementId) {
        this.measurementId = measurementId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public BigDecimal getShoulder() {
        return shoulder;
    }

    public void setShoulder(BigDecimal shoulder) {
        this.shoulder = shoulder;
    }

    public BigDecimal getChest() {
        return chest;
    }

    public void setChest(BigDecimal chest) {
        this.chest = chest;
    }

    public BigDecimal getWaist() {
        return waist;
    }

    public void setWaist(BigDecimal waist) {
        this.waist = waist;
    }

    public BigDecimal getHip() {
        return hip;
    }

    public void setHip(BigDecimal hip) {
        this.hip = hip;
    }

    public BigDecimal getSleeve() {
        return sleeve;
    }

    public void setSleeve(BigDecimal sleeve) {
        this.sleeve = sleeve;
    }

    public BigDecimal getTotalLength() {
        return totalLength;
    }

    public void setTotalLength(BigDecimal totalLength) {
        this.totalLength = totalLength;
    }

    public BigDecimal getRise() {
        return rise;
    }

    public void setRise(BigDecimal rise) {
        this.rise = rise;
    }

    public BigDecimal getThigh() {
        return thigh;
    }

    public void setThigh(BigDecimal thigh) {
        this.thigh = thigh;
    }

    public BigDecimal getHem() {
        return hem;
    }

    public void setHem(BigDecimal hem) {
        this.hem = hem;
    }
}
