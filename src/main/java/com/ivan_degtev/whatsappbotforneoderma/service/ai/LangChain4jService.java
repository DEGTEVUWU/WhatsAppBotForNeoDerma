package com.ivan_degtev.whatsappbotforneoderma.service.ai;

import com.ivan_degtev.whatsappbotforneoderma.component.DailyScheduler;
import com.ivan_degtev.whatsappbotforneoderma.config.LC4jAssistants.QuestionAnalyzer;
import com.ivan_degtev.whatsappbotforneoderma.config.LC4jAssistants.RAGAssistant;
import com.ivan_degtev.whatsappbotforneoderma.controller.AmoCrmController;
import com.ivan_degtev.whatsappbotforneoderma.controller.llm.LLMMemoryController;
import com.ivan_degtev.whatsappbotforneoderma.controller.whatsapp.WhatsAppSendController;
import com.ivan_degtev.whatsappbotforneoderma.dto.amoCrm.CustomFieldDto;
import com.ivan_degtev.whatsappbotforneoderma.dto.amoCrm.LeadDto;
import com.ivan_degtev.whatsappbotforneoderma.dto.amoCrm.ValueDto;
import com.ivan_degtev.whatsappbotforneoderma.exception.NotFoundException;
import com.ivan_degtev.whatsappbotforneoderma.model.Message;
import com.ivan_degtev.whatsappbotforneoderma.model.User;
import com.ivan_degtev.whatsappbotforneoderma.model.yClient.Appointment;
import com.ivan_degtev.whatsappbotforneoderma.model.yClient.ServiceInformation;
import com.ivan_degtev.whatsappbotforneoderma.repository.UserRepository;
import com.ivan_degtev.whatsappbotforneoderma.repository.yClient.AppointmentsRepository;
import com.ivan_degtev.whatsappbotforneoderma.repository.yClient.ServiceInformationRepository;
import com.ivan_degtev.whatsappbotforneoderma.service.impl.yClient.YClientSendServiceImpl;
import com.ivan_degtev.whatsappbotforneoderma.service.util.JsonLoggingService;
import com.ivan_degtev.whatsappbotforneoderma.tests.AssistantTest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@Slf4j
@DependsOn("dailyScheduler")
public class LangChain4jService {

    private final ServiceInformationRepository serviceInformationRepository;
    private final DailyScheduler dailyScheduler;
    private final AmoCrmController amoCrmController;
    @Value("${open.ai.token}")
    private String openAiToken;
    private final AssistantTest assistantTest;
    private final QuestionAnalyzer questionAnalyzer;
    private final RAGAssistant ragAssistant;
    private final UserRepository userRepository;
    private final AppointmentsRepository appointmentsRepository;
    private final WhatsAppSendController whatsAppSendController;
    private final LLMMemoryController llmMemoryController;
    private final YClientSendServiceImpl yClientSendService;

    private final JsonLoggingService jsonLogging;

    public LangChain4jService(
            @Value("${open.ai.token}") String openAiToken,
            AssistantTest assistantTest,
            QuestionAnalyzer questionAnalyzer,
            RAGAssistant ragAssistant,
            UserRepository userRepository,
            AppointmentsRepository appointmentsRepository,
            WhatsAppSendController whatsAppSendController,
            LLMMemoryController llmMemoryController,
            YClientSendServiceImpl yClientSendService,
            JsonLoggingService jsonLogging,
            ServiceInformationRepository serviceInformationRepository, DailyScheduler dailyScheduler, AmoCrmController amoCrmController) {
        this.openAiToken = openAiToken;
        this.assistantTest = assistantTest;
        this.questionAnalyzer = questionAnalyzer;
        this.ragAssistant = ragAssistant;
        this.userRepository = userRepository;
        this.appointmentsRepository = appointmentsRepository;
        this.whatsAppSendController = whatsAppSendController;
        this.llmMemoryController = llmMemoryController;
        this.yClientSendService = yClientSendService;
        this.jsonLogging = jsonLogging;
        this.serviceInformationRepository = serviceInformationRepository;
        this.dailyScheduler = dailyScheduler;
        this.amoCrmController = amoCrmController;
    }


