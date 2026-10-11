package com.mirrorsoul.mirrorsoul_api.quality;

public record TrainingId(Kind kind, long sourceId, Long revision) {
    public enum Kind { VOICE, FACE, PROFILE, RAG }
    public static TrainingId parse(String value) {
        if (value == null || !value.matches("(VOICE|FACE|PROFILE)-[1-9][0-9]{0,18}|RAG-[1-9][0-9]{0,18}-[1-9][0-9]{0,18}"))
            throw QualitySupport.invalid();
        try {
            var parts = value.split("-");
            return new TrainingId(Kind.valueOf(parts[0]), Long.parseLong(parts[1]), parts.length == 3 ? Long.parseLong(parts[2]) : null);
        } catch (RuntimeException e) { throw QualitySupport.invalid(); }
    }
    public static String of(Kind kind, Long id) { return kind + "-" + id; }
    @Override public String toString() { return kind + "-" + sourceId + (revision == null ? "" : "-" + revision); }
}
