import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ReferralBot extends TelegramLongPollingBot {

    private static final String DB_FILE = "referrals.json";
    private static ObjectMapper mapper = new ObjectMapper();
    private static Map<String, Referral> db = new HashMap<>();

    // Load JSON database on startup
    static {
        File file = new File(DB_FILE);
        if (file.exists()) {
            try {
                db = mapper.readValue(file, new TypeReference<Map<String, Referral>>() {});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getBotUsername() {
        return "igl_referral_bot"; // your bot username
    }

    @Override
    public String getBotToken() {
        return "YOUR_BOT_TOKEN"; // replace with your BotFather token
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText().trim();
            String chatKey = String.valueOf(chatId);

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));

            switch (messageText.split(" ")[0]) {
                case "/start":
                    if (!db.containsKey(chatKey)) {
                        String code = generateReferralCode(6);
                        db.put(chatKey, new Referral(chatId, code));
                        saveDB();
                        message.setText("Hi " + update.getMessage().getFrom().getFirstName() +
                                "! 🎉\nYour referral code is: " + code +
                                "\nShare it with your friends!");
                    } else {
                        message.setText("Welcome back! Your referral code: " + db.get(chatKey).code);
                    }
                    break;

                case "/mycode":
                    if (db.containsKey(chatKey)) {
                        Referral r = db.get(chatKey);
                        message.setText("Your referral code: " + r.code +
                                "\nUsed by: " + (r.referee != null ? r.referee : "Nobody yet"));
                    } else {
                        message.setText("You don't have a code yet. Type /start to get one!");
                    }
                    break;

                case "/use":
                    String[] parts = messageText.split(" ");
                    if (parts.length < 2) {
                        message.setText("Usage: /use CODE");
                        break;
                    }
                    String codeUsed = parts[1].toUpperCase();
                    boolean found = false;
                    for (Referral r : db.values()) {
                        if (r.code.equals(codeUsed)) {
                            found = true;
                            if (r.chatId == chatId) {
                                message.setText("You cannot use your own code!");
                            } else if (r.referee != null) {
                                message.setText("This code has already been used.");
                            } else {
                                r.referee = update.getMessage().getFrom().getFirstName();
                                saveDB();
                                message.setText("Referral successful! 🎉");
                            }
                            break;
                        }
                    }
                    if (!found) {
                        message.setText("Invalid referral code.");
                    }
                    break;

                default:
                    message.setText("Unknown command. Try /start, /mycode, or /use CODE");
            }

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    // ----------------------------
    // Helper: Generate referral code
    // ----------------------------
    private String generateReferralCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(rand.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (db.values().stream().anyMatch(r -> r.code.equals(code)));
        return code;
    }

    // ----------------------------
    // Save JSON database
    // ----------------------------
    private void saveDB() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(DB_FILE), db);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------
    // Referral class
    // ----------------------------
    public static class Referral {
        public long chatId;
        public String code;
        public String referee;

        public Referral() {}

        public Referral(long chatId, String code) {
            this.chatId = chatId;
            this.code = code;
            this.referee = null;
        }
    }

    // ----------------------------
    // Main
    // ----------------------------
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new ReferralBot());
            System.out.println("Referral bot is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
