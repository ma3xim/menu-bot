package com.foodmate.bot.service;

import com.foodmate.bot.entity.Tag;
import com.foodmate.bot.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<Tag> findAll() {
        return tagRepository.findAllByOrderByNameAsc();
    }
}
