package app.regram.ai.network;

public interface GenerationCallback {

    void onChunk(String chunk);

    void onResponse(String response);

    void onError(int code, String message);

    default void onThinking() {
    }

    default void onReasoning(String chunk) {
    }
}
