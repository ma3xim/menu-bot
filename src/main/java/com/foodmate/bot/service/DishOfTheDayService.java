package com.foodmate.bot.service;

import com.foodmate.bot.entity.DishOfTheDay;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.User;
import com.foodmate.bot.exception.BotBusinessException;
import com.foodmate.bot.repository.DishOfTheDayRepository;
import com.foodmate.bot.telegram.TelegramSender;
import com.foodmate.bot.util.RecipeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@RequiredArgsConstructor
public class DishOfTheDayService {

    private final DishOfTheDayRepository dishOfTheDayRepository;
    private final RecipeService recipeService;
    private final GroupNotifyService groupNotifyService;
    private final TelegramSender telegramSender;

    @Transactional(readOnly = true)
    public Optional<DishOfTheDay> findLatest() {
        return dishOfTheDayRepository.findFirstByOrderBySetAtDesc();
    }

    @Transactional
    public DishOfTheDay setDishOfTheDay(Long recipeId, User user) {
        Recipe recipe = recipeService.getDetailed(recipeId);
        GroupNotifyService.Target target = groupNotifyService.resolveTarget()
                .orElseThrow(() -> new BotBusinessException(
                        "Не знаю группу для закрепления. Напишите боту в нужной теме группы один раз, затем повторите."));

        String text = "📌 Блюдо дня\n\n" + RecipeFormatter.formatCard(recipe, false)
                + "\n\nВыбрал(а): " + (user.getDisplayName() != null ? user.getDisplayName() : "участник");

        Message sent = telegramSender.sendTextTo(target.chatId(), target.threadId(), text, null);
        if (sent == null || sent.getMessageId() == null) {
            throw new BotBusinessException("Не удалось отправить сообщение в группу (проверьте права бота).");
        }

        try {
            telegramSender.pinMessage(target.chatId(), sent.getMessageId());
        } catch (IllegalStateException e) {
            throw new BotBusinessException(e.getMessage());
        }
        DishOfTheDay entry = new DishOfTheDay();
        entry.setRecipe(recipe);
        entry.setChatId(target.chatId());
        entry.setThreadId(target.threadId());
        entry.setMessageId(sent.getMessageId());
        entry.setSetBy(user);
        return dishOfTheDayRepository.save(entry);
    }
}
