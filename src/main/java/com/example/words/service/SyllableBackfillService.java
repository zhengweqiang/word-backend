package com.example.words.service;

import com.example.words.dto.GenerateWordDetailsRequest;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.dto.SyllableBackfillFailureResponse;
import com.example.words.dto.SyllableBackfillResponse;
import com.example.words.dto.SyllableDetailDto;
import com.example.words.dto.SyllableSegmentDto;
import com.example.words.model.MetaWord;
import com.example.words.model.SyllableDetail;
import com.example.words.model.SyllableSegment;
import com.example.words.repository.MetaWordRepository;
import com.example.words.util.WordNormalizationUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SyllableBackfillService {

    private static final int MAX_BATCH_SIZE = 500;

    private final MetaWordRepository metaWordRepository;
    private final AiGenerationService aiGenerationService;

    public SyllableBackfillService(
            MetaWordRepository metaWordRepository,
            AiGenerationService aiGenerationService) {
        this.metaWordRepository = metaWordRepository;
        this.aiGenerationService = aiGenerationService;
    }

    public SyllableBackfillResponse backfillPublishedPlanWords(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        List<MetaWord> candidates = metaWordRepository.findPublishedPlanWordsMissingSyllables(limit);
        List<SyllableBackfillFailureResponse> failures = new ArrayList<>();
        int updated = 0;

        for (MetaWord candidate : candidates) {
            try {
                AiGenerationService.GeneratedWordEntryV2 generated = aiGenerationService.generateWordEntryV2(
                        new GenerateWordDetailsRequest(null, candidate.getWord())
                );
                MetaWordEntryDtoV2 entry = generated.entry();
                SyllableDetail detail = validateAndMap(candidate.getWord(), entry.getSyllableDetail());
                candidate.setSyllableDetail(detail);
                metaWordRepository.save(candidate);
                updated++;
            } catch (RuntimeException exception) {
                failures.add(new SyllableBackfillFailureResponse(
                        candidate.getId(),
                        candidate.getWord(),
                        failureMessage(exception)
                ));
            }
        }

        return new SyllableBackfillResponse(
                candidates.size(),
                updated,
                0,
                failures
        );
    }

    SyllableDetail validateAndMap(String word, SyllableDetailDto detail) {
        if (detail == null || detail.getSegments() == null || detail.getSegments().isEmpty()) {
            throw new IllegalArgumentException("AI did not return syllable segments");
        }

        List<SyllableSegment> segments = new ArrayList<>();
        StringBuilder spelling = new StringBuilder();
        for (SyllableSegmentDto segment : detail.getSegments()) {
            if (segment == null || isBlank(segment.getText())) {
                throw new IllegalArgumentException("Syllable text must not be blank");
            }
            if (isBlank(segment.getUkPhonetic()) && isBlank(segment.getUsPhonetic())) {
                throw new IllegalArgumentException("Each syllable requires UK or US phonetic text");
            }

            String text = segment.getText().trim();
            spelling.append(text);
            segments.add(new SyllableSegment(
                    text,
                    trimToNull(segment.getUkPhonetic()),
                    trimToNull(segment.getUsPhonetic()),
                    trimToNull(segment.getUkAudioUrl()),
                    trimToNull(segment.getUsAudioUrl())
            ));
        }

        if (!WordNormalizationUtils.normalize(spelling.toString())
                .equals(WordNormalizationUtils.normalize(word))) {
            throw new IllegalArgumentException("Syllable spelling does not reconstruct the word");
        }
        return new SyllableDetail(segments);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String failureMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
