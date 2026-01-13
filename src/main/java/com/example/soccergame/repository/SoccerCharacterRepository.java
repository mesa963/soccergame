package com.example.soccergame.repository;

import com.example.soccergame.model.SoccerCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoccerCharacterRepository extends JpaRepository<SoccerCharacter, Long> {
}
