package com.borrowbuddy.entity;

import com.borrowbuddy.entity.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "borrow_requests", indexes = {
        @Index(name = "idx_requests_item", columnList = "item_id"),
        @Index(name = "idx_requests_borrower", columnList = "borrower_id"),
        @Index(name = "idx_requests_owner", columnList = "owner_id"),
        @Index(name = "idx_requests_status", columnList = "status"),
        @Index(name = "idx_requests_dates", columnList = "item_id,status,start_date,return_date")
})
@Getter
@Setter
@NoArgsConstructor
public class BorrowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_requests_item"))
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false, foreignKey = @ForeignKey(name = "fk_requests_borrower"))
    private Student borrower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_requests_owner"))
    private Student owner;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (requestDate == null) {
            requestDate = LocalDate.now();
        }
        if (status == null) {
            status = RequestStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
