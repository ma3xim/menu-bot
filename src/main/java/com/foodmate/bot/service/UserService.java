package com.foodmate.bot.service;

import com.foodmate.bot.entity.User;
import com.foodmate.bot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreate(Long telegramId, String displayName) {
        return userRepository.findByTelegramId(telegramId)
                .map(user -> {
                    if (displayName != null && !displayName.equals(user.getDisplayName())) {
                        user.setDisplayName(displayName);
                    }
                    return user;
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setTelegramId(telegramId);
                    user.setDisplayName(displayName);
                    return userRepository.save(user);
                });
    }
}
