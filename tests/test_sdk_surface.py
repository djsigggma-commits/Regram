import ast
import functools
import os
import re
import sys

import pytest

import baseline
import corpus

PIP_INSTALLED_ROOTS = frozenset({
    "bs4", "lxml", "packaging", "PIL", "requests", "yaml",
    "certifi", "chardet", "charset_normalizer", "idna", "soupsieve",
    "urllib3", "pip", "pkg_resources", "setuptools", "wheel",
}) | ({"debugpy"} if os.environ.get("REGRAM_PY_DEBUGGER") == "1" else set())

STDLIB_ROOTS = frozenset(sys.stdlib_module_names) | {"__future__"}

REQUIREMENT_SPLIT = re.compile(r"[<>=!~\[;,\s]")

BASE_PLUGIN_REQUIRED_NAMES = (
    "BasePlugin",
    "HookResult",
    "HookStrategy",
    "MethodHook",
    "MenuItemData",
    "MenuItemType",
    "PluginsController",
    "MethodReplacement",
    "AppEvent",
)

MODULES_BASELINE = "sdk_modules.json"
NAMES_BASELINE = "sdk_names.json"

pytestmark = pytest.mark.skipif(
    not os.path.isdir(corpus.CORPUS_DIR),
    reason=f"plugin corpus not found at {corpus.CORPUS_DIR}")


def _module_surface(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        tree = ast.parse(fh.read(), filename=path)
    names = set()
    declared = None
    for node in tree.body:
        if isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            names.add(node.name)
        elif isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name):
                    if target.id == "__all__":
                        try:
                            value = ast.literal_eval(node.value)
                        except (ValueError, SyntaxError):
                            value = None
                        if isinstance(value, (list, tuple, set)):
                            declared = {
                                item for item in value if isinstance(item, str)}
                    names.add(target.id)
        elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name):
            names.add(node.target.id)
        elif isinstance(node, (ast.Import, ast.ImportFrom)):
            for alias in node.names:
                if alias.name == "*":
                    continue
                names.add(alias.asname or alias.name.split(".")[0])
    if declared is not None:
        return frozenset(declared)
    return frozenset(name for name in names if not name.startswith("_"))


@functools.lru_cache(maxsize=1)
def sdk_surface():
    surface = {}
    for dirpath, dirnames, filenames in os.walk(corpus.PYTHON_ROOT):
        dirnames[:] = [d for d in dirnames if d != "__pycache__"]
        rel = os.path.relpath(dirpath, corpus.PYTHON_ROOT)
        package = "" if rel == "." else rel.replace(os.sep, ".")
        for filename in filenames:
            if not filename.endswith(".py"):
                continue
            stem = filename[:-3]
            if stem == "__init__":
                module = package
            elif package:
                module = f"{package}.{stem}"
            else:
                module = stem
            if not module:
                continue
            surface[module] = _module_surface(os.path.join(dirpath, filename))
    for module in sorted(surface):
        if "." not in module:
            continue
        parent, leaf = module.rsplit(".", 1)
        if parent in surface:
            surface[parent] = surface[parent] | {leaf}
    return surface


def _top_level_constant(tree, name):
    for node in tree.body:
        if not isinstance(node, ast.Assign):
            continue
        if not any(isinstance(t, ast.Name) and t.id == name for t in node.targets):
            continue
        try:
            return ast.literal_eval(node.value)
        except (ValueError, SyntaxError):
            return None
    return None


def _requirement_roots(tree):
    declared = _top_level_constant(tree, "__requirements__")
    if isinstance(declared, str):
        declared = declared.split()
    if not isinstance(declared, (list, tuple)):
        return frozenset()
    roots = set()
    for item in declared:
        if not isinstance(item, str):
            continue
        head = REQUIREMENT_SPLIT.split(item.strip())[0]
        if head:
            roots.add(head.lower().replace("-", "_"))
    return frozenset(roots)


