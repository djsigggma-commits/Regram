import importlib.util
import os
import re
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import corpus

PY_ALIASES = os.path.join(corpus.PYTHON_ROOT, "extera_utils", "class_aliases.py")
JAVA_ALIASES = os.path.join(corpus.JAVA_ROOT, "app", "regram", "plugins", "ClassAliases.java")
GATE = os.path.join(corpus.JAVA_ROOT, "app", "regram", "plugins", "PluginSinkGate.java")

_PAIR = re.compile(r'\{"([^"]+)",\s*"([^"]+)"\}')


def _java_source():
    if not os.path.isfile(JAVA_ALIASES):
        pytest.skip(f"missing {JAVA_ALIASES}")
    with open(JAVA_ALIASES, encoding="utf-8") as handle:
        return handle.read()


def _block(source, name):
    start = source.index(name + " = {")
    end = source.index("};", start)
    return _PAIR.findall(source[start:end])


@pytest.fixture(scope="module")
def python_aliases():
    if not os.path.isfile(PY_ALIASES):
        pytest.skip(f"missing {PY_ALIASES}")
    spec = importlib.util.spec_from_file_location("regram_aliases_bridge", PY_ALIASES)
    module = importlib.util.module_from_spec(spec)
    sys.modules["regram_aliases_bridge"] = module
    spec.loader.exec_module(module)
    return module


def test_java_exact_table_matches_python(python_aliases):
    java = dict(_block(_java_source(), "EXACT"))
    assert java == dict(python_aliases._EXACT), (
        "таблицы алиасов разошлись: ClassAliases.java и class_aliases.py должны "
        "совпадать, иначе DEX-ядро плагина и python-сторона резолвят имена по-разному")


def test_java_prefix_table_matches_python(python_aliases):
    java = [tuple(pair) for pair in _block(_java_source(), "PREFIXES")]
    assert java == [tuple(pair) for pair in python_aliases._PREFIXES], (
        "порядок префиксов важен: первый подходящий и выигрывает")


def test_every_alias_target_exists_in_our_tree(python_aliases):
    missing = []
    for target in sorted(set(python_aliases._EXACT.values())):
        if target == "org.telegram.messenger.R" or corpus.find_java_source(target):
            continue
        missing.append(target)
    assert not missing, (
        "алиас ведёт в несуществующий класс: " + ", ".join(missing) +
        ("" if corpus.MODULE_ROOTS else "; сабмодули не выкачаны"))


def test_legacy_package_name_still_resolves(python_aliases):
    """Плагины, писавшиеся под прежнее имя пакета, не должны отвалиться."""
    assert python_aliases.LEGACY_ROOT == "app.exteraless"
    for name in ("app.exteraless.plugins.PluginServices",
                 "app.exteraless.appearance.AltSeekbar",
                 "app.exteraless.plugins.Plugin$Holder"):
        assert python_aliases.resolve(name) == name.replace("app.exteraless.", "app.regram.")
    source = _java_source()
    assert '"app.exteraless."' in source and '"app.regram."' in source, (
        "ClassAliases.java не знает про старое имя пакета: python-сторона "
        "подставит его, а DEX-ядро плагина — нет")


def test_classes_in_real_exteragram_packages_exist_there():
    """Класс из настоящего пакета ``com.exteragram.*`` обязан лежать по этому имени.

    Форма ``from com.exteragram.messenger.plugins.ui.components import X`` идёт
    через импорт Chaquopy по настоящему Java-пакету и мимо подстановки имён, так
    что alias-таблица её не спасает: без файла по месту плагин получает
    ``ImportError: cannot import name ... (unknown location)``.
    """
    for name in ("com.exteragram.messenger.plugins.ui.components.PluginEditTextCell",
                 "com.exteragram.messenger.plugins.Plugin",
                 "com.exteragram.messenger.plugins.PluginsController"):
        assert corpus.find_java_source(name), (
            f"{name} исчез из настоящего пакета com.exteragram — плагины, "
            f"импортирующие его по имени эталона, перестанут загружаться")
    # Реализация при этом живёт у нас: shim и наследуемый класс не должны
    # разъезжаться при переименовании пакета.
    assert corpus.find_java_source("app.regram.plugins.ui.components.PluginEditTextCell")
    assert corpus.find_java_source("app.regram.plugins.Plugin")


def test_class_resolution_hook_falls_back_to_aliases():
    if not os.path.isfile(GATE):
        pytest.skip(f"missing {GATE}")
    with open(GATE, encoding="utf-8") as handle:
        source = handle.read()
    assert "ClassAliases.resolve" in source, (
        "подмена имён в Class.forName пропала — плагины с зашитым DEX перестанут "
        "находить наши классы по именам exteraGram")
