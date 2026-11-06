package com.borrowbuddy.repository;

import com.borrowbuddy.entity.Student;
import com.borrowbuddy.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByCollegeEmail(String collegeEmail);

    boolean existsByCollegeEmail(String collegeEmail);

    boolean existsByStudentId(String studentId);

    List<Student> findByRole(Role role);
}
