"""Source/merge contract checks; these do not validate Android runtime behavior."""
import importlib.util
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
MAIN=ROOT/'TMessagesProj/src/main'


def test_version_and_package_identity_are_separate():
    props=dict(line.split('=',1) for line in (ROOT/'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
    assert props['APP_VERSION_NAME']=='13.1.1'
    assert props['APP_VERSION_CODE']=='7237'
    assert props['APP_PACKAGE']=='app.regram.android'
    assert props['REGRAM_VERSION']=='1.4.8.8'  # Client version, independent of upstream 13.1.1.
    assert 'public static final int LAYER = 232;' in (MAIN/'java/org/telegram/tgnet/TLRPC.java').read_text()


def test_new_native_bridges_and_pinned_dependencies():
    restore=(ROOT/'tools/restore_submodules.sh').read_text()
    for sha in ('1398c1648f52666b3249111ec3c5b531946e41e4',
                '5c65e47cb8d68727c20d89382d0bc51292486b9a',
                '4cbcd3078e6ae10d05124379623a1bf03fcb9350',
                '31a5d6fbbef8136e4a2b3adaf7b95e2ccaa7436d',
                'eab341a401913ac50254ff20e9efeebeaae872ef',
                '31f1b542f88e7b4be9a01e749920d857535fc715'):
        assert sha in restore
    cmake=(ROOT/'TMessagesProj/jni/CMakeLists.txt').read_text()
    assert 'wallet_engine_jni' in cmake and 'prisma/bridge/PrismaJni.cpp' in cmake
    calls=(ROOT/'TMessagesProj/jni/voip/CMakeLists.txt').read_text()
    assert 'tgcalls/tgcalls/Instance.cpp' in calls
    assert 'voip/tgcalls/Instance.cpp' not in calls
    # Upstream restored the old FQCN as a compatibility shim, not the old engine.
    shim = (MAIN/'java/org/telegram/messenger/CodeHighlighting.java').read_text()
    assert 'extends org.telegram.utils.code.highlight.CodeHighlighting' in shim
    assert 'native ' not in shim
    assert (MAIN/'java/org/telegram/utils/code/highlight/CodeHighlighting.java').is_file()
    assert 'com.neovisionaries:nv-websocket-client:2.14' in (ROOT/'TMessagesProj/build.gradle').read_text()


def test_wallet_security_is_not_dropped_during_namespace_merge():
    hooks=(MAIN/'java/app/regram/plugins/xposed/XposedHooks.java').read_text()
    assert 'org.telegram.ui.Wallet.' in hooks
    assert 'java.security.KeyStore' in hooks
    assert hooks.count('if (deniedTarget(')==3
    loader=(MAIN/'python/extera_utils/plugin_loader.py').read_text()
    assert 'get_reflection_policy() is not _reflection_gate' in loader
    assert 'seal_reflection_policy()' in loader
    # The separately pinned Chaquopy runtime must retain its own wallet deny list.
    runtime=ROOT/'TMessagesProj/chaquopy/product/runtime/src/main/python/java/chaquopy.pyx'
    if runtime.exists():
        assert 'org.telegram.ui.Wallet.' in runtime.read_text()
        assert 'def seal_reflection_policy()' in runtime.read_text()


def test_both_sides_of_chat_and_activity_lifecycle_merge_survive():
    chat=(MAIN/'java/org/telegram/ui/ChatActivity.java').read_text()
    assert 'private boolean handlePenTouch(' in chat
    assert 'public ChatActionCell findWalletSendLanding(' in chat
    launch=(MAIN/'java/org/telegram/ui/LaunchActivity.java').read_text()
    for text in ('TermuxBridge.getInstance().detach(this)',
                 'NativeDocViewer.closeFor(this)',
                 'GramWalletStorageAuth.onActivityDestroyed(this)'):
        assert text in launch
    assert not (MAIN/'java/app/exteraless').exists()
    assert not (MAIN/'kotlin/app/exteraless').exists()


def test_merge_tool_keeps_namespace_binary_and_three_way_changes(tmp_path):
    spec=importlib.util.spec_from_file_location('merge1302',ROOT/'tools/merge_base_1302.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    assert module.normalize(b'app.exteraless.X')==b'app.regram.X'
    assert module.normalize(b'\0app.exteraless.X')==b'\0app.exteraless.X'
    old,new,local,stage=[tmp_path/n for n in ('old','new','local','stage')]
    rel='TMessagesProj/src/main/java/app/exteraless/Example.java'
    for root,body in ((old,'package app.exteraless;\nbase\n\nunchanged\n'),
                      (new,'package app.exteraless;\nupstream\n\nunchanged\n')):
        path=root/rel;path.parent.mkdir(parents=True);path.write_text(body)
    dest=local/rel.replace('app/exteraless','app/regram')
    dest.parent.mkdir(parents=True);dest.write_text('package app.regram;\nbase\n\nlocal change\n')
    subprocess.run([sys.executable,str(ROOT/'tools/merge_base_1302.py'),
                    '--old',str(old),'--new',str(new),'--local',str(local),
                    '--stage',str(stage)],check=True,capture_output=True,text=True)
    assert (stage/dest.relative_to(local)).read_text()=='package app.regram;\nupstream\n\nlocal change\n'
    assert 'upstream' not in dest.read_text()  # staging never modifies checkout
