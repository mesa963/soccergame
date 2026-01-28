package com.example.soccergame.model;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "SC_ROOMS")
public class GameRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "room_seq")
    @SequenceGenerator(name = "room_seq", sequenceName = "ROOM_SEQ", allocationSize = 1)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roomCode;

    private String selectedPack; // For GUESS_WHO

    @Enumerated(EnumType.STRING)
    private GameType gameType = GameType.GUESS_WHO;

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.WAITING;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<GamePlayer> players = new ArrayList<>();

    private Long votingTargetId;
    private String votingType; // "GUESS" or "CHANGE"

    @ElementCollection
    private Set<Long> yesVotes = new HashSet<>();

    @ElementCollection
    private Set<Long> noVotes = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "room_accuse_votes", joinColumns = @JoinColumn(name = "room_id"))
    @MapKeyColumn(name = "voter_id")
    @Column(name = "target_id")
    private java.util.Map<Long, Long> accuseVotes = new java.util.HashMap<>();

    // Impostor Game Fields
    @Column(columnDefinition = "NUMBER(1) DEFAULT 1 NOT NULL")
    private int impostorCount = 1;

    @Column(columnDefinition = "NUMBER(1) DEFAULT 0 NOT NULL")
    private boolean impostorHints = false;
    private String currentCategory; // For the round
    private String currentWord; // For the round
    private String impostorCategoryPreference; // "RANDOM" or specific category

    public enum RoomStatus {
        WAITING, IN_GAME, FINISHED
    }

    public enum GameType {
        GUESS_WHO, IMPOSTOR
    }

    public GameRoom() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getSelectedPack() {
        return selectedPack;
    }

    public void setSelectedPack(String selectedPack) {
        this.selectedPack = selectedPack;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public List<GamePlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<GamePlayer> players) {
        this.players = players;
    }

    public Long getVotingTargetId() {
        return votingTargetId;
    }

    public void setVotingTargetId(Long votingTargetId) {
        this.votingTargetId = votingTargetId;
    }

    public String getVotingType() {
        return votingType;
    }

    public void setVotingType(String votingType) {
        this.votingType = votingType;
    }

    public Set<Long> getYesVotes() {
        return yesVotes;
    }

    public void setYesVotes(Set<Long> yesVotes) {
        this.yesVotes = yesVotes;
    }

    public Set<Long> getNoVotes() {
        return noVotes;
    }

    public void setNoVotes(Set<Long> noVotes) {
        this.noVotes = noVotes;
    }

    public java.util.Map<Long, Long> getAccuseVotes() {
        return accuseVotes;
    }

    public void setAccuseVotes(java.util.Map<Long, Long> accuseVotes) {
        this.accuseVotes = accuseVotes;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public int getImpostorCount() {
        return impostorCount;
    }

    public void setImpostorCount(int impostorCount) {
        this.impostorCount = impostorCount;
    }

    public boolean isImpostorHints() {
        return impostorHints;
    }

    public void setImpostorHints(boolean impostorHints) {
        this.impostorHints = impostorHints;
    }

    public String getCurrentCategory() {
        return currentCategory;
    }

    public void setCurrentCategory(String currentCategory) {
        this.currentCategory = currentCategory;
    }

    public String getCurrentWord() {
        return currentWord;
    }

    public void setCurrentWord(String currentWord) {
        this.currentWord = currentWord;
    }

    public String getImpostorCategoryPreference() {
        return impostorCategoryPreference;
    }

    public void setImpostorCategoryPreference(String impostorCategoryPreference) {
        this.impostorCategoryPreference = impostorCategoryPreference;
    }
}
