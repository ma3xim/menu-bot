package com.foodmate.bot.service;

import com.foodmate.bot.entity.BotSetting;
import com.foodmate.bot.repository.BotSettingRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BotSettingsService {

    public static final String NOTIFY_CHAT_ID = "notify.chat_id";
    public static final String NOTIFY_THREAD_ID = "notify.thread_id";

    private final BotSettingRepository botSettingRepository;

    @Transactional(readOnly = true)
    public Optional<Long> getLong(String key) {
        return botSettingRepository.findById(key)
                .map(BotSetting::getValue)
                .filter(StringUtils::hasText)
                .map(Long::valueOf);
    }

    @Transactional(readOnly = true)
    public Optional<Integer> getInteger(String key) {
        return botSettingRepository.findById(key)
                .map(BotSetting::getValue)
                .filter(StringUtils::hasText)
                .map(Integer::valueOf);
    }

    private void put(String key, String value) {
        BotSetting setting = botSettingRepository.findById(key).orElseGet(BotSetting::new);
        setting.setKey(key);
        setting.setValue(value);
        botSettingRepository.save(setting);
    }

    @Transactional
    public void saveNotifyTarget(Long chatId, Integer threadId) {
        if (chatId != null) {
            put(NOTIFY_CHAT_ID, String.valueOf(chatId));
        }
        if (threadId != null) {
            put(NOTIFY_THREAD_ID, String.valueOf(threadId));
        }
    }
}
