import sys
import types

import pytest

from test_sdk_nx_port import loader, sdk


@pytest.fixture
def audit(monkeypatch):
    records = []
    module = types.ModuleType('extera_utils.audit_gate')
    module.note_denied_class = lambda pid, name, perm: records.append((pid, name, perm))
    monkeypatch.setitem(sys.modules, 'extera_utils.audit_gate', module)
    return records


def owner_gate(loader, monkeypatch, owner='test_plugin', perms=(), unsafe=False,
               engine=False):
    monkeypatch.setattr(loader, 'plugin_frame_owner', lambda: owner)
    monkeypatch.setattr(loader, 'unsafe_mode', lambda: unsafe)
    monkeypatch.setattr(loader, '_engine_lookup', lambda: engine)
    monkeypatch.setattr(loader, 'has_permission',
                        lambda perm, pid=None: perm in perms)
    return loader._reflection_gate


def test_ordinary_targets_allowed_without_frame_walk(loader, monkeypatch):
    walked = []
    monkeypatch.setattr(loader, 'plugin_frame_owner', lambda: walked.append(1) or 'pid')
    gate = loader._reflection_gate
    for target in ["org.telegram.messenger.MessagesController",
                   "org.telegram.ui.ActionBar.ActionBarLayout",
                   "android.view.View",
                   "java.lang.String",
                   "kotlin.jvm.internal.Intrinsics",
                   "com.android.volley.Request"]:
        assert gate("Class.forName", target, None) is True
        assert gate("Field.set", target, "anyField") is True
    assert walked == []


def test_interior_targets_denied_for_plugin_frames(loader, monkeypatch, audit):
    gate = owner_gate(loader, monkeypatch)
    for target in ["app.regram.plugins.PluginRuntime",
                   "app.regram.plugins.PluginGrantStore",
                   "app.regram.plugins.PluginPermissions",
                   "app.regram.plugins.PluginTrustLevel"]:
        for op in ["Class.forName", "ClassLoader.loadClass", "Method.invoke",
                   "Field.get", "Field.set", "AccessibleObject.setAccessible",
                   "Constructor.newInstance", "Class.newInstance", "cast",
                   "Proxy.newProxyInstance", "MethodHandles.Lookup", "Unsafe"]:
            assert gate(op, target, "member") is False
    assert audit == []


def test_published_plugin_surface_stays_reflectable(loader, monkeypatch, audit):
    gate = owner_gate(loader, monkeypatch)
    for target in ["app.regram.plugins.PluginsController",
                   "app.regram.plugins.ui.PluginSettingsActivity",
                   "app.regram.plugins.models.SwitchSetting",
                   "com.exteragram.messenger.plugins.PluginsController",
                   "com.exteragram.messenger.plugins.ui.PluginSettingsActivity"]:
        for op in ["Class.forName", "Method.invoke", "Field.get",
                   "AccessibleObject.setAccessible"]:
            assert gate(op, target, "member") is True
    assert audit == []


def test_interior_targets_allowed_for_engine_frames(loader, monkeypatch):
    gate = owner_gate(loader, monkeypatch, owner=None)
    assert gate("Class.forName", "app.regram.plugins.PluginRuntime", None) is True
    assert gate("Method.invoke", "app.regram.plugins.PluginSinkGate", "hook") is True


def test_interior_denied_via_alias_and_signatures(loader, monkeypatch):
    gate = owner_gate(loader, monkeypatch)
    assert gate("Class.forName", "com.exteragram.messenger.plugins.PluginRuntime",
                None) is False
    assert gate("Class.forName", "[Lapp/regram/plugins/PluginRuntime;", None) is False
    assert gate("ClassLoader.loadClass", "app/regram/plugins/PluginRuntime",
                None) is False


def test_alias_resolution_keeps_plain_targets_plain(loader, monkeypatch):
    gate = owner_gate(loader, monkeypatch)
    assert gate("Class.forName", "com.exteragram.messenger.ExteraConfig", None) is True
    assert gate("Class.forName", "com.exteragram.messenger.utils.SomeUtil", None) is True
    assert gate("Class.forName", "[Ljava/lang/String;", None) is True


def test_unsafe_classes_require_native(loader, monkeypatch, audit):
    gate = owner_gate(loader, monkeypatch, perms=())
    assert gate("Unsafe", "sun.misc.Unsafe", "allocateInstance") is False
    assert gate("Unsafe", "jdk.internal.misc.Unsafe", "getObject") is False
    assert audit == [('test_plugin', 'sun.misc.Unsafe', 'native'),
                     ('test_plugin', 'jdk.internal.misc.Unsafe', 'native')]
    gate = owner_gate(loader, monkeypatch, perms={'native'})
    assert gate("Unsafe", "sun.misc.Unsafe", "allocateInstance") is True


