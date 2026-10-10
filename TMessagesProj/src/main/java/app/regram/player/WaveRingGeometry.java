package app.regram.player;

/** Fixed 3-degree samples; trigonometry is computed once, not for every frame. */
final class WaveRingGeometry {
    static final int STEP_DEGREES = 3;
    static final int STEPS = 360 / STEP_DEGREES;
    static final float[] X = new float[STEPS + 1];
    static final float[] Y = new float[STEPS + 1];
    private final float[] waveSin = new float[STEPS + 1];
    private final float[] waveCos = new float[STEPS + 1];

    static {
        for (int i = 0; i <= STEPS; i++) {
            double angle = Math.toRadians(i * STEP_DEGREES - 90);
            X[i] = (float) Math.cos(angle);
            Y[i] = (float) Math.sin(angle);
        }
    }

    WaveRingGeometry(int waves) {
        for (int i = 0; i <= STEPS; i++) {
            double angle = waves * Math.toRadians(i * STEP_DEGREES);
            waveSin[i] = (float) Math.sin(angle);
            waveCos[i] = (float) Math.cos(angle);
        }
    }

    float radiusAt(int i, float radius, float amplitude, float sinPhase, float cosPhase) {
        return radius + amplitude * (waveSin[i] * cosPhase - waveCos[i] * sinPhase);
    }
}
