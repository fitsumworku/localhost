package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "securities")
public class SecurityEntity {
    /**
     * CREATE TABLE Securities (
     *     Security_ID BIGSERIAL PRIMARY KEY,
     *     Ticker VARCHAR(20) NOT NULL,
     *     Name VARCHAR(255) NOT NULL,
     *     Asset_Type VARCHAR(50) NOT NULL,
     *     Exchange VARCHAR(50) NOT NULL,
     *     Status VARCHAR(8) NOT NULL,
     *     Sector VARCHAR(100) NOT NULL,
     *     UNIQUE (Ticker, Exchange),
     *     CONSTRAINT chk_security_status CHECK (Status IN ('ACTIVE','HALTED','DELISTED'))
     * );
     */

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID securityId;

    @Column
    private String ticker;

    @Column
    private String name;

    @Column(name = "asset_type")
    private String assetType;

    @Column
    private String exchange;

    @Column
    private String status;

    @Column
    private String sector;

    @OneToMany(mappedBy = "security", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccountPositionEntity> positions = new ArrayList<>();

    public SecurityEntity() {}

    public SecurityEntity(String ticker, String name, String assetType, String exchange, String status, String sector) {
        this.ticker = ticker;
        this.name = name;
        this.assetType = assetType;
        this.exchange = exchange;
        this.status = status;
        this.sector = sector;
    }

    public UUID getSecurityId() {
        return securityId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public List<AccountPositionEntity> getPositions() {
        return positions;
    }

    public void setPositions(List<AccountPositionEntity> positions) {
        this.positions = positions;
    }

    public void addPosition(AccountPositionEntity position) {
        positions.add(position);
        position.setSecurity(this);
    }

    public void removePosition(AccountPositionEntity position) {
        positions.remove(position);
        position.setSecurity(null);
    }

    
}
