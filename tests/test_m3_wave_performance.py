"""Wavy progress must avoid per-point arc trigonometry without changing shape."""
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_wavy_arc_recurrence_matches_direct_sine_cosine():
    source = (ROOT / 'TMessagesProj/src/main/java/app/regram/appearance/M3CircularProgress.java').read_text()
    assert 'angleCos * stepCos - angleSin * stepSin' in source
    assert 'angleSin * stepCos + angleCos * stepSin' in source
    assert 'Math.cos(rad)' not in source and 'Math.sin(rad)' not in source
    assert 'private final Path path = new Path()' in source
    for start, sweep, steps in ((45, 360, 180), (270, -240, 120), (3, 10, 8)):
        delta = math.radians(sweep / steps)
        step_cos, step_sin = math.cos(delta), math.sin(delta)
        angle_cos, angle_sin = math.cos(math.radians(start)), math.sin(math.radians(start))
        for i in range(steps + 1):
            rad = math.radians(start + sweep * (i / steps))
            assert abs(angle_cos - math.cos(rad)) < 1e-12
            assert abs(angle_sin - math.sin(rad)) < 1e-12
            angle_cos, angle_sin = (angle_cos * step_cos - angle_sin * step_sin,
                                    angle_sin * step_cos + angle_cos * step_sin)
