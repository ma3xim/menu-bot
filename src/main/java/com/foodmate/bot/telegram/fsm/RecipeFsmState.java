package com.foodmate.bot.telegram.fsm;

public enum RecipeFsmState {
    WAIT_NAME,
    WAIT_DESCRIPTION,
    WAIT_TIME,
    WAIT_INGREDIENTS,
    WAIT_INSTRUCTIONS,
    WAIT_TAGS,
    WAIT_VIDEO,
    CONFIRM,
    EDIT_HUB,
    EDIT_FIELD
}
