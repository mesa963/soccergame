package com.example.soccergame.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "PLAYERS")
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
    private SoccerCharacter assignedCharacter;

    @Column(length = 2000)
    private String notes;

    private boolean host = false;
    private boolean guessed = false;
    private String pendingGuess;
    private Integer guessOrder;

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

    public SoccerCharacter getAssignedCharacter() {
        return assignedCharacter;
    }

    public void setAssignedCharacter(SoccerCharacter assignedCharacter) {
        this.assignedCharacter = assignedCharacter;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public String getPendingGuess() {
        return pendingGuess;
    }

    public void setPendingGuess(String pendingGuess) {
        this.pendingGuess = pendingGuess;
    }

    public Integer getGuessOrder() {
        return guessOrder;
    }

    public void setGuessOrder(Integer guessOrder) {
        this.guessOrder = guessOrder;
    }
}
