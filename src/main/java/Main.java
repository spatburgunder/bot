import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import spark.Service;


public class Main {
    public static void main(String[] args) throws TelegramApiException, InterruptedException {
        // Создаем экземпляр TelegramBotsApi
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        try {
            // Регистрация бота
            botsApi.registerBot(new MyBot());
            System.out.println("Бот запущен");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        // Настройка healthcheck
        Service httpService = Service.ignite();
        httpService.port(8080); // Порт для healthcheck
        httpService.get("/health", (req, res) -> "OK");

        // Ждем завершения работы бота
        while (true) {
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}

