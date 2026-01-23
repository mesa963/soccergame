package com.example.soccergame.repository;

import com.example.soccergame.model.ImpostorWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImpostorWordRepository extends JpaRepository<ImpostorWord, Long> {
    List<ImpostorWord> findByCategory(String category);
}
