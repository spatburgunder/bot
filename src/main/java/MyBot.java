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
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class MyBot extends TelegramLongPollingBot {
    //Long chatId;
    Long bossChatId = 639284651L;
    boolean isUpdateSuccessful; // флаг успешности обновления
    private static final String marked = "\uD83D\uDC49 "; //эмодзи для отметки выбранной кнопки
    private static final String completeButton = "✅ Дальше";
    private static final String guestButton = "\uD83D\uDC64 Гость";
    private static final String[] names = {"Александр А.","Александр В.","Александр П.","Алексей","Андрей","Глеб","Егор","Игорь","Кирилл","Михаил В.","Михаил Г.","Павел","Роман"};
    public enum UserState {
        WAITING_FOR_START,
        START_PROCESSING,
        COUNT_PROCESSING,
        PRICE_PROCESSING,
        PROCESSING_TYPE_CHOOSING,
        GUEST_NAME_PROCESSING,
        ADDITIONAL_ITEM_PRICE_PROCESSING,
        PARTICIPANTS_CHOOSING,
        ADDITIONAL_ITEM_PROCESSING,
        WAITING_FOR_FEEDBACK,
        FEEDBACK_PROCESSING,
        DONATE_REQUEST,
        POLL_PASSING
    }
    private static final Map<Long, UserSession> sessionMap = new HashMap<>();
    private static class UserSession {
        private UserState state = UserState.WAITING_FOR_START; // начальный
        String itemName = "\uD83D\uDCA8"; // начальный
        Boolean itemSharedFlag = true;
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
        Map<String, Integer> participantsCounts = new LinkedHashMap<>();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try{
        Long chatId = getChatIdFromUpdate(update);
        UserSession session = sessionMap.computeIfAbsent(chatId, k -> new UserSession());

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            //запись в сессию
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
                changeStateTo(UserState.WAITING_FOR_START,session);
            }

            if (messageText.equals("/calculate")){
                // удаление сообщений
                if(session.askWhoMessageId != null){session.messagesToDelete.add(session.askWhoMessageId);}
                if(!session.messagesToDelete.isEmpty()){deleteMessages(chatId,session.messagesToDelete);}
                // обновление сессии
                sessionMap.remove(chatId);
                session = sessionMap.computeIfAbsent(chatId, k -> new UserSession());
                session.firstName = update.getMessage().getFrom().getFirstName(); // ? имя только для лога в changeStateTo
                changeStateTo(UserState.START_PROCESSING,session);
            }

            if (messageText.equals("/poll")){
                // удаление сообщений
                if(session.askWhoMessageId != null){session.messagesToDelete.add(session.askWhoMessageId);}
                if(!session.messagesToDelete.isEmpty()){deleteMessages(chatId,session.messagesToDelete);}
                session.totalPizzas = 0;
                changeStateTo(UserState.POLL_PASSING,session);
            }

            if (messageText.equals("/feedback")){
                changeStateTo(UserState.WAITING_FOR_FEEDBACK,session);
            }

            if (messageText.equals("/donate")){
                changeStateTo(UserState.DONATE_REQUEST,session);
            }

            switch (session.state) {
                default:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    sendMessage(chatId, "Не могу обработать сообщение. \nИспользуй кнопки выше или начни сначала /start",true);
                    break;

                case WAITING_FOR_START:
                    sendMessage(chatId,
                            "Доступные команды:\n\n"
                            +"/calculate - начать новый расчет \uD83C\uDF55\n\n"
                            +"/poll - запустить опрос \uD83D\uDCCA\n\n"
                            +"/feedback - оставить обратную связь \uD83D\uDC8C\n\n"
                            //+"/donate - подать на хлеб \uD83D\uDCB8"
                            , false);
                    break;

                case START_PROCESSING:
                    sendMessage(chatId, "Введи количество \"" + session.itemName + "\"", true);
                    changeStateTo(UserState.COUNT_PROCESSING,session);
                    break;

                case COUNT_PROCESSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    if (messageText.matches("\\d+") && Integer.parseInt(messageText) > 0) {
                        session.totalPizzas = Integer.parseInt(messageText);
                        sendMessage(chatId, "Сколько стоит \""+session.itemName+"\"?", true);

                        if (session.totalPizzas==1) {
                            changeStateTo(UserState.ADDITIONAL_ITEM_PRICE_PROCESSING, session);
                        } else {
                            changeStateTo(UserState.PRICE_PROCESSING, session);
                        }
                    } else {
                        sendMessage(chatId, "Введи корректное количество", true);
                    }
                    break;

                case PRICE_PROCESSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщения к удалению
                    if (messageText.matches("\\d+") && Double.parseDouble(messageText) > 0) {
                        session.pizzaPrice = Double.parseDouble(messageText);

                        InlineKeyboardMarkup inlineKeyboard = InlineKeyboardMarkup.builder()
                                .keyboardRow(List.of(
                                        InlineKeyboardButton.builder().text("Выберу участников вручную ").callbackData("partisipantsAsking").build()))
                                .keyboardRow(List.of(
                                        InlineKeyboardButton.builder().text("Создать опрос [beta] \uD83D\uDCCA").callbackData("createPoll").build()))
                                .build();
                        sendMessageWithKeyboard(chatId, "Как будем считать?",null,inlineKeyboard,true);

                        changeStateTo(UserState.PROCESSING_TYPE_CHOOSING,session);

                    } else {
                        sendMessage(chatId, "Введи корректную цену", true);
                    }
                    break;

                case ADDITIONAL_ITEM_PRICE_PROCESSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщения к удалению
                    if (messageText.matches("\\d+") && Double.parseDouble(messageText) > 0) {
                        session.pizzaPrice = Double.parseDouble(messageText); // запись цены
                        askWhoAtePizza(chatId); // запрос участников
                        changeStateTo(UserState.PARTICIPANTS_CHOOSING,session);
                    } else {
                        sendMessage(chatId, "Введи корректную цену", true);
                    }
                    break;

                case ADDITIONAL_ITEM_PROCESSING:
                    deleteMessages(chatId, session.messagesToDelete);
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    session.itemName = messageText;
                    sendMessage(chatId, "Сколько стоит \""+session.itemName+"\"?", true);
                    changeStateTo(UserState.ADDITIONAL_ITEM_PRICE_PROCESSING,session);
                    break;

                case GUEST_NAME_PROCESSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    // временные мапы, чтоб добавить в них гостя и заменить настоящие
                    LinkedHashMap<String, Boolean> newParticipants = new LinkedHashMap<>();
                    LinkedHashMap<String, Integer> newParticipantsCounts = new LinkedHashMap<>();
                    for (Map.Entry<String, Boolean> entry : session.participants.entrySet()) {
                        String key = entry.getKey();
                        Boolean value = entry.getValue();
                        if (key.equals(guestButton)) {
                            newParticipants.put(messageText, false); // вставляем новую пару перед guestButton
                            newParticipantsCounts.put(messageText, 0); // вставляем новую пару перед guestButton
                        }
                        newParticipants.put(key, value);
                        newParticipantsCounts.put(key, session.participantsCounts.get(key));
                    }
                    session.participants = newParticipants; // заменяем с гостем
                    session.participantsCounts = newParticipantsCounts; // заменяем с гостем

                    updateButtonState(chatId, session.askWhoMessageId, messageText); //обновляем клаву кнопкой с гостем
                    if(isUpdateSuccessful) {
                        sendMessage(chatId, "Добавлена кнопка " + messageText + " ☝\uFE0F", true);
                    }
                    changeStateTo(UserState.PARTICIPANTS_CHOOSING,session);
                    break;

                case WAITING_FOR_FEEDBACK:
                    sendMessage(chatId,"Напиши, что передать боссу",false);
                    changeStateTo(UserState.FEEDBACK_PROCESSING,session);
                    break;

                case FEEDBACK_PROCESSING:
                    sendMessage(chatId,"Спасибо, отзыв отправлен",false);
                    changeStateTo(UserState.WAITING_FOR_START,session);
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
                            .text("USDT")
                            .callbackData("USDT")
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

                    changeStateTo(UserState.WAITING_FOR_START,session);
                    sendMessage(bossChatId,
                            "❗"+session.firstName+" "+session.lastName+" ("+session.userName+") went to donate",
                            false);
                    // Удаляем сессию после окончания
                    sessionMap.remove(chatId);
                    break;

                case POLL_PASSING:
                    sendPoll(chatId);
                    changeStateTo(UserState.WAITING_FOR_START,session);
                    break;
            }
        }


// update без сообщения, но с колбэком (нажали инлайн кнопку)
        else if (update.hasCallbackQuery()) {
            String callback = update.getCallbackQuery().getData();
            System.out.println(session.firstName+" > "+callback);

                switch (callback) {
// Обработка других кнопок в зависимости от state
                    default:
                        switch (session.state) {
                            case PARTICIPANTS_CHOOSING:
                            // Перерисовка клавы со сменой статуса кнопки
                            updateButtonState(chatId, session.askWhoMessageId, callback);
                            break;
                            case ADDITIONAL_ITEM_PROCESSING:
                                deleteMessages(chatId, session.messagesToDelete);
                                String callbackFirstPart = callback.split(":")[0];
                                String callbackSecondPart = callback.split(":")[1];
                                if (callbackFirstPart.equals("shared")) {session.itemSharedFlag = true;}
                                if (callbackFirstPart.equals("byPiece")) {session.itemSharedFlag = false;}
                                if (callback.equals("byPiece:other")) {
                                    sendMessage(chatId, "Что добавим поштучно?", true);
                                    break;
                                }
                                if (callback.equals("shared:other")) {
                                    sendMessage(chatId, "Что поделим поровну?", true);
                                    break;
                                }
                                session.itemName = callbackSecondPart; // название лежит после разделителя :
                                sendMessage(chatId, "Сколько стоит \""+session.itemName+"\"?", true);
                                changeStateTo(UserState.ADDITIONAL_ITEM_PRICE_PROCESSING,session);
                                break;
                        }
                        break;
// Выбрана кнопка ГОСТЬ
                    case guestButton:
                        sendMessage(chatId, "Как зовут гостя?", true);
                        changeStateTo(UserState.GUEST_NAME_PROCESSING,session);
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
                            changeStateTo(UserState.WAITING_FOR_START,session);
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
                        /*
                        ReplyKeyboardMarkup replyKeyboard = ReplyKeyboardMarkup.builder()
                                .selective(true).resizeKeyboard(true).oneTimeKeyboard(true)
                                .inputFieldPlaceholder("Напиши или выбери")
                                .keyboard(Collections.singletonList(
                                        new KeyboardRow() {{
                                            add("\uD83E\uDED6"); //чайник
                                            add("☕\uFE0F"); //кофе
                                            add("\uD83C\uDF55"); //пицца
                                            add("\uD83D\uDE95"); //такси
                                        }}
                                )).build();
                        */
                        InlineKeyboardMarkup inlineKeyboard = InlineKeyboardMarkup.builder()
                                .keyboardRow(List.of(
                                        InlineKeyboardButton.builder().text("\uD83C\uDF55").callbackData("shared:\uD83C\uDF55").build(),
                                        InlineKeyboardButton.builder().text("\uD83E\uDED6").callbackData("shared:\uD83E\uDED6").build(),
                                        InlineKeyboardButton.builder().text("\uD83D\uDE95").callbackData("shared:\uD83D\uDE95").build(),
                                        InlineKeyboardButton.builder().text("еще \uD83D\uDCA8").callbackData("shared:\uD83D\uDCA8").build(),
                                        InlineKeyboardButton.builder().text("другое").callbackData("shared:other").build()))
                                .build();
                        sendMessageWithKeyboard(chatId,"А) Делим также поровну:", null, inlineKeyboard,true);

                        InlineKeyboardMarkup inlineKeyboard2 = InlineKeyboardMarkup.builder()
                                .keyboardRow(List.of(
                                        InlineKeyboardButton.builder().text("\uD83C\uDF7A").callbackData("byPiece:\uD83C\uDF7A").build(),
                                        InlineKeyboardButton.builder().text("☕\uFE0F").callbackData("byPiece:☕\uFE0F").build(),
                                        InlineKeyboardButton.builder().text("\uD83C\uDF54").callbackData("byPiece:\uD83C\uDF54").build(),
                                        InlineKeyboardButton.builder().text("\uD83C\uDF2F").callbackData("byPiece:\uD83C\uDF2F").build(),
                                        InlineKeyboardButton.builder().text("другое").callbackData("byPiece:other").build()))
                                .build();
                        sendMessageWithKeyboard(chatId,"Б) Добавляем поштучно:", null, inlineKeyboard2,true);

                        session.currentPizza=1; // сброс указателя для новой позиции
                        session.totalPizzas = 1; // всегда 1 шт
                        deleteMessage(chatId,session.resultMessageId);
                        // сброс меток участников
                        session.participants.replaceAll((k, v) -> false); // false для всех участников в карте
                        // мапа для счетчика каждого участника
                        for (String key : session.participants.keySet()) {
                            session.participantsCounts.put(key, 0); // инициализация счетчика для всех ключей
                        }
                        changeStateTo(UserState.ADDITIONAL_ITEM_PROCESSING,session);
                        break;
// Выбрана кнопка
                    case "USDT":
                        SendMessage message = SendMessage
                                .builder()
                                .chatId(chatId)
                                .text("tap to copy:\n\n`UQDHek98PSWxFCENGWoGTJ-vcWHreMnhu-UxeeIuiAfnUfiK`")
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
// Выбрана кнопка ручного рассчета
                    case "partisipantsAsking":
                        deleteMessages(chatId, session.messagesToDelete);
                        askWhoAtePizza(chatId); // Первый запрос участников
                        changeStateTo(UserState.PARTICIPANTS_CHOOSING,session);
                        break;
// Выбрана кнопка рассчета через опрос
                    case "createPoll":
                        sendPoll(chatId);
                        changeStateTo(UserState.WAITING_FOR_START,session);
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
        if (session.itemSharedFlag) {
            msgText.append("На кого делим ").append(session.itemName);
            if (session.totalPizzas > 1) {
                msgText.append(" №").append(session.currentPizza);
            }
            msgText.append("?");
        } else {
            msgText.append("Кому добавляем ").append(session.itemName).append("?");
        }

        if (session.participants.isEmpty()) {// Создаем LinkedHashMap для кнопок инлайн клавиатуры
            for (String name : names) {
                session.participants.put(name, false);
            }
            // Дополнительно для гостя и перехода
            session.participants.put(guestButton, false);
            session.participants.put(completeButton, false);
        }
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
    private void sendPoll(Long chatId) {
        UserSession session = sessionMap.get(chatId);
        List<String> options = null;
        List<String> opts = List.of(
                "1\uFE0F⃣ Первый",
                "2\uFE0F⃣ Второй",
                "3\uFE0F⃣ Третий",
                "4\uFE0F⃣ Четвертый",
                "5\uFE0F⃣ Пятый",
                "6\uFE0F⃣ Шестой",
                "7\uFE0F⃣ Седьмой",
                "8\uFE0F⃣ Восьмой",
                "9\uFE0F⃣ Девятый",
                "На меня не рассчитывать");
        if (session.totalPizzas==0) {
            options = opts;
        } else if (session.totalPizzas<10){
            options = new ArrayList<>(opts.subList(0, session.totalPizzas)); // независимая копия подсписка opts.subList
            options.add("На меня не рассчитывать");
        } else {
            sendMessage(chatId,"Опрос можно сделать только до 10 штук",false);
        }

        SendPoll poll = SendPoll
                .builder()
                .chatId(chatId)
                .question("На меня рассчитывать кальян:")
                .options(options)
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
                            "Ты будешь получать уведомления по нему, пока не отправишь новую команду (или пока я не зависну)",
                    null,
                    inlineKeyboard,
                    true);
        } catch (Exception e) {e.printStackTrace();}
    }
    private void deleteMessages(Long chatId, List<Integer> messageIds) {
        UserSession session = sessionMap.get(chatId);
        //System.out.println(session.firstName+" - deleteMessages");
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
        //System.out.println(session.firstName+" - deleteMessage");
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
        //System.out.println(session.firstName+" - createInlineKeyboardMarkup");

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

    private InlineKeyboardMarkup createInlineKeyboardMarkupWithCounts(Long chatId,Map<String, Integer> options) {
        UserSession session = sessionMap.get(chatId);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();// объект встроенной клавиатуры
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>(); // итоговый список списков кнопок для setKeyboard

        // список с кнопками из списка с именами
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (String option : options.keySet()) {
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            inlineKeyboardButton.setCallbackData(option); // callback кнопки
            if (options.get(option) == 0) {
                inlineKeyboardButton.setText(option); // название кнопки без счетчика
            } else {
                inlineKeyboardButton.setText(option + " (" + options.get(option) + ")"); // название кнопки со счетчиком
            }
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
        //System.out.println(session.firstName+" - updateButtonState");
        // добавляем отметки в список
        if (session.participants.containsKey(callback)) {
            session.participants.put(callback, !session.participants.get(callback)); // переключаем значение в выбранном имени
        }

        if (session.participantsCounts.containsKey(callback)) {
            session.participantsCounts.put(callback, session.participantsCounts.getOrDefault(callback, 0) + 1); // обновляем счетчик при выборе имени
        }
        // Создаем клавиатуру с обновленными кнопками
        InlineKeyboardMarkup markup;
        if (!session.itemSharedFlag) {
             markup = createInlineKeyboardMarkupWithCounts(chatId, session.participantsCounts); // кнопки с отметками количества
        } else {
             markup = createInlineKeyboardMarkup(chatId, session.participants); // кнопки с отметками выбора
        }
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
        if (session.itemSharedFlag) {
            session.pricePerCount =
                    session.pizzaPrice /
                            session.participants.values().stream().filter(value -> value).count(); // Расчитываем долю в пицце
            // Складываем в массив участников и долю
            session.participantsWithPriceCurrent = session.participants.entrySet().stream()
                    .filter(Map.Entry::getValue) // Фильтруем по значению true
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> session.pricePerCount));
        } else { // рассчеты с поштучным добавлением
            session.pricePerCount = session.pizzaPrice;
            session.participantsWithPriceCurrent = session.participantsCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0) // Фильтруем по счетчику > 0
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> session.pizzaPrice*entry.getValue()));
        }


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
        //System.out.println(session.firstName+" - calculateResultsTotal");

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
        //System.out.println(session.firstName+" - sendResults");
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
                .text("Итог:\n"+resultStr+"\nВсего:\n"+ resultSumTotal)
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

    private void changeStateTo(UserState newState, UserSession session){
        session.state = newState;
        System.out.println(session.firstName+" >>> ["+newState+"]");
    }
    public Long getChatIdByPollId(String pollId) {
        for (Map.Entry<Long, UserSession> entry : sessionMap.entrySet()) {
            UserSession session = entry.getValue();
            if (session.sentPollId.equals(pollId)) {
                return entry.getKey(); // Возвращаем chatId
            }
        }
        System.out.println("poll id не найден ни в одной сессии");
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