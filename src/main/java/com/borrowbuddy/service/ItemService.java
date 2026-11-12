package com.borrowbuddy.service;

import com.borrowbuddy.dto.PageResponse;
import com.borrowbuddy.dto.item.ItemDto;
import com.borrowbuddy.dto.item.ItemRequest;
import com.borrowbuddy.entity.Item;
import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.ItemCondition;
import com.borrowbuddy.entity.enums.RequestStatus;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.exception.ResourceNotFoundException;
import com.borrowbuddy.repository.BorrowRequestRepository;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.repository.ItemSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final BorrowRequestRepository requestRepository;
    private final BorrowTransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final MapperService mapperService;

    public ItemService(ItemRepository itemRepository,
                       BorrowRequestRepository requestRepository,
                       BorrowTransactionRepository transactionRepository,
                       CurrentUserService currentUserService,
                       MapperService mapperService) {
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.mapperService = mapperService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemDto> browse(String query, String category, ItemCondition condition,
                                        Boolean availableOnly, int page, int size,
                                        String sortBy, String direction) {
        Student current = currentUserService.currentOrNull();
        Sort sort = buildSort(sortBy, direction);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 48), sort);
        Page<Item> result = itemRepository.findAll(
                ItemSpecifications.browse(query, category, condition, availableOnly), pageable);
        return PageResponse.from(result.map(item -> mapperService.toItemDto(item, current)));
    }

    @Transactional(readOnly = true)
    public List<ItemDto> featured() {
        Student current = currentUserService.currentOrNull();
        return itemRepository.findTop6ByDeletedFalseAndAvailabilityStatusOrderByCreatedAtDesc(
                        AvailabilityStatus.AVAILABLE)
                .stream().map(item -> mapperService.toItemDto(item, current)).toList();
    }

    @Transactional(readOnly = true)
    public ItemDto get(Long id) {
        Student current = currentUserService.currentOrNull();
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        boolean owner = current != null && item.getOwner().getId().equals(current.getId());
        if (item.isDeleted() && !owner && !currentUserService.isAdmin(current)) {
            throw new ResourceNotFoundException("Item not found");
        }
        return mapperService.toItemDto(item, current);
    }

    @Transactional(readOnly = true)
    public List<ItemDto> mine(Student current) {
        return itemRepository.findByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(current.getId())
                .stream().map(item -> mapperService.toItemDto(item, current)).toList();
    }

    @Transactional
    public ItemDto create(Student current, ItemRequest request) {
        validateImageUrl(request.imageUrl());
        Item item = new Item();
        apply(item, request);
        item.setOwner(current);
        Item saved = itemRepository.save(item);
        return mapperService.toItemDto(saved, current);
    }

    @Transactional
    public ItemDto update(Student current, Long id, ItemRequest request) {
        validateImageUrl(request.imageUrl());
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (item.isDeleted()) {
            throw new ResourceNotFoundException("Item not found");
        }
        if (!item.getOwner().getId().equals(current.getId()) && !currentUserService.isAdmin(current)) {
            throw new BusinessException("Only the owner can edit this item", HttpStatus.FORBIDDEN);
        }
        apply(item, request);
        Item saved = itemRepository.save(item);
        return mapperService.toItemDto(saved, current);
    }

    @Transactional
    public void delete(Student current, Long id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (!item.getOwner().getId().equals(current.getId()) && !currentUserService.isAdmin(current)) {
            throw new BusinessException("Only the owner can delete this item", HttpStatus.FORBIDDEN);
        }
        if (item.isDeleted()) {
            return;
        }
        long activeTransactions = transactionRepository.countByRequestItemIdAndStatusIn(
                item.getId(), List.of(TransactionStatus.AWAITING_HANDOVER,
                        TransactionStatus.BORROWED, TransactionStatus.OVERDUE));
        long activeRequests = requestRepository.countByItemIdAndStatusIn(
                item.getId(), List.of(RequestStatus.PENDING, RequestStatus.APPROVED));
        if (activeTransactions > 0 || activeRequests > 0) {
            throw new BusinessException(
                    "This item has active requests or an active handover. Resolve them before deleting the listing.",
                    HttpStatus.CONFLICT);
        }
        // Soft delete: keeps borrowing history and reviews intact.
        item.setDeleted(true);
        item.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        itemRepository.save(item);
    }

    private void apply(Item item, ItemRequest request) {
        item.setItemName(request.itemName().trim());
        item.setCategory(request.category().trim());
        item.setDescription(request.description().trim());
        item.setImageUrl(request.imageUrl() == null || request.imageUrl().isBlank()
                ? null : request.imageUrl().trim());
        item.setItemCondition(request.itemCondition());
        item.setPricePerDay(normalizeMoney(request.pricePerDay()));
        item.setSecurityDeposit(normalizeMoney(request.securityDeposit()));
        item.setContactPhone(request.contactPhone() == null || request.contactPhone().isBlank()
                ? null : request.contactPhone().trim());
        item.setAvailabilityStatus(request.availabilityStatus() == null
                ? AvailabilityStatus.AVAILABLE : request.availabilityStatus());
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value.signum() < 0) {
            throw new BusinessException("Price and deposit cannot be negative");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (!imageUrl.startsWith("/uploads/") && !imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            throw new BusinessException("Invalid image URL");
        }
    }

    private Sort buildSort(String sortBy, String direction) {
        String field = switch (sortBy == null ? "" : sortBy.toLowerCase(Locale.ROOT)) {
            case "itemname" -> "itemName";
            case "category" -> "category";
            case "price", "priceperday" -> "pricePerDay";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }
}
