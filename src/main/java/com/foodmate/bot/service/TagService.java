package com.foodmate.bot.service;

import com.foodmate.bot.entity.Tag;
import com.foodmate.bot.exception.NotFoundException;
import com.foodmate.bot.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional
    public List<Tag> findAllUsed() {
        tagRepository.deleteUnused();
        return tagRepository.findAllUsedByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Tag requireById(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Тег не найден"));
    }

    @Transactional
    public void deleteUnused() {
        tagRepository.deleteUnused();
    }
}
