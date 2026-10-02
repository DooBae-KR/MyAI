package com.personal.ai.data.codingtest;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 문제 원문은 저장하지 않는다. 출처 정보와 사용자가 직접 쓴 요약만 가진다. */
@Entity
@Table(name = "CODING_PROBLEM")
public class CodingProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50) private String source;
    @Column(length = 500) private String sourceUrl;
    @Column(length = 100) private String externalId;
    @Column(length = 100) private String company;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 30) private String difficulty;
    @Column(length = 100) private String category;
    @Column(length = 200) private String algorithm;
    @Column(length = 200) private String dataStructure;
    @Column(length = 30) private String language;
    @Column(length = 2000) private String descriptionSummary;
    private Integer estimatedTime;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CodingProblem() {}

    public CodingProblem(String title) {
        this.title = title;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public String getExternalId() { return externalId; }
    public String getCompany() { return company; }
    public String getTitle() { return title; }
    public String getDifficulty() { return difficulty; }
    public String getCategory() { return category; }
    public String getAlgorithm() { return algorithm; }
    public String getDataStructure() { return dataStructure; }
    public String getLanguage() { return language; }
    public String getDescriptionSummary() { return descriptionSummary; }
    public Integer getEstimatedTime() { return estimatedTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** 선택 항목을 한 번에 채운다. 비어 있는 값은 null로 둔다. */
    public CodingProblem details(String source, String sourceUrl, String externalId, String company, String difficulty,
                                 String category, String algorithm, String dataStructure, String language,
                                 String descriptionSummary, Integer estimatedTime) {
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.externalId = externalId;
        this.company = company;
        this.difficulty = difficulty;
        this.category = category;
        this.algorithm = algorithm;
        this.dataStructure = dataStructure;
        this.language = language;
        this.descriptionSummary = descriptionSummary;
        this.estimatedTime = estimatedTime;
        return this;
    }
}
