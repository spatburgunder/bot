import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class MyBot extends TelegramLongPollingBot {
    Long chatId;
    Long bossChatId = 639284651L;
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
        FEEDBACK_PROCESSING
    }
    private static class UserSession {
        private UserState state = UserState.WAITING_FOR_START;
        String firstName;
        String lastName;
        String userName;
        Long userId;
        int totalPizzas;
        double pizzaPrice;
        double pricePerCount;
        int currentPizza = 1;
        Map<String, Double> participantsWithPrice = new HashMap<>();
        Map<String, Double> participantsWithPriceTotal = new HashMap<>();
        Integer askWhoMessageId = null;
        Integer sentMessageId = null;
        List<Integer> messagesToDelete = new ArrayList<>(); // Список сообщений к удалению
        Map<String, Boolean> participants = new LinkedHashMap<>();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            UserSession session = sessionMap.computeIfAbsent(chatId, k -> new UserSession());
            session.firstName = update.getMessage().getFrom().getFirstName();
            System.out.println(session.firstName+" >> "+messageText);

            if (messageText.equals("/start")){
                // удаление сообщений
                if(session.askWhoMessageId != null){session.messagesToDelete.add(session.askWhoMessageId);}
                if(!session.messagesToDelete.isEmpty()){deleteMessages(chatId,session.messagesToDelete);}
                // обновление сессии
                sessionMap.remove(chatId);
                session = sessionMap.computeIfAbsent(chatId, k -> new UserSession());
                session.firstName = update.getMessage().getFrom().getFirstName();
                session.state = UserState.START_PROCESSING;
            }

            if (messageText.equals("/feedback")){
                session.lastName = update.getMessage().getFrom().getLastName();
                    if(session.lastName==null){session.lastName="";}
                session.userName = update.getMessage().getFrom().getUserName();
                    if(session.userName!=null){session.userName = "@"+session.userName;}
                session.userId = update.getMessage().getFrom().getId();
                session.state = UserState.WAITING_FOR_FEEDBACK;
            }

            switch (session.state) {
                case WAITING_FOR_START:
                    sendMessage(chatId,
                            "Доступные команды:\n\n"
                            +"/start - начать новый расчет \uD83C\uDF55\n\n"
                            +"/feedback - оставить обратную связь \uD83D\uDC8C\n\n", false);
                    break;

                case START_PROCESSING:
                    sendMessage(chatId, "Сколько было пицц?", true);
                    session.state = UserState.COUNT_PROCESSING;
                    break;

                case COUNT_PROCESSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    if (messageText.matches("\\d+") && Integer.parseInt(messageText) > 0) {
                        session.totalPizzas = Integer.parseInt(messageText);
                        sendMessage(chatId, "Сколько стоит одна пицца?", true);
                        session.state = UserState.PRICE_PROCESSING;
                    } else {
                        sendMessage(chatId, "Введите корректное количество", true);
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
                        sendMessage(chatId, "Введите корректную цену", true);
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
                    updateButtonState(chatId, session.askWhoMessageId, messageText); //обновляем клаву
                    sendMessage(chatId,"Добавлена кнопка "+messageText+" ☝\uFE0F",true);
                    session.state = UserState.PARTICIPANTS_CHOOSING;
                    break;

                case PARTICIPANTS_CHOOSING:
                    session.messagesToDelete.add(update.getMessage().getMessageId()); // Сообщение к удалению
                    sendMessage(chatId, "Используй кнопки выше или начни заново - /start", true);
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
                                    +"Отзыв от пользователя "
                                    +session.firstName+" "
                                    +session.lastName+" ("
                                    +session.userName+"), id "
                                    +session.userId+":\n"
                                    +messageText,false);
                    // Удаляем сессию после окончания
                    sessionMap.remove(chatId);
                    break;
            }
        }


// update без сообщения, но с колбэком (нажали инлайн кнопку)
        else if (update.hasCallbackQuery()){
            chatId = update.getCallbackQuery().getMessage().getChatId();
            String callback = update.getCallbackQuery().getData();
            UserSession session = sessionMap.computeIfAbsent(chatId, k -> new UserSession());
            System.out.println(session.firstName+" > "+callback);

            //state выбор участников
            if(session.state == UserState.PARTICIPANTS_CHOOSING) {
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
                            // Удаляем сессию после окончания
                            sessionMap.remove(chatId);
                        }
                        // Засчитаны не все, переход к след пицце
                        else {
                            calculateResults(chatId); // Расчет
                            // Очищаем расчеты по текущему
                            session.participantsWithPrice.clear(); // Сбрасываем список
                            session.pricePerCount = 0; // Сбрасываем доли по пицце
                            // Убираем сообщения по текущему
                            session.messagesToDelete.add(session.askWhoMessageId);
                            deleteMessages(chatId, session.messagesToDelete);
                            // Переходим к след. пицце
                            session.currentPizza++;
                            askWhoAtePizza(chatId);
                        }
                        break;
                }
            } else {sendMessage(chatId, "На этом шаге не получится обработать кнопку.",true);}
        }
