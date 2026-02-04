package com.example.soccergame.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.*;

@Entity
@Table(name = "SC_PLAYERS")
public class GamePlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "room_id")
    @JsonIgnore
    private GameRoom room;

    @ManyToOne
    @JoinColumn(name = "character_id")
    private CategoryItem assignedCharacter;

    @Column(length = 2000)
    private String notes;

    @Column(length = 2000)
    private String invalidNotes;

    private boolean host = false;
    private boolean guessed = false;

    @Column(nullable = false)
    private boolean isImpostor;
    private String pendingGuess;
    private String pendingCategory;
    private Integer guessOrder; // 1st, 2nd, etc.
    private Integer visualOrder;

    @Column(nullable = false)
    private boolean eliminated = false;

    public GamePlayer() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GameRoom getRoom() {
        return room;
    }

    public void setRoom(GameRoom room) {
        this.room = room;
    }

    public CategoryItem getAssignedCharacter() {
        return assignedCharacter;
    }

    public void setAssignedCharacter(CategoryItem assignedCharacter) {
        this.assignedCharacter = assignedCharacter;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getInvalidNotes() {
        return invalidNotes;
    }

    public void setInvalidNotes(String invalidNotes) {
        this.invalidNotes = invalidNotes;
    }

    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }

    public boolean isGuessed() {
        return guessed;
    }

    public void setGuessed(boolean guessed) {
        this.guessed = guessed;
    }

    public boolean isImpostor() {
        return isImpostor;
    }

    public void setImpostor(boolean impostor) {
        this.isImpostor = impostor;
    }

    public String getPendingGuess() {
        return pendingGuess;
    }

    public void setPendingGuess(String pendingGuess) {
        this.pendingGuess = pendingGuess;
    }

    public String getPendingCategory() {
        return pendingCategory;
    }

    public void setPendingCategory(String pendingCategory) {
        this.pendingCategory = pendingCategory;
    }

    public Integer getGuessOrder() {
        return guessOrder;
    }

    public void setGuessOrder(Integer guessOrder) {
        this.guessOrder = guessOrder;
    }

    public Integer getVisualOrder() {
        return visualOrder;
    }

    public void setVisualOrder(Integer visualOrder) {
        this.visualOrder = visualOrder;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }
}
