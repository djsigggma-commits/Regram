import ast
import importlib.util
import json
import os
import sys
import types

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import baseline
import corpus

PYTHON_ROOT = os.environ.get("EXTERALESS_PYTHON_ROOT", corpus.PYTHON_ROOT)
METADATA_PARSER = os.path.join(PYTHON_ROOT, "extera_utils", "metadata_parser.py")

SYNTAX_BASELINE = "plugin_parse_syntax.json"
METADATA_BASELINE = "plugin_parse_metadata.json"
BASE_CLASS_BASELINE = "plugin_parse_base_class.json"


def _java_imports(source, path):
    roots = set()
    for node in ast.walk(ast.parse(source, filename=path)):
        if isinstance(node, ast.Import):
            names = [alias.name for alias in node.names]
        elif isinstance(node, ast.ImportFrom):
            names = [node.module or ""]
        else:
            continue
        for name in names:
            root = name.split(".")[0]
            if root in corpus.JAVA_ROOTS:
                roots.add(name)
    return sorted(roots)


def _install_stubs(names):
    for name in names:
        parts = name.split(".")
        for index in range(1, len(parts) + 1):
            dotted = ".".join(parts[:index])
            if dotted in sys.modules:
                continue
            module = types.ModuleType(dotted)
            module.__path__ = []
            sys.modules[dotted] = module
            if index > 1:
                setattr(sys.modules[".".join(parts[:index - 1])], parts[index - 1], module)


def _load_module(path, name):
    with open(path, encoding="utf-8") as handle:
        source = handle.read()
    _install_stubs(_java_imports(source, path))
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


@pytest.fixture(scope="session")
def metadata_parser():
    if not os.path.isfile(METADATA_PARSER):
        pytest.skip(f"no metadata parser at {METADATA_PARSER}")
    return _load_module(METADATA_PARSER, "regram_metadata_parser")


@pytest.fixture(scope="session")
def plugins():
    loaded = corpus.load_corpus()
    if not loaded:
        pytest.skip(f"no plugin corpus at {corpus.CORPUS_DIR}")
    return loaded


def _report(new, gone, kind, reasons):
    lines = []
    if new:
        lines.append(f"{len(new)} plugin(s) newly {kind}:")
        for name in new:
            lines.append(f"  {name}: {reasons.get(name, '')}")
    if gone:
        lines.append(
            f"{len(gone)} baselined plugin(s) no longer {kind} "
            f"(refresh the baseline): {', '.join(gone)}")
    return "\n".join(lines)


def test_corpus_files_parse_as_python(plugins):
    reasons = {}
    for plugin in plugins:
        try:
            ast.parse(plugin.source, filename=plugin.name)
        except SyntaxError as error:
            reasons[plugin.name] = f"{type(error).__name__}: {error}"
        except ValueError as error:
            reasons[plugin.name] = f"{type(error).__name__}: {error}"
    new, gone = baseline.compare(SYNTAX_BASELINE, reasons)
    assert not new, _report(new, gone, "unparsable", reasons)


def test_metadata_parser_reads_every_plugin(metadata_parser, plugins):
    required = ("id", "name", "version")
    reasons = {}
    for plugin in plugins:
        try:
            meta = metadata_parser.read_metadata(plugin.path)
        except Exception as error:
            reasons[plugin.name] = f"{type(error).__name__}: {error}"
            continue
        missing = [
            field for field in required
            if not isinstance(meta.get(field), str) or not meta[field].strip()
        ]
        if missing:
            reasons[plugin.name] = "missing fields: " + ", ".join(missing)
    new, gone = baseline.compare(METADATA_BASELINE, reasons)
    assert not new, _report(new, gone, "rejected by the metadata parser", reasons)


def test_metadata_parser_json_contract_never_raises(metadata_parser, plugins):
    for plugin in plugins:
        payload = json.loads(metadata_parser.read_metadata_json(plugin.path))
        assert isinstance(payload.get("ok"), bool), plugin.name
        if payload["ok"]:
            assert payload["meta"]["id"], plugin.name
        else:
            assert payload["error"], plugin.name


def _base_plugin_names(tree):
    names = {"BasePlugin"}
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            for alias in node.names:
                if alias.name == "BasePlugin":
                    names.add(alias.asname or alias.name)
        elif isinstance(node, ast.Assign) and isinstance(node.value, ast.Name) \
                and node.value.id in names:
            for target in node.targets:
                if isinstance(target, ast.Name):
                    names.add(target.id)
    return names


def _declares_base_plugin_subclass(tree):
    names = _base_plugin_names(tree)
    for node in ast.walk(tree):
        if not isinstance(node, ast.ClassDef):
            continue
        for base in node.bases:
            if isinstance(base, ast.Name) and base.id in names:
                return True
            if isinstance(base, ast.Attribute) and base.attr in names:
                return True
    return False


def test_every_plugin_declares_a_base_plugin_subclass(plugins):
    reasons = {}
    for plugin in plugins:
        tree = plugin.tree
        if tree is None:
            continue
        if not _declares_base_plugin_subclass(tree):
            reasons[plugin.name] = "no class derived from BasePlugin"
    new, gone = baseline.compare(BASE_CLASS_BASELINE, reasons)
    assert not new, _report(new, gone, "without a BasePlugin subclass", reasons)
