package app.regram.utils;

/** Allocation-free sine samples at evenly spaced angles. Reset for each short path. */
public final class SineWave {
    private double sin, cos, stepSin, stepCos;

    public void reset(double angle, double step) {
        sin = Math.sin(angle);
        cos = Math.cos(angle);
        stepSin = Math.sin(step);
        stepCos = Math.cos(step);
    }

    public double value() {
        return sin;
    }

    public void advance() {
        double nextSin = sin * stepCos + cos * stepSin;
        cos = cos * stepCos - sin * stepSin;
        sin = nextSin;
    }
}
