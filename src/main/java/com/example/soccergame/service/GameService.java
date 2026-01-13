package com.example.soccergame.service;

import com.example.soccergame.model.*;
import com.example.soccergame.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GameService {

        @Autowired
        private GameRoomRepository roomRepository;

        @Autowired
        private GamePlayerRepository playerRepository;

        @Autowired
        private SoccerCharacterRepository characterRepository;

        @Autowired
        private SimpMessagingTemplate messagingTemplate;

        @Transactional
        public GameRoom createRoom(String playerName) {
                String roomCode = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                GameRoom room = new GameRoom();
                room.setRoomCode(roomCode);
                room.setStatus(GameRoom.RoomStatus.WAITING);
                room = roomRepository.save(room);

                GamePlayer player = new GamePlayer();
                player.setName(playerName);
                player.setRoom(room);
                player.setHost(true);
                player = playerRepository.save(player);

                // Link in memory so the returned object has the player list populated
                room.getPlayers().add(player);

                return room;
        }

        @Transactional
        public GamePlayer joinRoom(String roomCode, String playerName) {
                GameRoom room = roomRepository.findByRoomCode(roomCode)
                                .orElseThrow(() -> new RuntimeException("Room not found"));

                if (room.getStatus() != GameRoom.RoomStatus.WAITING) {
                        throw new RuntimeException("Game already started");
                }

                GamePlayer player = new GamePlayer();
                player.setName(playerName);
                player.setRoom(room);
                player = playerRepository.save(player);

                messagingTemplate.convertAndSend("/topic/room/" + roomCode, "PLAYER_JOINED");
                return player;
        }

        @Transactional
        public void startGame(String roomCode) {
                GameRoom room = roomRepository.findByRoomCode(roomCode)
                                .orElseThrow(() -> new RuntimeException("Room not found"));

                List<GamePlayer> players = room.getPlayers();
                List<SoccerCharacter> characters = characterRepository.findAll();

                if (characters.size() < players.size()) {
                        throw new RuntimeException("Not enough characters in DB");
                }

                Collections.shuffle(characters);

                for (int i = 0; i < players.size(); i++) {
                        players.get(i).setAssignedCharacter(characters.get(i));
                        playerRepository.save(players.get(i));
                }

                room.setStatus(GameRoom.RoomStatus.IN_GAME);
                roomRepository.save(room);
                messagingTemplate.convertAndSend("/topic/room/" + roomCode, "GAME_STARTED");
        }

        @Transactional
        public void updateNotes(Long playerId, String notes) {
                GamePlayer player = playerRepository.findById(playerId)
                                .orElseThrow(() -> new RuntimeException("Player not found"));
                player.setNotes(notes);
                playerRepository.save(player);
        }

        @Transactional
        public void submitGuess(Long playerId, String guessName) {
                GamePlayer player = playerRepository.findById(playerId)
                                .orElseThrow(() -> new RuntimeException("Player not found"));
                player.setPendingGuess(guessName);
                playerRepository.save(player);

                messagingTemplate.convertAndSend("/topic/room/" + player.getRoom().getRoomCode(),
                                "GUESS_SUBMITTED:" + player.getName() + ":" + guessName + ":" + player.getId());
        }

        @Transactional
        public void processVote(Long targetId, Long voterId, boolean yes, String type) {
                GamePlayer target = playerRepository.findById(targetId)
                                .orElseThrow(() -> new RuntimeException("Target not found"));
                GameRoom room = target.getRoom();

                // Track the vote
                if (yes) {
                        room.getYesVotes().add(voterId);
                        room.getNoVotes().remove(voterId);
                } else {
                        room.getNoVotes().add(voterId);
                        room.getYesVotes().remove(voterId);
                }
                roomRepository.save(room);

                int totalVotersNeeded = room.getPlayers().size() - 1;
                int currentTotalVotes = room.getYesVotes().size() + room.getNoVotes().size();

                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                "VOTE_PROGRESS:" + currentTotalVotes + ":" + totalVotersNeeded + ":" + type);

                if (currentTotalVotes >= totalVotersNeeded) {
                        boolean approved = room.getYesVotes().size() > room.getNoVotes().size();

                        // Clear voting state
                        room.getYesVotes().clear();
                        room.getNoVotes().clear();
                        room.setVotingType(null);
                        room.setVotingTargetId(null);
                        roomRepository.save(room);

                        if (type.equals("GUESS")) {
                                executeValidateGuess(targetId, approved);
                        } else {
                                if (approved)
                                        executeChange(targetId);
                                else
                                        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                                        "CHANGE_REJECTED:" + target.getName());
                        }
                }
        }

        private void executeValidateGuess(Long playerId, boolean correct) {
                GamePlayer player = playerRepository.findById(playerId).get();
                if (correct) {
                        player.setGuessed(true);
                        player.setPendingGuess(null);

                        GameRoom room = player.getRoom();
                        int currentMax = room.getPlayers().stream()
                                        .filter(p -> p.getGuessOrder() != null)
                                        .mapToInt(GamePlayer::getGuessOrder)
                                        .max().orElse(0);
                        player.setGuessOrder(currentMax + 1);

                        playerRepository.save(player);
                        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                        "GUESS_VALIDATED_CORRECT:" + player.getName());
                } else {
                        player.setPendingGuess(null);
                        playerRepository.save(player);
                        messagingTemplate.convertAndSend("/topic/room/" + player.getRoom().getRoomCode(),
                                        "GUESS_VALIDATED_INCORRECT:" + player.getName());
                }
        }

        @Transactional
        public void resetGame(String roomCode) {
                GameRoom room = roomRepository.findByRoomCode(roomCode)
                                .orElseThrow(() -> new RuntimeException("Room not found"));

                List<GamePlayer> players = room.getPlayers();
                List<SoccerCharacter> characters = characterRepository.findAll();

                if (characters.size() < players.size()) {
                        throw new RuntimeException("Not enough characters in DB");
                }

                Collections.shuffle(characters);

                for (int i = 0; i < players.size(); i++) {
                        GamePlayer p = players.get(i);
                        p.setAssignedCharacter(characters.get(i));
                        p.setGuessed(false);
                        p.setPendingGuess(null);
                        p.setGuessOrder(null);
                        playerRepository.save(p);
                }

                room.setStatus(GameRoom.RoomStatus.IN_GAME);
                roomRepository.save(room);
                messagingTemplate.convertAndSend("/topic/room/" + roomCode, "GAME_STARTED");
        }

        @Transactional
        public void proposeChange(Long targetId, Long requesterId) {
                GamePlayer target = playerRepository.findById(targetId)
                                .orElseThrow(() -> new RuntimeException("Target player not found"));
                GamePlayer requester = playerRepository.findById(requesterId)
                                .orElseThrow(() -> new RuntimeException("Requester player not found"));

                messagingTemplate.convertAndSend("/topic/room/" + target.getRoom().getRoomCode(),
                                "CHANGE_PROPOSED:" + target.getName() + ":" + target.getId() + ":"
                                                + requester.getName());
        }

        @Transactional
        public void executeChange(Long targetId) {
                GamePlayer target = playerRepository.findById(targetId)
                                .orElseThrow(() -> new RuntimeException("Target player not found"));

                List<SoccerCharacter> characters = characterRepository.findAll();
                Collections.shuffle(characters);

                // Assign a new random character (category)
                target.setAssignedCharacter(characters.get(0));
                playerRepository.save(target);

                messagingTemplate.convertAndSend("/topic/room/" + target.getRoom().getRoomCode(),
                                "CHANGE_EXECUTED:" + target.getName());
        }

        @Transactional
        public void addCustomCategory(String categoryName) {
                SoccerCharacter category = new SoccerCharacter(null, categoryName, "", "", "");
                characterRepository.save(category);
        }

        public void seedCharacters() {
                if (characterRepository.count() == 0) {
                        List<SoccerCharacter> characters = Arrays.asList(
                                        new SoccerCharacter(null, "Ganadores de Copa América y Mundial", "", "", ""),
                                        new SoccerCharacter(null, "Jugadores con 3 Champions en clubes distintos", "",
                                                        "", ""),
                                        new SoccerCharacter(null, "Goleadores en 4 ligas top de Europa", "", "", ""),
                                        new SoccerCharacter(null, "Porteros con un gol oficial de campo", "", "", ""),
                                        new SoccerCharacter(null, "Jugadores que jugaron en Real Madrid y Barcelona",
                                                        "", "", ""),
                                        new SoccerCharacter(null, "Ganadores de Balón de Oro africanos", "", "", ""),
                                        new SoccerCharacter(null, "Jugadores con más de 100 goles en su selección", "",
                                                        "", ""),
                                        new SoccerCharacter(null, "Campeones del mundo como jugador y entrenador", "",
                                                        "", ""),
                                        new SoccerCharacter(null, "Ganadores de Libertadores y Champions League", "",
                                                        "", ""),
                                        new SoccerCharacter(null, "Jugadores que usaron el dorsal 10 en Brasil", "", "",
                                                        ""),
                                        new SoccerCharacter(null, "Fichajes de más de 100 millones de euros", "", "",
                                                        ""),
                                        new SoccerCharacter(null, "Jugadores que nunca recibieron una tarjeta roja", "",
                                                        "", ""));
                        characterRepository.saveAll(characters);
                }
        }
}
