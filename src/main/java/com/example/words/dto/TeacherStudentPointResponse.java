package com.example.words.dto;

public record TeacherStudentPointResponse(
        Long studentId,
        String studentName,
        Integer availablePoints,
        Integer lifetimeEarnedPoints,
        Integer lifetimeSpentPoints,
        Long todayEarnedPoints
) {
}
