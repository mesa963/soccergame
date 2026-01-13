package com.example.soccergame.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "ROOMS")
public class GameRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roomCode;

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

    public enum RoomStatus {
        WAITING, IN_GAME, FINISHED
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
}
