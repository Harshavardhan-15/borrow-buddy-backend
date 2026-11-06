package com.borrowbuddy.service;

import com.borrowbuddy.dto.dashboard.DashboardStats;
import com.borrowbuddy.dto.transaction.TransactionDto;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.RequestStatus;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.repository.BorrowRequestRepository;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final ItemRepository itemRepository;
    private final BorrowRequestRepository requestRepository;
    private final BorrowTransactionRepository transactionRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationService notificationService;
    private final MapperService mapperService;

    public DashboardService(ItemRepository itemRepository,
                            BorrowRequestRepository requestRepository,
                            BorrowTransactionRepository transactionRepository,
                            ReviewRepository reviewRepository,
                            NotificationService notificationService,
                            MapperService mapperService) {
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.transactionRepository = transactionRepository;
        this.reviewRepository = reviewRepository;
        this.notificationService = notificationService;
        this.mapperService = mapperService;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats(Student current) {
        List<TransactionDto> upcoming = transactionRepository
                .findActiveForStudent(current.getId(), List.of(
                        TransactionStatus.AWAITING_HANDOVER,
                        TransactionStatus.BORROWED,
                        TransactionStatus.OVERDUE))
                .stream()
                .limit(6)
                .map(transaction -> mapperService.toTransactionDto(transaction, current))
                .toList();

        return new DashboardStats(
                itemRepository.countByOwnerIdAndDeletedFalse(current.getId()),
                requestRepository.countByBorrowerIdAndStatusIn(
                        current.getId(), List.of(RequestStatus.PENDING, RequestStatus.APPROVED)),
                requestRepository.countByOwnerIdAndStatus(current.getId(), RequestStatus.PENDING),
                transactionRepository.countByRequestBorrowerIdAndStatusIn(
                        current.getId(), List.of(TransactionStatus.BORROWED, TransactionStatus.OVERDUE)),
                transactionRepository.countByRequestBorrowerIdAndStatusIn(
                        current.getId(), List.of(TransactionStatus.COMPLETED)),
                transactionRepository.countByRequestOwnerIdAndStatusIn(
                        current.getId(), List.of(TransactionStatus.COMPLETED)),
                notificationService.unreadCount(current),
                reviewRepository.averageRatingFor(current.getId()),
                upcoming,
                notificationService.recent(current)
        );
    }
}
