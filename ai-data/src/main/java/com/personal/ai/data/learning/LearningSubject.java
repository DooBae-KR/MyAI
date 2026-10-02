package com.personal.ai.data.learning;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "LEARNING_SUBJECT")
public class LearningSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToMany
    @JoinTable(name = "LEARNING_SUBJECT_PREREQ",
            joinColumns = @JoinColumn(name = "SUBJECT_ID"),
            inverseJoinColumns = @JoinColumn(name = "PREREQ_ID"))
    private Set<LearningSubject> prerequisites = new HashSet<>();

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearningSubject() {}

    public LearningSubject(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<LearningSubject> getPrerequisites() { return prerequisites; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
