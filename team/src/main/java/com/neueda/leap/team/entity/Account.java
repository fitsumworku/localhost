package com.neueda.leap.team.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;
    @Column(name = "account_name", length = 20, updatable = false)
    private String accountName;
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;
    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    protected Account() {}
    public Account(User owner, Instant now) {
        this(owner, now, null);
    }
    public Account(User owner, Instant now, String accountName) {
        this.owner = owner;
        this.currency = "USD";
        this.accountName = accountName;
        this.createdDate = now;
        this.updatedDate = now;
    }
    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getCurrency() { return currency; }
    public String getAccountName() { return accountName; }
    public Instant getCreatedDate() { return createdDate; }
    public Instant getUpdatedDate() { return updatedDate; }
}
