package com.example.words.service;

import com.example.words.dto.DefinitionDto;
import com.example.words.dto.DictionaryWordEntryResponse;
import com.example.words.dto.ExampleSentenceDto;
import com.example.words.dto.GenerateDictionaryWordWithAiResponse;
import com.example.words.dto.InflectionDto;
import com.example.words.dto.MetaWordEntryDto;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.dto.SyllableDetailDto;
import com.example.words.dto.SyllableSegmentDto;
import com.example.words.dto.MetaWordSuggestionDto;
import com.example.words.dto.PartOfSpeechDto;
import com.example.words.dto.PhoneticDto;
import com.example.words.model.Definition;
import com.example.words.model.DictionaryWord;
import com.example.words.model.ExampleSentence;
import com.example.words.model.Inflection;
import com.example.words.model.MetaWord;
import com.example.words.model.SyllableDetail;
import com.example.words.model.SyllableSegment;
import com.example.words.model.PartOfSpeech;
import com.example.words.model.Phonetic;
import com.example.words.model.Tag;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.TagRepository;
import com.example.words.util.WordNormalizationUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionaryWordService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryWordService.class);
    private static final int DEFAULT_SUGGESTION_LIMIT = 8;
    private static final int MAX_SUGGESTION_LIMIT = 20;

    private final DictionaryWordRepository dictionaryWordRepository;
    private final MetaWordRepository metaWordRepository;
    private final TagRepository tagRepository;
    private final TagService tagService;
    private final DictionaryService dictionaryService;

    public DictionaryWordService(
            DictionaryWordRepository dictionaryWordRepository,
            MetaWordRepository metaWordRepository,
            TagRepository tagRepository,
            TagService tagService,
            DictionaryService dictionaryService) {
        this.dictionaryWordRepository = dictionaryWordRepository;
        this.metaWordRepository = metaWordRepository;
        this.tagRepository = tagRepository;
        this.tagService = tagService;
        this.dictionaryService = dictionaryService;
    }

    public List<DictionaryWord> findByDictionaryId(Long dictionaryId) {
        return dictionaryWordRepository.findByDictionaryIdOrderByDisplayOrder(dictionaryId);
    }

    public List<DictionaryWord> findByMetaWordId(Long metaWordId) {
        return dictionaryWordRepository.findByMetaWordId(metaWordId);
    }

    public Page<MetaWord> findMetaWordsByDictionaryId(Long dictionaryId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return metaWordRepository.findByDictionaryId(dictionaryId, pageable);
    }

    public Page<DictionaryWordEntryResponse> findEntriesByDictionaryId(
            Long dictionaryId,
            int page,
            int size,
            String keyword,
            String sortBy,
            String sortDir) {
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.max(size, 1));
        Page<DictionaryWord> entryPage = dictionaryWordRepository.findEntriesPage(
                dictionaryId,
                normalizeKeywordPattern(keyword),
                normalizeSortBy(sortBy),
                normalizeSortDir(sortDir),
                pageable
        );
        return toEntryResponsePage(entryPage);
    }

    public List<MetaWordSuggestionDto> findSuggestionsForDictionary(Long dictionaryId, String keyword, Integer limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return List.of();
        }

        int normalizedLimit = DEFAULT_SUGGESTION_LIMIT;
        if (limit != null) {
            normalizedLimit = Math.max(1, Math.min(limit, MAX_SUGGESTION_LIMIT));
        }

        Pageable pageable = PageRequest.of(0, normalizedLimit);
        return metaWordRepository.findSuggestionsByDictionaryIdAndKeyword(dictionaryId, normalizedKeyword, pageable)
                .stream()
                .map(this::toSuggestionDto)
                .toList();
    }

    public Optional<DictionaryWord> findByDictionaryIdAndMetaWordId(Long dictionaryId, Long metaWordId) {
        return dictionaryWordRepository.findByDictionaryIdAndMetaWordId(dictionaryId, metaWordId);
    }

    public boolean existsByDictionaryIdAndMetaWordId(Long dictionaryId, Long metaWordId) {
        return dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(dictionaryId, metaWordId);
    }

    @Transactional
    public DictionaryWord save(DictionaryWord dictionaryWord) {
        DictionaryWord saved = dictionaryWordRepository.save(normalizeEntry(dictionaryWord));
        refreshDictionaryCounts(saved.getDictionaryId());
        return saved;
    }

    @Transactional
    public void saveIfNotExists(Long dictionaryId, Long metaWordId) {
        if (dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(dictionaryId, metaWordId)) {
            return;
        }
        Long defaultChapterTagId = tagService.getOrCreateDefaultChapterTagId(dictionaryId);
        int nextOrder = nextEntryOrder(dictionaryId, defaultChapterTagId);
        dictionaryWordRepository.save(new DictionaryWord(dictionaryId, metaWordId, defaultChapterTagId, nextOrder));
        refreshDictionaryCounts(dictionaryId);
        log.debug("Saved dictionary-word relation into default chapter: dictionary={}, word={}", dictionaryId, metaWordId);
    }

    @Transactional
    public void deleteByDictionaryId(Long dictionaryId) {
        dictionaryWordRepository.deleteByDictionaryId(dictionaryId);
        dictionaryService.updateCounts(dictionaryId, 0, 0);
    }

    @Transactional
    public void deleteAll() {
        dictionaryWordRepository.deleteAll();
    }

    @Transactional
    public int saveAllBatch(Long dictionaryId, List<Long> metaWordIds) {
        return saveAllBatchIgnoringDuplicates(dictionaryId, metaWordIds);
    }

    @Transactional
    public int saveAllBatchIgnoringDuplicates(Long dictionaryId, List<Long> metaWordIds) {
        if (metaWordIds == null || metaWordIds.isEmpty()) {
            return 0;
        }

        Long defaultChapterTagId = tagService.getOrCreateDefaultChapterTagId(dictionaryId);
        int nextOrder = nextEntryOrder(dictionaryId, defaultChapterTagId);
        List<DictionaryWord> entries = new ArrayList<>();
        for (Long metaWordId : metaWordIds) {
            if (metaWordId == null) {
                continue;
            }
            entries.add(new DictionaryWord(dictionaryId, metaWordId, defaultChapterTagId, nextOrder++));
        }
        if (entries.isEmpty()) {
            return 0;
        }
        dictionaryWordRepository.saveAll(entries);
        refreshDictionaryCounts(dictionaryId);
        return entries.size();
    }

    @Transactional
    public WordListProcessResult processWordList(Long dictionaryId, List<MetaWordEntryDto> words) {
        if (words == null || words.isEmpty()) {
            return new WordListProcessResult(0, 0, 0, 0, 0);
        }
        log.debug("Processing word list for dictionary {}, input size: {}", dictionaryId, words.size());
        int total = 0;
        int existed = 0;
        int created = 0;
        int added = 0;
        int failed = 0;

        Long defaultChapterTagId = tagService.getOrCreateDefaultChapterTagId(dictionaryId);
        int nextOrder = nextEntryOrder(dictionaryId, defaultChapterTagId);
        List<DictionaryWord> entriesToCreate = new ArrayList<>();

        for (MetaWordEntryDto dto : words) {
            if (dto == null || dto.getWord() == null || dto.getWord().trim().isEmpty()) {
                continue;
            }
            total++;
            String word = dto.getWord().trim();
            try {
                Optional<MetaWord> existingMetaWordOpt = metaWordRepository.findByNormalizedWord(WordNormalizationUtils.normalize(word))
                        .or(() -> metaWordRepository.findByWord(word));
                MetaWord metaWord;
                if (existingMetaWordOpt.isPresent()) {
                    metaWord = existingMetaWordOpt.get();
                    existed++;
                    updateMetaWordFields(metaWord, dto);
                    metaWord = metaWordRepository.save(metaWord);
                } else {
                    metaWord = new MetaWord();
                    metaWord.setWord(word);
                    updateMetaWordFields(metaWord, dto);
                    metaWord = metaWordRepository.save(metaWord);
                    created++;
                }
                entriesToCreate.add(new DictionaryWord(dictionaryId, metaWord.getId(), defaultChapterTagId, nextOrder++));
                added++;
            } catch (Exception e) {
                log.error("Failed to process word: {}", word, e);
                failed++;
            }
        }

        if (!entriesToCreate.isEmpty()) {
            dictionaryWordRepository.saveAll(entriesToCreate);
            refreshDictionaryCounts(dictionaryId);
        }

        return new WordListProcessResult(total, existed, created, added, failed);
    }

    @Transactional
    public WordListProcessResult processWordListV2(Long dictionaryId, List<MetaWordEntryDtoV2> words) {
        if (words == null || words.isEmpty()) {
            return new WordListProcessResult(0, 0, 0, 0, 0);
        }
        log.debug("Processing word list V2 for dictionary {}, input size: {}", dictionaryId, words.size());
        int total = 0;
        int existed = 0;
        int created = 0;
        int added = 0;
        int failed = 0;

        Long defaultChapterTagId = tagService.getOrCreateDefaultChapterTagId(dictionaryId);
        int nextOrder = nextEntryOrder(dictionaryId, defaultChapterTagId);
        List<DictionaryWord> entriesToCreate = new ArrayList<>();

        for (MetaWordEntryDtoV2 dto : words) {
            if (dto == null || dto.getWord() == null || dto.getWord().trim().isEmpty()) {
                continue;
            }
            total++;
            String word = dto.getWord().trim();
            try {
                Optional<MetaWord> existingMetaWordOpt = metaWordRepository.findByNormalizedWord(WordNormalizationUtils.normalize(word))
                        .or(() -> metaWordRepository.findByWord(word));
                MetaWord metaWord;
                if (existingMetaWordOpt.isPresent()) {
                    metaWord = existingMetaWordOpt.get();
                    existed++;
                    updateMetaWordFields(metaWord, dto);
                    metaWord = metaWordRepository.save(metaWord);
                } else {
                    metaWord = new MetaWord();
                    metaWord.setWord(word);
                    updateMetaWordFields(metaWord, dto);
                    metaWord = metaWordRepository.save(metaWord);
                    created++;
                }
                entriesToCreate.add(new DictionaryWord(dictionaryId, metaWord.getId(), defaultChapterTagId, nextOrder++));
                added++;
            } catch (Exception e) {
                log.error("Failed to process word: {}", word, e);
                failed++;
            }
        }

        if (!entriesToCreate.isEmpty()) {
            dictionaryWordRepository.saveAll(entriesToCreate);
            refreshDictionaryCounts(dictionaryId);
        }

        return new WordListProcessResult(total, existed, created, added, failed);
    }

    @Transactional
    public GenerateDictionaryWordWithAiResponse saveGeneratedWordV2(
            Long dictionaryId,
            Long preferredMetaWordId,
            Long configId,
            String providerName,
            String modelName,
            MetaWordEntryDtoV2 entry) {
        if (entry == null || entry.getWord() == null || entry.getWord().trim().isEmpty()) {
            throw new IllegalArgumentException("Generated word entry must not be empty");
        }

        int existed = 0;
        int created = 0;
        int added = 0;

        MetaWord metaWord = resolveMetaWordForV2Entry(preferredMetaWordId, entry.getWord().trim())
                .orElseGet(() -> {
                    MetaWord createdMetaWord = new MetaWord();
                    createdMetaWord.setWord(entry.getWord().trim());
                    return createdMetaWord;
                });

        if (metaWord.getId() == null) {
            created++;
        } else {
            existed++;
        }

        metaWord.setWord(entry.getWord().trim());
        updateMetaWordFields(metaWord, entry);
        MetaWord savedMetaWord = metaWordRepository.save(metaWord);

        if (!dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(dictionaryId, savedMetaWord.getId())) {
            saveIfNotExists(dictionaryId, savedMetaWord.getId());
            added = 1;
        }

        return new GenerateDictionaryWordWithAiResponse(
                dictionaryId,
                savedMetaWord.getId(),
                configId,
                providerName,
                modelName,
                savedMetaWord.getWord(),
                savedMetaWord.getTranslation(),
                savedMetaWord.getPartOfSpeech(),
                savedMetaWord.getPhonetic(),
                savedMetaWord.getDefinition(),
                savedMetaWord.getExampleSentence(),
                1,
                existed,
                created,
                added,
                0
        );
    }

    public static class WordListProcessResult {
        private final int total;
        private final int existed;
        private final int created;
        private final int added;
        private final int failed;

        public WordListProcessResult(int total, int existed, int created, int added, int failed) {
            this.total = total;
            this.existed = existed;
            this.created = created;
            this.added = added;
            this.failed = failed;
        }

        public int getTotal() { return total; }
        public int getExisted() { return existed; }
        public int getCreated() { return created; }
        public int getAdded() { return added; }
        public int getFailed() { return failed; }
    }

    private Page<DictionaryWordEntryResponse> toEntryResponsePage(Page<DictionaryWord> entryPage) {
        List<DictionaryWord> entries = entryPage.getContent();
        Map<Long, MetaWord> metaWordMap = loadMetaWords(entries);
        Map<Long, Tag> tagMap = loadTags(entries);
        List<DictionaryWordEntryResponse> content = entries.stream()
                .map(entry -> toEntryResponse(entry, metaWordMap.get(entry.getMetaWordId()), tagMap.get(entry.getChapterTagId())))
                .toList();
        return new PageImpl<>(content, entryPage.getPageable(), entryPage.getTotalElements());
    }

    private Map<Long, MetaWord> loadMetaWords(List<DictionaryWord> entries) {
        Set<Long> ids = new LinkedHashSet<>();
        for (DictionaryWord entry : entries) {
            ids.add(entry.getMetaWordId());
        }
        Map<Long, MetaWord> result = new HashMap<>();
        for (MetaWord metaWord : metaWordRepository.findAllById(ids)) {
            result.put(metaWord.getId(), metaWord);
        }
        return result;
    }

    private Map<Long, Tag> loadTags(List<DictionaryWord> entries) {
        Set<Long> ids = new LinkedHashSet<>();
        for (DictionaryWord entry : entries) {
            if (entry.getChapterTagId() != null) {
                ids.add(entry.getChapterTagId());
            }
        }
        Map<Long, Tag> result = new HashMap<>();
        for (Tag tag : tagRepository.findAllById(ids)) {
            result.put(tag.getId(), tag);
        }
        return result;
    }

    private DictionaryWordEntryResponse toEntryResponse(DictionaryWord entry, MetaWord metaWord, Tag tag) {
        return new DictionaryWordEntryResponse(
                entry.getId(),
                entry.getDictionaryId(),
                entry.getMetaWordId(),
                metaWord == null ? null : metaWord.getWord(),
                metaWord == null ? null : metaWord.getTranslation(),
                metaWord == null ? null : metaWord.getPhonetic(),
                metaWord == null ? null : metaWord.getDefinition(),
                entry.getChapterTagId(),
                tag == null ? null : tag.getPathName(),
                entry.getEntryOrder()
        );
    }

    private DictionaryWord normalizeEntry(DictionaryWord dictionaryWord) {
        Long chapterTagId = dictionaryWord.getChapterTagId();
        if (chapterTagId == null) {
            chapterTagId = tagService.getOrCreateDefaultChapterTagId(dictionaryWord.getDictionaryId());
            dictionaryWord.setChapterTagId(chapterTagId);
        } else {
            tagService.getChapterTagOrThrow(chapterTagId, dictionaryWord.getDictionaryId());
        }
        if (dictionaryWord.getEntryOrder() == null || dictionaryWord.getEntryOrder() < 1) {
            dictionaryWord.setEntryOrder(nextEntryOrder(dictionaryWord.getDictionaryId(), chapterTagId));
        }
        return dictionaryWord;
    }

    private int nextEntryOrder(Long dictionaryId, Long chapterTagId) {
        Integer maxOrder = dictionaryWordRepository.findMaxEntryOrderByDictionaryIdAndChapterTagId(dictionaryId, chapterTagId);
        return (maxOrder == null ? 0 : maxOrder) + 1;
    }

    private String normalizeKeywordPattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        return "%" + trimmed + "%";
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "entryOrder";
        }
        return switch (sortBy) {
            case "word" -> "word";
            case "translation" -> "translation";
            case "chapter" -> "chapter";
            case "entryOrder" -> "entryOrder";
            default -> "entryOrder";
        };
    }

    private String normalizeSortDir(String sortDir) {
        return "desc".equalsIgnoreCase(sortDir) ? "desc" : "asc";
    }

    private void refreshDictionaryCounts(Long dictionaryId) {
        int uniqueWordCount = (int) dictionaryWordRepository.countDistinctMetaWordIdByDictionaryId(dictionaryId);
        int entryCount = (int) dictionaryWordRepository.countByDictionaryId(dictionaryId);
        dictionaryService.updateCounts(dictionaryId, uniqueWordCount, entryCount);
    }

    private void updateMetaWordFields(MetaWord metaWord, MetaWordEntryDto dto) {
        if (dto.getPhonetic() != null && !dto.getPhonetic().trim().isEmpty()) {
            metaWord.setPhonetic(dto.getPhonetic().trim());
        }
        if (dto.getDefinition() != null && !dto.getDefinition().trim().isEmpty()) {
            metaWord.setDefinition(dto.getDefinition().trim());
        }
        if (dto.getPartOfSpeech() != null && !dto.getPartOfSpeech().trim().isEmpty()) {
            metaWord.setPartOfSpeech(dto.getPartOfSpeech().trim());
        }
        if (dto.getExampleSentence() != null && !dto.getExampleSentence().trim().isEmpty()) {
            metaWord.setExampleSentence(dto.getExampleSentence().trim());
        }
        if (dto.getTranslation() != null && !dto.getTranslation().trim().isEmpty()) {
            metaWord.setTranslation(dto.getTranslation().trim());
        }
        metaWord.setDifficulty(dto.getDifficulty() != null ? dto.getDifficulty() : 2);
    }

    private void updateMetaWordFields(MetaWord metaWord, MetaWordEntryDtoV2 dto) {
        if (dto.getPhonetic() != null) {
            Phonetic phonetic = metaWord.getPhoneticDetail();
            if (phonetic == null) {
                phonetic = new Phonetic();
            }
            if (dto.getPhonetic().getUk() != null) {
                phonetic.setUk(dto.getPhonetic().getUk().trim());
            }
            if (dto.getPhonetic().getUs() != null) {
                phonetic.setUs(dto.getPhonetic().getUs().trim());
            }
            metaWord.setPhoneticDetail(phonetic);
        }

        if (dto.getSyllableDetail() != null) {
            metaWord.setSyllableDetail(convertSyllableDetail(dto.getSyllableDetail()));
        }

        if (dto.getPartOfSpeech() != null && !dto.getPartOfSpeech().isEmpty()) {
            List<PartOfSpeech> partOfSpeechDetail = convertPartOfSpeechDtos(dto.getPartOfSpeech());
            metaWord.setPartOfSpeechDetail(partOfSpeechDetail);
            syncFlatMetaWordFields(metaWord, dto, partOfSpeechDetail);
        }

        metaWord.setDifficulty(dto.getDifficulty() != null ? dto.getDifficulty() : 2);
    }

    private SyllableDetail convertSyllableDetail(SyllableDetailDto detail) {
        List<SyllableSegment> segments = detail.getSegments() == null
                ? List.of()
                : detail.getSegments().stream()
                        .filter(Objects::nonNull)
                        .map(this::convertSyllableSegment)
                        .toList();
        return new SyllableDetail(segments);
    }

    private SyllableSegment convertSyllableSegment(SyllableSegmentDto segment) {
        return new SyllableSegment(
                segment.getText(),
                segment.getUkPhonetic(),
                segment.getUsPhonetic(),
                segment.getUkAudioUrl(),
                segment.getUsAudioUrl()
        );
    }

    private Optional<MetaWord> resolveMetaWordForV2Entry(Long preferredMetaWordId, String word) {
        if (preferredMetaWordId != null) {
            return metaWordRepository.findById(preferredMetaWordId);
        }
        return metaWordRepository.findByNormalizedWord(WordNormalizationUtils.normalize(word))
                .or(() -> metaWordRepository.findByWord(word));
    }

    private void syncFlatMetaWordFields(MetaWord metaWord, MetaWordEntryDtoV2 dto, List<PartOfSpeech> partOfSpeechDetail) {
        if (dto.getPhonetic() != null) {
            String flattenedPhonetic = firstNonBlank(
                    dto.getPhonetic().getUk(),
                    dto.getPhonetic().getUs()
            );
            if (flattenedPhonetic != null) {
                metaWord.setPhonetic(flattenedPhonetic.trim());
            }
        }

        if (dto.getPartOfSpeech() != null && !dto.getPartOfSpeech().isEmpty()) {
            String firstPos = dto.getPartOfSpeech().stream()
                    .map(PartOfSpeechDto::getPos)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .findFirst()
                    .orElse(null);
            if (firstPos != null) {
                metaWord.setPartOfSpeech(firstPos);
            }
        }

        if (partOfSpeechDetail == null || partOfSpeechDetail.isEmpty()) {
            return;
        }

        for (PartOfSpeech partOfSpeech : partOfSpeechDetail) {
            if (partOfSpeech == null || partOfSpeech.getDefinitions() == null) {
                continue;
            }
            for (Definition definition : partOfSpeech.getDefinitions()) {
                if (definition == null) {
                    continue;
                }
                if (metaWord.getDefinition() == null && definition.getDefinition() != null && !definition.getDefinition().trim().isEmpty()) {
                    metaWord.setDefinition(definition.getDefinition().trim());
                }
                if (metaWord.getTranslation() == null && definition.getTranslation() != null && !definition.getTranslation().trim().isEmpty()) {
                    metaWord.setTranslation(definition.getTranslation().trim());
                }
                if (metaWord.getExampleSentence() == null && definition.getExampleSentences() != null) {
                    for (ExampleSentence exampleSentence : definition.getExampleSentences()) {
                        if (exampleSentence != null
                                && exampleSentence.getSentence() != null
                                && !exampleSentence.getSentence().trim().isEmpty()) {
                            metaWord.setExampleSentence(exampleSentence.getSentence().trim());
                            break;
                        }
                    }
                }
                if (metaWord.getDefinition() != null && metaWord.getTranslation() != null && metaWord.getExampleSentence() != null) {
                    return;
                }
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private List<PartOfSpeech> convertPartOfSpeechDtos(List<PartOfSpeechDto> dtos) {
        if (dtos == null) {
            return null;
        }

        return dtos.stream().map(dto -> {
            PartOfSpeech pos = new PartOfSpeech();
            pos.setPos(dto.getPos() != null ? dto.getPos().trim() : null);

            if (dto.getDefinitions() != null) {
                pos.setDefinitions(convertDefinitionDtos(dto.getDefinitions()));
            }

            if (dto.getInflection() != null) {
                pos.setInflection(convertInflectionDto(dto.getInflection()));
            }

            pos.setSynonyms(dto.getSynonyms());
            pos.setAntonyms(dto.getAntonyms());

            return pos;
        }).toList();
    }

    private List<Definition> convertDefinitionDtos(List<DefinitionDto> dtos) {
        if (dtos == null) {
            return null;
        }

        return dtos.stream().map(dto -> {
            Definition def = new Definition();
            def.setDefinition(dto.getDefinition() != null ? dto.getDefinition().trim() : null);
            def.setTranslation(dto.getTranslation() != null ? dto.getTranslation().trim() : null);

            if (dto.getExampleSentences() != null) {
                def.setExampleSentences(convertExampleSentenceDtos(dto.getExampleSentences()));
            }

            return def;
        }).toList();
    }

    private List<ExampleSentence> convertExampleSentenceDtos(List<ExampleSentenceDto> dtos) {
        if (dtos == null) {
            return null;
        }

        return dtos.stream().map(dto -> {
            ExampleSentence ex = new ExampleSentence();
            ex.setSentence(dto.getSentence() != null ? dto.getSentence().trim() : null);
            ex.setTranslation(dto.getTranslation() != null ? dto.getTranslation().trim() : null);
            return ex;
        }).toList();
    }

    private Inflection convertInflectionDto(InflectionDto dto) {
        if (dto == null) {
            return null;
        }

        Inflection inflection = new Inflection();
        inflection.setPlural(dto.getPlural() != null ? dto.getPlural().trim() : null);
        inflection.setPast(dto.getPast() != null ? dto.getPast().trim() : null);
        inflection.setPastParticiple(dto.getPastParticiple() != null ? dto.getPastParticiple().trim() : null);
        inflection.setPresentParticiple(dto.getPresentParticiple() != null ? dto.getPresentParticiple().trim() : null);
        inflection.setThirdPersonSingular(dto.getThirdPersonSingular() != null ? dto.getThirdPersonSingular().trim() : null);
        inflection.setComparative(dto.getComparative() != null ? dto.getComparative().trim() : null);
        inflection.setSuperlative(dto.getSuperlative() != null ? dto.getSuperlative().trim() : null);

        return inflection;
    }

    private MetaWordSuggestionDto toSuggestionDto(MetaWord metaWord) {
        return new MetaWordSuggestionDto(
                metaWord.getId(),
                metaWord.getWord(),
                metaWord.getPhonetic(),
                metaWord.getDefinition(),
                metaWord.getPartOfSpeech(),
                metaWord.getExampleSentence(),
                metaWord.getTranslation(),
                metaWord.getDifficulty()
        );
    }
}
