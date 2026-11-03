package app.regram.input;

/** Android-independent S Pen protocol state. All times are monotonic event times. */
public final class SPenState {
    public static final int BUTTONS = 2 | 32;
    public static final int DOWN = 0, UP = 1, MOVE = 2, CANCEL = 3;
    public static final int HOVER_MOVE = 7, HOVER_ENTER = 9, HOVER_EXIT = 10;
    public static final int BUTTON_PRESS = 11, BUTTON_RELEASE = 12;

    public static int normalize(int action) {
        return action >= 211 && action <= 214 ? action - 211 : action;
    }

    public static final class DoubleTap {
        private String key;
        private long time;
        private float x, y;

        public boolean down(String nextKey, long nextTime, float nextX, float nextY) {
            boolean match = nextKey != null && nextKey.equals(key)
                    && nextTime > time && nextTime - time < 400
                    && Math.abs(nextX - x) < 100 && Math.abs(nextY - y) < 100;
            if (match) {
                reset();
            } else {
                key = nextKey;
                time = nextTime;
                x = nextX;
                y = nextY;
            }
            return match;
        }

        public void reset() { key = null; time = 0; }
    }

    public static final class Hover {
        private boolean pressed, armed, touching;
        private long windowEpoch;

        public void windowEpoch(long epoch) {
            if (windowEpoch != epoch) reset();
            windowEpoch = epoch;
        }

        public void contact(int action, int buttons) {
            action = normalize(action);
            if (action == DOWN || action == MOVE || action == UP || action == CANCEL) {
                touching = action == DOWN || action == MOVE;
                armed = false;
                pressed = (buttons & BUTTONS) != 0;
            }
        }

        public boolean step(int action, int buttons, int actionButton) {
            if ((action == BUTTON_PRESS || action == BUTTON_RELEASE)
                    && actionButton != 0 && (actionButton & BUTTONS) == 0) return false;
            boolean next = (buttons & BUTTONS) != 0;
            if (action == BUTTON_PRESS) next = true;
            if (action == BUTTON_RELEASE) next = false;
            if (action == HOVER_ENTER || action == HOVER_EXIT || action == CANCEL) {
                armed = false;
                pressed = next;
                if (action == HOVER_ENTER) touching = false;
                return false;
            }
            if (action != HOVER_MOVE && action != BUTTON_PRESS && action != BUTTON_RELEASE) return false;
            if (touching) {
                pressed = next;
                armed = false;
                return false;
            }
            if (next && !pressed) armed = true;
            boolean click = !next && pressed && armed;
            if (!next) armed = false;
            pressed = next;
            return click;
        }

        public void reset() { pressed = armed = touching = false; }
    }

    private SPenState() {}
}
