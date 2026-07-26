package com.mirrorsoul.mirrorsoul_api.recommendation;

public enum EmbeddingType {
    JOB("job_embedding", "job_source_hash"),
    PROFILE("profile_embedding", "profile_source_hash"),
    CLONE_SUMMARY("clone_summary_embedding", "clone_summary_source_hash"),
    CONVERSATION("conversation_embedding", "conversation_source_hash"),
    INTERVIEW("interview_embedding", "interview_source_hash");

    private final String embeddingColumn;
    private final String sourceHashColumn;

    EmbeddingType(String embeddingColumn, String sourceHashColumn) {
        this.embeddingColumn = embeddingColumn;
        this.sourceHashColumn = sourceHashColumn;
    }

    String embeddingColumn() {
        return embeddingColumn;
    }

    String sourceHashColumn() {
        return sourceHashColumn;
    }
}
