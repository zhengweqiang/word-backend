package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.words.dto.GenerateWordDetailsRequest;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.dto.SyllableBackfillResponse;
import com.example.words.dto.SyllableDetailDto;
import com.example.words.dto.SyllableSegmentDto;
import com.example.words.model.MetaWord;
import com.example.words.repository.MetaWordRepository;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyllableBackfillServiceTest {

    @Test
    void backfillShouldSaveValidResultsAndIsolateInvalidOrFailedWords() {
        MetaWord resilient = word(1L, "resilient");
        MetaWord book = word(2L, "book");
        MetaWord fragile = word(3L, "fragile");
        List<MetaWord> saved = new ArrayList<>();
        MetaWordRepository repository = repository(List.of(resilient, book, fragile), saved);

        FakeAiGenerationService ai = new FakeAiGenerationService(Map.of(
                "resilient", entry("resilient", List.of(
                        segment("re", "/rɪ/"),
                        segment("sil", "/ˈzɪl/"),
                        segment("ient", "/iənt/")
                )),
                "book", entry("book", List.of(segment("boo", "/bʊ/")))
        ));
        SyllableBackfillService service = new SyllableBackfillService(repository, ai);

        SyllableBackfillResponse response = service.backfillPublishedPlanWords(50);

        assertEquals(3, response.getAttempted());
        assertEquals(1, response.getUpdated());
        assertEquals(2, response.getFailures().size());
        assertEquals(List.of(resilient), saved);
        assertEquals("re", resilient.getSyllableDetail().getSegments().get(0).getText());
    }

    private MetaWord word(Long id, String value) {
        MetaWord word = new MetaWord();
        word.setId(id);
        word.setWord(value);
        return word;
    }

    private MetaWordEntryDtoV2 entry(String word, List<SyllableSegmentDto> segments) {
        MetaWordEntryDtoV2 entry = new MetaWordEntryDtoV2();
        entry.setWord(word);
        entry.setSyllableDetail(new SyllableDetailDto(segments));
        return entry;
    }

    private SyllableSegmentDto segment(String text, String phonetic) {
        return new SyllableSegmentDto(text, phonetic, phonetic, null, null);
    }

    private MetaWordRepository repository(List<MetaWord> candidates, List<MetaWord> saved) {
        return (MetaWordRepository) Proxy.newProxyInstance(
                MetaWordRepository.class.getClassLoader(),
                new Class<?>[]{MetaWordRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findPublishedPlanWordsMissingSyllables")) {
                        return candidates;
                    }
                    if (method.getName().equals("save")) {
                        MetaWord value = (MetaWord) args[0];
                        saved.add(value);
                        return value;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class FakeAiGenerationService extends AiGenerationService {

        private final Map<String, MetaWordEntryDtoV2> entries;

        private FakeAiGenerationService(Map<String, MetaWordEntryDtoV2> entries) {
            super(null, null, null);
            this.entries = entries;
        }

        @Override
        public GeneratedWordEntryV2 generateWordEntryV2(GenerateWordDetailsRequest request) {
            MetaWordEntryDtoV2 entry = entries.get(request.getWord());
            if (entry == null) {
                throw new IllegalStateException("AI generation failed");
            }
            return new GeneratedWordEntryV2(1L, "test", "test", entry);
        }
    }
}
