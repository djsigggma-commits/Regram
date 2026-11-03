"""Contracts for the exact Actions snapshot; not Android/device validation."""
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'TMessagesProj/src/main'
SHA = '8b7ec40ecada6ab3cfcc24e9f3d8ae262c02de63'
CHAQUOPY_SHA = 'eab341a401913ac50254ff20e9efeebeaae872ef'


def java(path):
    return (MAIN / 'java' / path).read_text()


def test_exact_actions_snapshot_and_native_pins():
    manifest = json.loads((ROOT / 'docs/upstream/base-39628177709.json').read_text())
    assert manifest['upstream_commit'] == SHA
    assert manifest['actions_run'].endswith('/39628177709')
    assert manifest['base_version'] == '13.0.2'
    assert manifest['gitlinks']['TMessagesProj/chaquopy'] == CHAQUOPY_SHA
    # Historical provenance stays immutable; current pins belong to the next snapshot.
    current = json.loads((ROOT / 'docs/upstream/base-41526234112.json').read_text())
    assert current['previous_commit'] == SHA
    assert current['gitlinks']['TMessagesProj/chaquopy'] == CHAQUOPY_SHA
    assert len(manifest['delta']) == 216
    assert manifest['deletions'] == []


def test_qr_login_warning_and_regram_icon_survive_merge():
    assert 'R.string.QrLoginConfirm' in java('app/regram/components/QRCodeSheet.java')
    for locale in ('values', 'values-ar', 'values-es', 'values-pl-rPL', 'values-ru-rRU', 'values-zh-rCN'):
        strings = ET.parse(MAIN / 'res' / locale / 'strings_oe.xml').getroot()
        assert len(strings.findall("string[@name='QrLoginConfirm']")) == 1
    aliases = ET.parse(MAIN / 'res/values/drawables.xml').getroot()
    assert aliases.find("item[@name='ic_foreground_solid']").text == '@drawable/regram_icon_tile'
    assert 'R.drawable.exteraless_icon_tile' not in java('app/regram/drawer/MainMenuHelper.java')


def test_wallet_pause_is_wired_across_lifecycle_and_dispatch():
    for path in ('org/telegram/ui/ActionBar/BaseFragment.java', 'org/telegram/ui/ActionBar/BottomSheet.java'):
        text = java(path)
        assert 'WalletPluginFreeze.enterIfWallet(this)' in text
        assert 'WalletPluginFreeze.leave(this)' in text
    for path in ('PythonPluginsEngine.java', 'PluginServices.java', 'xposed/XposedHooks.java', 'utils/ClassProxyFactory.java'):
        assert 'WalletPluginFreeze.isFrozen()' in java('app/regram/plugins/' + path)
    freeze = java('org/telegram/ui/Wallet/WalletPluginFreeze.java')
    assert 'WeakHashMap' in freeze
    assert 'SystemClock.elapsedRealtime()' in freeze
    runtime = ROOT / 'TMessagesProj/chaquopy/product/runtime/src/main/python/java/chaquopy.pyx'
    if runtime.exists():
        assert 'org.telegram.ui.Wallet.WalletPluginFreeze' in runtime.read_text()
        assert 'org.telegram.ui.Wallet.' in runtime.read_text()  # deny list retained


def test_player_conflicts_keep_both_pause_and_local_performance_guards():
    wave = java('app/regram/player/WavySeekBar.java')
    assert 'if (!frozen && (amplitude > 0f || targetAmp != amplitude)' in wave
    assert '&& isShown() && getWindowVisibility() == VISIBLE)' in wave
    colors = java('app/regram/player/PlayerColors.java').split('public static int noCoverSeed', 1)[1]
    assert colors.index('PlayerColorOverride.currentColor()') < colors.index('theme.isMonet()')
    assert 'Integer monet = MonetHelper.getColorOrNull' in colors
    chat = java('org/telegram/ui/Cells/ChatMessageCell.java')
    assert 'bubbleAyuDeleted() && currentMessageObject.isOutOwnerForLayout()' in chat
    assert 'DeletedReplyQuote.rewrite(' in java('org/telegram/messenger/SendMessagesHelper.java')


def test_background_pending_requests_use_bounded_keepalive():
    native = ROOT / 'TMessagesProj/jni/tgnet'
    assert '#define CONNECTION_BACKGROUND_PENDING_KEEP_TIME 120000' in (native / 'Defines.h').read_text()
    assert 'llabs(now - backgroundPauseTime) < CONNECTION_BACKGROUND_PENDING_KEEP_TIME' in (native / 'ConnectionsManager.cpp').read_text()


def test_chaquopy_paused_proxy_defaults_are_return_type_safe(tmp_path):
    """Run the pinned real Java handler with a stub returning no Python result."""
    handler = ROOT / 'TMessagesProj/chaquopy/product/runtime/src/main/java/com/chaquo/python/PyInvocationHandler.java'
    javac, java_bin = shutil.which('javac'), shutil.which('java')
    if not handler.exists() or not javac or not java_bin:
        pytest.skip('pinned Chaquopy sources and JDK required')
    package = tmp_path / 'com/chaquo/python'
    package.mkdir(parents=True)
    shutil.copyfile(handler, package / handler.name)
    (package / 'PyObject.java').write_text('''package com.chaquo.python;
public class PyObject {
    public static PyObject fromJava(Object proxy) { return new PyObject(); }
    public PyObject callAttrThrows(String name, Object[] args) { return null; }
    public Object toJava(Class<?> type) { throw new AssertionError("null result converted"); }
}
''')
    (tmp_path / 'Check.java').write_text('''import com.chaquo.python.*;
import java.lang.reflect.*;
public class Check {
    interface API {
        boolean b(); char c(); byte by(); short s(); int i();
        long l(); float f(); double d(); Object o(); void v();
    }
    public static void main(String[] args) {
        API p = (API) Proxy.newProxyInstance(Check.class.getClassLoader(),
                new Class<?>[]{API.class}, new PyInvocationHandler(new PyObject()));
        if (p.b() || p.c() != 0 || p.by() != 0 || p.s() != 0 || p.i() != 0
                || p.l() != 0L || p.f() != 0f || p.d() != 0d || p.o() != null) {
            throw new AssertionError("wrong proxy default");
        }
        p.v();
    }
}
''')
    subprocess.run([javac, '-d', str(tmp_path), *map(str, tmp_path.rglob('*.java'))], check=True, capture_output=True, text=True)
    subprocess.run([java_bin, '-cp', str(tmp_path), 'Check'], check=True, capture_output=True, text=True)
