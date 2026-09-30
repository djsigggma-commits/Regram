import ast
import functools
import os
import zipfile

import pytest

import baseline
import corpus

KEYWORDS_BASELINE = "sdk_signature_keywords.json"
POSITIONAL_BASELINE = "sdk_signature_positional.json"
REQUIRED_BASELINE = "sdk_signature_required.json"

ARCHIVE_DIR = os.environ.get(
    "EXTERALESS_PLUGIN_ARCHIVES", "/home/coral/openExtera/plugin-corpus/archives")

WIDGET_MODULE = "ui.settings"
WIDGET_MIN_PLUGINS = 3
WIDGET_TOP = 6

MIN_SIGNATURES = 300

REQUIRED_SIGNATURE_KEYS = (
    "base_plugin.BasePlugin.__init__",
    "ui.settings.Switch.__init__",
)

pytestmark = pytest.mark.skipif(
    not os.path.isdir(corpus.CORPUS_DIR),
    reason=f"plugin corpus not found at {corpus.CORPUS_DIR}")


class Signature:

    __slots__ = ("positional", "posonly", "required_positional", "vararg",
                 "kwonly", "kwonly_required", "kwargs", "bound")

    def __init__(self, positional, posonly, required_positional, vararg,
                 kwonly, kwonly_required, kwargs, bound):
        self.positional = tuple(positional)
        self.posonly = posonly
        self.required_positional = tuple(required_positional)
        self.vararg = vararg
        self.kwonly = tuple(kwonly)
        self.kwonly_required = tuple(kwonly_required)
        self.kwargs = kwargs
        self.bound = bound

    @property
    def keywordable(self):
        start = max(self.posonly, self.bound)
        return frozenset(self.positional[start:]) | frozenset(self.kwonly)

    def __repr__(self):
        return f"<Signature {self.positional} kwonly={self.kwonly}>"


def _signature_from_args(args, bound):
    positional = [a.arg for a in args.posonlyargs] + [a.arg for a in args.args]
    n_defaults = len(args.defaults)
    cut = len(positional) - n_defaults if n_defaults else len(positional)
    kwonly = [a.arg for a in args.kwonlyargs]
    kwonly_required = [a.arg for a, d in zip(args.kwonlyargs, args.kw_defaults)
                       if d is None]
    return Signature(
        positional=positional,
        posonly=len(args.posonlyargs),
        required_positional=positional[:cut],
        vararg=args.vararg is not None,
        kwonly=kwonly,
        kwonly_required=kwonly_required,
        kwargs=args.kwarg is not None,
        bound=bound)


def _decorator_names(node):
    out = set()
    for dec in node.decorator_list:
        target = dec.func if isinstance(dec, ast.Call) else dec
        if isinstance(target, ast.Name):
            out.add(target.id)
        elif isinstance(target, ast.Attribute):
            out.add(target.attr)
    return out


def _dotted(node):
    parts = []
    while isinstance(node, ast.Attribute):
        parts.append(node.attr)
        node = node.value
    if not isinstance(node, ast.Name):
        return None
    parts.append(node.id)
    return tuple(reversed(parts))


def _annotation_is_classvar(node):
    dotted = _dotted(node.value if isinstance(node, ast.Subscript) else node)
    return bool(dotted) and dotted[-1] in ("ClassVar", "InitVar")


def _module_of(root, path):
    rel = os.path.relpath(path, root)
    parts = rel[:-3].split(os.sep)
    if parts and parts[-1] == "__init__":
        parts = parts[:-1]
    return ".".join(parts)


def _absolute_module(module, node):
    if not node.level:
        return node.module or ""
    package = module.split(".")
    if node.level > len(package):
        return None
    base = package[:len(package) - node.level + 1]
    if node.module:
        base = base + node.module.split(".")
    return ".".join(base)


