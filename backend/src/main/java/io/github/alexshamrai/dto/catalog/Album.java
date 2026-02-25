package io.github.alexshamrai.dto.catalog;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Album(String title, Integer year, List<String> songs) {}