@functools.lru_cache(maxsize=1)
def corpus_plugin_ids():
    ids = set()
    for plugin in corpus.load_corpus():
        tree = plugin.tree
        if tree is None:
            continue
        value = _top_level_constant(tree, "__id__")
        if isinstance(value, str) and value:
            ids.add(value)
    return frozenset(ids)


def _plugin_imports(tree):
    found = []
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            if node.level:
                continue
            names = tuple(a.name for a in node.names if a.name != "*")
            found.append(((node.module or ""), names))
        elif isinstance(node, ast.Import):
            for alias in node.names:
                found.append((alias.name, ()))
    return found


@functools.lru_cache(maxsize=1)
def scan_corpus():
    surface = sdk_surface()
    ids = corpus_plugin_ids()
    report = {
        "missing_module": {},
        "missing_name": {},
        "third_party": {},
        "resolved": 0,
        "sdk_imports": 0,
        "import_statements": 0,
        "unparsable": [],
    }
    for plugin in corpus.load_corpus():
        tree = plugin.tree
        if tree is None:
            report["unparsable"].append(plugin.name)
            continue
        requirements = _requirement_roots(tree)
        for module, names in _plugin_imports(tree):
            report["import_statements"] += 1
            root = module.split(".")[0]
            if not root or root in STDLIB_ROOTS or root in corpus.JAVA_ROOTS:
                continue
            if (root in PIP_INSTALLED_ROOTS
                    or root.lower().replace("-", "_") in requirements
                    or root in ids):
                report["third_party"].setdefault(root, set()).add(plugin.name)
                continue
            report["sdk_imports"] += 1
            if module not in surface:
                report["missing_module"].setdefault(
                    module, set()).add(plugin.name)
                continue
            for name in names:
                if name in surface[module]:
                    report["resolved"] += 1
                else:
                    report["missing_name"].setdefault(
                        f"{module}:{name}", set()).add(plugin.name)
    return report


def _format(entries, keys):
    lines = []
    for key in keys:
        plugins = sorted(entries.get(key, ()))
        sample = ", ".join(plugins[:3]) or "?"
        lines.append(f"  {key} - {len(plugins)} plugin(s), e.g. {sample}")
    return "\n".join(lines)


def test_sdk_surface_is_not_empty():
    surface = sdk_surface()
    assert "base_plugin" in surface and "ui.settings" in surface, (
        f"SDK python root looks wrong: {corpus.PYTHON_ROOT} gave "
        f"{sorted(surface)[:10]}")


def test_no_new_missing_sdk_modules():
    report = scan_corpus()
    new, _stale = baseline.compare(MODULES_BASELINE, report["missing_module"])
    assert not new, (
        f"{len(new)} SDK module(s) imported by plugins are absent from "
        f"{corpus.PYTHON_ROOT} and not in the baseline:\n"
        + _format(report["missing_module"], new))


def test_no_new_missing_sdk_names():
    report = scan_corpus()
    new, _stale = baseline.compare(NAMES_BASELINE, report["missing_name"])
    assert not new, (
        f"{len(new)} name(s) imported from our SDK modules do not exist "
        f"there and are not in the baseline:\n"
        + _format(report["missing_name"], new))


def test_base_plugin_surface_is_complete():
    surface = sdk_surface()
    assert "base_plugin" in surface, (
        f"base_plugin.py missing from {corpus.PYTHON_ROOT}")
    absent = [n for n in BASE_PLUGIN_REQUIRED_NAMES if n not in surface["base_plugin"]]
    assert not absent, (
        "base_plugin must keep exporting the names the plugin corpus imports "
        f"most often, but {absent} are gone")


def test_ui_settings_exports_widgets_used_by_corpus():
    surface = sdk_surface()
    assert "ui.settings" in surface, (
        f"ui/settings.py missing from {corpus.PYTHON_ROOT}")
    absent = [n for n in ("Header", "Divider", "Switch", "Input", "Text", "Selector")
              if n not in surface["ui.settings"]]
    assert not absent, (
        f"ui.settings lost widgets that plugins import: {absent}")
