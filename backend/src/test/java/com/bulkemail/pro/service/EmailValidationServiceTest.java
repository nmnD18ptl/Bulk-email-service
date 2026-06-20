package com.bulkemail.pro.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailValidationServiceTest {

    private EmailValidationService service;

    @BeforeEach
    void setUp() {
        service = new EmailValidationService();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "bad", "@nodomain.com", "spaces in@name.com"})
    void rejects_invalid_syntax(String email) {
        assertFalse(service.isValidSyntax(email));
    }

    @Test
    void accepts_common_syntax() {
        assertTrue(service.isValidSyntax("user.name+tag@example.com"));
    }

    @Test
    void validate_exercises_full_validation_path() {
        assertDoesNotThrow(() -> service.validate("user@example.com"));
    }
}