class SdkIndex:

    def __init__(self):
        self.signatures = {}
        self.class_bases = {}
        self.dataclass_fields = {}
        self.is_dataclass = set()
        self.has_init = set()
        self.aliases = {}
        self.modules = set()
        self.classes = set()

    def resolve_class(self, module, name):
        candidate = f"{module}.{name}"
        if candidate in self.classes:
            return candidate
        alias = self.aliases.get(candidate)
        if alias and alias in self.classes:
            return alias
        return None


def _collect_class(index, module, qualname, node):
    index.classes.add(qualname)
    decorators = _decorator_names(node)
    if "dataclass" in decorators:
        index.is_dataclass.add(qualname)
    bases = []
    for base in node.bases:
        dotted = _dotted(base)
        bases.append(dotted[-1] if dotted else None)
    index.class_bases[qualname] = tuple(bases)
    fields = []
    for stmt in node.body:
        if isinstance(stmt, (ast.FunctionDef, ast.AsyncFunctionDef)):
            decs = _decorator_names(stmt)
            bound = 0 if "staticmethod" in decs else 1
            index.signatures[f"{qualname}.{stmt.name}"] = _signature_from_args(
                stmt.args, bound)
            if stmt.name == "__init__":
                index.has_init.add(qualname)
        elif isinstance(stmt, ast.ClassDef):
            _collect_class(index, module, f"{qualname}.{stmt.name}", stmt)
        elif isinstance(stmt, ast.AnnAssign) and isinstance(stmt.target, ast.Name):
            if _annotation_is_classvar(stmt.annotation):
                continue
            fields.append((stmt.target.id, stmt.value is not None))
    index.dataclass_fields[qualname] = tuple(fields)


def _collect_module(index, module, tree):
    index.modules.add(module)
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            index.signatures[f"{module}.{node.name}"] = _signature_from_args(
                node.args, 0)
        elif isinstance(node, ast.ClassDef):
            _collect_class(index, module, f"{module}.{node.name}", node)
        elif isinstance(node, ast.ImportFrom):
            target = _absolute_module(module, node)
            if target is None:
                continue
            for alias in node.names:
                if alias.name == "*":
                    continue
                local = alias.asname or alias.name
                index.aliases[f"{module}.{local}"] = f"{target}.{alias.name}"


def _dataclass_field_chain(index, qualname, seen):
    if qualname in seen:
        return []
    seen.add(qualname)
    module = qualname.rsplit(".", 1)[0]
    inherited = []
    for base in index.class_bases.get(qualname, ()):
        if not base:
            continue
        resolved = index.resolve_class(module, base)
        if resolved and resolved in index.is_dataclass:
            inherited.extend(_dataclass_field_chain(index, resolved, seen))
    order = []
    slot = {}
    for name, has_default in inherited + list(index.dataclass_fields.get(qualname, ())):
        if name not in slot:
            order.append(name)
        slot[name] = has_default
    return [(name, slot[name]) for name in order]


def _synthesize_dataclass_inits(index):
    for qualname in sorted(index.is_dataclass):
        if qualname in index.has_init:
            continue
        fields = _dataclass_field_chain(index, qualname, set())
        positional = ["self"] + [name for name, _ in fields]
        required = ["self"] + [name for name, has_default in fields if not has_default]
        index.signatures[f"{qualname}.__init__"] = Signature(
            positional=positional,
            posonly=0,
            required_positional=required,
            vararg=False,
            kwonly=(),
            kwonly_required=(),
            kwargs=False,
            bound=1)


def _init_in_chain(index, qualname, depth=0):
    if depth > 6:
        return True
    if f"{qualname}.__init__" in index.signatures:
        return True
    module = qualname.rsplit(".", 1)[0]
    for base in index.class_bases.get(qualname, ()):
        if base is None:
            return True
        resolved = index.resolve_class(module, base)
        if resolved is None:
            return True
        if _init_in_chain(index, resolved, depth + 1):
            return True
    return False


def _synthesize_implicit_inits(index):
    for qualname in sorted(index.classes):
        if qualname not in index.class_bases:
            continue
        if _init_in_chain(index, qualname):
            continue
        index.signatures[f"{qualname}.__init__"] = Signature(
            positional=("self",),
            posonly=0,
            required_positional=("self",),
            vararg=False,
            kwonly=(),
            kwonly_required=(),
            kwargs=False,
            bound=1)


