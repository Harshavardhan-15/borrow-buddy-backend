package com.borrowbuddy.service;

import com.borrowbuddy.dto.auth.UserDto;
import com.borrowbuddy.dto.borrow.BorrowRequestDto;
import com.borrowbuddy.dto.item.ItemDto;
import com.borrowbuddy.dto.item.ItemSummary;
import com.borrowbuddy.dto.item.OwnerSummary;
import com.borrowbuddy.dto.notification.NotificationDto;
import com.borrowbuddy.dto.report.ReportDto;
import com.borrowbuddy.dto.review.ReviewDto;
import com.borrowbuddy.dto.transaction.TransactionDto;
import com.borrowbuddy.entity.*;
import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.RequestStatus;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.repository.BorrowRequestRepository;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import com.borrowbuddy.repository.RatingAggregate;
import com.borrowbuddy.repository.ReservationEnd;
import com.borrowbuddy.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MapperService {

    private final ReviewRepository reviewRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final BorrowTransactionRepository transactionRepository;

    public MapperService(ReviewRepository reviewRepository,
                         BorrowRequestRepository borrowRequestRepository,
                         BorrowTransactionRepository transactionRepository) {
        this.reviewRepository = reviewRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.transactionRepository = transactionRepository;
    }

    public OwnerSummary toOwnerSummary(Student student) {
        double average = reviewRepository.averageRatingFor(student.getId());
        long count = reviewRepository.countByReviewedStudentId(student.getId());
        return new OwnerSummary(student.getId(), student.getName(), student.getProfileImage(), average, count);
    }

    /**
     * Builds owner summaries for many students with one aggregate query instead
     * of two queries per student. Used by list endpoints to avoid N+1 queries.
     */
    public Map<Long, OwnerSummary> ownerSummaries(Collection<Student> students) {
        if (students == null || students.isEmpty()) {
            return Map.of();
        }
        Map<Long, Student> unique = new LinkedHashMap<>();
        for (Student student : students) {
            if (student != null && student.getId() != null) {
                unique.putIfAbsent(student.getId(), student);
            }
        }
        if (unique.isEmpty()) {
            return Map.of();
        }

        Map<Long, RatingAggregate> ratings = reviewRepository.ratingAggregates(unique.keySet()).stream()
                .collect(Collectors.toMap(RatingAggregate::getStudentId, aggregate -> aggregate));

        Map<Long, OwnerSummary> summaries = new HashMap<>();
        for (Student student : unique.values()) {
            RatingAggregate aggregate = ratings.get(student.getId());
            double average = aggregate == null || aggregate.getAverage() == null ? 0.0 : aggregate.getAverage();
            long count = aggregate == null || aggregate.getCount() == null ? 0L : aggregate.getCount();
            summaries.put(student.getId(), new OwnerSummary(
                    student.getId(), student.getName(), student.getProfileImage(), average, count));
        }
        return summaries;
    }

    public UserDto toUserDto(Student student) {
        return new UserDto(
                student.getId(),
                student.getName(),
                student.getCollegeEmail(),
                student.getStudentId(),
                student.getRole(),
                student.getProfileImage(),
                reviewRepository.averageRatingFor(student.getId()),
                reviewRepository.countByReviewedStudentId(student.getId()),
                student.getCreatedAt()
        );
    }

    public ItemSummary toItemSummary(Item item) {
        return new ItemSummary(
                item.getId(),
                item.getItemName(),
                item.getCategory(),
                item.getImageUrl(),
                item.getItemCondition(),
                item.getAvailabilityStatus(),
                item.getPricePerDay(),
                item.getSecurityDeposit(),
                item.getOwner().getId(),
                item.getOwner().getName()
        );
    }

    public ItemDto toItemDto(Item item, Student current) {
        LocalDate today = LocalDate.now();
        LocalDate reservedUntil = borrowRequestRepository.findCurrentReservationEnd(item.getId(), today);
        return toItemDto(item, current, toOwnerSummary(item.getOwner()), reservedUntil);
    }

    /**
     * Maps a list of items with batched reservation and owner-rating lookups,
     * so a page of items costs a constant number of queries.
     */
    public List<ItemDto> toItemDtos(List<Item> items, Student current) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<Long> itemIds = items.stream().map(Item::getId).filter(Objects::nonNull).toList();
        Map<Long, LocalDate> reservations = itemIds.isEmpty() ? Map.of()
                : borrowRequestRepository.findCurrentReservationEnds(itemIds, today).stream()
                .collect(Collectors.toMap(ReservationEnd::getItemId, ReservationEnd::getReturnDate));
        Map<Long, OwnerSummary> owners = ownerSummaries(items.stream().map(Item::getOwner).toList());

        return items.stream()
                .map(item -> toItemDto(item, current,
                        owners.get(item.getOwner().getId()),
                        reservations.get(item.getId())))
                .toList();
    }

    public ItemDto toItemDto(Item item, Student current, OwnerSummary owner, LocalDate reservedUntil) {
        LocalDate today = LocalDate.now();
        boolean reservedNow = reservedUntil != null;
        boolean availableNow = !item.isDeleted()
                && item.getAvailabilityStatus() == AvailabilityStatus.AVAILABLE
                && !reservedNow;
        boolean ownedByMe = current != null && item.getOwner().getId().equals(current.getId());

        return new ItemDto(
                item.getId(),
                item.getItemName(),
                item.getCategory(),
                item.getDescription(),
                item.getImageUrl(),
                item.getItemCondition(),
                item.getAvailabilityStatus(),
                item.getPricePerDay(),
                item.getSecurityDeposit(),
                current == null ? null : item.getContactPhone(),
                availableNow,
                reservedNow,
                reservedNow ? reservedUntil.plusDays(1) : today,
                owner,
                item.getCreatedAt(),
                ownedByMe
        );
    }

    public BorrowRequestDto toBorrowRequestDto(BorrowRequest request) {
        BorrowTransaction transaction = transactionRepository.findByRequestId(request.getId()).orElse(null);
        return new BorrowRequestDto(
                request.getId(),
                toItemSummary(request.getItem()),
                toOwnerSummary(request.getBorrower()),
                toOwnerSummary(request.getOwner()),
                request.getRequestDate(),
                request.getStartDate(),
                request.getReturnDate(),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getStatus()
        );
    }

    public TransactionDto toTransactionDto(BorrowTransaction transaction, Student current) {
        BorrowRequest request = transaction.getRequest();
        LocalDate today = LocalDate.now();
        Long currentId = current == null ? null : current.getId();
        Long borrowerId = request.getBorrower().getId();
        Long ownerId = request.getOwner().getId();

        boolean participant = currentId != null && (currentId.equals(borrowerId) || currentId.equals(ownerId));
        boolean overdue = (transaction.getStatus() == TransactionStatus.BORROWED
                || transaction.getStatus() == TransactionStatus.OVERDUE)
                && request.getReturnDate().isBefore(today);
        boolean awaiting = transaction.getStatus() == TransactionStatus.AWAITING_HANDOVER;
        boolean qrAvailable = currentId != null && currentId.equals(ownerId) && awaiting;
        boolean canConfirmHandover = participant && awaiting;
        boolean canConfirmReturn = participant
                && (transaction.getStatus() == TransactionStatus.BORROWED
                || transaction.getStatus() == TransactionStatus.OVERDUE);
        boolean canReview = participant
                && transaction.getStatus() == TransactionStatus.COMPLETED
                && currentId != null
                && !reviewRepository.existsByReviewerIdAndTransactionId(currentId, transaction.getId());
        Long reviewId = currentId == null ? null
                : reviewRepository.findByReviewerIdAndTransactionId(currentId, transaction.getId())
                .map(Review::getId).orElse(null);

        return new TransactionDto(
                transaction.getId(),
                request.getId(),
                toItemSummary(request.getItem()),
                toOwnerSummary(request.getBorrower()),
                toOwnerSummary(request.getOwner()),
                request.getStartDate(),
                request.getReturnDate(),
                transaction.getHandoverDate(),
                transaction.getActualReturnDate(),
                transaction.getStatus(),
                overdue,
                qrAvailable,
                canConfirmHandover,
                canConfirmReturn,
                canReview,
                reviewId,
                transaction.getCreatedAt()
        );
    }

    public NotificationDto toNotificationDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getMessage(),
                notification.getNotificationType(),
                notification.isRead(),
                notification.getReferenceId(),
                notification.getLink(),
                notification.getCreatedAt()
        );
    }

    public ReviewDto toReviewDto(Review review) {
        return new ReviewDto(
                review.getId(),
                toOwnerSummary(review.getReviewer()),
                toOwnerSummary(review.getReviewedStudent()),
                review.getTransaction().getId(),
                review.getTransaction().getRequest().getItem().getItemName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }

    public ReportDto toReportDto(Report report) {
        return new ReportDto(
                report.getId(),
                toOwnerSummary(report.getReporter()),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    public boolean isParticipant(BorrowTransaction transaction, Student student) {
        return transaction.getRequest().getBorrower().getId().equals(student.getId())
                || transaction.getRequest().getOwner().getId().equals(student.getId());
    }

    public boolean hasPendingRequest(BorrowRequest request) {
        return request.getStatus() == RequestStatus.PENDING;
    }
}
