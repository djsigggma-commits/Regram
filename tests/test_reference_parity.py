import ast
import collections
import functools
import hashlib
import importlib.util
import os
import re
import sys
import tempfile
import warnings

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import baseline
import corpus
import javaapi

PARITY_BASELINE = "reference_parity.json"
MISSING_BASELINE = "reference_missing_classes.json"

REFERENCE_ROOT = javaapi.REFERENCE_ROOT
OUR_JAVA_ROOT = os.environ.get("EXTERALESS_JAVA_ROOT", corpus.JAVA_ROOT)
OUR_KOTLIN_ROOT = javaapi.KOTLIN_ROOT
ALIASES_PATH = os.path.join(corpus.PYTHON_ROOT, "extera_utils", "class_aliases.py")

EXTERA_ROOT = "com.exteragram.messenger."
MUST_EXIST_THRESHOLD = 5
GENERATED = ("R", "BuildConfig")

_ROOTS = "|".join(corpus.JAVA_ROOTS)
_FQCN = re.compile(
    r"^(?:" + _ROOTS + r")(?:\.[A-Za-z_]\w*)*\.[A-Z]\w*(?:\$[A-Za-z_]\w*)*$")
_QUOTED = re.compile(
    r"[\"']((?:" + _ROOTS + r")(?:\.[A-Za-z_]\w*)*\.[A-Z]\w*(?:\$[A-Za-z_]\w*)*)[\"']")
_IMPORT_FROM = re.compile(
    r"^[ \t]*from[ \t]+((?:" + _ROOTS + r")(?:\.[A-Za-z_]\w*)*)[ \t]+import[ \t]+"
    r"(\([^)]*\)|[^\n\x23]+)", re.MULTILINE)
_IMPORT_PLAIN = re.compile(
    r"^[ \t]*import[ \t]+((?:" + _ROOTS + r")(?:\.[A-Za-z_]\w*)+)", re.MULTILINE)
_IMPORTED_NAME = re.compile(r"([A-Za-z_]\w*)(?:[ \t]+as[ \t]+[A-Za-z_]\w*)?")

_JUNK_SUBSTRINGS = ("$r8$lambda", "$$Nest", "EnumSwitchMapping", "$default",
                    "$lambda", "externalSyntheticLambda")
_JUNK_PREFIXES = ("access$", "-$$Nest", "lambda$", "$r8$", "this$", "val$")
_JUNK_NUMBERED = re.compile(r"^m\d+(?:\$|$)")
_JUNK_EXACT = {"INSTANCE", "Companion", "DefaultImpls", "WhenMappings",
               "$VALUES", "VALUES", "$ENTRIES", "ENTRIES", "$stable",
               "CREATOR", "writeToParcel", "describeContents",
               "default", "serialVersionUID", "Deobfuscator"}
_ENUM_SYNTHETIC = {"values", "valueOf", "getEntries"}
_SYNTHETIC_PARAM = ("DefaultConstructorMarker", "$i$f$")

_WHITESPACE_RUN = re.compile(r"[ \t]{2,}")

_TMPDIR = tempfile.TemporaryDirectory(prefix="regram-parity-")
_NORMALISED = {}
_INDEX_CACHE = {}
NORMALISE_ERRORS = []
INDEX_ERRORS = []


def _sanitise(raw):
    out = []
    i, n = 0, len(raw)
    while i < n:
        ch = raw[i]
        two = raw[i:i + 2]
        if two == "//":
            j = raw.find("\n", i)
            i = n if j < 0 else j
        elif two == "/*":
            j = raw.find("*/", i + 2)
            end = n if j < 0 else j + 2
            out.append("\n" * raw.count("\n", i, end))
            i = end
        elif ch == '"':
            j = i + 1
            while j < n and raw[j] != '"':
                j += 2 if raw[j] == "\\" else 1
            end = min(j + 1, n)
            out.append('""')
            out.append("\n" * raw.count("\n", i, end))
            i = end
        elif ch == "'":
            j = i + 1
            while j < n and raw[j] != "'":
                j += 2 if raw[j] == "\\" else 1
            end = min(j + 1, n)
            out.append("'0'")
            out.append("\n" * raw.count("\n", i, end))
            i = end
        else:
            out.append(ch)
            i += 1
    return _WHITESPACE_RUN.sub(" ", "".join(out))


