package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.example.words.util.WordNormalizationUtils;

@Entity
@Table(name = "meta_words")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word", nullable = false, columnDefinition = "TEXT")
    private String word;

    @Column(name = "normalized_word", nullable = false, columnDefinition = "TEXT")
    private String normalizedWord;

    @Column(name = "phonetic", columnDefinition = "TEXT")
    private String phonetic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "phonetic_detail", columnDefinition = "jsonb")
    private Phonetic phoneticDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "syllable_detail", columnDefinition = "jsonb")
    private SyllableDetail syllableDetail;

    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition;

    @Column(name = "part_of_speech", columnDefinition = "TEXT")
    private String partOfSpeech;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "part_of_speech_detail", columnDefinition = "jsonb")
    private List<PartOfSpeech> partOfSpeechDetail;

    @Column(name = "example_sentence", columnDefinition = "TEXT")
    private String exampleSentence;

    @Column(name = "translation", columnDefinition = "TEXT")
    private String translation;

    @Column(name = "difficulty")
    private Integer difficulty;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public MetaWord(String word, String phonetic, String definition, String partOfSpeech) {
        this.word = word;
        this.normalizedWord = WordNormalizationUtils.normalize(word);
        this.phonetic = phonetic;
        this.definition = definition;
        this.partOfSpeech = partOfSpeech;
    }

    // Constructor for new format
    public MetaWord(String word, Phonetic phoneticDetail, List<PartOfSpeech> partOfSpeechDetail) {
        this.word = word;
        this.normalizedWord = WordNormalizationUtils.normalize(word);
        this.phoneticDetail = phoneticDetail;
        this.partOfSpeechDetail = partOfSpeechDetail;
    }

    @PrePersist
    @PreUpdate
    public void normalizeWord() {
        this.normalizedWord = WordNormalizationUtils.normalize(word);
    }
}
