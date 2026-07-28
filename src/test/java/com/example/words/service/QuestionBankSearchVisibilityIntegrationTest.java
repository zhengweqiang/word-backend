package com.example.words.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;

import com.example.words.dto.QuestionBankItemResponse;
import com.example.words.dto.QuestionBankSearchRequest;
import com.example.words.model.AppUser;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.UserRole;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.QuestionBankItemRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class QuestionBankSearchVisibilityIntegrationTest {

    @Autowired
    private QuestionBankItemRepository questionRepository;

    @Autowired
    private DictionaryRepository dictionaryRepository;

    @Autowired
    private MetaWordRepository metaWordRepository;

    @Autowired
    private DictionaryWordRepository dictionaryWordRepository;

    private QuestionBankService service;

    @BeforeEach
    void setUp() {
        service = new QuestionBankService(
                questionRepository,
                dictionaryRepository,
                metaWordRepository,
                dictionaryWordRepository,
                mock(ExamPaperAccessService.class),
                new ExamPaperAnswerNormalizer(),
                new ObjectMapper());
    }

    @Test
    void teacherSeesSharedActiveAndOwnNonActiveQuestionsOnly() {
        questionRepository.saveAllAndFlush(List.of(
                question("own draft", 7L, QuestionBankItemStatus.DRAFT),
                question("own archived", 7L, QuestionBankItemStatus.ARCHIVED),
                question("other active", 8L, QuestionBankItemStatus.ACTIVE),
                question("other draft", 8L, QuestionBankItemStatus.DRAFT),
                question("other archived", 8L, QuestionBankItemStatus.ARCHIVED)));

        Page<QuestionBankItemResponse> result = service.search(
                new QuestionBankSearchRequest(), teacher(7L));

        Set<String> visibleStems = result.getContent().stream()
                .map(QuestionBankItemResponse::getStem)
                .collect(Collectors.toSet());
        assertEquals(Set.of("own draft", "own archived", "other active"), visibleStems);
        assertFalse(visibleStems.contains("other draft"));
        assertFalse(visibleStems.contains("other archived"));
    }

    private QuestionBankItem question(String stem, Long createdByUserId, QuestionBankItemStatus status) {
        QuestionBankItem question = new QuestionBankItem();
        question.setQuestionType(QuestionType.FILL_IN_BLANK);
        question.setStem(stem);
        question.setOptionsJson("{}");
        question.setAcceptedAnswersJson("[\"answer\"]");
        question.setDefaultScore(new BigDecimal("1.00"));
        question.setTags("[]");
        question.setCreatedByUserId(createdByUserId);
        question.setLastModifiedByUserId(createdByUserId);
        question.setStatus(status);
        question.setCreatedAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        question.setUpdatedAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        return question;
    }

    private AppUser teacher(Long id) {
        AppUser teacher = new AppUser();
        teacher.setId(id);
        teacher.setRole(UserRole.TEACHER);
        return teacher;
    }
}