def _resolve_aliases(index):
    for _ in range(4):
        changed = False
        for local, target in index.aliases.items():
            if local in index.signatures:
                continue
            final = index.signatures.get(target)
            if final is None:
                final = index.signatures.get(index.aliases.get(target, ""))
            if final is not None:
                index.signatures[local] = final
                changed = True
            if target in index.classes and local not in index.classes:
                index.classes.add(local)
                index.aliases.setdefault(local, target)
                changed = True
        if not changed:
            break
    for local, target in list(index.aliases.items()):
        init = index.signatures.get(f"{target}.__init__")
        if init is not None and f"{local}.__init__" not in index.signatures:
            index.signatures[f"{local}.__init__"] = init


@functools.lru_cache(maxsize=8)
def sdk_index(root=None):
    root = root or corpus.PYTHON_ROOT
    index = SdkIndex()
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d != "__pycache__"]
        for filename in sorted(filenames):
            if not filename.endswith(".py"):
                continue
            path = os.path.join(dirpath, filename)
            module = _module_of(root, path)
            if not module:
                continue
            with open(path, encoding="utf-8", errors="replace") as fh:
                source = fh.read()
            try:
                tree = ast.parse(source, filename=path)
            except SyntaxError:
                continue
            _collect_module(index, module, tree)
    _synthesize_dataclass_inits(index)
    _synthesize_implicit_inits(index)
    _resolve_aliases(index)
    return index


def _class_method(index, qualname, attr, depth=0):
    if depth > 6:
        return None
    key = f"{qualname}.{attr}"
    if key in index.signatures:
        return key
    module = qualname.rsplit(".", 1)[0]
    for base in index.class_bases.get(qualname, ()):
        if not base:
            continue
        resolved = index.resolve_class(module, base)
        if not resolved:
            continue
        found = _class_method(index, resolved, attr, depth + 1)
        if found:
            return found
    return None


def _shadowed_names(tree):
    out = set()

    def store(target):
        if isinstance(target, ast.Name):
            out.add(target.id)
        elif isinstance(target, (ast.Tuple, ast.List)):
            for item in target.elts:
                store(item)
        elif isinstance(target, ast.Starred):
            store(target.value)

    for node in ast.walk(tree):
        if isinstance(node, ast.Assign):
            for target in node.targets:
                store(target)
        elif isinstance(node, (ast.AnnAssign, ast.AugAssign)):
            store(node.target)
        elif isinstance(node, (ast.For, ast.AsyncFor)):
            store(node.target)
        elif isinstance(node, ast.NamedExpr):
            store(node.target)
        elif isinstance(node, ast.comprehension):
            store(node.target)
        elif isinstance(node, ast.withitem):
            if node.optional_vars is not None:
                store(node.optional_vars)
        elif isinstance(node, ast.ExceptHandler):
            if node.name:
                out.add(node.name)
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            out.add(node.name)
            args = node.args
            for arg in (list(args.posonlyargs) + list(args.args)
                        + list(args.kwonlyargs)):
                out.add(arg.arg)
            if args.vararg:
                out.add(args.vararg.arg)
            if args.kwarg:
                out.add(args.kwarg.arg)
        elif isinstance(node, ast.ClassDef):
            out.add(node.name)
        elif isinstance(node, (ast.Global, ast.Nonlocal)):
            out.update(node.names)
        elif isinstance(node, ast.Lambda):
            args = node.args
            for arg in (list(args.posonlyargs) + list(args.args)
                        + list(args.kwonlyargs)):
                out.add(arg.arg)
    return out


