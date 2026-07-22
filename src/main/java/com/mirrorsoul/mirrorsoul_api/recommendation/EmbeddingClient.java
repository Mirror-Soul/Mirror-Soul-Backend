package com.mirrorsoul.mirrorsoul_api.recommendation;

public interface EmbeddingClient {

    float[] embed(String input);
}