def _normalised_path(path):
    cached = _NORMALISED.get(path)
    if cached is not None:
        return cached
    digest = hashlib.md5(path.encode("utf-8")).hexdigest()[:12]
    target = os.path.join(_TMPDIR.name, digest + "_" + os.path.basename(path))
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            raw = fh.read()
        with open(target, "w", encoding="utf-8") as fh:
            fh.write(_sanitise(raw))
    except Exception as exc:
        NORMALISE_ERRORS.append((path, f"{type(exc).__name__}: {exc}"))
        target = path
    _NORMALISED[path] = target
    return target


def _index(path, package):
    key = (path, package)
    cached = _INDEX_CACHE.get(key)
    if cached is None:
        try:
            cached = javaapi.parse_file(_normalised_path(path), package)
        except Exception as exc:
            INDEX_ERRORS.append((path, f"{type(exc).__name__}: {exc}"))
            cached = {}
        _INDEX_CACHE[key] = cached
    return cached


def _candidate_paths(root, fqcn):
    outer = fqcn.split("$", 1)[0]
    parts = outer.split(".")
    out = []
    if not os.path.isdir(root):
        return out
    for cut in range(len(parts), 0, -1):
        path = os.path.join(root, *parts[:cut]) + ".java"
        if os.path.isfile(path):
            out.append((path, ".".join(parts[:cut - 1])))
    return out


def type_in(root, fqcn):
    for path, package in _candidate_paths(root, fqcn):
        found = javaapi.lookup(_index(path, package), fqcn)
        if found is not None:
            return found
    return None


def kotlin_file_exists(fqcn):
    if not os.path.isdir(OUR_KOTLIN_ROOT):
        return False
    outer = fqcn.split("$", 1)[0]
    parts = outer.split(".")
    for cut in range(len(parts), 0, -1):
        if os.path.isfile(os.path.join(OUR_KOTLIN_ROOT, *parts[:cut]) + ".kt"):
            return True
    package = os.path.join(OUR_KOTLIN_ROOT, *parts[:-1])
    if not os.path.isdir(package):
        return False
    needle = re.compile(
        r"^(?:[\w@\[\]() ]*\b)?(?:class|object|interface)\s+" + parts[-1] + r"\b",
        re.MULTILINE)
    for name in os.listdir(package):
        if not name.endswith(".kt"):
            continue
        with open(os.path.join(package, name), encoding="utf-8",
                  errors="replace") as fh:
            if needle.search(fh.read()):
                return True
    return False


