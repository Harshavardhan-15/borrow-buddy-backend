package com.borrowbuddy.service;

import com.borrowbuddy.dto.review.CreateReviewRequest;
import com.borrowbuddy.dto.review.ReviewDto;
import com.borrowbuddy.dto.review.ReviewSummaryDto;
import com.borrowbuddy.dto.transaction.TransactionDto;
import com.borrowbuddy.entity.BorrowRequest;
import com.borrowbuddy.entity.BorrowTransaction;
import com.borrowbuddy.entity.Review;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.NotificationType;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.exception.ResourceNotFoundException;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import com.borrowbuddy.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BorrowTransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final MapperService mapperService;
    private final NotificationService notificationService;

    public ReviewService(ReviewRepository reviewRepository,
                         BorrowTransactionRepository transactionRepository,
                         CurrentUserService currentUserService,
                         MapperService mapperService,
                         NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.mapperService = mapperService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReviewDto create(Student current, CreateReviewRequest request) {
        BorrowTransaction transaction = transactionRepository.findById(request.transactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (!mapperService.isParticipant(transaction, current)) {
            throw new BusinessException("You are not part of this transaction", HttpStatus.FORBIDDEN);
        }
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new BusinessException("You can review only after the item has been returned", HttpStatus.CONFLICT);
        }
        if (reviewRepository.existsByReviewerIdAndTransactionId(current.getId(), transaction.getId())) {
            throw new BusinessException("You have already reviewed this transaction", HttpStatus.CONFLICT);
        }

        BorrowRequest borrowRequest = transaction.getRequest();
        Student counterpart = borrowRequest.getBorrower().getId().equals(current.getId())
                ? borrowRequest.getOwner()
                : borrowRequest.getBorrower();

        Review review = new Review();
        review.setReviewer(current);
        review.setReviewedStudent(counterpart);
        review.setTransaction(transaction);
        review.setRating(request.rating());
        review.setComment(request.comment() == null ? null : request.comment().trim());
        Review saved = reviewRepository.save(review);

        notificationService.notify(counterpart, NotificationType.REVIEW_RECEIVED,
                current.getName() + " left you a " + request.rating() + "-star review for \""
                        + borrowRequest.getItem().getItemName() + "\".",
                saved.getId(), "reviews.html");

        return mapperService.toReviewDto(saved);
    }

    @Transactional(readOnly = true)
    public ReviewSummaryDto forStudent(Long studentId) {
        List<ReviewDto> reviews = reviewRepository.findByReviewedStudentIdOrderByCreatedAtDesc(studentId)
                .stream().map(mapperService::toReviewDto).toList();
        return new ReviewSummaryDto(
                reviewRepository.averageRatingFor(studentId),
                reviewRepository.countByReviewedStudentId(studentId),
                reviews
        );
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> given(Student current) {
        return reviewRepository.findByReviewerIdOrderByCreatedAtDesc(current.getId())
                .stream().map(mapperService::toReviewDto).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> pending(Student current) {
        Map<Long, BorrowTransaction> merged = new LinkedHashMap<>();
        transactionRepository.findByRequestBorrowerIdOrderByCreatedAtDesc(current.getId())
                .forEach(t -> merged.put(t.getId(), t));
        transactionRepository.findByRequestOwnerIdOrderByCreatedAtDesc(current.getId())
                .forEach(t -> merged.put(t.getId(), t));

        return merged.values().stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .filter(t -> !reviewRepository.existsByReviewerIdAndTransactionId(current.getId(), t.getId()))
                .sorted(Comparator.comparing(BorrowTransaction::getCreatedAt).reversed())
                .map(t -> mapperService.toTransactionDto(t, current))
                .toList();
    }
}
