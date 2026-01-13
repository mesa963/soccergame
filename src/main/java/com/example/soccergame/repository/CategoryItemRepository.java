package com.example.soccergame.repository;

import com.example.soccergame.model.CategoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryItemRepository extends JpaRepository<CategoryItem, Long> {
    List<CategoryItem> findByPackType(String packType);
}