@functools.lru_cache(maxsize=1)
def aliases():
    spec = importlib.util.spec_from_file_location(
        "regram_aliases_for_parity", ALIASES_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@functools.lru_cache(maxsize=1)
def reverse_aliases():
    module = aliases()
    exact = {ours: theirs for theirs, ours in module._EXACT.items()}
    prefixes = tuple(sorted(
        ((new, old) for old, new in module._PREFIXES),
        key=lambda pair: -len(pair[0])))
    return exact, prefixes


def reference_name_for(ours):
    exact, prefixes = reverse_aliases()
    outer, sep, nested = ours.partition("$")
    if outer in exact:
        return exact[outer] + sep + nested
    for new, old in prefixes:
        if outer.startswith(new):
            return old + outer[len(new):] + sep + nested
    return None


def round_trips(reference, ours):
    back = reference_name_for(ours)
    return back is not None and back == reference


@functools.lru_cache(maxsize=1)
def reference_refs():
    refs = collections.defaultdict(set)

    def add(name, plugin):
        if not _FQCN.match(name) or not name.startswith(EXTERA_ROOT):
            return
        if name.split("$", 1)[0].rsplit(".", 1)[-1] in GENERATED:
            return
        refs[name].add(plugin)

    with warnings.catch_warnings():
        warnings.simplefilter("ignore", SyntaxWarning)
        for plugin in corpus.load_corpus():
            source = plugin.source
            tree = plugin.tree
            if tree is not None:
                for node in ast.walk(tree):
                    if isinstance(node, ast.ImportFrom):
                        if node.level or not node.module:
                            continue
                        if node.module.split(".")[0] not in corpus.JAVA_ROOTS:
                            continue
                        for alias in node.names:
                            if alias.name != "*":
                                add(node.module + "." + alias.name, plugin.name)
                    elif isinstance(node, ast.Import):
                        for alias in node.names:
                            add(alias.name, plugin.name)
                    elif isinstance(node, ast.Constant):
                        if isinstance(node.value, str):
                            add(node.value, plugin.name)
            for match in _QUOTED.finditer(source):
                add(match.group(1), plugin.name)
            for match in _IMPORT_PLAIN.finditer(source):
                add(match.group(1), plugin.name)
            for match in _IMPORT_FROM.finditer(source):
                module, names = match.group(1), match.group(2).strip("()")
                for part in names.split(","):
                    part = part.strip()
                    if not part or part == "*":
                        continue
                    named = _IMPORTED_NAME.match(part)
                    if named:
                        add(module + "." + named.group(1), plugin.name)
    return {name: frozenset(users) for name, users in refs.items()}


def is_junk(name):
    if name in _JUNK_EXACT:
        return True
    if _JUNK_NUMBERED.match(name):
        return True
    if any(part in name for part in _JUNK_SUBSTRINGS):
        return True
    return any(name.startswith(part) for part in _JUNK_PREFIXES)


def is_synthetic_signature(params):
    return any(any(mark in param for mark in _SYNTHETIC_PARAM)
               for param in params)


@functools.lru_cache(maxsize=4096)
def private_only(path):
    try:
        with open(_normalised_path(path), encoding="utf-8",
                  errors="replace") as fh:
            text = javaapi.strip_noise(fh.read())
    except OSError:
        return frozenset(), frozenset()
    private_methods, public_methods = set(), set()
    private_fields, public_fields = set(), set()
    for line in text.split("\n"):
        line = _WHITESPACE_RUN.sub(" ", line).strip()
        if not line:
            continue
        for match in javaapi._MEMBER.finditer(line):
            name = match.group("name")
            if name in javaapi.KEYWORDS:
                continue
            mods = set(match.group("mods").split())
            target = private_methods if "private" in mods else public_methods
            target.add(name)
        for match in javaapi._FIELD.finditer(line):
            declared = match.group("type").strip()
            if not declared or declared in javaapi.KEYWORDS or "(" in declared:
                continue
            mods = set(match.group("mods").split())
            target = private_fields if "private" in mods else public_fields
            target.add(match.group("name"))
    return (frozenset(private_methods - public_methods),
            frozenset(private_fields - public_fields))


def public_surface(jtype, drop_junk):
    hidden_methods, hidden_fields = private_only(jtype.path)
    enum = jtype.kind == "enum"
    methods = {}
    for name, overloads in jtype.methods.items():
        if name in hidden_methods:
            continue
        if drop_junk and (is_junk(name) or (enum and name in _ENUM_SYNTHETIC)):
            continue
        arities = {len(params) for params in overloads
                   if not (drop_junk and is_synthetic_signature(params))}
        if arities:
            methods[name] = arities
    constructors = {len(params) for params in jtype.constructors
                    if not (drop_junk and is_synthetic_signature(params))}
    fields = {name for name in jtype.fields
              if name not in hidden_fields
              and not (drop_junk and (is_junk(name)
                                      or (enum and name in _ENUM_SYNTHETIC)))}
    nested = {name for name in jtype.nested
              if not (drop_junk and is_junk(name))}
    return methods, constructors, fields, nested


def diff_pair(reference, ours, ref_type, our_type):
    ref_methods, ref_ctors, ref_fields, ref_nested = public_surface(ref_type, True)
    our_methods, our_ctors, our_fields, our_nested = public_surface(our_type, False)
    findings = []

    def record(kind, detail):
        findings.append("|".join((reference, ours, kind, detail)))

    for name in sorted(ref_methods):
        if name not in our_methods:
            record("missing_method", name)
            continue
        for arity in sorted(ref_methods[name] - our_methods[name]):
            record("missing_arity", f"{name}/{arity}")
    for arity in sorted(ref_ctors - our_ctors):
        record("missing_constructor_arity", str(arity))
    for name in sorted(ref_fields - our_fields):
        record("missing_field", name)
    for name in sorted(ref_nested - our_nested):
        record("missing_nested", name)
    return findings


@functools.lru_cache(maxsize=1)
def survey():
    resolve = aliases().resolve
    pairs, findings, missing = [], [], []
    for reference in sorted(reference_refs()):
        ref_type = type_in(REFERENCE_ROOT, reference)
        ours = resolve(reference)
        our_type = type_in(OUR_JAVA_ROOT, ours)
        exists = our_type is not None or kotlin_file_exists(ours)
        pairs.append((reference, ours, ref_type is not None, exists))
        if not exists:
            missing.append(f"{reference}|{ours}")
        if ref_type is None or our_type is None:
            continue
        findings.extend(diff_pair(reference, ours, ref_type, our_type))
    return tuple(pairs), tuple(sorted(set(findings))), tuple(sorted(set(missing)))


def users_of(reference):
    return len(reference_refs().get(reference, ()))


def describe(entry):
    reference, ours, kind, detail = entry.split("|", 3)
    hint = "" if round_trips(reference, ours) else " [alias не разворачивается обратно]"
    return (f"  {users_of(reference):>4} плагинов  {reference} -> {ours}: "
            f"{kind} {detail}{hint}")


if not corpus.load_corpus():
    pytest.skip("нет корпуса плагинов", allow_module_level=True)
if not os.path.isdir(os.path.join(REFERENCE_ROOT, "com", "exteragram")):
    pytest.skip("нет эталонных исходников exteraGram", allow_module_level=True)


def test_reference_sources_are_present_and_parsed():
    anchor = type_in(REFERENCE_ROOT, "com.exteragram.messenger.plugins.Plugin")
    assert anchor is not None, (
        f"эталон {REFERENCE_ROOT} есть на диске, но Plugin не разобрался")
    methods, constructors, _, _ = public_surface(anchor, True)
    assert len(methods) >= 10, (
        f"эталонный Plugin разобрался пустым: методы {sorted(methods)}; "
        f"разбор сломан, парити-тест зеленел бы вхолостую")
    assert constructors, "у эталонного Plugin не нашлось ни одного конструктора"
    pairs, _, _ = survey()
    parsed = sum(1 for pair in pairs if pair[2])
    assert parsed * 2 >= len(pairs), (
        f"эталон разобрался только для {parsed} классов из {len(pairs)}")
    assert not NORMALISE_ERRORS, f"не прочитались файлы: {NORMALISE_ERRORS[:5]}"
    assert not INDEX_ERRORS, f"не разобрались файлы: {INDEX_ERRORS[:5]}"


def test_no_new_reference_gaps():
    _, findings, _ = survey()
    new, gone = baseline.compare(PARITY_BASELINE, findings)
    if not new:
        return
    new.sort(key=lambda entry: (-users_of(entry.split("|", 1)[0]), entry))
    lines = "\n".join(describe(entry) for entry in new[:40])
    pytest.fail(
        f"новые расхождения с эталоном exteraGram 12.9.2: {len(new)}\n{lines}\n"
        f"(в базе {PARITY_BASELINE} закрыто {len(gone)} прежних записей)")


def test_classes_the_catalogue_uses_exist_on_our_side():
    _, _, missing = survey()
    hot = [entry for entry in missing
           if users_of(entry.split("|", 1)[0]) >= MUST_EXIST_THRESHOLD]
    new, _ = baseline.compare(MISSING_BASELINE, hot)
    if not new:
        return
    new.sort(key=lambda entry: (-users_of(entry.split("|", 1)[0]), entry))
    lines = "\n".join(
        f"  {users_of(entry.split('|', 1)[0]):>4} плагинов  "
        f"{entry.split('|')[0]} -> {entry.split('|')[1]}" for entry in new)
    pytest.fail(
        f"классы, которые зовут минимум {MUST_EXIST_THRESHOLD} плагинов, "
        f"у нас не найдены: {len(new)}\n{lines}")
