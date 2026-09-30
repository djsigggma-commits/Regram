import ast
import functools
import importlib.util
import os
import sys
import warnings
import zipfile

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import baseline
import corpus
import javaapi

METHOD_BASELINE = "dynamic_proxy_methods.json"
ARITY_BASELINE = "dynamic_proxy_arity.json"

OWNED_PREFIXES = ("app.regram.", "com.exteragram.", "org.telegram.")
FACTORY_CALLS = ("find_class", "jclass", "autoclass", "load_class", "get_class")
OBJECT_METHODS = frozenset((
    "toString", "equals", "hashCode", "clone", "finalize", "getClass"))

ALIASES_PATH = os.path.join(corpus.PYTHON_ROOT, "extera_utils", "class_aliases.py")
ARCHIVE_DIR = os.environ.get(
    "EXTERALESS_PLUGIN_ARCHIVES", "/home/coral/openExtera/plugin-corpus/archives")

DELEGATE_FQCN = "app.regram.plugins.ui.components.PluginCellDelegate"
DELEGATE_METHODS = frozenset((
    "sharePlugin", "openInExternalApp", "deletePlugin", "togglePlugin",
    "openPluginSettings", "pinPlugin", "canOpenInExternalApp"))
PERMISSIONS_FQCN = "app.regram.plugins.ui.components.PluginPermissionsDelegate"
PERMISSIONS_METHODS = frozenset(("openPluginPermissions",))

MIN_PROXY_CLASSES = 200
MIN_OWNED_PROXY_CLASSES = 60

pytestmark = pytest.mark.skipif(
    not os.path.isdir(corpus.CORPUS_DIR),
    reason=f"plugin corpus not found at {corpus.CORPUS_DIR}")


