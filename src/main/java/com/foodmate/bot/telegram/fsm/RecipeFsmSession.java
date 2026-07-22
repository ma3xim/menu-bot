package com.foodmate.bot.telegram.fsm;

import com.foodmate.bot.dto.IngredientLineDto;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RecipeFsmSession {

    private RecipeFsmState state;
    private Long editingRecipeId;
    private String name;
    private String description;
    private Integer cookingTimeMinutes;
    private List<IngredientLineDto> ingredients = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private String videoFileId;
    private String videoFileUniqueId;
    private String videoKind;
    private Long filterTagId;
}
