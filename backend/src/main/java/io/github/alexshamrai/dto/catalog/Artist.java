package io.github.alexshamrai.dto.catalog;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Artist(String name, List<Album> albums) {}