    public void mainMethodByWorkWithLLM(
            User currentUser,
            Message currentMessage
    ) {
        String currentChatId = currentUser.getChatId();
        String textMessage = currentMessage.getText();
        String currentUserPhone = currentUser.getSenderPhoneNumber();

        if (questionAnalyzer.greetingMessage(textMessage)) {
            String greeting = """
                    Привет! Это ассистент компании NeoDerma! 😊
                                        
                    В этом приветственном сообщении я расскажу вам о некоторых правилах работы с нашим ИИ, чтобы улучшить ваш опыт:
                                        
                    1. *Что я могу сделать для вас:*
                       - Записать вас на любую услугу к подходящему специалисту на свободную дату и время.
                       - Предоставить общую информацию о наших услугах и помочь выбрать свободного мастера.
                                        
                    2. *Как общаться:*
                       - Пожалуйста, дожидайтесь ответа на предыдущий вопрос, прежде чем отправлять следующий.
                       - Из-за особенностей WhatsApp, ожидание ответа иногда может занимать до 2 минут. Спасибо за ваше терпение!
                                        
                    3. *Подтверждение записи:*
                       - После выбора услуги, мастера и времени записи на приём, пожалуйста, подтвердите своё намерение.\s
                       - Вы можете сделать это, просто написав в чат: *"Подтверждаю запись к [имя мастера]"*.
                                        
                    4. *Дополнительные команды:*
                       - Для повторного вызова этого сообщения напишите в чат: *"правила"*.
                       - Для очистки истории сообщений напишите: *"очисть историю"*.
                                        
                    Желаю вам хорошего дня! 🌟
                    """;
            var answerFromSendMessage = whatsAppSendController.sendMessage(greeting, currentUserPhone).subscribe();
            jsonLogging.info("Отправил сообщение - приветствие ", answerFromSendMessage);
        } else if (questionAnalyzer.cleanHistoryMessage(textMessage)) {
            llmMemoryController.deleteMessages(currentChatId);

            String answerForDeletingHistory = """
                    Ваша историю чата удалена!
                    Давайте начнем переписку с чистого листа!
                    """;
            var answerFromSendMessage = whatsAppSendController.sendMessage(
                            answerForDeletingHistory,
                            currentUserPhone
                    )
                    .subscribe();
            jsonLogging.info("Отправил сообщение об удаление истории ", answerFromSendMessage);
        }

        //ВАЖНО! ВРЕМЕННАЯ ЛОГИКА ДЛЯ ТЕСТИРОВАНИЯ ЛЛМ БЕЗ РЕАЛЬНОЙ ЗАПИСИ В YCLIENT -
//        ЗАКОММЕНТИРОВАТЬ ПРИ РАБОТЕ
        else {
            String LLMAnswer = assistantTest.chat(currentChatId, textMessage, currentChatId);
            jsonLogging.info("Ответ от ЛЛМ по сути вопроса: {}", LLMAnswer);
            var answerFromSendMessage = whatsAppSendController
                    .sendMessage(LLMAnswer, currentUserPhone)
                    .subscribe();
            jsonLogging.info("Отправил сообщение из сервиса LangChain4j в чатпуш сервис - в сообщению юзеру, " +
                    "ответ от метода отправки {}", answerFromSendMessage);
        }


        //ВАЖНО! ФИНАЛЬНЫЙ МЕТОД ПО ОТПРАВКЕ ЗАПРОС НА СОЗДАНИЕ БРОНИ В YCLIENT. РАСКОМЕНТИРОВАТЬ ПРИ ТЕСТЕ И ОТПРАВКЕ КОДА В ПРОД
//        else {
//            String LLMAnswer = assistantTest.chat(currentChatId, textMessage, currentChatId);
//            jsonLogging.info("Ответ от ЛЛМ по сути вопроса: {}", LLMAnswer);
//
//            Optional<Appointment> currentAppointment = appointmentsRepository.findByUser_UniqueIdForAppointment(
//                    currentUser.getUniqueIdForAppointment()
//            );
//            if (currentAppointment.isPresent()) {
//                if (isAppointmentReadyForShipment(currentAppointment.get())) {
//                    List<Appointment> currentAppointments = List.of(currentAppointment.get());
//                    List<ServiceInformation> currentServiceInformation =
//                            currentAppointment.get().getServicesInformation();
//
//                    //Отправка пост запроса на Yclient с записью
//                    yClientSendService.sendBookingRequest(
//                                    currentUser,
//                                    currentAppointments,
//                                    currentServiceInformation
//                            )
//                            .subscribe(responseEntity -> {
//                                Map<String, Object> responseBody = responseEntity.getBody();
//                                if (responseBody != null) {
//                                    boolean success = (Boolean) responseBody.get("success");
//                                    if (success) {
//                                        log.info("Запись прошла успешно.");
//                                        addingFinalFlagAboutSuccessfulRecording(currentAppointments);
//                                    } else {
//                                        log.warn("Запись не удалась.");
//                                    }
//                                }
//                            });
//                }
//            }

    }

