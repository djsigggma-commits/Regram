package app.regram.ai.network;

import java.util.Locale;

public final class ReasoningFilter {

    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private boolean inReasoning;
    private boolean reasoningSignal;

    public String consumeReasoning() {
        if (reasoning.length() == 0) {
            return null;
        }
        String text = reasoning.toString();
        reasoning.setLength(0);
        return text;
    }

    public boolean consumeReasoningSignal() {
        boolean signal = reasoningSignal;
        reasoningSignal = false;
        return signal;
    }

    public String filter(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        pending.append(chunk);
        StringBuilder out = new StringBuilder();
        while (pending.length() > 0) {
            if (inReasoning) {
                int close = indexOfIgnoreCase(CLOSE);
                if (close < 0) {
                    int keep = tailLength(CLOSE);
                    reasoning.append(pending, 0, pending.length() - keep);
                    pending.delete(0, pending.length() - keep);
                    break;
                }
                reasoning.append(pending, 0, close);
                pending.delete(0, close + CLOSE.length());
                inReasoning = false;
                continue;
            }
            int open = indexOfIgnoreCase(OPEN);
            if (open < 0) {
                int keep = tailLength(OPEN);
                out.append(pending, 0, pending.length() - keep);
                pending.delete(0, pending.length() - keep);
                break;
            }
            out.append(pending, 0, open);
            pending.delete(0, open + OPEN.length());
            inReasoning = true;
            reasoningSignal = true;
        }
        return out.toString();
    }

    public String flush() {
        String rest = inReasoning ? "" : pending.toString();
        pending.setLength(0);
        inReasoning = false;
        return rest;
    }

    private int indexOfIgnoreCase(String tag) {
        return pending.toString().toLowerCase(Locale.ROOT).indexOf(tag);
    }

    private int tailLength(String tag) {
        int max = Math.min(tag.length() - 1, pending.length());
        for (int size = max; size > 0; size--) {
            if (tag.regionMatches(true, 0, pending.substring(pending.length() - size), 0, size)) {
                return size;
            }
        }
        return 0;
    }
}
