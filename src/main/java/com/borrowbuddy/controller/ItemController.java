package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.PageResponse;
import com.borrowbuddy.dto.item.ItemDto;
import com.borrowbuddy.dto.item.ItemRequest;
import com.borrowbuddy.entity.enums.ItemCondition;
import com.borrowbuddy.service.CurrentUserService;
import com.borrowbuddy.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;
    private final CurrentUserService currentUserService;

    public ItemController(ItemService itemService, CurrentUserService currentUserService) {
        this.itemService = itemService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ItemDto>> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ItemCondition condition,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        return ApiResponse.ok(itemService.browse(q, category, condition, availableOnly, page, size, sort, dir));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<ItemDto>> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ItemCondition condition,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        return ApiResponse.ok(itemService.browse(q, category, condition, availableOnly, page, size, sort, dir));
    }

    @GetMapping("/featured")
    public ApiResponse<List<ItemDto>> featured() {
        return ApiResponse.ok(itemService.featured());
    }

    @GetMapping("/mine")
    public ApiResponse<List<ItemDto>> mine() {
        return ApiResponse.ok(itemService.mine(currentUserService.requireCurrent()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ItemDto> get(@PathVariable Long id) {
        return ApiResponse.ok(itemService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ItemDto> create(@Valid @RequestBody ItemRequest request) {
        return ApiResponse.ok("Item listed successfully",
                itemService.create(currentUserService.requireCurrent(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ItemDto> update(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return ApiResponse.ok("Item updated",
                itemService.update(currentUserService.requireCurrent(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        itemService.delete(currentUserService.requireCurrent(), id);
        return ApiResponse.ok("Item removed", null);
    }
}
