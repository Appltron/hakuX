"""Kernel import census from census.sh output.

Reads the "== <image>" and "hakuX-native-imports: title <id> <ordinals>"
lines that tools/native/census.sh prints and writes a Markdown report: per
title, how many kernel exports it imports; the union over a chosen set of
titles; and that union grouped by name prefix (subsystem).

Usage: python imports.py census.txt [--set "Project Gotham,Fable,Otogi,Dead or Alive"]
"""

import argparse
import collections
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
TABLE = os.path.join(ROOT, "native", "kernel", "kexports_table.h")


def load_names():
    names = {}
    with open(TABLE, encoding="utf-8") as f:
        for line in f:
            m = re.match(r'NC_KEXPORT\((\d+), "([^"]+)", (\w+), (\d+)\)', line)
            if m:
                names[int(m.group(1))] = (m.group(2), m.group(3))
    return names


def parse(path):
    titles = []  # (image, title id, set(ordinals))
    image = None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if line.startswith("== "):
                image = os.path.basename(line[3:])
            m = re.search(r"hakuX-native-imports: title (\w+) ([\d,]+)", line)
            if m and image:
                titles.append((image, m.group(1), {int(x) for x in m.group(2).split(",")}))
    return titles


def prefix(name):
    m = re.match(r"(Xc|Xe|Xbox|Av|Dbg|Ex|Hal|Io|Ke|Kf|Ki|Mm|Nt|Ob|Ps|Rtl|Interlocked|Kd|Phy|Write|Read|Launch|Idex|Hal)", name)
    return m.group(1) if m else "other"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("census")
    ap.add_argument("--set", default="Project Gotham,Fable,Otogi,Dead or Alive")
    args = ap.parse_args()
    names = load_names()
    titles = parse(args.census)
    picks = [p.strip().lower() for p in args.set.split(",") if p.strip()]

    out = ["| Title | Title ID | Kernel imports |", "|---|---|---|"]
    chosen = set()
    for image, tid, ords in titles:
        out.append("| %s | %s | %d |" % (image.replace(".xiso.iso", "").replace(".iso", ""), tid, len(ords)))
        if any(p in image.lower() for p in picks):
            chosen |= ords
    every = set().union(*(o for _, _, o in titles)) if titles else set()
    out += ["", "Union over the chosen set (%s): **%d** exports; over all titles: **%d**." %
            (args.set, len(chosen), len(every)), ""]

    groups = collections.defaultdict(list)
    for o in sorted(chosen):
        name, abi = names.get(o, ("?%d" % o, "?"))
        groups[prefix(name)].append("%s (%d)" % (name, o))
    out += ["| Prefix | Count | Exports |", "|---|---|---|"]
    for g in sorted(groups, key=lambda k: -len(groups[k])):
        out.append("| %s | %d | %s |" % (g, len(groups[g]), ", ".join(groups[g])))
    sys.stdout.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
