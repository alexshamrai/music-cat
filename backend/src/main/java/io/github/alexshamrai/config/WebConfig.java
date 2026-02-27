package io.github.alexshamrai.config;

import io.github.alexshamrai.domain.Genre;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToGenreConverter());
    }

    private static class StringToGenreConverter implements Converter<String, Genre> {

        @Override
        public Genre convert(String source) {
            return Genre.fromDisplayName(source);
        }
    }
}
