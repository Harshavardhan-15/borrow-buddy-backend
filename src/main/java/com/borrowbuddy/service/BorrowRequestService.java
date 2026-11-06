package com.borrowbuddy.service;

import com.borrowbuddy.dto.borrow.BorrowRequestDto;
import com.borrowbuddy.dto.borrow.CreateBorrowRequest;
import com.borrowbuddy.entity.BorrowRequest;
import com.borrowbuddy.entity.BorrowTransaction;
import com.borrowbuddy.entity.Item;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.NotificationType;
import com.borrowbuddy.entity.enums.RequestStatus;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.exception.ResourceNotFoundException;
import com.borrowbuddy.repository.BorrowRequestRepository;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import com.borrowbuddy.repository.ItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BorrowRequestService {

    private static final int MAX_BORROW_DAYS = 90;

    private final BorrowRequestRepository requestRepository;
    private final BorrowTransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final CurrentUserService currentUserService;
    private final MapperService mapperService;
    private final NotificationService notificationService;

    public BorrowRequestService(BorrowRequestRepository requestRepository,
                                BorrowTransactionRepository transactionRepository,
                                ItemRepository itemRepository,
                                CurrentUserService currentUserService,
                                MapperService mapperService,
                                NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.currentUserService = currentUserService;
        this.mapperService = mapperService;
        this.notificationService = notificationService;
    }

    @Transactional
    public BorrowRequestDto create(Student current, CreateBorrowRequest payload) {
        Item item = itemRepository.findById(payload.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (item.isDeleted()) {
            throw new ResourceNotFoundException("Item not found");
        }
        if (item.getOwner().getId().equals(current.getId())) {
            throw new BusinessException("You cannot borrow your own item");
        }
        if (item.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new BusinessException("This item is currently marked as unavailable", HttpStatus.CONFLICT);
        }

        validateDates(payload.startDate(), payload.returnDate());

        boolean duplicate = requestRepository.existsByItemIdAndBorrowerIdAndStatusIn(
                item.getId(), current.getId(), List.of(RequestStatus.PENDING, RequestStatus.APPROVED));
        if (duplicate) {
            throw new BusinessException("You already have an active request for this item", HttpStatus.CONFLICT);
        }
        long overlaps = requestRepository.countOverlappingApproved(
                item.getId(), payload.startDate(), payload.returnDate(), -1L);
        if (overlaps > 0) {
            throw new BusinessException("Those dates are already reserved. Please choose different dates.",
                    HttpStatus.CONFLICT);
        }

        BorrowRequest request = new BorrowRequest();
        request.setItem(item);
        request.setBorrower(current);
        request.setOwner(item.getOwner());
        request.setRequestDate(LocalDate.now());
        request.setStartDate(payload.startDate());
        request.setReturnDate(payload.returnDate());
        request.setMessage(payload.message() == null ? null : payload.message().trim());
        request.setStatus(RequestStatus.PENDING);
        BorrowRequest saved = requestRepository.save(request);

        notificationService.notify(item.getOwner(), NotificationType.BORROW_REQUEST,
                current.getName() + " requested to borrow \"" + item.getItemName() + "\" from "
                        + payload.startDate() + " to " + payload.returnDate() + ".",
                saved.getId(), "borrow-requests.html");
        return mapperService.toBorrowRequestDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BorrowRequestDto> sent(Student current) {
        return requestRepository.findByBorrowerIdOrderByCreatedAtDesc(current.getId())
                .stream().map(mapperService::toBorrowRequestDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BorrowRequestDto> received(Student current) {
        return requestRepository.findByOwnerIdOrderByCreatedAtDesc(current.getId())
                .stream().map(mapperService::toBorrowRequestDto).toList();
    }

    @Transactional(readOnly = true)
    public BorrowRequestDto get(Student current, Long id) {
        BorrowRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));
        if (!request.getBorrower().getId().equals(current.getId())
                && !request.getOwner().getId().equals(current.getId())
                && !currentUserService.isAdmin(current)) {
            throw new BusinessException("You do not have access to this request", HttpStatus.FORBIDDEN);
        }
        return mapperService.toBorrowRequestDto(request);
    }

    /**
     * Approval locks the request row first and then the item row, so two owners
     * (or two tabs) cannot approve overlapping reservations concurrently.
     */
    @Transactional
    public BorrowRequestDto approve(Student current, Long id) {
        BorrowRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));
        if (!request.getOwner().getId().equals(current.getId()) && !currentUserService.isAdmin(current)) {
            throw new BusinessException("Only the item owner can approve this request", HttpStatus.FORBIDDEN);
        }
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("This request is no longer pending", HttpStatus.CONFLICT);
        }
        if (request.getReturnDate().isBefore(LocalDate.now())) {
            throw new BusinessException("The requested return date has already passed");
        }

        Item item = itemRepository.findByIdForUpdate(request.getItem().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (item.isDeleted()) {
            throw new BusinessException("This listing has been removed", HttpStatus.CONFLICT);
        }
        if (item.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new BusinessException("This item is marked as unavailable. Set it to available first.",
                    HttpStatus.CONFLICT);
        }
        long overlaps = requestRepository.countOverlappingApproved(
                item.getId(), request.getStartDate(), request.getReturnDate(), request.getId());
        if (overlaps > 0) {
            throw new BusinessException("Another approved reservation overlaps these dates", HttpStatus.CONFLICT);
        }

        request.setStatus(RequestStatus.APPROVED);
        requestRepository.save(request);

        BorrowTransaction transaction = new BorrowTransaction();
        transaction.setRequest(request);
        transaction.setStatus(TransactionStatus.AWAITING_HANDOVER);
        // The QR token itself is generated (and its hash stored) when the owner
        // opens the handover QR. Only a hash is ever persisted.
        transaction.setQrTokenExpiresAt(request.getReturnDate()
                .plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusSeconds(1).toInstant());
        transactionRepository.save(transaction);

        notificationService.notify(request.getBorrower(), NotificationType.REQUEST_APPROVED,
                "Your request for \"" + item.getItemName() + "\" was approved. Coordinate the handover with "
                        + current.getName() + ".",
                request.getId(), "borrowing-history.html");

        // Auto-reject other pending requests that conflict with the approved window.
        List<BorrowRequest> conflicts = requestRepository.findConflictingPending(
                item.getId(), request.getStartDate(), request.getReturnDate(), request.getId());
        for (BorrowRequest conflict : conflicts) {
            conflict.setStatus(RequestStatus.REJECTED);
            requestRepository.save(conflict);
            notificationService.notify(conflict.getBorrower(), NotificationType.REQUEST_REJECTED,
                    "Your request for \"" + item.getItemName() + "\" was declined because those dates were booked.",
                    conflict.getId(), "borrow-requests.html");
        }

        return mapperService.toBorrowRequestDto(request);
    }

    @Transactional
    public BorrowRequestDto reject(Student current, Long id) {
        BorrowRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));
        if (!request.getOwner().getId().equals(current.getId()) && !currentUserService.isAdmin(current)) {
            throw new BusinessException("Only the item owner can reject this request", HttpStatus.FORBIDDEN);
        }
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("This request is no longer pending", HttpStatus.CONFLICT);
        }
        request.setStatus(RequestStatus.REJECTED);
        requestRepository.save(request);
        notificationService.notify(request.getBorrower(), NotificationType.REQUEST_REJECTED,
                "Your request for \"" + request.getItem().getItemName() + "\" was declined by "
                        + current.getName() + ".",
                request.getId(), "borrow-requests.html");
        return mapperService.toBorrowRequestDto(request);
    }

    @Transactional
    public BorrowRequestDto cancel(Student current, Long id) {
        BorrowRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found"));
        if (!request.getBorrower().getId().equals(current.getId())) {
            throw new BusinessException("Only the borrower can cancel this request", HttpStatus.FORBIDDEN);
        }
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("Only pending requests can be cancelled", HttpStatus.CONFLICT);
        }
        request.setStatus(RequestStatus.CANCELLED);
        requestRepository.save(request);
        notificationService.notify(request.getOwner(), NotificationType.REQUEST_CANCELLED,
                request.getBorrower().getName() + " cancelled their request for \""
                        + request.getItem().getItemName() + "\".",
                request.getId(), "borrow-requests.html");
        return mapperService.toBorrowRequestDto(request);
    }

    private void validateDates(LocalDate startDate, LocalDate returnDate) {
        LocalDate today = LocalDate.now();
        if (startDate.isBefore(today)) {
            throw new BusinessException("Start date cannot be in the past");
        }
        if (!returnDate.isAfter(startDate)) {
            throw new BusinessException("Return date must be after the start date");
        }
        if (ChronoUnit.DAYS.between(startDate, returnDate) > MAX_BORROW_DAYS) {
            throw new BusinessException("Borrowing period cannot exceed " + MAX_BORROW_DAYS + " days");
        }
    }
}
