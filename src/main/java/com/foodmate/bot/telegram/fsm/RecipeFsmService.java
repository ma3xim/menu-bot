package com.foodmate.bot.telegram.fsm;

import com.foodmate.bot.dto.IngredientLineDto;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.RecipeIngredient;
import com.foodmate.bot.entity.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RecipeFsmService {

    private final Map<Long, RecipeFsmSession> sessions = new ConcurrentHashMap<>();

    public RecipeFsmSession startAdd(Long telegramId) {
        RecipeFsmSession session = new RecipeFsmSession();
        session.setState(RecipeFsmState.WAIT_NAME);
        sessions.put(telegramId, session);
        return session;
    }

    public RecipeFsmSession startEdit(Long telegramId, Recipe recipe) {
        RecipeFsmSession session = new RecipeFsmSession();
        session.setState(RecipeFsmState.EDIT_HUB);
        session.setEditingRecipeId(recipe.getId());
        session.setName(recipe.getName());
        session.setDescription(recipe.getDescription());
        session.setCookingTimeMinutes(recipe.getCookingTimeMinutes());
        session.setCookingInstructions(recipe.getCookingInstructions());
        session.setVideoFileId(recipe.getVideoFileId());
        session.setVideoFileUniqueId(recipe.getVideoFileUniqueId());
        session.setVideoKind(recipe.getVideoKind());

        List<IngredientLineDto> ingredients = new ArrayList<>();
        if (recipe.getIngredients() != null) {
            for (RecipeIngredient ri : recipe.getIngredients()) {
                String name = ri.getIngredient() != null ? ri.getIngredient().getName() : null;
                if (StringUtils.hasText(name)) {
                    ingredients.add(new IngredientLineDto(name, ri.getAmount(), ri.getUnit()));
                }
            }
        }
        session.setIngredients(ingredients);

        List<String> tags = new ArrayList<>();
        if (recipe.getTags() != null) {
            for (Tag tag : recipe.getTags()) {
                if (StringUtils.hasText(tag.getName())) {
                    tags.add(tag.getName());
                }
            }
        }
        session.setTags(tags);

        sessions.put(telegramId, session);
        return session;
    }

    public Optional<RecipeFsmSession> get(Long telegramId) {
        return Optional.ofNullable(sessions.get(telegramId));
    }

    public void clear(Long telegramId) {
        sessions.remove(telegramId);
    }
}
