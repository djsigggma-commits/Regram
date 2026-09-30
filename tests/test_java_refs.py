import ast
import functools
import importlib.util
import os
import re
import sys
import warnings

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import baseline
import corpus

BASELINE_NAME = "java_refs.json"
OWNED_PREFIXES = ("org.telegram.", "com.exteragram.", "app.regram.")
GENERATED = ("R", "BuildConfig")
ALIASES_PATH = os.path.join(corpus.PYTHON_ROOT, "extera_utils", "class_aliases.py")
PROGUARD_RULES = os.path.join(corpus.REPO, "TMessagesProj", "proguard-rules.pro")
SOURCE_ROOTS = (
    corpus.JAVA_ROOT,
    os.path.join(corpus.REPO, "TMessagesProj", "src", "main", "kotlin"))

_ROOTS = "|".join(corpus.JAVA_ROOTS)
_FQCN = re.compile(
    r"^(?:" + _ROOTS + r")(?:\.[A-Za-z_]\w*)*\.[A-Z]\w*(?:\$[A-Za-z_]\w*)*$")
_QUOTED = re.compile(
    r"[\"']((?:" + _ROOTS + r")(?:\.[A-Za-z_]\w*)*\.[A-Z]\w*(?:\$[A-Za-z_]\w*)*)[\"']")
_IMPORT_FROM = re.compile(
    r"^[ \t]*from[ \t]+((?:" + _ROOTS + r")(?:\.[A-Za-z_]\w*)*)[ \t]+import[ \t]+"
    r"(\([^)]*\)|[^\n#]+)", re.MULTILINE)
_IMPORT_PLAIN = re.compile(
    r"^[ \t]*import[ \t]+((?:" + _ROOTS + r")(?:\.[A-Za-z_]\w*)+)", re.MULTILINE)
_IMPORTED_NAME = re.compile(r"([A-Za-z_]\w*)(?:[ \t]+as[ \t]+[A-Za-z_]\w*)?")
_KOTLIN_DECL = re.compile(
    r"^(?:[\w@\[\]() ]*\b)?(?:class|object|interface)\s+(\w+)", re.MULTILINE)
_ANY_DECL = re.compile(
    r"\b(?:class|interface|enum|record|@interface)\s+(\w+)")


