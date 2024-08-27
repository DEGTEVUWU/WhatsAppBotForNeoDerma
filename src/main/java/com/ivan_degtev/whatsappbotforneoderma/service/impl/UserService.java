package com.ivan_degtev.whatsappbotforneoderma.service.impl;

import com.ivan_degtev.whatsappbotforneoderma.dto.WebhookPayload;
import com.ivan_degtev.whatsappbotforneoderma.exception.NotFoundException;
import com.ivan_degtev.whatsappbotforneoderma.mapper.UserMapper;
import com.ivan_degtev.whatsappbotforneoderma.model.Message;
import com.ivan_degtev.whatsappbotforneoderma.model.User;
import com.ivan_degtev.whatsappbotforneoderma.repository.UserRepository;
import com.ivan_degtev.whatsappbotforneoderma.service.ai.LangChain4jService;
import com.ivan_degtev.whatsappbotforneoderma.service.util.JsonLoggingService;
import com.ivan_degtev.whatsappbotforneoderma.service.util.UserChecks;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final MessageService messageService;
    private final LangChain4jService langChain4jService;
    private final JsonLoggingService jsonLogging;

    private final UserChecks userChecks;
    /**
     * Основной метод по мапингу json вопросов-ответов с клиентов во внутренние сущности юзера и сообщения
     * При первичном обращении - юзер создается, если он уже есть в БД по уникальному чат-айди
     * - добавляются только новые сообщения
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void addingUserWhenThereIsNone(WebhookPayload webhookPayload) {
        jsonLogging.info("получил пейлоад из нового метода в сервисе {}", webhookPayload.toString());
        String chatPushMessageId = webhookPayload.getPayload().getNewMessage().getMessage().getId();
        String currentChatId = webhookPayload.getPayload().getNewMessage().getChatId();
        Message currentMessage = new Message();

        if (!userChecks.checkingExistenceUserByChatId(currentChatId)) {
            User user = userMapper.convertWebhookPayloadToUser(webhookPayload);
            currentMessage = messageService.addNewMessage(webhookPayload, chatPushMessageId);

            List<Message> messages = new ArrayList<>();
            messages.add(currentMessage);
            user.setMessages(messages);

            userRepository.save(user);
        } else if (userChecks.checkingExistenceUserByChatId(currentChatId)) {
            currentMessage = messageService.addNextMessage(webhookPayload, chatPushMessageId);
        }

        User currentUser = userRepository.findUserByChatId(currentChatId)
                        .orElseThrow(() -> new NotFoundException("Юзер с айди чата " + currentChatId + " не найден!"));

//        String currentUniqueIdForAppointment = webhookPayload.getPayload().getNewMessage().getMessage().getId();
        currentUser = userChecks.addingUniqueIdForAppointmentIsNone(currentUser);

        langChain4jService.mainMethodByWorkWithLLM(currentUser, currentMessage);
    }

//    /**
//     * Метод добавляет уникальный номер для юзера, для дальнейшей связи между юзером и объектами записи на сеанс
//     * ПРоисходит это лишь при первом сообщении(добавление нового юззера в базу)
//     * или
//     * если все связанные сущности сеанса у юзера финализированы(завершены) и это значит, что юзер хочет создать новую
//     * запись на сеас
//     * @param currentUser
//     */
//    @Transactional
//    public void addingUniqueIdForAppointmentIsNone(User currentUser) {
//        if (currentUser.getUniqueIdForAppointment() == null) {
//            currentUser.setUniqueIdForAppointment(UUID.randomUUID().toString());
//            userRepository.save(currentUser);
//        } else if (currentUser.getAppointments()
//                .stream()
//                .allMatch(appointment ->
//                        Boolean.TRUE.equals(appointment.getCompletedBooking()) &&
//                        Boolean.TRUE.equals(appointment.getApplicationSent())))
//        {
//            currentUser.setUniqueIdForAppointment(UUID.randomUUID().toString());
//            userRepository.save(currentUser);
//        }
//        jsonLogging.info("Возвращаю из метода addingUniqueIdForAppointmentIsNone текущего юзера с изменениями {}",
//                currentUser.toString());
//    }
//
//    /**
//     * Утилитный метод проверяет есть ли юзер по чат-айди в БД, для понимания были ли с ним ранее диалоги. При отсутсвии -
//     * юзер добавляется в БД, при наличии - добавляются только новые сообщения, связываюсь с текущим юзером по чат-айди
//     */
//    private boolean CheckingExistenceUserByChatId(String currentChatId) {
//        return userRepository.existsByChatId(currentChatId);
//    }
}
