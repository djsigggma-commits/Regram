import types

from test_sdk_nx_port import sdk, load_module


def test_context_private_directory_belongs_only_to_its_plugin(sdk, monkeypatch, tmp_path):
    files = load_module(monkeypatch, 'file_utils')
    opened = []
    context = types.SimpleNamespace(getDir=lambda name, mode: (
        opened.append(name), types.SimpleNamespace(getAbsolutePath=lambda: str(tmp_path / ('app_' + name))))[1])
    monkeypatch.setattr(files, '_context', lambda: context)
    monkeypatch.setattr(files, 'get_plugins_dir', lambda: str(tmp_path / 'plugins'))
    monkeypatch.setattr(files, 'get_cache_dir', lambda: str(tmp_path / 'cache'))
    monkeypatch.setattr(files, '_own_files', lambda plugin: set())
    assert files._is_own_path('native_sample', tmp_path / 'app_native_sample/bridge/libsample.so')
    assert not files._is_own_path('other', tmp_path / 'app_native_sample/bridge/libsample.so')
    assert not files._is_own_path('native_sample', tmp_path / 'app_native_sample_other/file')
    assert not files._is_own_path('native_sample', tmp_path / 'app_native_sample/../other/file')
    files._own_roots('../escape')
    assert '../escape' not in opened