"""Bundled Custom Profile must preserve the original server-facing core and show credit."""
import ast
import base64
import hashlib
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'TMessagesProj/src/main'
PAYLOAD = MAIN / 'assets/regram/custom_profile.py'
SHA = '466b66b99475f12845828eb9a7c48fd3c21269933fc8354b5e458be580c5314f'
DEX_SHA = '47d4f4d2ef2006b97d6762babc59aed239465aeeabbd1ee4c68090f2c1466206'


def test_original_dex_and_server_unchanged():
    content = PAYLOAD.read_bytes()
    assert len(content) == 1634835
    assert hashlib.sha256(content).hexdigest() == SHA
    tree = ast.parse(content.decode('utf-8'))
    constants = {node.targets[0].id: node.value.value for node in tree.body
                 if isinstance(node, ast.Assign) and len(node.targets) == 1
                 and isinstance(node.targets[0], ast.Name)
                 and isinstance(node.value, ast.Constant)}
    assert constants['__id__'] == 'custom_profile'
    assert constants['__version__'] == '1.9'
    assert constants['__author__'].lower() == '@roflplugins'
    assert constants['_DEX_SHA'] == DEX_SHA
    dex = zlib.decompress(base64.b64decode(constants['_DEX_B64']))
    assert dex[:4] == b'dex\n'
    assert hashlib.sha256(dex).hexdigest() == DEX_SHA
    assert b'https://customprofile.pixivdl.net:23344/cpb' in dex


def test_author_visible_in_feature_and_plugin_settings():
    plugin = PAYLOAD.read_text(encoding='utf-8')
    feature = (MAIN / 'java/app/regram/ui/CustomProfileActivity.java').read_text()
    assert plugin.count('Создатель / Creator: @roflplugins') == 2  # including core-load failure
    assert '"@roflplugins"' in feature
    assert '"https://t.me/roflplugins"' in feature
    # Bundled payload remains intact, but its extra-features entry was removed
    # at the user's request. Existing installations remain manageable in Plugins.
    assert not (MAIN / 'java/app/regram/ui/RegramSettingsActivity.java').exists()


def test_install_only_after_consent_with_pinned_asset():
    bridge = (MAIN / 'java/app/regram/ui/BundledCustomProfile.java').read_text()
    feature = (MAIN / 'java/app/regram/ui/CustomProfileActivity.java').read_text()
    assert SHA in bridge and 'private static final int SIZE = 1634835;' in bridge
    assert 'if (isInstalled(controller))' in bridge
    assert 'installPlugin(staged, false' in bridge
    assert 'setPositiveButton(getString(R.string.RegramCustomProfileInstall)' in feature
    assert 'PluginTrustLevel.setLevel(BundledCustomProfile.ID, PluginTrustLevel.TRUSTED)' in feature
    assert 'controller.setPluginEnabled(BundledCustomProfile.ID, true)' in feature
