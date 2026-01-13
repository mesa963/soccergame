package com.example.soccergame.repository;

import com.example.soccergame.model.GamePlayer;
import com.example.soccergame.model.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {
    List<GamePlayer> findByRoom(GameRoom room);
}
