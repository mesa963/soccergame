package com.example.soccergame.model;

import javax.persistence.*;

@Entity
@Table(name = "SC_IMPOSTOR_WORDS")
public class ImpostorWord {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "imp_word_seq")
    @SequenceGenerator(name = "imp_word_seq", sequenceName = "IMP_WORD_SEQ", allocationSize = 1)
    private Long id;

    private String category;
    private String word;
    private String hint;

    public ImpostorWord() {
    }

    public ImpostorWord(Long id, String category, String word, String hint) {
        this.id = id;
        this.category = category;
        this.word = word;
        this.hint = hint;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }
}
