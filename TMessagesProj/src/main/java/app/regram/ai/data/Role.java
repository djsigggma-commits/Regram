package app.regram.ai.data;

import java.io.Serializable;
import java.util.Objects;

import app.regram.ai.AiConfig;

public class Role implements Comparable<Role>, Serializable {

    private String name;
    private String prompt;
    private long emojiId;
    private boolean suggestion;

    public Role(String name, String prompt) {
        this.name = name;
        this.prompt = prompt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public long getEmojiId() {
        return emojiId;
    }

    public Role setEmojiId(long emojiId) {
        this.emojiId = emojiId;
        return this;
    }

    public boolean isSuggestion() {
        return suggestion;
    }

    public Role setSuggestion(boolean suggestion) {
        this.suggestion = suggestion;
        return this;
    }

    public boolean isSelected() {
        return Objects.equals(AiConfig.getSelectedRole(), name);
    }

    @Override
    public int compareTo(Role other) {
        return name.compareTo(other.getName());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return Objects.equals(name, ((Role) other).name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
