package com.foodmate.bot.telegram;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.send.SendVideoNote;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramSender {

    private final TelegramClient telegramClient;
    private final UpdateContext updateContext;

    public void sendText(Long chatId, String text) {
        sendText(chatId, text, null);
    }

    public void sendText(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        sendTextTo(chatId, updateContext.threadId(), text, keyboard);
    }

    public Message sendTextTo(Long chatId, Integer threadId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId)
                .text(text);
        if (threadId != null) {
            builder.messageThreadId(threadId);
        }
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        return execute(builder.build());
    }

    public void pinMessage(Long chatId, Integer messageId) {
        try {
            telegramClient.execute(PinChatMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .disableNotification(false)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to pin message {} in {}: {}", messageId, chatId, e.getMessage());
            throw new IllegalStateException(
                    "Не удалось закрепить сообщение. Дайте боту право «Закреплять сообщения» в группе.", e);
        }
    }

    public void editText(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText.EditMessageTextBuilder builder = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text);
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        try {
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.debug("Edit failed, sending new message: {}", e.getMessage());
            sendText(chatId, text, keyboard);
        }
    }

    public void answerCallback(String callbackQueryId, String text) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(false)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback: {}", e.getMessage());
        }
    }

    public void sendDocument(Long chatId, String filename, String content, String caption) {
        try {
            InputFile file = new InputFile(
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                    filename
            );
            SendDocument.SendDocumentBuilder builder = SendDocument.builder()
                    .chatId(chatId)
                    .document(file)
                    .caption(caption);
            Integer threadId = updateContext.threadId();
            if (threadId != null) {
                builder.messageThreadId(threadId);
            }
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.error("Failed to send document", e);
            sendText(chatId, "Не удалось отправить файл: " + e.getMessage());
        }
    }

    public void sendFileTo(Long chatId, Integer threadId, Path filePath, String caption) {
        try {
            InputFile file = new InputFile(filePath.toFile(), filePath.getFileName().toString());
            SendDocument.SendDocumentBuilder builder = SendDocument.builder()
                    .chatId(chatId)
                    .document(file)
                    .caption(caption);
            if (threadId != null) {
                builder.messageThreadId(threadId);
            }
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.error("Failed to send file {}", filePath, e);
            throw new IllegalStateException("Не удалось отправить файл в Telegram: " + e.getMessage(), e);
        }
    }

    public void sendRecipeVideo(Long chatId, String fileId, String kind) {
        if (!StringUtils.hasText(fileId)) {
            return;
        }
        Integer threadId = updateContext.threadId();
        try {
            String normalized = kind == null ? "VIDEO" : kind.toUpperCase();
            switch (normalized) {
                case "VIDEO_NOTE" -> {
                    SendVideoNote.SendVideoNoteBuilder builder = SendVideoNote.builder()
                            .chatId(chatId)
                            .videoNote(new InputFile(fileId));
                    if (threadId != null) {
                        builder.messageThreadId(threadId);
                    }
                    telegramClient.execute(builder.build());
                }
                case "DOCUMENT" -> {
                    SendDocument.SendDocumentBuilder builder = SendDocument.builder()
                            .chatId(chatId)
                            .document(new InputFile(fileId))
                            .caption("🎬 Видео рецепта");
                    if (threadId != null) {
                        builder.messageThreadId(threadId);
                    }
                    telegramClient.execute(builder.build());
                }
                default -> {
                    SendVideo.SendVideoBuilder builder = SendVideo.builder()
                            .chatId(chatId)
                            .video(new InputFile(fileId))
                            .caption("🎬 Видео рецепта");
                    if (threadId != null) {
                        builder.messageThreadId(threadId);
                    }
                    telegramClient.execute(builder.build());
                }
            }
        } catch (TelegramApiException e) {
            log.warn("Failed to send recipe video: {}", e.getMessage());
            sendText(chatId, "Не удалось отправить видео рецепта (возможно, устарел file_id).");
        }
    }

    private Message execute(SendMessage message) {
        try {
            return telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {} thread={}: {}",
                    message.getChatId(), message.getMessageThreadId(), e.getMessage());
            return null;
        }
    }
}
