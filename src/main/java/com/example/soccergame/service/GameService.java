package com.example.soccergame.service;

import com.example.soccergame.model.*;
import com.example.soccergame.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameService {

        @Autowired
        private GameRoomRepository roomRepository;

        @Autowired
        private GamePlayerRepository playerRepository;

        @Autowired
        private CategoryItemRepository characterRepository;

        @Autowired
        private SimpMessagingTemplate messagingTemplate;

        @Autowired
        private ImpostorWordRepository impostorWordRepository;

        @Transactional
        public GameRoom createRoom(String playerName, String packType, String gameTypeStr, int impostorCount,
                        boolean hints, String impostorCategory) {
                String roomCode = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                GameRoom room = new GameRoom();
                room.setRoomCode(roomCode);
                room.setStatus(GameRoom.RoomStatus.WAITING);

                GameRoom.GameType type = GameRoom.GameType.GUESS_WHO;
                if ("IMPOSTOR".equalsIgnoreCase(gameTypeStr)) {
                        type = GameRoom.GameType.IMPOSTOR;
                        room.setImpostorCount(impostorCount > 0 ? impostorCount : 1);
                        room.setImpostorHints(hints);
                        room.setImpostorCategoryPreference(impostorCategory);
                } else {
                        room.setSelectedPack(packType != null ? packType : "FUTBOL");
                }
                room.setGameType(type);

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

                // Check for duplicate name
                boolean nameExists = room.getPlayers().stream()
                                .anyMatch(p -> p.getName().equalsIgnoreCase(playerName));
                if (nameExists) {
                        throw new RuntimeException("Nombre ya en uso en esta sala");
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

                if (room.getGameType() == GameRoom.GameType.IMPOSTOR) {
                        startImpostorGame(room);
                } else {
                        startGuessWhoGame(room);
                }
        }

        private void startGuessWhoGame(GameRoom room) {
                List<GamePlayer> players = room.getPlayers();
                // Filter by the room's selected pack
                List<CategoryItem> characters = characterRepository.findByPackType(room.getSelectedPack());

                if (characters.size() < players.size()) {
                        throw new RuntimeException("Not enough characters in DB for pack '" + room.getSelectedPack() +
                                        "'. Found " + characters.size() + ", needed " + players.size());
                }

                Collections.shuffle(characters);
                // Shuffle players to determine visual order
                List<GamePlayer> shuffledPlayers = new ArrayList<>(players);
                Collections.shuffle(shuffledPlayers);

                for (int i = 0; i < players.size(); i++) {
                        GamePlayer p = players.get(i);
                        p.setAssignedCharacter(characters.get(i));
                        // Find this player in the shuffled list to assign order
                        p.setVisualOrder(shuffledPlayers.indexOf(p));

                        // Reset status for "Play Again"
                        p.setGuessed(false);
                        p.setEliminated(false);
                        p.setPendingGuess(null);
                        p.setGuessOrder(null);
                        p.setImpostor(false);

                        playerRepository.save(p);
                }

                room.setStatus(GameRoom.RoomStatus.IN_GAME);
                room.getAccuseVotes().clear();
                room.getYesVotes().clear();
                room.getNoVotes().clear();
                roomRepository.save(room);
                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(), "GAME_STARTED");
        }

        private void startImpostorGame(GameRoom room) {
                List<ImpostorWord> words = impostorWordRepository.findAll();
                if (words.isEmpty()) {
                        throw new RuntimeException("No words configured for Impostor Game");
                }

                // Filter by preference if set
                if (room.getImpostorCategoryPreference() != null
                                && !room.getImpostorCategoryPreference().equals("RANDOM")) {
                        List<ImpostorWord> filtered = words.stream()
                                        .filter(w -> w.getCategory().equals(room.getImpostorCategoryPreference()))
                                        .collect(Collectors.toList());
                        if (!filtered.isEmpty()) {
                                words = filtered;
                        }
                }

                // Pick random word (prefer a different one than the current if possible)
                List<ImpostorWord> candidates = new ArrayList<>(words);
                if (candidates.size() > 1 && room.getCurrentWord() != null) {
                        candidates = candidates.stream()
                                        .filter(w -> !w.getWord().equals(room.getCurrentWord()))
                                        .collect(Collectors.toList());
                        if (candidates.isEmpty()) {
                                candidates = new ArrayList<>(words);
                        }
                }

                ImpostorWord selected = candidates.get(new Random().nextInt(candidates.size()));
                room.setCurrentCategory(selected.getCategory());
                room.setCurrentWord(selected.getWord());

                // Assign Impostors and Visual Order
                List<GamePlayer> players = room.getPlayers();

                // Determine visual order
                List<GamePlayer> visualOrderList = new ArrayList<>(players);
                Collections.shuffle(visualOrderList);

                int impostorCount = Math.min(room.getImpostorCount(), players.size() - 1);
                if (impostorCount < 1)
                        impostorCount = 1;

                // Pick N random players for impostors
                List<GamePlayer> potentialImpostors = new ArrayList<>(players);
                Collections.shuffle(potentialImpostors);
                Set<Long> impostorIds = potentialImpostors.stream()
                                .limit(impostorCount)
                                .map(GamePlayer::getId)
                                .collect(Collectors.toSet());

                for (GamePlayer p : players) {
                        // Reset everything
                        p.setGuessed(false);
                        p.setEliminated(false);
                        p.setGuessOrder(null);
                        p.setAssignedCharacter(null); // Not used in this mode but clean up

                        if (impostorIds.contains(p.getId())) {
                                p.setImpostor(true);
                                p.setPendingGuess(room.isImpostorHints() ? selected.getHint() : null);
                        } else {
                                p.setImpostor(false);
                                p.setPendingGuess(null);
                        }

                        // Set consistent visual order
                        p.setVisualOrder(visualOrderList.indexOf(p));

                        playerRepository.save(p);
                }

                room.setStatus(GameRoom.RoomStatus.IN_GAME);
                room.getAccuseVotes().clear();
                room.getYesVotes().clear();
                room.getNoVotes().clear();
                roomRepository.save(room);
                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(), "GAME_STARTED");
        }

        @Transactional
        public void updateNotes(Long playerId, String validNotes, String invalidNotes) {
                GamePlayer player = playerRepository.findById(playerId)
                                .orElseThrow(() -> new RuntimeException("Player not found"));
                player.setNotes(validNotes);
                player.setInvalidNotes(invalidNotes);
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
                                if (approved) {
                                        try {
                                                executeChange(targetId);
                                        } catch (RuntimeException e) {
                                                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                                                "CHANGE_REJECTED:" + target.getName());
                                        }
                                } else {
                                        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                                        "CHANGE_REJECTED:" + target.getName());
                                }
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

                if (room.getGameType() == GameRoom.GameType.IMPOSTOR) {
                        startImpostorGame(room);
                } else {
                        startGuessWhoGame(room);
                }
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

                // Reshuffle from the same pack
                List<CategoryItem> characters = characterRepository.findByPackType(target.getRoom().getSelectedPack());

                // Exclude currently assigned characters in the room
                List<Long> assignedIds = target.getRoom().getPlayers().stream()
                                .filter(p -> p.getAssignedCharacter() != null)
                                .map(p -> p.getAssignedCharacter().getId())
                                .collect(Collectors.toList());

                List<CategoryItem> available = characters.stream()
                                .filter(c -> !assignedIds.contains(c.getId()))
                                .collect(Collectors.toList());

                if (available.isEmpty()) {
                        // Fallback: if we truly ran out (rare), just pick random from all
                        available = characters;
                }

                Collections.shuffle(available);

                // Assign a new random character (category)
                target.setAssignedCharacter(available.get(0));
                playerRepository.save(target);

                messagingTemplate.convertAndSend("/topic/room/" + target.getRoom().getRoomCode(),
                                "CHANGE_EXECUTED:" + target.getName());
        }

        @Transactional
        public void addCustomCategory(String categoryName, String packType) {
                String normalizedPack = packType != null ? packType.toUpperCase().trim() : "FUTBOL";
                CategoryItem category = new CategoryItem(null, categoryName, normalizedPack, "", "");
                characterRepository.save(category);
        }

        public void seedCharacters() {
                migrateSoccerToFutbol(); // Run simple migration check
                seedImpostorWords();

                if (characterRepository.count() == 0) {
                        List<CategoryItem> items = new ArrayList<>();

                        // FUTBOL PACK
                        items.add(new CategoryItem(null, "Ganadores de Copa América y Mundial", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Jugadores con 3 Champions en clubes distintos", "FUTBOL", "",
                                        ""));
                        items.add(new CategoryItem(null, "Goleadores en 4 ligas top de Europa", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Porteros con un gol oficial de campo", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Jugadores de Real Madrid y Barcelona", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Ganadores de Balón de Oro africanos", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Jugadores con +100 goles en selección", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Campeones como jugador y entrenador", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Ganadores de Libertadores y Champions", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Jugadores con dorsal 10 en Brasil", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Fichajes de +100 millones de euros", "FUTBOL", "", ""));
                        items.add(new CategoryItem(null, "Jugadores sin tarjeta roja en su carrera", "FUTBOL", "", ""));

                        // MOVIES PACK
                        items.add(new CategoryItem(null, "Ganadores del Oscar a Mejor Director", "MOVIES", "", ""));
                        items.add(new CategoryItem(null, "Películas de Marvel con más de 1B en taquilla", "MOVIES", "",
                                        ""));
                        items.add(new CategoryItem(null, "Actores que han interpretado al Joker", "MOVIES", "", ""));
                        items.add(new CategoryItem(null, "Películas de terror slasher clásicas", "MOVIES", "", ""));
                        items.add(new CategoryItem(null, "Ganadoras del Oscar a Mejor Película Animada", "MOVIES", "",
                                        ""));
                        items.add(new CategoryItem(null, "Trilogías famosas de ciencia ficción", "MOVIES", "", ""));
                        items.add(new CategoryItem(null, "Villanos icónicos de Disney", "MOVIES", "", ""));
                        items.add(new CategoryItem(null, "Directores mexicanos ganadores del Oscar", "MOVIES", "", ""));
                        items.add(new CategoryItem(null, "Películas protagonizadas por Tom Hanks", "MOVIES", "", ""));

                        characterRepository.saveAll(items);
                }
        }

        public void seedImpostorWords() {
                if (impostorWordRepository.count() == 0) {
                        List<ImpostorWord> list = new ArrayList<>();
                        list.add(new ImpostorWord(null, "Animales", "León", "El rey de la selva"));
                        list.add(new ImpostorWord(null, "Animales", "Elefante", "Tiene mucha memoria y trompa"));
                        list.add(new ImpostorWord(null, "Comida", "Pizza", "De origen italiano, redonda"));
                        list.add(new ImpostorWord(null, "Países", "México", "Tacos, mariachis y picante"));
                        list.add(new ImpostorWord(null, "Deportes", "Fútbol", "11 contra 11, gol"));
                        list.add(new ImpostorWord(null, "Profesiones", "Doctor", "Cura a los enfermos"));
                        list.add(new ImpostorWord(null, "Transporte", "Avión", "Vuela por los aires"));
                        impostorWordRepository.saveAll(list);
                }
        }
        // ADMIN METHODS

        public List<CategoryItem> getAllCategories() {
                return characterRepository.findAll();
        }

        public List<GameRoom> getAllRooms() {
                return roomRepository.findAll();
        }

        @Transactional
        public void deleteRoom(String roomCode) {
                GameRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
                if (room != null) {
                        roomRepository.delete(room);
                }
        }

        @Transactional
        public void deleteCategoryItem(Long id) {
                if (characterRepository.existsById(id)) {
                        characterRepository.deleteById(id);
                }
        }

        @Transactional
        public void updateCategoryItem(Long id, String name, String packType) {
                CategoryItem item = characterRepository.findById(id).orElse(null);
                if (item != null) {
                        item.setName(name);
                        item.setPackType(packType != null ? packType.toUpperCase().trim() : "FUTBOL");
                        characterRepository.save(item);
                }
        }

        @Transactional
        public void migrateSoccerToFutbol() {
                List<CategoryItem> items = characterRepository.findByPackType("SOCCER");
                if (!items.isEmpty()) {
                        for (CategoryItem item : items) {
                                item.setPackType("FUTBOL");
                        }
                        characterRepository.saveAll(items);
                        System.out.println("Migrated " + items.size() + " categories from SOCCER to FUTBOL.");
                }
        }

        public List<String> getAllPacks() {
                List<CategoryItem> all = characterRepository.findAll();
                Set<String> packs = new HashSet<>();
                for (CategoryItem item : all) {
                        if (item.getPackType() != null) {
                                packs.add(item.getPackType());
                        }
                }
                return new ArrayList<>(packs);
        }

        public List<ImpostorWord> getAllImpostorWords() {
                return impostorWordRepository.findAll();
        }

        public List<String> getImpostorCategories() {
                return impostorWordRepository.findAll().stream()
                                .map(ImpostorWord::getCategory)
                                .distinct()
                                .sorted()
                                .collect(Collectors.toList());
        }

        @Transactional
        public void addImpostorWord(String category, String word, String hint) {
                ImpostorWord w = new ImpostorWord(null, category, word, hint);
                impostorWordRepository.save(w);
        }

        @Transactional
        public void deleteImpostorWord(Long id) {
                impostorWordRepository.deleteById(id);
        }

        @Transactional
        public void castAccuseVote(Long voterId, Long targetId) {
                GamePlayer voter = playerRepository.findById(voterId)
                                .orElseThrow(() -> new RuntimeException("Voter not found"));

                if (voter.isEliminated()) {
                        throw new RuntimeException("Eliminated players cannot vote");
                }

                GameRoom room = voter.getRoom();

                // Store vote
                room.getAccuseVotes().put(voterId, targetId);
                roomRepository.save(room);

                // Check completion (only active players count)
                long activePlayers = room.getPlayers().stream().filter(p -> !p.isEliminated()).count();
                int votesCast = room.getAccuseVotes().size();

                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                "ACCUSE_PROGRESS:" + votesCast + ":" + activePlayers);

                if (votesCast >= activePlayers) {
                        resolveAccusation(room);
                }
        }

        private void resolveAccusation(GameRoom room) {
                Map<Long, Integer> counts = new HashMap<>();
                for (Long targetId : room.getAccuseVotes().values()) {
                        counts.put(targetId, counts.getOrDefault(targetId, 0) + 1);
                }

                // Find max
                Long maxTargetId = null;
                int maxVotes = -1;
                boolean tie = false;

                for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
                        if (entry.getValue() > maxVotes) {
                                maxVotes = entry.getValue();
                                maxTargetId = entry.getKey();
                                tie = false;
                        } else if (entry.getValue() == maxVotes) {
                                tie = true;
                        }
                }

                if (tie || maxTargetId == null) {
                        // Tie -> No one ejected, game continues
                        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                        "ACCUSE_RESULT:TIE:Nadie fue expulsado (Empate). El juego continúa.");
                } else {
                        GamePlayer accused = playerRepository.findById(maxTargetId).orElse(null);
                        if (accused != null) {
                                if (accused.isImpostor()) {
                                        // Impostor caught -> Town Wins
                                        room.setStatus(GameRoom.RoomStatus.FINISHED);
                                        roomRepository.save(room);
                                        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                                        "ACCUSE_RESULT:IMPOSTOR_CAUGHT:¡" + accused.getName()
                                                                        + " era el Impostor! Ganan los Aldeanos. 🏆");
                                } else {
                                        // Innocent -> Ejected
                                        accused.setEliminated(true);
                                        playerRepository.save(accused);

                                        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                                        "ACCUSE_RESULT:INNOCENT_EJECTED:" + accused.getName()
                                                                        + " NO era el Impostor. Ha sido eliminado. 💀");

                                        // Check if Impostors win (Impostors >= Innocents)
                                        long activeInnocents = room.getPlayers().stream()
                                                        .filter(p -> !p.isEliminated() && !p.isImpostor()).count();
                                        long activeImpostors = room.getPlayers().stream()
                                                        .filter(p -> !p.isEliminated() && p.isImpostor()).count();

                                        if (activeImpostors >= activeInnocents) {
                                                room.setStatus(GameRoom.RoomStatus.FINISHED);
                                                roomRepository.save(room);
                                                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(),
                                                                "GAME_OVER:IMPOSTOR_WINS:¡Los Impostores han tomado el control!");
                                        }
                                }
                        }
                }

                // Reset votes for next round if game continues
                if (room.getStatus() != GameRoom.RoomStatus.FINISHED) {
                        room.getAccuseVotes().clear();
                        roomRepository.save(room);
                }
        }
}
