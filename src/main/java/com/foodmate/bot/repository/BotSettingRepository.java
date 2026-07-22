package com.foodmate.bot.repository;

import com.foodmate.bot.entity.BotSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotSettingRepository extends JpaRepository<BotSetting, String> {
}
