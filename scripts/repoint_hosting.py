"""Repoints every hosting reference after the repository was renamed and split.

Three different things used to share one path, and they now point at different
places:

  the source repository   drente748/sawn          -> drente748/sawn
  the manifest URL        ...github.io/warden/...    -> ...github.io/sawn-releases/...
  the APK download URL    github.com/.../warden/... -> github.com/.../sawn-releases/...

The order matters: the two hosting URLs have to move before the plain
repository path, or the first replacement would swallow them.
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

REPLACEMENTS = [
    ("drente748.github.io/sawn-releases", "drente748.github.io/sawn-releases"),
    ("github.com/drente748/sawn-releases/releases", "github.com/drente748/sawn-releases/releases"),
    ("drente748.github.io/sawn-releases", "drente748.github.io/sawn-releases"),
    ("drente748/sawn", "drente748/sawn"),
]

TEXT_SUFFIXES = (".md", ".kt", ".kts", ".ps1", ".py", ".yml", ".yaml", ".xml", ".json", ".html")


def main():
    changed = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or ".git" in path.parts:
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            raw = path.read_bytes()
        except OSError:
            continue
        has_bom = raw.startswith(b"\xef\xbb\xbf")
        text = raw.decode("utf-8-sig" if has_bom else "utf-8")
        updated = text
        for old, new in REPLACEMENTS:
            updated = updated.replace(old, new)
        if updated != text:
            path.write_bytes((b"\xef\xbb\xbf" if has_bom else b"") + updated.encode("utf-8"))
            changed.append(str(path.relative_to(ROOT)))
    print("rewritten %d file(s):" % len(changed))
    for name in changed:
        print("  " + name)

    leftovers = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or ".git" in path.parts:
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            content = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if "drente748/sawn" in content or "drente748.github.io/sawn-releases" in content:
            leftovers.append(str(path.relative_to(ROOT)))
    if leftovers:
        print("")
        print("STILL POINTING AT THE OLD PATHS:")
        for name in leftovers:
            print("  " + name)
        return 1
    print("")
    print("no reference to the old paths remains")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
