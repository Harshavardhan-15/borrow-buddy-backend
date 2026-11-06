package com.borrowbuddy.entity;

import com.borrowbuddy.entity.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A handover record created when an owner approves a borrowing request.
 * The raw QR token is never stored - only its SHA-256 hash.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_request", columnList = "request_id", unique = true),
        @Index(name = "idx_transactions_status", columnList = "status"),
        @Index(name = "idx_transactions_qr_hash", columnList = "qr_token_hash", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class BorrowTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_transactions_request"))
    private BorrowRequest request;

    @Column(name = "handover_date")
    private Instant handoverDate;

    @Column(name = "actual_return_date")
    private Instant actualReturnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status = TransactionStatus.AWAITING_HANDOVER;

    @Column(name = "qr_token_hash", unique = true, length = 64)
    private String qrTokenHash;

    @Column(name = "qr_token_expires_at")
    private Instant qrTokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = TransactionStatus.AWAITING_HANDOVER;
        }
    }
}
