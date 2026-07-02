package io.github.alexshamrai.service;

/**
 * Tag-name validation shared by TagService and the artist/album setTags flows.
 * Tag lists are stored comma-separated in Google Sheets ("rock, classic"), so a comma
 * inside a tag name would silently split it into multiple tags on the next pull.
 */
final class TagNames {

    private TagNames() {}

    static String requireValid(String name) {
        if (name.contains(",")) {
            throw new IllegalArgumentException(
                    "Tag name must not contain a comma (tags are stored comma-separated in Google Sheets): '"
                            + name + "'");
        }
        return name;
    }
}
