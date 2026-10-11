"""The collapsed attachment camera tile shows art, while capture stays available."""
from pathlib import Path
import hashlib

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatAttachAlertPhotoLayout.java'
ART = ROOT / 'TMessagesProj/src/main/res/drawable-nodpi/regram_attach_camera_art.jpg'


def test_art_is_the_supplied_image():
    assert hashlib.sha256(ART.read_bytes()).hexdigest() == '8721d590026a80afa36104f87fdf7ddbede5991eafb5ed9472ec1be5c737d516'
    image = pytest.importorskip('PIL.Image')
    with image.open(ART) as art:
        assert art.size == (736, 981)
        assert art.format == 'JPEG'


def test_collapsed_tile_always_draws_art_not_cached_or_live_camera_preview():
    source = SOURCE.read_text()
    decoration = source[source.index('private class CameraViewItemDecoration'):
                        source.index('protected class CameraViewInternal')]
    assert 'private final Drawable placeholderDrawable;' in decoration
    assert 'getDrawable(R.drawable.regram_attach_camera_art)' in decoration
    assert 'builder.add(R.drawable.regram_attach_camera_art)' in decoration
    assert 'placeholderDrawable.draw(c);' in decoration
    assert 'cameraDrawable.draw(c);' in decoration
    assert 'cameraView.draw(c)' not in decoration
    assert 'BitmapFactory.decodeFile' not in decoration
    assert 'R.drawable.icplaceholder' not in decoration
    assert 'Math.max((right - left) / (float) artWidth,' in decoration
    assert 'c.clipRect(left, top, right, bottom);' in decoration
    # Static art no longer schedules an endless live-preview redraw loop.
    draw = decoration[decoration.index('private void draw('):decoration.index('public void updateBitmap()')]
    assert 'gridView.invalidate()' not in draw


def test_camera_opening_and_expanded_preview_are_preserved():
    source = SOURCE.read_text()
    assert 'private void openCameraByClick()' in source
    assert 'openCamera(true);' in source
    assert 'cameraView = new CameraViewInternal(' in source
    camera = source[source.index('protected class CameraViewInternal'):]
    assert 'if (!cameraAnimationInProgress && cameraOpened)' in camera
    assert 'super.dispatchDraw(canvas);' in camera
    assert 'drawInDecoration' not in source
