package com.example.soccergame.controller;

import com.example.soccergame.model.CategoryItem;
import com.example.soccergame.model.GameRoom;
import com.example.soccergame.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private GameService gameService;

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryItem>> getAllCategories() {
        return ResponseEntity.ok(gameService.getAllCategories());
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<GameRoom>> getAllRooms() {
        return ResponseEntity.ok(gameService.getAllRooms());
    }

    @DeleteMapping("/rooms/{roomCode}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomCode) {
        gameService.deleteRoom(roomCode);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        gameService.deleteCategoryItem(id);
        return ResponseEntity.ok().build();
    }

    // Using POST for update simplicity or PUT
    @PutMapping("/categories/{id}")
    public ResponseEntity<Void> updateCategory(@PathVariable Long id, @RequestParam String name,
            @RequestParam String packType) {
        gameService.updateCategoryItem(id, name, packType);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/packs")
    public ResponseEntity<List<String>> getAllPacks() {
        return ResponseEntity.ok(gameService.getAllPacks());
    }
}
