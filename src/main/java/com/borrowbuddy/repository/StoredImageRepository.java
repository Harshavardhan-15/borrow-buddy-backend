package com.borrowbuddy.repository;

import com.borrowbuddy.entity.StoredImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredImageRepository extends JpaRepository<StoredImage, String> {
}
