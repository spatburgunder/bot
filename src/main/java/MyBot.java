import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollOption;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class MyBot extends TelegramLongPollingBot {
    //Long chatId;
    Long bossChatId = 639284651L;
    boolean isUpdateSuccessful; // флаг успешности обновления
    private static final Map<Long, UserSession> sessionMap = new HashMap<>();
    private static final String marked = "\uD83D\uDC49 "; //эмодзи для отметки выбранной кнопки
    private static final String completeButton = "✅ Дальше";
    private static final String guestButton = "\uD83D\uDC64 Гость";
    private static final String[] names = {"Александр А.","Александр В.","Александр П.","Алексей","Андрей","Глеб","Егор","Игорь","Кирилл","Михаил В.","Михаил Г.","Павел","Роман"};
    public enum UserState {
        WAITING_FOR_START,
        START_PROCESSING,
        COUNT_PROCESSING,
        PRICE_PROCESSING,
        GUEST_NAME_PROCESSING,
        PARTICIPANTS_CHOOSING,
        WAITING_FOR_FEEDBACK,
        FEEDBACK_PROCESSING,
        DONATE_REQUEST,
        POLL_PASSING
    }
    private static class UserSession {
        private UserState state = UserState.WAITING_FOR_START; // начальный
        String itemName = "\uD83D\uDCA8"; // начальный
        String firstName;
        String lastName;
        String userName;
        int totalPizzas;
        double pizzaPrice;
        double pricePerCount;
        int currentPizza = 1; //счетчик
        Map<String, Double> participantsWithPriceCurrent = new HashMap<>();
        Map<String,Map<String, Double>> participantsFinalMap = new HashMap<>();
        Integer askWhoMessageId = null;
        Integer sentMessageId = null;
        Integer resultMessageId = null;
        String sentPollId = null;
        List<PollOption> pollOptions;
        List<Integer> messagesToDelete = new ArrayList<>(); // Список сообщений к удалению
        Map<String, Boolean> participants = new LinkedHashMap<>();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try{
        Long chatId = getChatIdFromUpdate(update);
        UserSession session = sessionMap.computeIfAbsent(chatId, k -> new UserSession());

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            session.firstName = update.getMessage().getFrom().getFirstName();
            System.out.println(session.firstName+" >> "+messageText);
            session.lastName = update.getMessage().getFrom().getLastName();
            if(session.lastName==null){session.lastName="";}
            session.userName = update.getMessage().getFrom().getUserName();
            if(session.userName!=null){session.userName = "@"+session.userName;}

            if (messageText.equals("/start")){
                // удаление сообщений
                if(session.askWhoMessageId != null){session.messagesToDelete.add(session.askWhoMessageId);}
                if(!session.messagesToDelete.isEmpty()){deleteMessages(chatId,session.messagesToDelete);}
                session.state = UserState.WAITING_FOR_START;
            }

            if (messageText.equals("/calculate")){
                // удаление сообщений
                if(session.askWhoMessageId != null){session.messagesToDelete.add(session.askWhoMessageId);}
                if(!session.messagesToDelete.isEmpty()){deleteMessages(chatId,session.messagesToDelete);}
                // обновление сессии
                sessionMap.remove(chatId);
                session = sessionMap.computeIfAbsent(chatId, k -> new UserSession());
                session.firstName = update.getMessage().getFrom().getFirstName();
                session.state = UserState.START_PROCESSING;
            }

            if (messageText.equals("/poll")){
                // удаление сообщений
                if(session.askWhoMessageId != null){session.messagesToDelete.add(session.askWhoMessageId);}
                if(!session.messagesToDelete.isEmpty()){deleteMessages(chatId,session.messagesToDelete);}
                session.state = UserState.POLL_PASSING;
            }

            if (messageText.equals("/feedback")){
                session.state = UserState.WAITING_FOR_FEEDBACK;
            }

            if (messageText.equals("/donate")){
                session.state = UserState.DONATE_REQUEST;
            }

            switch (session.state) {
                case WAITING_FOR_START:
                    sendMessage(chatId,
                            "Доступные команды:\n\n"
                            +"/calculate - начать новый расчет \uD83C\uDF55\n\n"
                            +"/feedback - оставить обратную связь \uD83D\uDC8C\n\n"
                            //+"/donate - подать на хлеб \uD83D\uDCB8"
                            , false);
                    break;

                case START_PROCESSING:
                    if(!messageText.equals("/calculate")){
                        session.itemName = messageText;
                        session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                        sendMessage(chatId, "Сколько стоит \""+session.itemName+"\"?", true);
                        session.state = UserState.PRICE_PROCESSING;
                    } else {
                        sendMessage(chatId, "Введи количество \"" + session.itemName + "\"", true);
                        session.state = UserState.COUNT_PROCESSING;
                    }
                    break;

                case COUNT_PROCESSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    if (messageText.matches("\\d+") && Integer.parseInt(messageText) > 0) {
                        session.totalPizzas = Integer.parseInt(messageText);
                        sendMessage(chatId, "Сколько стоит \""+session.itemName+"\"?", true);
                        session.state = UserState.PRICE_PROCESSING;
                    } else {
                        sendMessage(chatId, "Введи корректное количество", true);
                    }
                    break;

                case PRICE_PROCESSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    if (messageText.matches("\\d+") && Double.parseDouble(messageText) > 0) {
                        session.pizzaPrice = Double.parseDouble(messageText);
                        {// Создаем LinkedHashMap
                            for (String name : names) {
                                session.participants.put(name, false);
                            }
                            // Дополнительно для гостя и перехода
                            session.participants.put(guestButton, false);
                            session.participants.put(completeButton, false);
                        }
                        askWhoAtePizza(chatId); // Первый запрос участников
                        session.state = UserState.PARTICIPANTS_CHOOSING;
                    } else {
                        sendMessage(chatId, "Введи корректную цену", true);
                    }
                    break;

                case GUEST_NAME_PROCESSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    // временная мапа, чтоб добавить в нее гостя и заменить ей настоящуюю
                    LinkedHashMap<String, Boolean> newParticipants = new LinkedHashMap<>();
                    for (Map.Entry<String, Boolean> entry : session.participants.entrySet()) {
                        if (entry.getKey().equals(guestButton)) {
                            newParticipants.put(messageText, false); // вставляем новую пару перед guestButton
                        }
                        newParticipants.put(entry.getKey(), entry.getValue());
                    }
                    session.participants = newParticipants; // заменяем с гостем
                    updateButtonState(chatId, session.askWhoMessageId, messageText); //обновляем клаву кнопкой с гостем
                    if(isUpdateSuccessful) {
                        sendMessage(chatId, "Добавлена кнопка " + messageText + " ☝\uFE0F", true);
                    }
                    session.state = UserState.PARTICIPANTS_CHOOSING;
                    break;

                case PARTICIPANTS_CHOOSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    sendMessage(chatId, "Используй кнопки выше или начни сначала /start", true);
                    break;

                case WAITING_FOR_FEEDBACK:
                    sendMessage(chatId,"Напиши, что передать боссу",false);
                    session.state = UserState.FEEDBACK_PROCESSING;
                    break;

                case FEEDBACK_PROCESSING:
                    sendMessage(chatId,"Спасибо, отзыв отправлен",false);
                    session.state = UserState.WAITING_FOR_START;
                    sendMessage(bossChatId,
                            "❗❗❗❗❗❗❗❗❗❗\n"
                                    +session.firstName+" "+session.lastName+" ("+session.userName+") передал:\n"
                                    +messageText,
                            false);
                    // Удаляем сессию после окончания
                    sessionMap.remove(chatId);
                    break;

                case DONATE_REQUEST:

                    // создаем инлайн кнопки
                    /*InlineKeyboardButton button1 = InlineKeyboardButton
                            .builder()
                            .text("\uD83D\uDFE2 сбер")
                            .url("https://www.sberbank.com/sms/pbpn?requisiteNumber=79201191976")
                            .build();*/
                    InlineKeyboardButton button2 = InlineKeyboardButton
                            .builder()
                            .text("\uD83D\uDFE1 Тиньк")
                            .url("https://www.tinkoff.ru/rm/r_LSOkAqROCG.TCbgNCMCIj/Jh5ys37674")
                            .build();
                    /*InlineKeyboardButton button3 = InlineKeyboardButton
                            .builder()
                            .text("\uD83D\uDD35 втб")
                            .url("https://vtb.paymo.ru/collect-money/qr/?transaction=4de11b5b-10c4-4fba-86ce-b931828c3961")
                            .build();
                    */
                    InlineKeyboardButton button4 = InlineKeyboardButton
                            .builder()
                            .text("TON Space")
                            .callbackData("TON")
                            .build();
                    // добавляем кнопку в клаву
                    InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                            .keyboard(List.of(
                                    //List.of(button1),
                                    List.of(button2),
                                    //List.of(button3),
                                    List.of(button4)
                            ))
                            .build();

                    // создаем сообщение
                    SendMessage sendMessage = SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Есть возможность поддержать \uD83E\uDD70\n")
                            .replyMarkup(markup)
                            .build();
                    try {
                        Message sentMessage = execute(sendMessage);
                        System.out.println(session.firstName+" << " + sentMessage.getText());
                        session.sentMessageId = sentMessage.getMessageId(); // ID отправленного сообщения
                        session.messagesToDelete.add(sentMessage.getMessageId()); // Сообщение к удалению
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                        sendMessage(chatId, "Что-то пошло не так: " + e.getMessage() + "\n\nПопробуй еще раз или начни сначала \n/start", true);
                    }

                    session.state = UserState.WAITING_FOR_START;
                    sendMessage(bossChatId,
                            "❗"+session.firstName+" "+session.lastName+" ("+session.userName+") went to donate",
                            false);
                    // Удаляем сессию после окончания
                    sessionMap.remove(chatId);
                    break;

                case POLL_PASSING:
                    SendPoll poll = SendPoll
                            .builder()
                            .chatId(chatId)
                            .question("На меня рассчитывать кальян:")
                            .options(List.of(
                                    "1\uFE0F⃣ Первый",
                                    "2\uFE0F⃣ Второй",
                                    "3\uFE0F⃣ Третий",
                                    "4\uFE0F⃣ Четвертый",
                                    "5\uFE0F⃣ Пятый",
                                    "6\uFE0F⃣ Шестой",
                                    "7\uFE0F⃣ Седьмой",
                                    "8\uFE0F⃣ Восьмой",
                                    "9\uFE0F⃣ Девятый",
                                    "На меня не рассчитывать"))
                            .isAnonymous(false)
                            .allowMultipleAnswers(true)
                            .type("")
                            .build();
                    InlineKeyboardMarkup inlineKeyboard = InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(InlineKeyboardButton.builder().text("Тут будет инлайн кнопка").callbackData("data1").build()))
                            .build();
                    try {
                        Message sentPoll = execute(poll);
                        session.sentPollId = sentPoll.getPoll().getId();
                        session.pollOptions = sentPoll.getPoll().getOptions();
                        sendMessageWithKeyboard(chatId,"Отправляй опрос в диалог.\n" +
                                "Ты будешь получать уведомления по нему, пока не отправишь новую команду",
                                null,
                                inlineKeyboard,
                                true);
                    } catch (Exception e) {e.printStackTrace();}

                    session.state = UserState.WAITING_FOR_START;
                    break;
            }
        }


// update без сообщения, но с колбэком (нажали инлайн кнопку)
        else if (update.hasCallbackQuery()) {
            String callback = update.getCallbackQuery().getData();
            System.out.println(session.firstName+" > "+callback);


                switch (callback) {
// Выбрана кнопка УЧАСТНИКА
                    default:
                        // Перерисовка клавы со сменой статуса кнопки
                        updateButtonState(chatId, session.askWhoMessageId, callback);
                        break;
// Выбрана кнопка ГОСТЬ
                    case guestButton:
                        sendMessage(chatId, "Как зовут гостя?", true);
                        session.state = UserState.GUEST_NAME_PROCESSING;
                        break;
// Выбрана кнопка ДАЛЬШЕ
                    case completeButton:
                        // Засчитаны все пиццы, ИТОГ
                        if (session.currentPizza == session.totalPizzas) {
                            // Убираем сообщения
                            session.messagesToDelete.add(session.askWhoMessageId);
                            deleteMessages(chatId, session.messagesToDelete);
                            // Расчет
                            calculateResults(chatId);
                            // Отправляем результат
                            sendResults(chatId);
                            session.state = UserState.WAITING_FOR_START;
                        }
                        // Засчитаны не все, переход к след пицце
                        else {
                            calculateResults(chatId); // Расчет
                            // Убираем сообщения по текущему
                            session.messagesToDelete.add(session.askWhoMessageId);
                            deleteMessages(chatId, session.messagesToDelete);
                            // Переходим к след. пицце
                            session.currentPizza++;
                            askWhoAtePizza(chatId);
                        }
                        break;
// Выбрана кнопка ДОБАВИТЬ ПОЗИЦИЮ
                    case "additional_item":
                        ReplyKeyboardMarkup replyKeyboard = ReplyKeyboardMarkup.builder()
                                .selective(true)
                                .resizeKeyboard(true)
                                .oneTimeKeyboard(true)
                                .inputFieldPlaceholder("Напиши или выбери")
                                .keyboard(Collections.singletonList(
                                        new KeyboardRow() {{
                                            add("\uD83D\uDCA8"); //дым
                                            add("\uD83E\uDED6"); //чайник
                                            add("☕\uFE0F"); //кофе
                                            //add("\uD83E\uDDC3"); //сок
                                            add("\uD83C\uDF55"); //пицца
                                            //add("\uD83C\uDF7A"); //пиво
                                            add("\uD83D\uDE95"); //такси
                                        }}
                                )).build();
                        InlineKeyboardMarkup inlineKeyboard = InlineKeyboardMarkup.builder()
                                .keyboardRow(List.of(
                                        InlineKeyboardButton.builder().text("еще \uD83D\uDCA8, но по другой цене").callbackData("data1").build()))
                                .keyboardRow(List.of(
                                        InlineKeyboardButton.builder().text("\uD83C\uDF55").callbackData("data2").build(),
                                        InlineKeyboardButton.builder().text("\uD83E\uDED6").callbackData("data3").build(),
                                        InlineKeyboardButton.builder().text("☕\uFE0F").callbackData("data4").build(),
                                        InlineKeyboardButton.builder().text("\uD83E\uDDC3").callbackData("data8").build(),
                                        InlineKeyboardButton.builder().text("\uD83D\uDE95").callbackData("data5").build()))
                                .keyboardRow(List.of(
                                        InlineKeyboardButton.builder().text("\uD83C\uDF7A поштучно").callbackData("data6").build(),
                                        InlineKeyboardButton.builder().text("другое").callbackData("data7").build()))
                                .build();

                        sendMessageWithKeyboard(chatId,"Напиши или выбери, что еще разделить *поровну*:", replyKeyboard,null,true);

                        session.currentPizza=1; // сброс указателя для новой позиции
                        session.totalPizzas = 1; // всегда 1 шт
                        deleteMessage(chatId,session.resultMessageId);
                        // сброс меток участников
                        for (String key : session.participants.keySet()) {
                            session.participants.put(key, false); // false для всех участников в карте
                        }
                        session.state = UserState.START_PROCESSING;
                        break;
// Выбрана кнопка TON SPACE
                    case "TON":
                        SendMessage message = SendMessage
                                .builder()
                                .chatId(chatId)
                                .text("tap to copy:\n\n`UQCDXUXV-YyXlyBJifKB1t_XFgX5MeT2GEeJtTDsdsB8mwgc`")
                                .build();
                        message.enableMarkdownV2(true);
                        try {
                            Message sentMessage = execute(message);
                            session.sentMessageId = sentMessage.getMessageId(); // ID отправленного сообщения
                            session.messagesToDelete.add(sentMessage.getMessageId()); // Сообщение к удалению
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                            sendMessage(chatId, "Что-то пошло не так: " + e.getMessage() + "\n\nПопробуй еще раз или начни сначала \n/start", true);
                        }
                        break;
                }

        }
// update без message, без callback, но с ответом на голосование
        else if(update.hasPollAnswer()) {

            String pollId = update.getPollAnswer().getPollId(); // id опроса
            Long chatIdPollCreator = getChatIdByPollId(pollId); // id чата создателя опроса
            UserSession sessionPollCreator = sessionMap.get(chatIdPollCreator); // сессия создателя
            List<String> options = new ArrayList<>(); // массив для выбранных вариантов
            for (Integer id : update.getPollAnswer().getOptionIds()) {
                options.add(sessionPollCreator.pollOptions.get(id).getText());
            }
            // сообщаем о новом голосе
            sendMessage(chatIdPollCreator,
                    update.getPollAnswer().getUser().getFirstName()+" выбрал:\n"+options,
                    true);
        }
        else {
            try {
                System.out.println(update);
                System.out.println("chatid - "+chatId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            sendMessage(chatId, "Не могу обработать такое сообщение",false);
        }

        } catch (Exception e) {
            System.out.println("Произошла ошибка: " + e.getMessage());
        }

    }

    private void askWhoAtePizza(Long chatId) {
        UserSession session = sessionMap.get(chatId);

        StringBuilder msgText = new StringBuilder();
        msgText.append("На кого делим ").append(session.itemName);
        if(session.totalPizzas > 1){
            msgText.append(" №").append(session.currentPizza);
        }
        msgText.append("?");

        InlineKeyboardMarkup markup = createInlineKeyboardMarkup(chatId,session.participants); // Создаем клаву с инлайн кнопками
        // создаем объект сообщения
        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(String.valueOf(msgText))
                .replyMarkup(markup)
                .build();
        try {
            Message sentMessage = execute(sendMessage);
            System.out.println(session.firstName+" << " + sentMessage.getText());
            session.askWhoMessageId = sentMessage.getMessageId(); // Записываем id отправленного сообщения
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Что-то пошло не так: " + e.getMessage() + "\n\nПопробуй еще раз или начни сначала \n/start", true);
        }
    }

    private void sendMessage(Long chatId, String text, Boolean deleteFlag) {
        UserSession session = sessionMap.get(chatId);
        try {
            // удаление reply клавиатуры
            ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
            keyboardRemove.setRemoveKeyboard(true);
            // объект сообщения
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.setReplyMarkup(keyboardRemove); // очистка reply клавы
            //message.setReplyMarkup(new ReplyKeyboardRemove(){{setRemoveKeyboard(true);}}); // очистка reply клавы

            Message sentMessage = execute(message);
            System.out.println(session.firstName+" << "+text.replaceAll("[\n]+|\\s+", " ")); // без переносов, пробелов и отступов
            session.sentMessageId = sentMessage.getMessageId(); // ID отправленного сообщения
            if(deleteFlag){
                session.messagesToDelete.add(sentMessage.getMessageId()); // Сообщение к удалению
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void sendMessageWithKeyboard(Long chatId, String text, ReplyKeyboardMarkup replyKeyboard, InlineKeyboardMarkup inlineKeyboard, Boolean deleteFlag) {
        UserSession session = sessionMap.get(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.enableMarkdown(true);
        if (replyKeyboard!=null){
            message.setReplyMarkup(replyKeyboard);
        } else if (inlineKeyboard!=null) {
            message.setReplyMarkup(inlineKeyboard);
        }
        try {
            Message sentMessage = execute(message);
            System.out.println(session.firstName+" << "+text.replaceAll("[\n]+|\\s+", " ")); // без переносов, пробелов и отступов
            session.sentMessageId = sentMessage.getMessageId(); // ID отправленного сообщения
            if(deleteFlag){
                session.messagesToDelete.add(sentMessage.getMessageId()); // Сообщение к удалению
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Что-то пошло не так: " + e.getMessage() + "\n\nПопробуй еще раз или начни сначала \n/start", true);
        }
    }

    private void deleteMessages(Long chatId, List<Integer> messageIds) {
        UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - deleteMessages");
        try {
            // Используем метод deleteMessages
            DeleteMessages deleteMessages =  DeleteMessages.builder()
                    .chatId(chatId)
                    .messageIds(messageIds)
                    .build();
            execute(deleteMessages);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Что-то пошло не так: " + e.getMessage() + "\n\nНачни сначала /start", true);
        }
    }
    private void deleteMessage(Long chatId, Integer messageId) {
        UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - deleteMessage");
        try {
            // Используем метод deleteMessage
            DeleteMessage deleteMessage = DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build();
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Что-то пошло не так: " + e.getMessage() + "\n\nНачни сначала /start", true);
        }
    }
    private InlineKeyboardMarkup createInlineKeyboardMarkup(Long chatId,Map<String, Boolean> options) {
            UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - createInlineKeyboardMarkup");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();// объект встроенной клавиатуры
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>(); // итоговый список списков кнопок для setKeyboard

            // список с кнопками из списка с именами
            List<InlineKeyboardButton> buttons = new ArrayList<>();
            for (String option : options.keySet()) {
                InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                inlineKeyboardButton.setCallbackData(option); // callback кнопки
                if(!options.get(option)) {
                    inlineKeyboardButton.setText(option); // название кнопки
                } else {inlineKeyboardButton.setText(marked+option);}
                buttons.add(inlineKeyboardButton); // обновляем список кнопок
            }

            // распределяем по кнопкам
            int elementsPerRow = 3; // количество кнопок в строке
            int numberOfRows = (int) Math.ceil(session.participants.size()/(float)elementsPerRow); // количество строк с кнопками. float чтобы не было целочисленного деления и было округление в большую сторону
            for (int i = 0; i < numberOfRows; i++) {
                List<InlineKeyboardButton> rowInline = new ArrayList<>();
                for (int j = 0; j < elementsPerRow; j++) {
                    int buttonIndex = i * elementsPerRow + j;
                    if (buttonIndex < buttons.size()) {
                        rowInline.add(buttons.get(buttonIndex));
                    }
                }
                keyboard.add(rowInline);
            }

            markup.setKeyboard(keyboard); // Устанавливаем клавиатуру

            return markup;
    }

    private void updateButtonState(Long chatId, Integer messageId, String callback) {
        UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - updateButtonState");
        // добавляем отметки в список
        if (session.participants.containsKey(callback)) {
            session.participants.put(callback, !session.participants.get(callback)); // переключаем значение
        }
        // Создаем клавиатуру с обновленными кнопками
        InlineKeyboardMarkup markup = createInlineKeyboardMarkup(chatId,session.participants);
        // Подготовка обновления
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId);
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(markup); // Устанавливаем новую разметку
        try {
            execute(editMarkup); // Отправляем обновление
            isUpdateSuccessful = true;
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Что-то пошло не так: " + e.getMessage() + "\n\nПопробуй еще раз или начни сначала \n/start", true);
            isUpdateSuccessful = false;
        }
    }

    private void calculateResults(Long chatId) {

        UserSession session = sessionMap.get(chatId);
        session.pricePerCount =
                session.pizzaPrice /
                        session.participants.values().stream().filter(value -> value).count(); // Расчитываем долю в пицце
        // Складываем в массив участников и долю
        session.participantsWithPriceCurrent = session.participants.entrySet().stream()
                .filter(Map.Entry::getValue) // Фильтруем по значению true
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> session.pricePerCount));

        calculateResultsTotal(chatId); // Добавление текущего к итоговому
        DecimalFormat df = new DecimalFormat("#.00"); //формат округления

        // строка с участниками
        String participants = session.participants.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
        // собираем сообщение
        StringBuilder message = new StringBuilder()
                .append(session.itemName);
        if(session.totalPizzas >1){
            message.append(" № ")
                    .append(session.currentPizza)
                    .append(" из ")
                    .append(session.totalPizzas);
        }
        message.append(" по ")
                .append(df.format(session.pricePerCount))
                .append(" руб.:")
                .append("\n")
                .append(participants);

        sendMessage(chatId, String.valueOf(message), false);

        // Очищаем расчеты по текущему
        session.participantsWithPriceCurrent.clear(); // Сбрасываем список
        session.pricePerCount = 0; // Сбрасываем доли по пицце
    }

    private void calculateResultsTotal(Long chatId) {
        UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - calculateResultsTotal");

        for (Map.Entry<String, Double> entry : session.participantsWithPriceCurrent.entrySet()) {
            String participantName = entry.getKey(); // Имя участника
            Double price = entry.getValue(); // Цена

            // Проверка наличия участника в participantsFinalMap
            if (session.participantsFinalMap.containsKey(participantName)) {
                // Участник найден, проверяем наличие товара
                Map<String, Double> participantItems = session.participantsFinalMap.get(participantName); // Карта товаров участника
                if (participantItems.containsKey(session.itemName)) {
                    // Товар уже есть, обновляем цену
                    participantItems.put(session.itemName, participantItems.get(session.itemName) + price);
                } else {
                    // Товара нет, добавляем новый
                    participantItems.put(session.itemName, price);
                }
            } else {
                // Участника нет, создаём новую запись
                Map<String, Double> newParticipantItems = new HashMap<>();
                newParticipantItems.put(session.itemName, price);
                session.participantsFinalMap.put(participantName, newParticipantItems);
            }
        }
    }

    private void sendResults(Long chatId) {
        UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - sendResults");
        deleteMessages(chatId,session.messagesToDelete);
        //формат округления
        DecimalFormat df = new DecimalFormat("#.00");

        //запись результирующей строки по каждому из участников
        StringBuilder resultStr = new StringBuilder();
        for(Map.Entry<String, Map<String, Double>> itemEntry : session.participantsFinalMap.entrySet()) {
            String name = itemEntry.getKey(); // имя
            Map<String, Double> currentValues = session.participantsFinalMap.get(name); // карта значений для данного имени
            StringBuilder valuesStr = new StringBuilder(); // для накопления значений

            // вычисляем сумму всех значений для каждого имени
            double totalSum = 0;
            for (Double value : currentValues.values()) {
                totalSum += value;
            }
            // собираем resultStr
            resultStr.append("\t\t\t*")
                    .append(name) // имя
                    .append("*: ")
                    .append(df.format(totalSum)); // сумма всех значений
            // собираем суммы за каждую позицию, если их >1
            if(currentValues.size()>1) {
                resultStr.append(" (");
                for (Map.Entry<String, Double> entry : currentValues.entrySet()) {
                    // добавляем разделитель, если это не первое значение
                    if (!valuesStr.isEmpty()) {
                        valuesStr.append(" + ");
                    }
                    valuesStr//.append(df.format(entry.getValue())) // строка с суммами за каждую позицию
                            //.append(" за ")
                            .append(entry.getKey());
                }
                // накопленные значения для каждого имени
                resultStr.append(valuesStr)
                        .append(")");
            } else {
                // позиция одна
                for (String key : currentValues.keySet()) {
                    resultStr.append(" ")
                            .append(key);
                }
            }
            resultStr.append("\n");
        }

            // сумма Всего
        // карта для агрегации всех стоимостей по позициям
        Map<String, Double> aggregatedPrices = new HashMap<>();
        for (Map<String, Double> itemsWithPrices : session.participantsFinalMap.values()) {
            for (Map.Entry<String, Double> entry : itemsWithPrices.entrySet()) {
                String item = entry.getKey();
                Double price = entry.getValue();
                // суммируем стоимости
                aggregatedPrices.put(item, aggregatedPrices.getOrDefault(item, 0.0) + price);
            }
        }
        StringBuilder resultSumTotal = new StringBuilder();
        for (Map.Entry<String, Double> entry : aggregatedPrices.entrySet()) {
            resultSumTotal.append("\t\t\t")
                    .append(entry.getKey())
                    .append(" - ")
                    .append((int)Math.round(entry.getValue()))
                    .append(" руб.\n");
        }

        // создаем инлайн кнопку
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("Добавить позицию из чека")
                .callbackData("additional_item")
                .build();

        // добавляем кнопку в клаву
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(button)))
                .build();

        // создаем сообщение
        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("Итог:\n"+resultStr+"\nВсего: "+ resultSumTotal)
                .replyMarkup(markup)
                .build();
        sendMessage.enableMarkdown(true);
        try {
            Message sentMessage = execute(sendMessage);
            session.resultMessageId = sentMessage.getMessageId();
            System.out.println(session.firstName+" << " + sentMessage.getText());
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Что-то пошло не так: " + e.getMessage() + "\n\nПопробуй еще раз или начни сначала \n/start", true);
        }

    }

    public Long getChatIdByPollId(String pollId) {
        for (Map.Entry<Long, UserSession> entry : sessionMap.entrySet()) {
            UserSession session = entry.getValue();
            System.out.println("entry.getKey(): "+entry.getKey());
            System.out.println("poll id from sessionmap: "+session.sentPollId);
            if (session.sentPollId.equals(pollId)) {
                return entry.getKey(); // Возвращаем chatId
            }
        }
        System.out.println("создатель опроса завершил сессию");
        return null; // chatId не найден
    }
    public Long getChatIdFromUpdate(Update update){
        if (update.hasMessage()) {
            return update.getMessage().getChatId(); // сообщение
        } else if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getMessage().getChatId(); // callback
        } else if (update.hasPollAnswer()) {
            return update.getPollAnswer().getUser().getId(); // ответ на голосование
        }
        return null;
    }
    @Override
    public String getBotUsername() {
        return "[test]";
    }
    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

}