    public void createRecordInAmoCrm(String currentChatId, Appointment currentAppointment) {
        User currentUser = userRepository.findUserByChatId(currentChatId)
                .orElseThrow(() -> new NotFoundException("Юзер с айди чата " + currentChatId + " не найден"));
        List<ServiceInformation> currentServiceInformation = serviceInformationRepository.findAllByAppointment(currentAppointment);

        LeadDto leadDto = new LeadDto();
        leadDto.setName(currentServiceInformation.get(0).getTitle());
        leadDto.setPipelineId(6373406);
        leadDto.setCreatedBy(0);
        leadDto.setPrice(Integer.valueOf(currentServiceInformation.get(0).getPriceMax()));
        leadDto.setStatusId(69559066);

        List<CustomFieldDto> customFields = new ArrayList<>();

        CustomFieldDto nameField = new CustomFieldDto();
        nameField.setFieldId(1881929);
        nameField.setValues(List.of(new ValueDto(currentUser.getSenderName())));

        CustomFieldDto phoneField = new CustomFieldDto();
        phoneField.setFieldId(1881929);
        phoneField.setValues(List.of(new ValueDto(currentUser.getSenderPhoneNumber())));

        CustomFieldDto dateField = new CustomFieldDto();
        dateField.setFieldId(1883033);
        dateField.setValues(List.of(new ValueDto(currentAppointment.getDatetime().toString())));

        customFields.add(nameField);
        customFields.add(phoneField);
        customFields.add(dateField);

        leadDto.setCustomFieldsValues(customFields);

        amoCrmController.addLead(leadDto);
    }

    /**
     * Добавляет в appointments флаг по отправке запрос на добавление записи в яклиент.
     * Финальное изменение
     * @param currentAppointments
     */
    @Transactional
    public void addingFinalFlagAboutSuccessfulRecording(List<Appointment> currentAppointments) {
        if (currentAppointments == null || currentAppointments.isEmpty()) {
            log.warn("Список встреч пуст или не задан.");
            return;
        }

        currentAppointments.stream()
                .filter(Objects::nonNull)
                .peek(appointment -> {
                    appointment.setApplicationSent(true);
                    appointmentsRepository.save(appointment);
                })
                .forEach(appointment -> log.info("Обновлена встреча с id: {}", appointment.getId()));
        log.info("Все встречи были обновлены с applicationSent=true.");
    }

    /**
     * Метод возвращает объект Appointment, полностью заполненный данными и  готовый к отправке
     *
     * @param appointment
     * @return
     */
    public boolean isAppointmentReadyForShipment(Appointment appointment) {
        return appointment != null
                && Boolean.TRUE.equals(appointment.getCompletedBooking())
                && Boolean.FALSE.equals(appointment.getApplicationSent());
    }

    /**
     * Проверка существования истории чата по айди памяти
     *
     * @param memoryId
     * @return
     */
    private boolean memoryCheckForEmptiness(String memoryId) {
        return llmMemoryController.getMessages(memoryId).toString().isEmpty();
    }

    /**
     * Тестовый формат метода для быстрой проверки ЛЛМ через сканер.
     * Подключить в DataInitializer(удобнее) или вызвать вручную из кода
     */
    public void testLLMLogicWithScanner() {
        User currentUser = new User();
        currentUser.setChatId("111");
        currentUser.setUniqueIdForAppointment(UUID.randomUUID().toString());
        userRepository.save(currentUser);


        Scanner scanner = new Scanner(System.in);
        while (true) {
            String question = scanner.nextLine();

            if (question.equals("exit")) {
                log.info("тестовый сканер закрыт!");
                break;
            }
            String currentChatId = currentUser.getChatId();

            String answer = assistantTest.chat("111", question, "111");
            log.info("Ответ от тест чата, сканер: {}", answer);

            User checkUser = userRepository.findUserByChatId(currentChatId).get();
            Appointment currentAppointment = appointmentsRepository.findAppointmentByUser(currentUser);

            if ( currentAppointment != null
                    && checkUser.getAppointments().get(0).getCompletedBooking() != null
                    && checkUser.getAppointments().get(0).getCompletedBooking()) {
                createRecordInAmoCrm(currentChatId, currentAppointment);
            }
        }
        log.info("Сканнер закрыт!");
    }

//    @Transactional
    public void testAmo() {
        User currentUser = new User();
        currentUser.setChatId("111");
        currentUser.setUniqueIdForAppointment(UUID.randomUUID().toString());
        userRepository.save(currentUser);

        ServiceInformation serviceInformation = new ServiceInformation();
        serviceInformation.setServiceId("123456");
        serviceInformation = serviceInformationRepository.save(serviceInformation);

        Appointment currentAppointment = new Appointment();
        currentAppointment.setStaffId("123456");
        OffsetDateTime appointmentDateTime = OffsetDateTime.of(2024, 9, 11, 7, 0, 0, 0, ZoneOffset.UTC);
        currentAppointment.setDatetime(appointmentDateTime);

        List<ServiceInformation> serviceInformationList = new ArrayList<>();
        serviceInformationList.add(serviceInformation);
        currentAppointment.setServicesInformation(serviceInformationList);

        List<Appointment> appointmentList = new ArrayList<>();
        appointmentList.add(currentAppointment);
        currentUser.setAppointments(appointmentList);

        userRepository.save(currentUser);

        createRecordInAmoCrm("111", currentAppointment);
    }

}

