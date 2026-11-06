package com.borrowbuddy.entity;

import com.borrowbuddy.entity.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "students", indexes = {
        @Index(name = "idx_students_email", columnList = "college_email", unique = true),
        @Index(name = "idx_students_student_id", columnList = "student_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "college_email", nullable = false, unique = true, length = 150)
    private String collegeEmail;

    /** BCrypt hash. Never serialized in any API response. */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "student_id", nullable = false, unique = true, length = 50)
    private String studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.STUDENT;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (role == null) {
            role = Role.STUDENT;
        }
    }
}
