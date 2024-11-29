import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


public class Main {
    public static void main(String[] args) throws TelegramApiException {
        // Создаем экземпляр TelegramBotsApi
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        try {
            // Регистрация бота
            botsApi.registerBot(new MyBot());
            System.out.println("Бот запущен");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}