@functools.lru_cache(maxsize=1)
def aliases():
    spec = importlib.util.spec_from_file_location(
        "regram_class_aliases_proxy_shape", ALIASES_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Names:

    def __init__(self, tree):
        self.imported = {}
        self.assigned = {}
        self.factories = set(FACTORY_CALLS)
        renames = []
        for node in ast.walk(tree):
            if isinstance(node, ast.ImportFrom):
                if node.level or not node.module:
                    continue
                if node.module.split(".")[0] not in corpus.JAVA_ROOTS:
                    continue
                for alias in node.names:
                    if alias.name != "*":
                        self.imported[alias.asname or alias.name] = \
                            f"{node.module}.{alias.name}"
            elif isinstance(node, ast.Import):
                for alias in node.names:
                    if alias.name.split(".")[0] in corpus.JAVA_ROOTS:
                        self.imported[alias.asname or alias.name.split(".")[-1]] = \
                            alias.name
            elif isinstance(node, (ast.Assign, ast.AnnAssign)):
                if node.value is None:
                    continue
                keys = _targets(node)
                if isinstance(node.value, ast.Name):
                    renames.append((keys, node.value.id))
                for key in keys:
                    self.assigned.setdefault(key, node.value)
        pending = True
        while pending:
            pending = False
            for keys, source in renames:
                if source in self.factories and not self.factories.issuperset(keys):
                    self.factories.update(keys)
                    pending = True

    def _lookup(self, key, seen):
        if key in self.imported:
            return self.imported[key]
        if key in seen:
            return None
        seen.add(key)
        node = self.assigned.get(key)
        return self.resolve(node, seen) if node is not None else None

    def resolve(self, node, seen=None):
        seen = set() if seen is None else seen
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            return node.value
        if isinstance(node, ast.Call):
            func = node.func
            name = getattr(func, "id", None) or getattr(func, "attr", None)
            if name in self.factories and node.args:
                return self.resolve(node.args[0], seen)
            return None
        if isinstance(node, ast.Name):
            return self._lookup(node.id, seen)
        if isinstance(node, ast.Attribute):
            parts = _dotted(node)
            if not parts:
                return None
            if parts[0] in ("self", "cls") and len(parts) > 1:
                base, rest = self._lookup(f"self.{parts[1]}", seen), parts[2:]
            elif parts[0] in corpus.JAVA_ROOTS:
                base, rest = ".".join(parts), []
            else:
                base, rest = self._lookup(parts[0], seen), parts[1:]
            if base is None:
                return None
            return "$".join([base] + rest) if rest else base
        return None


def _targets(node):
    raw = node.targets if isinstance(node, ast.Assign) else [node.target]
    out = []
    for target in raw:
        if isinstance(target, ast.Name):
            out.append(target.id)
        elif (isinstance(target, ast.Attribute)
              and isinstance(target.value, ast.Name)
              and target.value.id in ("self", "cls")):
            out.append(f"self.{target.attr}")
    return out


def _dotted(node):
    parts, current = [], node
    while isinstance(current, ast.Attribute):
        parts.append(current.attr)
        current = current.value
    if isinstance(current, ast.Name):
        parts.append(current.id)
        return list(reversed(parts))
    return None


def _is_dynamic_proxy(base):
    if not (isinstance(base, ast.Call) and base.args):
        return False
    func = base.func
    return (getattr(func, "id", None) or getattr(func, "attr", None)) == "dynamic_proxy"


def _python_arity(func):
    args = func.args
    for decorator in func.decorator_list:
        name = getattr(decorator, "id", None) or getattr(decorator, "attr", None)
        if name in ("staticmethod", "classmethod"):
            return None
    if args.vararg is not None:
        return None
    positional = len(args.posonlyargs) + len(args.args) - 1
    if positional < 0:
        return None
    required = positional - len(args.defaults)
    return max(required, 0), positional


def _proxy_methods(node):
    out = []
    for statement in node.body:
        if not isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        name = statement.name
        if name.startswith("__") and name.endswith("__"):
            continue
        if name in OBJECT_METHODS:
            continue
        if "_" in name:
            continue
        out.append((name, _python_arity(statement)))
    return out


def _parse(source, label):
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        try:
            return ast.parse(source, filename=label)
        except (SyntaxError, ValueError, RecursionError):
            return None


def _sources():
    for plugin in corpus.load_corpus():
        yield plugin.name, plugin.source
    if not os.path.isdir(ARCHIVE_DIR):
        return
    for filename in sorted(os.listdir(ARCHIVE_DIR)):
        path = os.path.join(ARCHIVE_DIR, filename)
        if not (os.path.isfile(path) and zipfile.is_zipfile(path)):
            continue
        with zipfile.ZipFile(path) as archive:
            for member in sorted(archive.namelist()):
                if not member.endswith(".py"):
                    continue
                yield (f"{filename}!{member}",
                       archive.read(member).decode("utf-8", errors="replace"))


@functools.lru_cache(maxsize=1)
def proxy_index():
    found, unresolved = [], {}
    for label, source in _sources():
        if "dynamic_proxy" not in source:
            continue
        tree = _parse(source, label)
        if tree is None:
            continue
        bases = [(node, base) for node in ast.walk(tree)
                 if isinstance(node, ast.ClassDef)
                 for base in node.bases if _is_dynamic_proxy(base)]
        if not bases:
            continue
        names = Names(tree)
        for node, base in bases:
            target = names.resolve(base.args[0])
            if target is None:
                unresolved.setdefault(ast.unparse(base.args[0]), set()).add(label)
                found.append((label, node.name, None, ()))
                continue
            found.append((label, node.name, aliases().resolve(target),
                          tuple(_proxy_methods(node))))
    return tuple(found), {k: sorted(v) for k, v in unresolved.items()}


@functools.lru_cache(maxsize=None)
def _closure(fqcn):
    order, unknown, stack, seen = [], False, [fqcn], set()
    while stack:
        current = stack.pop()
        if current in seen:
            continue
        seen.add(current)
        jtype = javaapi.type_of(current)
        if jtype is None:
            unknown = True
            continue
        order.append(jtype)
        stack.extend(javaapi.supertypes(jtype))
    return tuple(order), unknown


def _inherited_arities(fqcn, method):
    types, unknown = _closure(fqcn)
    arities = set()
    for jtype in types:
        arities |= jtype.method_arities(method)
    return arities, unknown


@functools.lru_cache(maxsize=1)
def classify():
    missing_interface, missing_method, arity_mismatch = {}, {}, {}
    for label, cls, fqcn, methods in proxy_index()[0]:
        if fqcn is None or not fqcn.startswith(OWNED_PREFIXES):
            continue
        jtype = javaapi.type_of(fqcn)
        if jtype is None:
            missing_interface.setdefault(fqcn, set()).add(label)
            continue
        for method, arity in methods:
            declared = jtype.method_arities(method)
            unknown = False
            if not declared:
                declared, unknown = _inherited_arities(fqcn, method)
            if not declared:
                if not unknown:
                    key = f"{fqcn}::{method}"
                    missing_method.setdefault(key, set()).add(f"{label}:{cls}")
                continue
            if arity is None:
                continue
            low, high = arity
            if not any(low <= count <= high for count in declared):
                shape = "/".join(str(count) for count in sorted(declared))
                key = f"{fqcn}::{method} takes {low}-{high}, Java declares {shape}"
                arity_mismatch.setdefault(key, set()).add(f"{label}:{cls}")
    return (
        {k: sorted(v) for k, v in missing_interface.items()},
        {k: sorted(v) for k, v in missing_method.items()},
        {k: sorted(v) for k, v in arity_mismatch.items()},
    )


def _report(entries, keys, limit=25):
    lines = []
    for key in keys[:limit]:
        users = entries.get(key, ())
        sample = ", ".join(users[:3]) if users else "no plugin recorded"
        lines.append(f"  {key}  ({len(users)} plugin(s): {sample})")
    if len(keys) > limit:
        lines.append(f"  ... and {len(keys) - limit} more")
    return "\n".join(lines)


def test_no_new_unimplementable_proxy_methods():
    _, missing, _ = classify()
    new, gone = baseline.compare(METHOD_BASELINE, missing)
    message = ""
    if new:
        message += (
            f"{len(new)} method(s) of dynamic_proxy classes are declared by no "
            "interface of ours; Chaquopy builds such a proxy but never calls "
            "them, so the plugin silently does nothing:\n"
            + _report(missing, new))
    if gone:
        message += (
            f"\n{len(gone)} entry(ies) of tests/baselines/{METHOD_BASELINE} are "
            "implementable again, drop them from the baseline:\n"
            + "\n".join(f"  {key}" for key in gone))
    assert not new, message


def test_no_new_proxy_arity_mismatches():
    _, _, mismatch = classify()
    new, gone = baseline.compare(ARITY_BASELINE, mismatch)
    message = ""
    if new:
        message += (
            f"{len(new)} method(s) of dynamic_proxy classes take a different "
            "number of parameters than the Java interface declares; the call "
            "reaches the proxy and blows up at runtime:\n"
            + _report(mismatch, new))
    if gone:
        message += (
            f"\n{len(gone)} entry(ies) of tests/baselines/{ARITY_BASELINE} match "
            "again, drop them from the baseline:\n"
            + "\n".join(f"  {key}" for key in gone))
    assert not new, message


def test_plugin_cell_delegate_matches_the_reference():
    delegate = javaapi.type_of(DELEGATE_FQCN)
    assert delegate is not None, (
        f"{DELEGATE_FQCN} is gone; plugins implement it through "
        "dynamic_proxy and get a proxy that hooks up to nothing")
    declared = set(delegate.methods)
    missing = sorted(DELEGATE_METHODS - declared)
    extra = sorted(declared - DELEGATE_METHODS)
    assert not missing, (
        f"{DELEGATE_FQCN} no longer declares {missing}; plugins define exactly "
        f"{sorted(DELEGATE_METHODS)} on their proxy, a renamed or dropped method "
        "is never invoked and the cell action dies without a trace")
    assert not extra, (
        f"{DELEGATE_FQCN} declares {extra} on top of the plugin contract; a "
        "plugin proxy leaves those unimplemented and the call crashes at "
        "runtime, put our own actions into a separate interface the way "
        f"{PERMISSIONS_FQCN} does")

    permissions = javaapi.type_of(PERMISSIONS_FQCN)
    assert permissions is not None, (
        f"{PERMISSIONS_FQCN} is gone; our permissions action belongs to a "
        "separate interface so that it stays out of the plugin contract")
    assert set(permissions.methods) == PERMISSIONS_METHODS, (
        f"{PERMISSIONS_FQCN} declares {sorted(permissions.methods)} instead of "
        f"{sorted(PERMISSIONS_METHODS)}")


def test_proxy_index_is_not_silently_empty():
    index, _ = proxy_index()
    assert len(index) >= MIN_PROXY_CLASSES, (
        f"only {len(index)} dynamic_proxy class(es) found in {corpus.CORPUS_DIR} "
        f"and {ARCHIVE_DIR}; the corpus holds several hundred, so the parser of "
        "this test is broken and every check above passes on an empty set")
    owned = [entry for entry in index
             if entry[2] is not None and entry[2].startswith(OWNED_PREFIXES)]
    assert len(owned) >= MIN_OWNED_PROXY_CLASSES, (
        f"only {len(owned)} dynamic_proxy class(es) resolve to an interface of "
        "ours; name resolution or class_aliases.resolve is broken and the shape "
        "checks above run on nothing")
