package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.student.StudentProfileDto;
import com.borrowbuddy.dto.student.UpdateProfileRequest;
import com.borrowbuddy.service.CurrentUserService;
import com.borrowbuddy.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final CurrentUserService currentUserService;

    public StudentController(StudentService studentService, CurrentUserService currentUserService) {
        this.studentService = studentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/profile")
    public ApiResponse<StudentProfileDto> profile() {
        return ApiResponse.ok(studentService.profile(currentUserService.requireCurrent()));
    }

    @PutMapping("/profile")
    public ApiResponse<StudentProfileDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("Profile updated", studentService.updateProfile(
                currentUserService.requireCurrent(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentProfileDto> student(@PathVariable Long id) {
        return ApiResponse.ok(studentService.publicProfile(id));
    }
}
