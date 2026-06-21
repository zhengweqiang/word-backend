package com.example.words.service;

import java.io.BufferedReader;
import com.example.words.dto.MetaWordDetailResponse;
import com.example.words.dto.MetaWordDictionaryReferenceDto;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryCreationType;
import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.model.SyllableDetail;
import com.example.words.model.SyllableSegment;
import com.example.words.model.Phonetic;
import com.example.words.model.PartOfSpeech;
import com.example.words.model.Definition;
import com.example.words.model.ExampleSentence;
import com.example.words.dto.MetaWordReferenceLocationDto;
import com.example.words.dto.MetaWordSearchRequest;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.dto.PartOfSpeechDto;
import com.example.words.dto.PhoneticDto;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.TagRepository;
import com.example.words.util.WordNormalizationUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class MetaWordService {

    private static final Logger log = LoggerFactory.getLogger(MetaWordService.class);
    private static final String BOOKS_DIR = "/app/books";
    private static final int IMPORT_BATCH_SIZE = 500;
    private static final int WORD_CACHE_LIMIT = 10_000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MetaWordRepository metaWordRepository;
    private final DictionaryService dictionaryService;
    private final DictionaryWordService dictionaryWordService;
    private final TagRepository tagRepository;
    private final CurrentUserService currentUserService;
    private final AccessControlService accessControlService;
    private final TransactionTemplate transactionTemplate;

    public MetaWordService(
            MetaWordRepository metaWordRepository,
            DictionaryService dictionaryService,
            DictionaryWordService dictionaryWordService,
            TagRepository tagRepository,
            CurrentUserService currentUserService,
            AccessControlService accessControlService,
            PlatformTransactionManager transactionManager) {
        this.metaWordRepository = metaWordRepository;
        this.dictionaryService = dictionaryService;
        this.dictionaryWordService = dictionaryWordService;
        this.tagRepository = tagRepository;
        this.currentUserService = currentUserService;
        this.accessControlService = accessControlService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<MetaWord> findAll() {
        return metaWordRepository.findAll();
    }

    public Optional<MetaWord> findById(Long id) {
        return metaWordRepository.findById(id);
    }

    public Optional<MetaWordDetailResponse> findDetailById(Long id) {
        return metaWordRepository.findById(id).map(this::toDetailResponse);
    }

    public Optional<MetaWord> findByWord(String word) {
        String normalizedWord = WordNormalizationUtils.normalize(word);
        if (normalizedWord == null || normalizedWord.isEmpty()) {
            return Optional.empty();
        }
        return metaWordRepository.findByNormalizedWord(normalizedWord)
                .or(() -> metaWordRepository.findByWord(word));
    }

    public List<MetaWord> findByDifficulty(Integer difficulty) {
        return metaWordRepository.findByDifficulty(difficulty);
    }

    public List<MetaWord> findByWordStartingWith(String prefix) {
        return metaWordRepository.findByWordStartingWith(prefix);
    }

    @Transactional
    public MetaWord save(MetaWord metaWord) {
        return metaWordRepository.save(metaWord);
    }

    @Transactional
    public MetaWord saveIfNotExists(String word, String phonetic, String definition, String partOfSpeech) {
        Optional<MetaWord> existing = metaWordRepository.findByNormalizedWord(WordNormalizationUtils.normalize(word));
        if (existing.isPresent()) {
            return existing.get();
        }
        MetaWord metaWord = new MetaWord(word, phonetic, definition, partOfSpeech);
        return metaWordRepository.save(metaWord);
    }
    
    @Transactional
    public MetaWord saveIfNotExists(String word, Phonetic phoneticDetail, java.util.List<PartOfSpeech> partOfSpeechDetail) {
        Optional<MetaWord> existing = metaWordRepository.findByNormalizedWord(WordNormalizationUtils.normalize(word));
        if (existing.isPresent()) {
            MetaWord metaWord = existing.get();
            // Update with new detailed information
            if (phoneticDetail != null) {
                metaWord.setPhoneticDetail(phoneticDetail);
            }
            if (partOfSpeechDetail != null && !partOfSpeechDetail.isEmpty()) {
                metaWord.setPartOfSpeechDetail(partOfSpeechDetail);
            }
            return metaWordRepository.save(metaWord);
        }
        MetaWord metaWord = new MetaWord(word, phoneticDetail, partOfSpeechDetail);
        return metaWordRepository.save(metaWord);
    }

    @Transactional
    public MetaWord saveWordIfNotExists(String word) {
        Optional<MetaWord> existing = metaWordRepository.findByNormalizedWord(WordNormalizationUtils.normalize(word));
        if (existing.isPresent()) {
            return existing.get();
        }
        MetaWord metaWord = new MetaWord();
        metaWord.setWord(word);
        metaWord.setDifficulty(2);
        return metaWordRepository.save(metaWord);
    }

    @Transactional
    public void deleteAll() {
        dictionaryWordService.deleteAll();
        dictionaryService.deleteAll();
        metaWordRepository.deleteAll();
    }

    public int importFromBooksDirectory() {
        return importBooksData().getWordCount();
    }

    public BooksImportResult importBooksData() {
        return importBooksData(BooksImportProgressListener.NOOP);
    }

    public BooksImportResult importBooksData(BooksImportProgressListener progressListener) {
        resetImportData();

        java.io.File dir = new java.io.File(BOOKS_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            log.error("Directory not found: {}", BOOKS_DIR);
            progressListener.onFilesDiscovered(0);
            return new BooksImportResult(0, 0);
        }

        java.io.File[] files = dir.listFiles((d, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".csv") || lowerName.endsWith(".json");
        });
        if (files == null || files.length == 0) {
            log.warn("No importable files found in directory: {}", BOOKS_DIR);
            progressListener.onFilesDiscovered(0);
            return new BooksImportResult(0, 0);
        }

        Arrays.sort(files, Comparator.comparing(java.io.File::getName));
        progressListener.onFilesDiscovered(files.length);

        long startTime = System.currentTimeMillis();
        log.info("Starting import from {} files", files.length);

        Map<String, Long> wordCache = createWordCache();
        int totalWordCount = 0;
        int importedDictionaryCount = 0;
        int processedFiles = 0;
        for (java.io.File file : files) {
            progressListener.onFileStarted(file.getName(), processedFiles, files.length);
            ImportFileResult result = importFromFile(file, wordCache);
            if (result.success()) {
                importedDictionaryCount++;
                totalWordCount += result.wordCount();
            }
            processedFiles++;
            progressListener.onFileCompleted(
                    file.getName(),
                    processedFiles,
                    files.length,
                    importedDictionaryCount,
                    totalWordCount,
                    result.success()
            );
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Import completed: {} words, {} dictionaries, time: {}ms ({}s)",
                totalWordCount, importedDictionaryCount, elapsedTime, elapsedTime / 1000);
        return new BooksImportResult(importedDictionaryCount, totalWordCount);
    }

    public ImportFileResult importFromFile(java.io.File file, Map<String, Long> wordCache) {
        try {
            if (isJsonFile(file.getName())) {
                return importFromJsonFile(file, wordCache);
            } else {
                return importFromCsvFile(file, wordCache);
            }
        } catch (Exception e) {
            log.error("Error importing file: {}", file.getName(), e);
            return ImportFileResult.failure();
        }
    }

    private ImportFileResult importFromCsvFile(java.io.File file, Map<String, Long> wordCache)
            throws IOException, CsvValidationException {
        String dictionaryName = stripExtension(file.getName());
        String category = dictionaryService.extractCategory(dictionaryName);
        Long dictionaryId = createImportedDictionary(file, dictionaryName, category);

        int wordCount = 0;
        List<CsvImportRow> batch = new ArrayList<>(IMPORT_BATCH_SIZE);

        try (BufferedReader fileReader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length < 2) {
                    log.warn("Invalid line (less than 2 columns) in {}: {}", file.getName(), Arrays.toString(line));
                    continue;
                }

                String word = line[0].trim();
                String definition = line[1].trim();

                if (word.isEmpty()) {
                    log.warn("Empty word in {}: {}", file.getName(), Arrays.toString(line));
                    continue;
                }

                if (definition.isEmpty()) {
                    log.warn("Empty definition in {}: word='{}', raw line: {}", file.getName(), word, Arrays.toString(line));
                }

                batch.add(new CsvImportRow(word, definition));
                if (batch.size() >= IMPORT_BATCH_SIZE) {
                    wordCount += persistCsvBatch(dictionaryId, category, batch, wordCache);
                    batch.clear();
                }
            }
        } catch (Exception e) {
            log.error("Error importing CSV file: {}", file.getName(), e);
            return ImportFileResult.failure();
        }

        if (!batch.isEmpty()) {
            wordCount += persistCsvBatch(dictionaryId, category, batch, wordCache);
        }

        log.info("Imported {} words from CSV file {}", wordCount, file.getName());
        return ImportFileResult.success(wordCount);
    }

    private ImportFileResult importFromJsonFile(java.io.File file, Map<String, Long> wordCache) throws IOException {
        int wordCount = 0;
        String dictionaryName = stripExtension(file.getName());
        String category = dictionaryService.extractCategory(dictionaryName);
        Long dictionaryId = createImportedDictionary(file, dictionaryName, category);
        List<MetaWordEntryDtoV2> batch = new ArrayList<>(IMPORT_BATCH_SIZE);

        try (JsonParser parser = objectMapper.getFactory().createParser(file)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("JSON import file must contain an array: " + file.getName());
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                MetaWordEntryDtoV2 entry = objectMapper.readValue(parser, MetaWordEntryDtoV2.class);
                if (entry == null || entry.getWord() == null || entry.getWord().trim().isEmpty()) {
                    continue;
                }

                batch.add(entry);
                if (batch.size() >= IMPORT_BATCH_SIZE) {
                    wordCount += persistJsonBatch(dictionaryId, category, batch, wordCache);
                    batch.clear();
                }
            }
        } catch (Exception e) {
            log.error("Error importing JSON file: {}", file.getName(), e);
            return ImportFileResult.failure();
        }

        if (!batch.isEmpty()) {
            wordCount += persistJsonBatch(dictionaryId, category, batch, wordCache);
        }

        log.info("Imported {} words from JSON file {}", wordCount, file.getName());
        return ImportFileResult.success(wordCount);
    }

    private void resetImportData() {
        transactionTemplate.executeWithoutResult(status -> {
            dictionaryWordService.deleteAll();
            dictionaryService.deleteAll();
            metaWordRepository.deleteAll();
        });
    }

    private Long createImportedDictionary(java.io.File file, String dictionaryName, String category) {
        return transactionTemplate.execute(status -> {
            Dictionary dictionary = new Dictionary(
                    dictionaryName,
                    file.getAbsolutePath(),
                    file.length(),
                    category,
                    DictionaryCreationType.IMPORTED
            );
            return dictionaryService.save(dictionary).getId();
        });
    }

    private int persistCsvBatch(
            Long dictionaryId,
            String category,
            List<CsvImportRow> rows,
            Map<String, Long> wordCache) {
        Integer insertedCount = transactionTemplate.execute(status -> {
            List<Long> metaWordIds = new ArrayList<>(rows.size());
            for (CsvImportRow row : rows) {
                try {
                    Long metaWordId = resolveCsvMetaWordId(row, category, wordCache);
                    if (metaWordId != null) {
                        metaWordIds.add(metaWordId);
                    }
                } catch (Exception e) {
                    log.error("Failed to persist word '{}' for dictionary {}", row.word(), dictionaryId, e);
                }
            }

            int inserted = dictionaryWordService.saveAllBatchIgnoringDuplicates(dictionaryId, metaWordIds);
            return inserted;
        });
        return insertedCount != null ? insertedCount : 0;
    }

    private int persistJsonBatch(
            Long dictionaryId,
            String category,
            List<MetaWordEntryDtoV2> rows,
            Map<String, Long> wordCache) {
        Integer insertedCount = transactionTemplate.execute(status -> {
            List<Long> metaWordIds = new ArrayList<>(rows.size());
            for (MetaWordEntryDtoV2 row : rows) {
                try {
                    Long metaWordId = resolveJsonMetaWordId(row, category, wordCache);
                    if (metaWordId != null) {
                        metaWordIds.add(metaWordId);
                    }
                } catch (Exception e) {
                    log.error("Failed to persist word '{}' for dictionary {}", row.getWord(), dictionaryId, e);
                }
            }

            int inserted = dictionaryWordService.saveAllBatchIgnoringDuplicates(dictionaryId, metaWordIds);
            return inserted;
        });
        return insertedCount != null ? insertedCount : 0;
    }

    private Long resolveCsvMetaWordId(CsvImportRow row, String category, Map<String, Long> wordCache) {
        String cacheKey = normalizeWordKey(row.word());
        Long cachedId = wordCache.get(cacheKey);
        if (cachedId != null) {
            return cachedId;
        }

        MetaWord metaWord = metaWordRepository.findByWord(row.word())
                .or(() -> metaWordRepository.findByNormalizedWord(cacheKey))
                .orElseGet(() -> {
                    MetaWord newMetaWord = new MetaWord();
                    newMetaWord.setWord(row.word());
                    newMetaWord.setDefinition(row.definition());
                    newMetaWord.setDifficulty(estimateDifficulty(category));
                    return metaWordRepository.save(newMetaWord);
                });

        wordCache.put(cacheKey, metaWord.getId());
        return metaWord.getId();
    }

    private Long resolveJsonMetaWordId(MetaWordEntryDtoV2 entry, String category, Map<String, Long> wordCache) {
        String word = entry.getWord().trim();
        String cacheKey = normalizeWordKey(word);
        Long cachedId = wordCache.get(cacheKey);

        MetaWord metaWord = null;
        if (cachedId != null) {
            metaWord = metaWordRepository.findById(cachedId).orElse(null);
        }
        if (metaWord == null) {
            metaWord = metaWordRepository.findByNormalizedWord(cacheKey)
                    .or(() -> metaWordRepository.findByWord(word))
                    .orElse(null);
        }

        boolean shouldSave = false;
        if (metaWord == null) {
            metaWord = new MetaWord(
                    word,
                    convertPhoneticDto(entry.getPhonetic()),
                    convertPartOfSpeechDtos(entry.getPartOfSpeech())
            );
            metaWord.setDifficulty(entry.getDifficulty() != null ? entry.getDifficulty() : estimateDifficulty(category));
            shouldSave = true;
        } else {
            if (entry.getPhonetic() != null) {
                metaWord.setPhoneticDetail(convertPhoneticDto(entry.getPhonetic()));
                shouldSave = true;
            }
            if (entry.getPartOfSpeech() != null && !entry.getPartOfSpeech().isEmpty()) {
                metaWord.setPartOfSpeechDetail(convertPartOfSpeechDtos(entry.getPartOfSpeech()));
                shouldSave = true;
            }
            if (entry.getDifficulty() != null) {
                metaWord.setDifficulty(entry.getDifficulty());
                shouldSave = true;
            }
        }

        if (entry.getSyllableDetail() != null) {
            metaWord.setSyllableDetail(new SyllableDetail(
                    entry.getSyllableDetail().getSegments() == null
                            ? List.of()
                            : entry.getSyllableDetail().getSegments().stream()
                                    .map(segment -> new SyllableSegment(
                                            segment.getText(),
                                            segment.getUkPhonetic(),
                                            segment.getUsPhonetic(),
                                            segment.getUkAudioUrl(),
                                            segment.getUsAudioUrl()))
                                    .toList()));
            shouldSave = true;
        }

        if (shouldSave) {
            metaWord = metaWordRepository.save(metaWord);
        }

        wordCache.put(cacheKey, metaWord.getId());
        return metaWord.getId();
    }

    private Map<String, Long> createWordCache() {
        return new LinkedHashMap<>(WORD_CACHE_LIMIT, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > WORD_CACHE_LIMIT;
            }
        };
    }

    private boolean isJsonFile(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    private String normalizeWordKey(String word) {
        return WordNormalizationUtils.normalize(word);
    }

    private Phonetic convertPhoneticDto(PhoneticDto dto) {
        if (dto == null) return null;
        return new Phonetic(
            dto.getUk() != null ? dto.getUk().trim() : null,
            dto.getUs() != null ? dto.getUs().trim() : null
        );
    }

    private java.util.List<PartOfSpeech> convertPartOfSpeechDtos(java.util.List<PartOfSpeechDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return null;
        
        return dtos.stream().map(dto -> {
            PartOfSpeech pos = new PartOfSpeech();
            pos.setPos(dto.getPos() != null ? dto.getPos().trim() : null);
            
            if (dto.getDefinitions() != null) {
                pos.setDefinitions(dto.getDefinitions().stream().map(defDto -> {
                    Definition def = new Definition();
                    def.setDefinition(defDto.getDefinition() != null ? defDto.getDefinition().trim() : null);
                    def.setTranslation(defDto.getTranslation() != null ? defDto.getTranslation().trim() : null);
                    
                    if (defDto.getExampleSentences() != null) {
                        def.setExampleSentences(defDto.getExampleSentences().stream().map(exDto -> {
                            ExampleSentence ex = new ExampleSentence();
                            ex.setSentence(exDto.getSentence() != null ? exDto.getSentence().trim() : null);
                            ex.setTranslation(exDto.getTranslation() != null ? exDto.getTranslation().trim() : null);
                            return ex;
                        }).toList());
                    }
                    
                    return def;
                }).toList());
            }
            
            // Handle inflection, synonyms, antonyms similarly...
            pos.setInflection(null); // Simplified for now
            pos.setSynonyms(dto.getSynonyms());
            pos.setAntonyms(dto.getAntonyms());
            
            return pos;
        }).toList();
    }

    private int estimateDifficulty(String category) {
        return switch (category) {
            case "小学", "初中", "中考" -> 1;
            case "高中", "四级", "高考" -> 2;
            case "六级", "考研", "雅思", "托福" -> 3;
            case "GRE", "GMAT", "考博", "SAT" -> 4;
            default -> 2;
        };
    }

    public Page<MetaWord> searchByKeyword(String keyword, Pageable pageable) {
        return metaWordRepository.findByWordStartingWith(keyword, pageable);
    }

    public Page<MetaWord> searchByDictionaryIdAndKeyword(Long dictionaryId, String keyword, Pageable pageable) {
        return metaWordRepository.findByDictionaryIdAndWordStartingWith(dictionaryId, keyword, pageable);
    }

    public Page<MetaWord> search(MetaWordSearchRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 10;
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Long dictionaryId = request.getDictionaryId();
        String keyword = request.getKeyword();
        
        if (dictionaryId != null) {
            if (keyword != null && !keyword.trim().isEmpty()) {
                return searchByDictionaryIdAndKeyword(dictionaryId, keyword.trim(), pageable);
            } else {
                return metaWordRepository.findByDictionaryId(dictionaryId, pageable);
            }
        } else {
            if (keyword != null && !keyword.trim().isEmpty()) {
                return searchByKeyword(keyword.trim(), pageable);
            } else {
                return metaWordRepository.findAll(pageable);
            }
        }
    }

    private MetaWordDetailResponse toDetailResponse(MetaWord metaWord) {
        List<DictionaryWord> dictionaryWords = dictionaryWordService.findByMetaWordId(metaWord.getId());
        Map<Long, Dictionary> visibleDictionaries = new LinkedHashMap<>();
        for (DictionaryWord dictionaryWord : dictionaryWords) {
            Dictionary dictionary = dictionaryService.findById(dictionaryWord.getDictionaryId()).orElse(null);
            if (dictionary == null) {
                continue;
            }
            if (!canView(dictionary)) {
                continue;
            }
            visibleDictionaries.put(dictionary.getId(), dictionary);
        }

        Map<Long, com.example.words.model.Tag> tagMap = new LinkedHashMap<>();
        for (com.example.words.model.Tag tag : tagRepository.findAllById(dictionaryWords.stream()
                .map(DictionaryWord::getChapterTagId)
                .filter(java.util.Objects::nonNull)
                .toList())) {
            tagMap.put(tag.getId(), tag);
        }

        Map<Long, MetaWordDictionaryReferenceDto> references = new LinkedHashMap<>();
        for (DictionaryWord dictionaryWord : dictionaryWords) {
            Dictionary dictionary = visibleDictionaries.get(dictionaryWord.getDictionaryId());
            if (dictionary == null) {
                continue;
            }
            MetaWordDictionaryReferenceDto reference = references.computeIfAbsent(dictionary.getId(), key ->
                    new MetaWordDictionaryReferenceDto(dictionary.getId(), dictionary.getName(), new ArrayList<>()));
            com.example.words.model.Tag tag = tagMap.get(dictionaryWord.getChapterTagId());
            reference.getLocations().add(new MetaWordReferenceLocationDto(
                    dictionaryWord.getChapterTagId(),
                    tag == null ? null : tag.getPathName(),
                    dictionaryWord.getEntryOrder()
            ));
        }

        return new MetaWordDetailResponse(
                metaWord.getId(),
                metaWord.getWord(),
                metaWord.getPhonetic(),
                metaWord.getDefinition(),
                metaWord.getPartOfSpeech(),
                metaWord.getExampleSentence(),
                metaWord.getTranslation(),
                metaWord.getDifficulty(),
                new ArrayList<>(references.values())
        );
    }

    private boolean canView(Dictionary dictionary) {
        try {
            accessControlService.ensureCanViewDictionary(currentUserService.getCurrentUser(), dictionary);
            return true;
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return false;
        }
    }

    public static class BooksImportResult {
        private final int dictionaryCount;
        private final int wordCount;

        public BooksImportResult(int dictionaryCount, int wordCount) {
            this.dictionaryCount = dictionaryCount;
            this.wordCount = wordCount;
        }

        public int getDictionaryCount() {
            return dictionaryCount;
        }

        public int getWordCount() {
            return wordCount;
        }
    }

    public static class ImportFileResult {
        private final boolean success;
        private final int wordCount;

        private ImportFileResult(boolean success, int wordCount) {
            this.success = success;
            this.wordCount = wordCount;
        }

        public static ImportFileResult success(int wordCount) {
            return new ImportFileResult(true, wordCount);
        }

        public static ImportFileResult failure() {
            return new ImportFileResult(false, 0);
        }

        public boolean success() {
            return success;
        }

        public int wordCount() {
            return wordCount;
        }
    }

    private record CsvImportRow(String word, String definition) {
    }
}
