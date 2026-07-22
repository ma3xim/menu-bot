package com.foodmate.bot.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationPropertiesBinding
public class CommaSeparatedLongListConverter implements Converter<String, List<Long>> {

    @Override
    public List<Long> convert(String source) {
        if (!StringUtils.hasText(source)) {
            return List.of();
        }
        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }
}
