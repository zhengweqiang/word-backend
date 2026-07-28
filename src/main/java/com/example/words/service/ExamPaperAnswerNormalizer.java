package com.example.words.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ExamPaperAnswerNormalizer {

    public List<String> normalizeOptionKeys(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public String normalizeBlankAnswer(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
