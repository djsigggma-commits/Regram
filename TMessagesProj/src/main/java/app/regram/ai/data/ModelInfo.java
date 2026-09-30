package app.regram.ai.data;

public class ModelInfo {

    private final String id;
    private final String name;
    private final boolean reasoning;
    private final long context;

    public ModelInfo(String id, String name, boolean reasoning, long context) {
        this.id = id;
        this.name = name;
        this.reasoning = reasoning;
        this.context = context;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isReasoning() {
        return reasoning;
    }

    public long getContext() {
        return context;
    }
}