// update message и callback пустые
        else {
            sendMessage(chatId, "Не могу обработать такое сообщение",false);
        }
    }

    private void askWhoAtePizza(Long chatId) {
        UserSession session = sessionMap.get(chatId);

        try {
            InlineKeyboardMarkup markup = createInlineKeyboardMarkup(session.participants); // Создаем клаву с инлайн кнопками
            // создаем объект сообщения
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText("Кто ел пиццу №" + (session.currentPizza) + "?"); // Сообщение
            sendMessage.setReplyMarkup(markup); // инлайн клавиатура
            Message sentMessage = execute(sendMessage);
            System.out.println(session.firstName+" << " + sentMessage.getText());
            session.askWhoMessageId = sentMessage.getMessageId(); // Записываем id отправленного сообщения
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(Long chatId, String text, Boolean deleteFlag) {
        UserSession session = sessionMap.get(chatId);
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
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

    private void deleteMessages(Long chatId, List<Integer> messageIds) {
        UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - deleteMessages");
        try {
            // Используем метод deleteMessages
            DeleteMessages deleteMessages = new DeleteMessages();
            deleteMessages.setChatId(String.valueOf(chatId));
            deleteMessages.setMessageIds(messageIds);
            execute(deleteMessages);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup createInlineKeyboardMarkup(Map<String, Boolean> options) {
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
        InlineKeyboardMarkup markup = createInlineKeyboardMarkup(session.participants);
        // Подготовка обновления
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId);
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(markup); // Устанавливаем новую разметку
        try {
            execute(editMarkup); // Отправляем обновление
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void calculateResults(Long chatId) {

        UserSession session = sessionMap.get(chatId);
        session.pricePerCount =
                session.pizzaPrice /
                        session.participants.values().stream().filter(value -> value).count(); // Расчитываем долю в пицце
        // Складываем в массив участников и долю
        session.participantsWithPrice = session.participants.entrySet().stream()
                .filter(Map.Entry::getValue) // Фильтруем по значению true
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> session.pricePerCount));

        calculateResultsTotal(chatId); // Добавление текущего к итоговому
        DecimalFormat df = new DecimalFormat("#.00"); //формат округления
        sendMessage(chatId,
                "Пицца № " +(session.currentPizza)+" из "+(session.totalPizzas)+" по " +
                        df.format(session.pricePerCount) +" руб.:\n"+
                        String.join(", ", session.participants.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList()),
                false);
    }

    private void calculateResultsTotal(Long chatId) {
        UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - calculateResultsTotal");
        // Добавляем к предыдущим расчетам
        for (Map.Entry<String, Double> entry : session.participantsWithPrice.entrySet()) {
            String key = entry.getKey();
            if (session.participantsWithPriceTotal.containsKey(key)) {
                session.participantsWithPriceTotal.put(key, session.participantsWithPriceTotal.get(key) + entry.getValue());
            } else {
                session.participantsWithPriceTotal.put(key, entry.getValue());
            }
        }
    }

    private void sendResults(Long chatId) {

        UserSession session = sessionMap.get(chatId);
        System.out.println(session.firstName+" - sendResults");
        DecimalFormat df = new DecimalFormat("#.00"); //формат округления

        StringBuilder resultStr = new StringBuilder(); //запись результата в строку
        for (Map.Entry<String, Double> entry : session.participantsWithPriceTotal.entrySet()) {
            resultStr.append("   ").append(entry.getKey()).append(": ").append(df.format(entry.getValue())).append("\n");
        }

        double sumTotal = 0;
        for (Double value : session.participantsWithPriceTotal.values()) {
            sumTotal += value;
        }

        String result = "Итог:\n"+resultStr+"\nВсего: "+ (int)Math.round(sumTotal) +" руб."; // итоговое сообщение
        sendMessage(chatId, result, false);
    }

    @Override
    public String getBotUsername() {
        return "[test]";
    }
    @Override
    public String getBotToken() {
        return "7150754579:AAFcyPkCEVTtLmZskv0gFyifa34jD7HL6Tw";
    }

}