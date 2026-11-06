package com.borrowbuddy.service;

import com.borrowbuddy.dto.PublicStats;
import com.borrowbuddy.entity.enums.TransactionStatus;
import com.borrowbuddy.repository.BorrowTransactionRepository;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicService {

    private final StudentRepository studentRepository;
    private final ItemRepository itemRepository;
    private final BorrowTransactionRepository transactionRepository;

    public PublicService(StudentRepository studentRepository,
                         ItemRepository itemRepository,
                         BorrowTransactionRepository transactionRepository) {
        this.studentRepository = studentRepository;
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PublicStats stats() {
        return new PublicStats(
                studentRepository.count(),
                itemRepository.countByDeletedFalse(),
                transactionRepository.countByStatus(TransactionStatus.COMPLETED)
        );
    }
}
