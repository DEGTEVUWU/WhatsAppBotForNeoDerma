package com.ivan_degtev.whatsappbotforneoderma.controller;

import com.ivan_degtev.whatsappbotforneoderma.dto.amoCrm.LeadDto;
import com.ivan_degtev.whatsappbotforneoderma.service.impl.AmoCrmService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * тут неск утилитных ручек по получению данных от амосрм по лидам и по колонкам(нужны для составления верного тела запроса на добавления записи)
 * И ручка addLead на добавление записи в нужную сделку и с нужным статусом
 */
@RestController
@RequiredArgsConstructor
public class AmoCrmController {

    private final AmoCrmService amoCrmService;

    @GetMapping("/leads")
    public ResponseEntity<String> getAllLeads(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        return amoCrmService.getAllLeads(page, limit);
    }

    @GetMapping("/leads/custom-fields")
    public String getCustomFieldsForLeads() {
        return amoCrmService.getCustomFieldsForLeads();
    }

    @GetMapping("/pipelines")
    public ResponseEntity<String> getAllPipelines() {
        try {
            return amoCrmService.getAllPipelines();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve pipelines from AmoCRM");
        }
    }

    @PostMapping(path = "/new_lead")
    public ResponseEntity<String> addLead(@RequestBody LeadDto leadDto) {
        try {
            String response = amoCrmService.addLead(leadDto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при добавлении лида: " + e.getMessage());
        }
    }
}

