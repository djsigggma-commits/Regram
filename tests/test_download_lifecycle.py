"""Source guards complement executable Vosk file/error path tests."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def test_vosk_cancel_interrupts_network_and_cleans_failed_install():
    code = (JAVA / 'app/regram/speech/VoskManager.java').read_text()
    cancel = code.split('public static void cancelDownload()', 1)[1].split('public static void download(', 1)[0]
    assert 'call.cancel()' in cancel
    assert '.readTimeout(30,' in code
    assert 'finally {\n            archive.delete();\n            deleteRecursive(staging);' in code
    assert code.index('new File(staging, READY_MARKER).createNewFile()') < code.index('deleteRecursive(target);')
    assert 'for (DownloadListener listener : new ArrayList<>(listeners))' in code


def test_updater_honors_cancel_after_network_completion():
    code = (JAVA / 'app/regram/updater/GitHubUpdater.java').read_text()
    assert 'if (success && !call.isCanceled())' in code
    assert 'while ((read = input.read(buffer)) != -1)' in code
    assert 'throw new java.io.InterruptedIOException("cancelled")' in code
