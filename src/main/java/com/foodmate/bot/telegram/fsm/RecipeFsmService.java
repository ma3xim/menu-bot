package com.foodmate.bot.telegram.fsm;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RecipeFsmService {

    private final Map<Long, RecipeFsmSession> sessions = new ConcurrentHashMap<>();

    public RecipeFsmSession startAdd(Long telegramId) {
        RecipeFsmSession session = new RecipeFsmSession();
        session.setState(RecipeFsmState.WAIT_NAME);
        sessions.put(telegramId, session);
        return session;
    }

    public RecipeFsmSession startEdit(Long telegramId, Long recipeId) {
        RecipeFsmSession session = new RecipeFsmSession();
        session.setState(RecipeFsmState.WAIT_NAME);
        session.setEditingRecipeId(recipeId);
        sessions.put(telegramId, session);
        return session;
    }

    public RecipeFsmSession startImport(Long telegramId) {
        RecipeFsmSession session = new RecipeFsmSession();
        session.setState(RecipeFsmState.WAIT_IMPORT_JSON);
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
