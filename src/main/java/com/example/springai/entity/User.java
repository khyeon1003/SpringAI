package com.example.springai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal gpa;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal grade;

    @Column(nullable = false)
    private Integer generalEducationCredits;

    @Column(nullable = false)
    private Integer majorCredits;

    @Builder
    public User(BigDecimal gpa, BigDecimal grade, Integer generalEducationCredits, Integer majorCredits) {
        this.gpa = gpa;
        this.grade = grade;
        this.generalEducationCredits = generalEducationCredits;
        this.majorCredits = majorCredits;
    }
}
