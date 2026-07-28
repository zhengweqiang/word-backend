package com.example.words.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExamPaperAnswerNormalizerTest {

    private final ExamPaperAnswerNormalizer normalizer = new ExamPaperAnswerNormalizer();

    @Test
    void normalizeOptionKeysTrimsUppercasesAndOrdersKeys() {
        assertEquals(List.of("A", "B", "C"), normalizer.normalizeOptionKeys(List.of(" b ", "a", "C ")));
    }

    @Test
    void normalizeOptionKeysDeduplicatesEquivalentKeysAndDiscardsBlanks() {
        assertEquals(List.of("A", "B"),
                normalizer.normalizeOptionKeys(List.of("A", " a ", "", "  ", "b", "B")));
    }

    @Test
    void normalizeBlankAnswerTrimsSurroundingWhitespace() {
        assertEquals("hello  world", normalizer.normalizeBlankAnswer("  hello  world  "));
    }

    @Test
    void normalizeBlankAnswerSupportsCaseInsensitiveEnglishComparison() {
        assertEquals(normalizer.normalizeBlankAnswer("TeSt"), normalizer.normalizeBlankAnswer(" test "));
    }
}
