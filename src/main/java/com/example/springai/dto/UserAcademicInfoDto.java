package com.example.springai.dto;

import java.math.BigDecimal;

public record UserAcademicInfoDto(
        Long id,
        BigDecimal gpa,
        BigDecimal grade,
        Integer generalEducationCredits,
        Integer majorCredits) {
}