@functools.lru_cache(maxsize=1)
def aliases():
    spec = importlib.util.spec_from_file_location(
        "regram_class_aliases_under_test", ALIASES_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _add(refs, name, plugin):
    if _FQCN.match(name):
        refs.setdefault(name, set()).add(plugin.name)


def _from_ast(tree, refs, plugin):
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            if node.level or not node.module:
                continue
            if node.module.split(".")[0] not in corpus.JAVA_ROOTS:
                continue
            for alias in node.names:
                if alias.name != "*":
                    _add(refs, node.module + "." + alias.name, plugin)
        elif isinstance(node, ast.Import):
            for alias in node.names:
                _add(refs, alias.name, plugin)
        elif isinstance(node, ast.Constant) and isinstance(node.value, str):
            _add(refs, node.value, plugin)


def _from_regex(source, refs, plugin):
    for match in _QUOTED.finditer(source):
        _add(refs, match.group(1), plugin)
    for match in _IMPORT_PLAIN.finditer(source):
        _add(refs, match.group(1), plugin)
    for match in _IMPORT_FROM.finditer(source):
        module, names = match.group(1), match.group(2).strip("()")
        for part in names.split(","):
            part = part.strip()
            if not part or part == "*":
                continue
            name = _IMPORTED_NAME.match(part)
            if name:
                _add(refs, module + "." + name.group(1), plugin)


@functools.lru_cache(maxsize=1)
def plugin_java_refs():
    refs = {}
    parsed = 0
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", SyntaxWarning)
        for plugin in corpus.load_corpus():
            tree = plugin.tree
            if tree is None:
                continue
            parsed += 1
            _from_ast(tree, refs, plugin)
            _from_regex(plugin.source, refs, plugin)
    resolve = aliases().resolve
    out = {}
    for name, users in refs.items():
        out.setdefault(resolve(name), set()).update(users)
    return parsed, {k: frozenset(v) for k, v in out.items()}


@functools.lru_cache(maxsize=1)
def kotlin_source_classes():
    found = set()
    for root in SOURCE_ROOTS:
        if not os.path.isdir(root):
            continue
        for dirpath, _, files in os.walk(root):
            rel = os.path.relpath(dirpath, root).replace(os.sep, ".")
            for fn in files:
                if not fn.endswith(".kt"):
                    continue
                with open(os.path.join(dirpath, fn), encoding="utf-8",
                          errors="replace") as fh:
                    body = fh.read()
                for match in _KOTLIN_DECL.finditer(body):
                    name = match.group(1)
                    found.add(f"{rel}.{name}" if rel != "." else name)
    return frozenset(found)


@functools.lru_cache(maxsize=None)
def _declared_in_file(outer):
    path = os.path.join(corpus.JAVA_ROOT, outer.replace(".", os.sep) + ".java")
    if not os.path.isfile(path):
        return frozenset()
    with open(path, encoding="utf-8", errors="replace") as fh:
        return frozenset(_ANY_DECL.findall(fh.read()))


def _in_fork_sources(name):
    outer, _, rest = name.partition("$")
    if outer.rpartition(".")[2] in GENERATED and _package_exists(outer):
        return True
    if outer in kotlin_source_classes():
        return True
    if outer not in corpus.java_source_classes():
        return False
    if not rest:
        return True
    known = set(corpus.java_nested_classes().get(outer, ()))
    parts = rest.split("$")
    if all(part in known for part in parts):
        return True
    known |= _declared_in_file(outer)
    return all(part in known for part in parts)


def _package_exists(outer):
    package = outer.rpartition(".")[0].replace(".", os.sep)
    return bool(package) and os.path.isdir(os.path.join(corpus.JAVA_ROOT, package))


@functools.lru_cache(maxsize=1)
def classify():
    _, refs = plugin_java_refs()
    kept, renamed = corpus.r8_mapping()
    dropped = corpus.r8_removed()
    buckets = {"removed_by_r8": {}, "renamed_by_r8": {}, "missing_in_fork": {}}
    for name, users in refs.items():
        outer = name.partition("$")[0]
        if name in kept:
            continue
        if name in renamed or (name == outer and outer in renamed):
            buckets["renamed_by_r8"][name] = users
            continue
        if (name in dropped or outer in dropped) and _in_fork_sources(name):
            buckets["removed_by_r8"][name] = users
            continue
        if _in_fork_sources(name):
            continue
        if name.startswith(OWNED_PREFIXES):
            buckets["missing_in_fork"][name] = users
    return buckets


def _report(bucket, limit=None):
    lines = []
    items = sorted(bucket.items(), key=lambda kv: (-len(kv[1]), kv[0]))
    shown = items if limit is None else items[:limit]
    for name, users in shown:
        examples = ", ".join(sorted(users)[:3])
        lines.append(f"  {name} - {len(users)} plugin(s): {examples}")
    if limit is not None and len(items) > limit:
        lines.append(f"  ... and {len(items) - limit} more")
    return "\n".join(lines)


def _require_corpus():
    if not corpus.load_corpus():
        pytest.skip(f"plugin corpus not found at {corpus.CORPUS_DIR}")


def _stale_mapping_reason():
    config = os.path.join(corpus.MAPPING_DIR, "configuration.txt")
    if not os.path.isfile(config):
        return None
    if not os.path.isfile(PROGUARD_RULES):
        return None
    with open(config, encoding="utf-8", errors="replace") as fh:
        used = set(line.rstrip("\n") for line in fh)
    with open(PROGUARD_RULES, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            if line not in used:
                return line.strip()
    return None


def _require_r8():
    _require_corpus()
    if not os.path.isfile(os.path.join(corpus.MAPPING_DIR, "mapping.txt")):
        pytest.skip(f"no release mapping in {corpus.MAPPING_DIR}")
    if not corpus.r8_mapping()[0]:
        pytest.skip(f"empty release mapping in {corpus.MAPPING_DIR}")
    missed = _stale_mapping_reason()
    if missed is not None:
        pytest.skip(
            "release mapping predates the current shrink config, rebuild it; "
            f"missing rule: {missed}")


def test_no_java_class_removed_by_r8():
    _require_r8()
    removed = classify()["removed_by_r8"]
    assert not removed, (
        f"R8 shrank away {len(removed)} Java class(es) that plugins load by name; "
        "add keep rules in TMessagesProj/proguard-rules.pro:\n"
        + _report(removed))


def test_no_java_class_renamed_by_r8():
    _require_r8()
    renamed = classify()["renamed_by_r8"]
    mapping = corpus.r8_mapping()[1]
    detail = "\n".join(
        f"  {name} -> {mapping.get(name, mapping.get(name.partition('$')[0]))}"
        f" - {len(users)} plugin(s): {', '.join(sorted(users)[:3])}"
        for name, users in sorted(renamed.items()))
    assert not renamed, (
        f"R8 renamed {len(renamed)} Java class(es) that plugins load by name; "
        "add keep rules in TMessagesProj/proguard-rules.pro:\n" + detail)


def test_no_new_missing_java_classes():
    _require_corpus()
    missing = classify()["missing_in_fork"]
    new, gone = baseline.compare(BASELINE_NAME, missing)
    message = ""
    if new:
        message += (
            f"{len(new)} Java class(es) used by plugins are absent from the fork "
            "(port them or add an alias in class_aliases.py):\n"
            + _report({k: missing[k] for k in new}, limit=20))
    if gone:
        message += (
            f"\n{len(gone)} entries in tests/baselines/{BASELINE_NAME} are no longer "
            "missing, drop them from the baseline:\n"
            + "\n".join(f"  {k}" for k in gone))
    assert not new, message
