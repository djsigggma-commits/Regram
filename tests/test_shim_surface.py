import os

import pytest

import baseline
import corpus
import javaapi

SHIM_ROOT = os.path.join(corpus.JAVA_ROOT, "com", "exteragram", "messenger")
BASELINE = "shim_surface.json"

IMPL_OF = {
    "com.exteragram.messenger.plugins.Plugin":
        "app.regram.plugins.Plugin",
    "com.exteragram.messenger.plugins.PluginsController":
        "app.regram.plugins.PluginsController",
    "com.exteragram.messenger.plugins.PythonPluginsEngine":
        "app.regram.plugins.PythonPluginsEngine",
    "com.exteragram.messenger.plugins.ui.PluginSettingsActivity":
        "app.regram.plugins.ui.PluginSettingsActivity",
    "com.exteragram.messenger.plugins.ui.PluginsActivity":
        "app.regram.plugins.ui.PluginsActivity",
}

OBJECT_METHODS = {"equals", "hashCode", "toString", "clone", "finalize",
                  "getClass", "notify", "notifyAll", "wait"}


def _reference_type(fqcn):
    path = os.path.join(javaapi.REFERENCE_ROOT, *fqcn.split(".")) + ".java"
    if not os.path.isfile(path):
        return None
    package = fqcn.rsplit(".", 1)[0]
    return javaapi._match(javaapi._parse_cached(path, package), fqcn)


def _methods(jtype):
    if jtype is None:
        return set()
    out = set()
    for name, signatures in jtype.methods.items():
        if name in OBJECT_METHODS or "$" in name:
            continue
        for params in signatures:
            out.add((name, len(params)))
    return out


def _shim_classes():
    out = []
    for dirpath, _, files in os.walk(SHIM_ROOT):
        for name in sorted(files):
            if not name.endswith(".java") or name == "package-info.java":
                continue
            rel = os.path.relpath(os.path.join(dirpath, name), corpus.JAVA_ROOT)
            out.append(rel[: -len(".java")].replace(os.sep, "."))
    return sorted(out)


def gaps():
    found = []
    for fqcn in _shim_classes():
        impl_name = IMPL_OF.get(fqcn)
        if impl_name is None:
            continue
        reference = _reference_type(fqcn)
        impl = javaapi.type_of(impl_name)
        shim = javaapi.type_of(fqcn)
        if reference is None or impl is None or shim is None:
            continue
        missing = (_methods(reference) & _methods(impl)) - _methods(shim)
        for name, arity in missing:
            found.append(f"{fqcn}#{name}/{arity}")
    return sorted(found)


def test_shims_declare_what_the_reference_declares_and_we_implement():
    new, gone = baseline.compare(BASELINE, gaps())
    if not new:
        return
    lines = "\n".join(f"  {entry}" for entry in new[:40])
    pytest.fail(
        "методы есть у эталона и у нашей реализации, но не объявлены в заглушке: "
        f"{len(new)}\n{lines}\n"
        "dex-модули плагинов зовут их по типу заглушки — метод, объявленный только "
        "у наследника, такой вызов не находит и падает NoSuchMethodError\n"
        f"(в базе {BASELINE} закрыто прежних записей: {len(gone)})")


def test_every_mapped_shim_resolves():
    unresolved = [name for name in IMPL_OF.values()
                  if javaapi.type_of(name) is None]
    assert not unresolved, unresolved
    assert not javaapi.PARSE_ERRORS, javaapi.PARSE_ERRORS[:5]
