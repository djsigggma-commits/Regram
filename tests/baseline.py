import json
import os

BASELINE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "baselines")


def load(name, directory=None):
    path = os.path.join(directory or BASELINE_DIR, name)
    if not os.path.isfile(path):
        return {}
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def save(name, payload, directory=None):
    target = directory or BASELINE_DIR
    os.makedirs(target, exist_ok=True)
    path = os.path.join(target, name)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write("\n")


def compare(name, found, directory=None):
    known = set(load(name, directory).get("known", ()))
    found = set(found)
    return sorted(found - known), sorted(known - found)
