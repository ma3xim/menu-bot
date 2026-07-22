package com.foodmate.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dish_of_the_day")
@Getter
@Setter
public class DishOfTheDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "thread_id")
    private Integer threadId;

    @Column(name = "message_id", nullable = false)
    private Integer messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_by_user_id")
    private User setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    @PrePersist
    void onCreate() {
        setAt = Instant.now();
    }
}