def test_permission_classes_follow_java_rules(loader, monkeypatch, audit):
    gate = owner_gate(loader, monkeypatch, perms=())
    assert gate("Class.forName", "org.telegram.messenger.MessagesStorage", None) is False
    assert gate("Class.forName", "org.telegram.messenger.SendMessagesHelper", None) is False
    assert gate("Class.forName", "dalvik.system.DexClassLoader", None) is False
    assert audit == [('test_plugin', 'org.telegram.messenger.MessagesStorage',
                      'messages.read'),
                     ('test_plugin', 'org.telegram.messenger.SendMessagesHelper',
                      'messages.send'),
                     ('test_plugin', 'dalvik.system.DexClassLoader', 'hooks')]
    gate = owner_gate(loader, monkeypatch, perms={'messages.read'})
    assert gate("Class.forName", "org.telegram.messenger.MessagesStorage", None) is True
    assert gate("Class.forName", "org.telegram.messenger.SendMessagesHelper", None) is False
    gate = owner_gate(loader, monkeypatch, perms={'messages.read', 'messages.send'})
    assert gate("Class.forName", "org.telegram.tgnet.ConnectionsManager", None) is True


def test_proxy_interface_tuples(loader, monkeypatch):
    gate = owner_gate(loader, monkeypatch)
    assert gate("Proxy.newProxyInstance",
                ("com.chaquo.python.DynamicProxy", "java.lang.Runnable"), None) is True
    assert gate("Proxy.getProxyClass", ("org.telegram.tgnet.RequestDelegate",),
                None) is True
    assert gate("Proxy.newProxyInstance",
                ("com.chaquo.python.DynamicProxy",
                 "app.regram.plugins.PluginRuntime"), None) is False
    assert gate("Proxy.getProxyClass", (), None) is True


def test_unsafe_mode_and_engine_lookup_allow_everything(loader, monkeypatch):
    gate = owner_gate(loader, monkeypatch, unsafe=True, perms=())
    assert gate("Class.forName", "app.regram.plugins.PluginRuntime", None) is True
    assert gate("Unsafe", "sun.misc.Unsafe", "allocateInstance") is True
    gate = owner_gate(loader, monkeypatch, engine=True, perms=())
    assert gate("Method.invoke", "app.regram.plugins.PluginRuntime", "run") is True


def test_non_string_targets_allowed(loader, monkeypatch):
    gate = owner_gate(loader, monkeypatch)
    assert gate("Class.forName", None, None) is True


def test_verdict_cache_reuses_classification(loader, monkeypatch):
    owner_gate(loader, monkeypatch)
    cache = loader._REFLECTION_VERDICT_CACHE
    cache.clear()
    gate = loader._reflection_gate
    assert gate("Class.forName", "org.telegram.messenger.MessagesStorage", None) is False
    assert 'org.telegram.messenger.MessagesStorage' in cache
    assert cache['org.telegram.messenger.MessagesStorage'] == \
        ('perm', 'messages.read')
    assert gate("Class.forName", "app.regram.plugins.PluginRuntime", None) is False
    assert cache['app.regram.plugins.PluginRuntime'] == 'deny'


def test_install_reflection_gate_installs_policy(loader, monkeypatch):
    installed = []
    chaquopy = types.ModuleType('java.chaquopy')
    chaquopy.set_reflection_policy = installed.append
    java = types.ModuleType('java')
    java.chaquopy = chaquopy
    monkeypatch.setitem(sys.modules, 'java', java)
    monkeypatch.setitem(sys.modules, 'java.chaquopy', chaquopy)
    loader._install_reflection_gate()
    assert installed == [loader._reflection_gate]
    loader._install_reflection_gate()
    assert installed == [loader._reflection_gate, loader._reflection_gate]


def test_install_reflection_gate_tolerates_missing_api(loader, monkeypatch, capsys):
    monkeypatch.delitem(sys.modules, 'java', raising=False)
    monkeypatch.delitem(sys.modules, 'java.chaquopy', raising=False)
    loader._install_reflection_gate()
    assert capsys.readouterr().err == ''


def test_install_reflection_gate_reports_set_failure(loader, monkeypatch, capsys):
    chaquopy = types.ModuleType('java.chaquopy')
    def set_reflection_policy(policy):
        raise RuntimeError("bridge rejected the policy")
    chaquopy.set_reflection_policy = set_reflection_policy
    java = types.ModuleType('java')
    java.chaquopy = chaquopy
    monkeypatch.setitem(sys.modules, 'java', java)
    monkeypatch.setitem(sys.modules, 'java.chaquopy', chaquopy)
    loader._install_reflection_gate()
    assert 'reflection gate install failed' in capsys.readouterr().err
