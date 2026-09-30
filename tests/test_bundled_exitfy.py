"""Guard the explicit exitFy 4.2 exception: never auto-run or overwrite it."""
import ast
import base64
import hashlib
import json
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "TMessagesProj/src/main"
PAYLOAD = MAIN / "assets/regram/exitFy_v2.py"
EXPECTED = "c073c4c6ac6915c36675d7f1eab6cc6e8e8d9e36af0d7fbc352968039c1438a6"


def test_exact_original_plugin_pinned_and_parseable():
    data = PAYLOAD.read_bytes()
    assert len(data) == 418356
    assert hashlib.sha256(data).hexdigest() == EXPECTED
    tree = ast.parse(data.decode('utf-8'))
    metadata = {node.targets[0].id: node.value.value for node in tree.body
                if isinstance(node, ast.Assign) and len(node.targets) == 1
                and isinstance(node.targets[0], ast.Name)
                and isinstance(node.value, ast.Constant)}
    assert metadata['__id__'] == 'exitFy_v2'
    assert metadata['__version__'] == '4.2'
    assert any(isinstance(node, ast.ClassDef) and node.name == 'ExitFyPlugin' for node in tree.body)
    assert b'# __DEX_BEGIN__' in data and b'# __NATIVE_BEGIN__' in data


def test_embedded_runtime_format_without_executing_it():
    text = PAYLOAD.read_text(encoding='utf-8')

    def payload(name):
        block = text.split('# __' + name + '_BEGIN__', 1)[1].split('# __' + name + '_END__', 1)[0]
        encoded = ''.join(line.strip()[1:].strip() for line in block.splitlines()
                          if line.strip().startswith('#'))
        return zlib.decompress(base64.b64decode(encoded))

    assert payload('DEX')[:4] == b'dex\n'
    native = json.loads(payload('NATIVE'))['libs']['arm64-v8a']
    lib = base64.b64decode(native['data'])
    assert lib[:4] == b'\x7fELF' and int.from_bytes(lib[18:20], 'little') == 183
    assert len(lib) == native['size']
    assert hashlib.sha256(lib).hexdigest() == native['sha256']


def test_install_requires_explicit_consent_and_keeps_user_copy():
    bridge = (MAIN / 'java/app/regram/ui/BundledExitFy.java').read_text()
    ui = (MAIN / 'java/app/regram/ui/ExitFyLiteActivity.java').read_text()
    assert EXPECTED in bridge
    assert 'getPlugin(ID) != null' in bridge
    assert 'new File(controller.getPluginsDir(), ID + ".py").exists()' in bridge
    assert 'if (isInstalled(controller))' in bridge
    assert 'installPlugin(staged, false' in bridge
    assert 'Build.VERSION.SDK_INT >= 29' in bridge
    assert '"arm64-v8a".equals(Build.SUPPORTED_ABIS[0])' in bridge
    assert 'setPositiveButton(getString(R.string.RegramExitFyInstall)' in ui
    assert 'PluginTrustLevel.setLevel(BundledExitFy.ID, PluginTrustLevel.TRUSTED)' in ui
    assert 'controller.setPluginEnabled(BundledExitFy.ID, true)' in ui
    assert 'controller.setEngineEnabled(true)' in ui
