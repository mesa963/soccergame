package com.example.soccergame.controller;

import com.example.soccergame.model.GamePlayer;
import com.example.soccergame.model.GameRoom;
import com.example.soccergame.repository.GameRoomRepository;
import com.example.soccergame.service.GameService;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRoomRepository roomRepository;

    @PostConstruct
    public void init() {
        gameService.seedCharacters();
    }

    @PostMapping("/create")
    public ResponseEntity<GameRoom> createRoom(@RequestParam String playerName,
            @RequestParam(defaultValue = "SOCCER") String packType) {
        return ResponseEntity.ok(gameService.createRoom(playerName, packType));
    }

    @PostMapping("/join")
    public ResponseEntity<GamePlayer> joinRoom(@RequestParam String roomCode, @RequestParam String playerName) {
        return ResponseEntity.ok(gameService.joinRoom(roomCode, playerName));
    }

    @GetMapping("/{roomCode}/players")
    public ResponseEntity<List<GamePlayer>> getPlayers(@PathVariable String roomCode) {
        return roomRepository.findByRoomCode(roomCode)
                .map(room -> ResponseEntity.ok(room.getPlayers()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{roomCode}/start")
    public ResponseEntity<Void> startGame(@PathVariable String roomCode) {
        gameService.startGame(roomCode);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/players/{playerId}/notes")
    public ResponseEntity<Void> updateNotes(@PathVariable Long playerId, @RequestBody String notes) {
        gameService.updateNotes(playerId, notes);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/players/{playerId}/guess")
    public ResponseEntity<Void> submitGuess(@PathVariable Long playerId, @RequestParam String guessName) {
        gameService.submitGuess(playerId, guessName);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/players/{playerId}/validate")
    public ResponseEntity<Void> validateGuess(@PathVariable Long playerId, @RequestParam Long voterId,
            @RequestParam boolean correct) {
        gameService.processVote(playerId, voterId, correct, "GUESS");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/categories")
    public ResponseEntity<Void> addCategory(@RequestParam String name,
            @RequestParam(defaultValue = "SOCCER") String packType) {
        gameService.addCustomCategory(name, packType);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomCode}/reset")
    public ResponseEntity<Void> resetGame(@PathVariable String roomCode) {
        gameService.resetGame(roomCode);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/players/{targetId}/request-change")
    public ResponseEntity<Void> proposeChange(@PathVariable Long targetId, @RequestParam Long requesterId) {
        gameService.proposeChange(targetId, requesterId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/players/{targetId}/execute-change")
    public ResponseEntity<Void> executeChange(@PathVariable Long targetId, @RequestParam Long voterId,
            @RequestParam boolean yes) {
        gameService.processVote(targetId, voterId, yes, "CHANGE");
        return ResponseEntity.ok().build();
    }
}