def _plugin_bindings(index, tree):
    bindings = {}
    star_modules = []
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            if node.level or not node.module:
                continue
            if node.module.split(".")[0] in corpus.JAVA_ROOTS:
                continue
            if node.module not in index.modules:
                continue
            for alias in node.names:
                if alias.name == "*":
                    star_modules.append(node.module)
                    continue
                bindings[alias.asname or alias.name] = f"{node.module}.{alias.name}"
        elif isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name.split(".")[0] in corpus.JAVA_ROOTS:
                    continue
                if alias.asname:
                    if alias.name in index.modules:
                        bindings[alias.asname] = alias.name
                else:
                    root = alias.name.split(".")[0]
                    if alias.name in index.modules or root in index.modules:
                        bindings[root] = root
    shadowed = _shadowed_names(tree)
    for name in list(bindings):
        if name in shadowed:
            del bindings[name]
    for module in star_modules:
        prefix = module + "."
        for key in index.signatures:
            if not key.startswith(prefix):
                continue
            leaf = key[len(prefix):]
            if "." in leaf or leaf.startswith("_"):
                continue
            if leaf not in shadowed and leaf not in bindings:
                bindings[leaf] = key
        for key in index.classes:
            if not key.startswith(prefix):
                continue
            leaf = key[len(prefix):]
            if "." in leaf or leaf.startswith("_"):
                continue
            if leaf not in shadowed and leaf not in bindings:
                bindings[leaf] = key
    return bindings


def _class_self_base(index, bindings, node):
    resolved = []
    for base in node.bases:
        dotted = _dotted(base)
        if dotted is None:
            return None
        path = bindings.get(dotted[0])
        if path is None:
            return None
        full = ".".join((path,) + dotted[1:])
        if full not in index.classes:
            return None
        resolved.append(full)
    if len(resolved) != 1:
        return None
    return resolved[0]


