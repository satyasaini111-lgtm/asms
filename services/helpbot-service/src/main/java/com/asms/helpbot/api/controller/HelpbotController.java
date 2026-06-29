package com.asms.helpbot.api.controller;

import com.asms.helpbot.application.HelpbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/helpbot")
@Tag(name = "HelpBot", description = "Rule-based FAQ assistant")
public class HelpbotController {

    private final HelpbotService helpbotService;

    public HelpbotController(HelpbotService helpbotService) {
        this.helpbotService = helpbotService;
    }

    @GetMapping("/ask")
    @Operation(summary = "Ask helpbot a question")
    public ResponseEntity<Map<String, String>> ask(@RequestParam String query) {
        String answer = helpbotService.answer(query);
        return ResponseEntity.ok(Map.of("query", query, "answer", answer));
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask helpbot via request body")
    public ResponseEntity<Map<String, String>> askPost(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        String answer = helpbotService.answer(query);
        return ResponseEntity.ok(Map.of("query", query, "answer", answer));
    }
}
