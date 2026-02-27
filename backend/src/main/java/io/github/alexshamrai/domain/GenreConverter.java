package io.github.alexshamrai.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GenreConverter implements AttributeConverter<Genre, String> {

    @Override
    public String convertToDatabaseColumn(Genre genre) {
        return genre == null ? null : genre.getDisplayName();
    }

    @Override
    public Genre convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Genre.fromDisplayName(dbData);
    }
}