def _class_own_attributes(node):
    out = set()
    for stmt in node.body:
        if isinstance(stmt, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            out.add(stmt.name)
        elif isinstance(stmt, ast.Assign):
            for target in stmt.targets:
                if isinstance(target, ast.Name):
                    out.add(target.id)
        elif isinstance(stmt, ast.AnnAssign) and isinstance(stmt.target, ast.Name):
            out.add(stmt.target.id)
    for inner in ast.walk(node):
        if isinstance(inner, ast.Assign):
            for target in inner.targets:
                if (isinstance(target, ast.Attribute)
                        and isinstance(target.value, ast.Name)
                        and target.value.id == "self"):
                    out.add(target.attr)
        elif isinstance(inner, ast.AnnAssign):
            target = inner.target
            if (isinstance(target, ast.Attribute)
                    and isinstance(target.value, ast.Name)
                    and target.value.id == "self"):
                out.add(target.attr)
    return out


def _iter_calls(node, stack, out):
    for child in ast.iter_child_nodes(node):
        if isinstance(child, ast.ClassDef):
            _iter_calls(child, stack + (child,), out)
            continue
        if isinstance(child, ast.Call):
            out.append((child, stack[-1] if stack else None))
        _iter_calls(child, stack, out)


def _resolve_call(index, bindings, classes, call, owner):
    func = call.func
    if isinstance(func, ast.Attribute) and isinstance(func.value, ast.Name) \
            and func.value.id == "self":
        if owner is None:
            return None, "self_outside_class"
        base, blocked = classes.get(id(owner), (None, None))
        if base is None:
            return None, "self_unknown_base"
        if func.attr in blocked:
            return None, "self_overridden"
        key = _class_method(index, base, func.attr)
        if key is None:
            return None, "self_attr_not_in_sdk"
        return key, None
    dotted = _dotted(func)
    if dotted is None:
        return None, "dynamic_callee"
    path = bindings.get(dotted[0])
    if path is None:
        return None, "unbound_root"
    full = ".".join((path,) + dotted[1:])
    if full in index.signatures:
        return full, None
    init = f"{full}.__init__"
    if init in index.signatures:
        return init, None
    if full in index.classes:
        return None, "class_without_init"
    if len(dotted) > 1:
        return None, "attribute_not_in_sdk"
    return None, "name_not_in_sdk"


def _describe(key):
    parts = key.split(".")
    for cut in range(len(parts) - 1, 0, -1):
        module = ".".join(parts[:cut])
        if module in ("base_plugin", "client_utils", "hook_utils", "android_utils",
                      "plugin_settings", "markdown_utils", "file_utils", "intents"):
            return f"{module}:{'.'.join(parts[cut:])}"
    if len(parts) >= 3 and parts[0] in ("ui", "elyx_runtime", "extera_utils"):
        return f"{parts[0]}.{parts[1]}:{'.'.join(parts[2:])}"
    return f"{'.'.join(parts[:-1])}:{parts[-1]}"


def _check(sig, call):
    n_pos = 0
    star = False
    for arg in call.args:
        if isinstance(arg, ast.Starred):
            star = True
        else:
            n_pos += 1
    keywords = []
    dstar = False
    for kw in call.keywords:
        if kw.arg is None:
            dstar = True
        else:
            keywords.append(kw.arg)
    unknown = []
    if not sig.kwargs:
        allowed = sig.keywordable
        unknown = [name for name in keywords if name not in allowed]
    overflow = None
    total = sig.bound + n_pos
    if not sig.vararg and not star and total > len(sig.positional):
        overflow = n_pos
    missing = []
    if not star and not dstar:
        filled = set(sig.positional[sig.bound:total])
        given = filled | set(keywords)
        for name in sig.required_positional[sig.bound:]:
            if name not in given:
                missing.append(name)
        for name in sig.kwonly_required:
            if name not in keywords:
                missing.append(name)
    return unknown, overflow, missing, keywords, (star or dstar)


def _archive_plugins():
    if not os.path.isdir(ARCHIVE_DIR):
        return ()
    out = []
    for filename in sorted(os.listdir(ARCHIVE_DIR)):
        path = os.path.join(ARCHIVE_DIR, filename)
        if not (os.path.isfile(path) and zipfile.is_zipfile(path)):
            continue
        with zipfile.ZipFile(path) as archive:
            for member in sorted(archive.namelist()):
                if not member.endswith(".py"):
                    continue
                try:
                    data = archive.read(member)
                except (KeyError, RuntimeError, zipfile.BadZipFile):
                    continue
                source = data.decode("utf-8", errors="replace")
                name = f"{filename}!{member}"
                out.append(corpus.Plugin(os.path.join(path, member), source))
                out[-1].name = name
    return tuple(out)


def _all_plugins():
    return tuple(corpus.load_corpus()) + _archive_plugins()


@functools.lru_cache(maxsize=8)
def scan(root=None):
    index = sdk_index(root)
    report = {
        "unknown_keyword": {},
        "too_many_positional": {},
        "missing_required": {},
        "widget_keywords": {},
        "widget_calls": {},
        "resolved": 0,
        "calls": 0,
        "plugins": 0,
        "unparsable": [],
        "unresolved": {},
        "loose": 0,
    }
    for plugin in _all_plugins():
        tree = plugin.tree
        if tree is None:
            report["unparsable"].append(plugin.name)
            continue
        report["plugins"] += 1
        bindings = _plugin_bindings(index, tree)
        calls = []
        _iter_calls(tree, (), calls)
        classes = {}
        for node in ast.walk(tree):
            if isinstance(node, ast.ClassDef):
                classes[id(node)] = (
                    _class_self_base(index, bindings, node),
                    _class_own_attributes(node))
        for call, owner in calls:
            report["calls"] += 1
            key, reason = _resolve_call(index, bindings, classes, call, owner)
            if key is None:
                report["unresolved"][reason] = report["unresolved"].get(reason, 0) + 1
                continue
            report["resolved"] += 1
            sig = index.signatures[key]
            unknown, overflow, missing, keywords, loose = _check(sig, call)
            if loose:
                report["loose"] += 1
            label = _describe(key)
            for name in unknown:
                report["unknown_keyword"].setdefault(
                    f"{label}:{name}", set()).add(plugin.name)
            if overflow is not None:
                report["too_many_positional"].setdefault(
                    f"{label}:{overflow}", set()).add(plugin.name)
            for name in missing:
                report["missing_required"].setdefault(
                    f"{label}:{name}", set()).add(plugin.name)
            if key.startswith(WIDGET_MODULE + ".") and key.endswith(".__init__"):
                widget = key[len(WIDGET_MODULE) + 1:-len(".__init__")]
                if "." not in widget:
                    report["widget_calls"][widget] = (
                        report["widget_calls"].get(widget, 0) + 1)
                    for name in keywords:
                        report["widget_keywords"].setdefault(
                            (widget, name), set()).add(plugin.name)
    return report


def _format(entries, keys, limit=3):
    lines = []
    for key in keys:
        plugins = sorted(entries.get(key, ()))
        sample = ", ".join(plugins[:limit]) or "?"
        lines.append(f"  {key} - {len(plugins)} plugin(s), e.g. {sample}")
    return "\n".join(lines)


def test_signature_map_is_not_silently_empty():
    index = sdk_index()
    assert len(index.signatures) >= MIN_SIGNATURES, (
        f"only {len(index.signatures)} signature(s) parsed out of "
        f"{corpus.PYTHON_ROOT}; the AST reader is broken")
    absent = [key for key in REQUIRED_SIGNATURE_KEYS
              if key not in index.signatures]
    assert not absent, (
        f"signature map lacks anchor entries {absent}; parser or SDK layout "
        f"changed")
    report = scan()
    assert report["resolved"] >= 1000, (
        f"only {report['resolved']} SDK call(s) resolved across "
        f"{report['plugins']} plugin(s); the call resolver is broken")


def test_no_new_unknown_keyword_arguments():
    report = scan()
    new, _stale = baseline.compare(KEYWORDS_BASELINE, report["unknown_keyword"])
    assert not new, (
        f"{len(new)} keyword argument(s) passed by plugins do not exist in our "
        f"SDK signatures and are not in the baseline:\n"
        + _format(report["unknown_keyword"], new))


def test_no_new_positional_overflow():
    report = scan()
    new, _stale = baseline.compare(POSITIONAL_BASELINE, report["too_many_positional"])
    assert not new, (
        f"{len(new)} call site(s) pass more positional arguments than our SDK "
        f"signatures accept and are not in the baseline:\n"
        + _format(report["too_many_positional"], new))


def test_no_new_missing_required_arguments():
    report = scan()
    new, _stale = baseline.compare(REQUIRED_BASELINE, report["missing_required"])
    assert not new, (
        f"{len(new)} required parameter(s) of our SDK are left unfilled by "
        f"plugin call sites and are not in the baseline:\n"
        + _format(report["missing_required"], new))


def test_settings_widgets_accept_what_the_catalogue_passes():
    index = sdk_index()
    report = scan()
    ranked = sorted(report["widget_calls"].items(), key=lambda kv: (-kv[1], kv[0]))
    widgets = [name for name, _count in ranked[:WIDGET_TOP]]
    assert widgets, (
        f"no {WIDGET_MODULE} constructor call resolved in the corpus at all")
    problems = []
    for widget in widgets:
        sig = index.signatures.get(f"{WIDGET_MODULE}.{widget}.__init__")
        if sig is None:
            problems.append(f"  {WIDGET_MODULE}.{widget} has no constructor")
            continue
        allowed = sig.keywordable
        for (owner, name), plugins in sorted(report["widget_keywords"].items()):
            if owner != widget or len(plugins) < WIDGET_MIN_PLUGINS:
                continue
            if name in allowed or sig.kwargs:
                continue
            sample = ", ".join(sorted(plugins)[:3])
            problems.append(
                f"  {WIDGET_MODULE}.{widget}(...) is called with {name}= by "
                f"{len(plugins)} plugin(s), e.g. {sample}, but the signature "
                f"only takes {sorted(allowed)}")
    assert not problems, (
        "settings widgets must accept every keyword the plugin catalogue "
        "relies on:\n" + "\n".join(problems))
