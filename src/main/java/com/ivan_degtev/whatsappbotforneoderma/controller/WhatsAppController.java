package com.ivan_degtev.whatsappbotforneoderma.controller;

import com.ivan_degtev.whatsappbotforneoderma.dto.SendingMessageResponse;
import com.ivan_degtev.whatsappbotforneoderma.service.impl.ChatpushServiceImpl;
import com.ivan_degtev.whatsappbotforneoderma.service.impl.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;


@RestController
@Slf4j
public class WhatsAppController {
    private final ChatpushServiceImpl chatpushService;
    private final MessageService messageService;

    @Value("${ngrok.url}")
    private String ngrokUrl;
    @Value("${chatpush.api.key}")
    private String chatpushApiKey;

    public WhatsAppController(
            ChatpushServiceImpl chatpushService,
            MessageService messageService,
            @Value("${ngrok.url}") String ngrokUrl,
            @Value("${chatpush.api.key}") String chatpushApiKey
    ) {
        this.chatpushService = chatpushService;
        this.messageService = messageService;
        this.ngrokUrl = ngrokUrl;
        this.chatpushApiKey = chatpushApiKey;
    }

    /**
     * УТИЛИТНАЯ Ручка, настраивает коннект с chatpush через прокси ngrok для вотсапа. После первичного(!!) перехода по ней -
     * сообщения с вотсапа будут поступать на следующую ручку /webhook,  как строка+мапа с хедерами
     */
    @PostMapping("/activate-webhook")
    public Mono<String> activateWebhook() {
        String webhookUrl = ngrokUrl + "/webhook";
        List<String> eventTypes = List.of("whatsapp_incoming_msg");

        return chatpushService.createWebhook(webhookUrl, eventTypes);
    }

    /**
     * На эту ручку будут приходить все входщие и исходящие сообщения с вотсапа, переброшенные сюда через чатпуш -> ngrok
     * Нужно в дальнейшей логике корректно фильтровать и не обрабатывать через LLM исходящие сообшения
     */
    @PostMapping("/webhook")
    public void handleWebhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody String payload
    ) {
        log.info("зашёл в метод чекрез ngrock");
        chatpushService.getMessageFromWebhook(headers, payload);
    }


    /**
     * Тестовый метод для получения всех настроенных веб-хуков, не используется
     */
    @GetMapping("/filter-webhooks")
    public Mono<Map<String, Object>> filterWebhooks() {
        Mono<Map<String, Object>> allWebhooks =  chatpushService.getAllWebhooks();
        log.info("ответ - все веб хуки {}", allWebhooks.toString());
        return allWebhooks;
    }
}
