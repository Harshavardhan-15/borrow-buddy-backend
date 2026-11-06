package com.borrowbuddy.service;

import com.borrowbuddy.dto.transaction.QrCodeDto;
import com.borrowbuddy.dto.transaction.TransactionDto;
import com.borrowbuddy.entity.BorrowRequest;
import com.borrowbuddy.entity.BorrowTransaction;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.NotificationType;
import com.borrowbuddy.entity.enums.RequestStatus;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.exception.ResourceNotFoundException;
import com.borrowbuddy.repository.BorrowRequestRepository;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransactionService {

    private final BorrowTransactionRepository transactionRepository;
    private final BorrowRequestRepository requestRepository;
    private final CurrentUserService currentUserService;
    private final MapperService mapperService;
    private final NotificationService notificationService;
    private final QrCodeService qrCodeService;

    public TransactionService(BorrowTransactionRepository transactionRepository,
                              BorrowRequestRepository requestRepository,
                              CurrentUserService currentUserService,
                              MapperService mapperService,
                              NotificationService notificationService,
                              QrCodeService qrCodeService) {
        this.transactionRepository = transactionRepository;
        this.requestRepository = requestRepository;
        this.currentUserService = currentUserService;
        this.mapperService = mapperService;
        this.notificationService = notificationService;
        this.qrCodeService = qrCodeService;
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> list(Student current) {
        Map<Long, BorrowTransaction> merged = new LinkedHashMap<>();
        transactionRepository.findByRequestBorrowerIdOrderByCreatedAtDesc(current.getId())
                .forEach(t -> merged.put(t.getId(), t));
        transactionRepository.findByRequestOwnerIdOrderByCreatedAtDesc(current.getId())
                .forEach(t -> merged.put(t.getId(), t));
        return merged.values().stream()
                .sorted(Comparator.comparing(BorrowTransaction::getCreatedAt).reversed())
                .map(t -> mapperService.toTransactionDto(t, current))
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionDto get(Student current, Long id) {
        BorrowTransaction transaction = getEntity(id);
        ensureParticipant(transaction, current);
        return mapperService.toTransactionDto(transaction, current);
    }

    /**
     * The owner opens the QR at handover time. A fresh 256-bit token is created
     * and only its SHA-256 hash is stored; any previously displayed QR is
     * invalidated. The token expires at the end of the reservation return date.
     */
    @Transactional
    public QrCodeDto getQrCode(Student current, Long id) {
        BorrowTransaction transaction = getEntity(id);
        BorrowRequest request = transaction.getRequest();
        if (!request.getOwner().getId().equals(current.getId()) && !currentUserService.isAdmin(current)) {
            throw new BusinessException("Only the item owner can display the handover QR code", HttpStatus.FORBIDDEN);
        }
        if (transaction.getStatus() != TransactionStatus.AWAITING_HANDOVER) {
            throw new BusinessException("This transaction is not awaiting handover", HttpStatus.CONFLICT);
        }
        if (request.getReturnDate().isBefore(LocalDate.now())) {
            throw new BusinessException("The handover window for this reservation has expired", HttpStatus.GONE);
        }

        String rawToken = qrCodeService.generateRawToken();
        Instant expiresAt = request.getReturnDate().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).minusSeconds(1).toInstant();
        transaction.setQrTokenHash(qrCodeService.hash(rawToken));
        transaction.setQrTokenExpiresAt(expiresAt);
        transactionRepository.save(transaction);

        return new QrCodeDto(rawToken, expiresAt, qrCodeService.toPngDataUrl(rawToken));
    }

    @Transactional
    public TransactionDto confirmHandover(Student current, Long id, String token) {
        BorrowTransaction transaction = transactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        ensureParticipant(transaction, current);
        applyHandover(transaction, current, token);
        return mapperService.toTransactionDto(transaction, current);
    }

    @Transactional
    public TransactionDto confirmHandoverByToken(Student current, String token) {
        BorrowTransaction transaction = transactionRepository.findByQrTokenHash(qrCodeService.hash(token.trim()))
                .orElseThrow(() -> new BusinessException("Invalid or already used QR code"));
        ensureParticipant(transaction, current);
        applyHandover(transaction, current, token);
        return mapperService.toTransactionDto(transaction, current);
    }

    @Transactional
    public TransactionDto confirmReturn(Student current, Long id) {
        BorrowTransaction transaction = transactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        ensureParticipant(transaction, current);
        if (transaction.getStatus() != TransactionStatus.BORROWED
                && transaction.getStatus() != TransactionStatus.OVERDUE) {
            throw new BusinessException("This item is not currently borrowed", HttpStatus.CONFLICT);
        }

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setActualReturnDate(Instant.now());
        BorrowRequest request = transaction.getRequest();
        request.setStatus(RequestStatus.COMPLETED);
        requestRepository.save(request);
        transactionRepository.save(transaction);

        String itemName = request.getItem().getItemName();
        notificationService.notify(request.getOwner(), NotificationType.ITEM_RETURNED,
                request.getBorrower().getName() + " returned \"" + itemName + "\". You can now leave a review.",
                transaction.getId(), "reviews.html");
        notificationService.notify(request.getBorrower(), NotificationType.ITEM_RETURNED,
                "You returned \"" + itemName + "\". Thanks for keeping the campus sharing community strong!",
                transaction.getId(), "reviews.html");

        return mapperService.toTransactionDto(transaction, current);
    }

    private void applyHandover(BorrowTransaction transaction, Student current, String token) {
        if (transaction.getStatus() != TransactionStatus.AWAITING_HANDOVER) {
            throw new BusinessException("Handover has already been completed for this transaction",
                    HttpStatus.CONFLICT);
        }
        if (transaction.getQrTokenExpiresAt() != null
                && transaction.getQrTokenExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("This QR code has expired. Ask the owner to generate a new one.",
                    HttpStatus.GONE);
        }
        if (!qrCodeService.matches(token, transaction.getQrTokenHash())) {
            throw new BusinessException("Invalid QR code. Ask the owner to show a fresh code.");
        }

        transaction.setStatus(TransactionStatus.BORROWED);
        transaction.setHandoverDate(Instant.now());
        transactionRepository.save(transaction);

        BorrowRequest request = transaction.getRequest();
        notificationService.notify(request.getOwner(), NotificationType.HANDOVER_COMPLETED,
                "\"" + request.getItem().getItemName() + "\" was handed over to "
                        + request.getBorrower().getName() + ". Return is due on " + request.getReturnDate() + ".",
                transaction.getId(), "borrowing-history.html");
        notificationService.notify(request.getBorrower(), NotificationType.HANDOVER_COMPLETED,
                "Handover confirmed for \"" + request.getItem().getItemName() + "\". Please return it by "
                        + request.getReturnDate() + ".",
                transaction.getId(), "borrowing-history.html");
    }

    private BorrowTransaction getEntity(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    private void ensureParticipant(BorrowTransaction transaction, Student current) {
        if (!mapperService.isParticipant(transaction, current)) {
            throw new BusinessException("You are not part of this transaction", HttpStatus.FORBIDDEN);
        }
    }
}
