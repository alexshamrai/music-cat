package io.github.alexshamrai.domain;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Genre {

    PROGRESSIVE_ROCK("Progressive Rock"),
    BLUES("Blues"),
    INSTRUMENTAL_GUITAR("Instrumental Guitar"),
    HARD_ROCK_AND_METAL("Hard Rock & Metal"),
    JAZZ_AND_FUNK("Jazz & Funk"),
    POP_AND_ROCK("Pop & Rock"),
    SOUNDTRACKS_AND_MUSICALS("Soundtracks & Musicals"),
    CLASSICAL("Classical Music");

    private final String displayName;

    private static final Map<String, Genre> BY_DISPLAY_NAME =
            Stream.of(values()).collect(Collectors.toMap(Genre::getDisplayName, Function.identity()));

    Genre(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static Genre fromDisplayName(String displayName) {
        Genre genre = BY_DISPLAY_NAME.get(displayName);
        if (genre == null) {
            throw new IllegalArgumentException(
                    "Unknown genre: '" + displayName + "'. Valid genres: " +
                            String.join(", ", BY_DISPLAY_NAME.keySet()));
        }
        return genre;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
