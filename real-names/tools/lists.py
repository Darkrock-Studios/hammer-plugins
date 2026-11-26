"""A culture's lists, all in resources/names/<culture>.txt: each a line "[<list>]", such as
"[feminine-1950]", then its lines, a name and its count on each, a tab between them."""
import os
from collections import Counter


def read(path):
    """The lists in the culture's file at `path`, each as its lines, by name; none when there is no file."""
    lists, lines = {}, None
    if os.path.exists(path):
        # Split as the plugin does, only at line ends: splitlines() also breaks at characters such as U+2028.
        with open(path, encoding="utf-8-sig") as f:
            for line in (line.rstrip("\r") for line in f.read().split("\n")):
                if line.startswith("[") and line.endswith("]"):
                    lines = lists[line[1:-1]] = []
                elif line.strip(" \t"):
                    if lines is None:
                        raise ValueError(f"{path} has lines before its first list")
                    lines.append(line)
    return lists


def write(path, name, counts, most=None):
    """Sets the list `name` in the culture's file at `path` to the `most` most common of `counts`, (name, count)
    pairs, keeping its other lists; with none, the file has no such list."""
    ranked = sorted(counts, key=lambda item: (-item[1], item[0]))
    twice = {each for each, count in Counter(each for each, _ in ranked).items() if count > 1}
    if twice:
        raise ValueError(f"{name} has {sorted(twice)[:5]} more than once")
    ranked = ranked[:most]
    lists = read(path)
    if ranked:
        lists[name] = [f"{each}\t{count}" for each, count in ranked]
    elif lists.pop(name, None) is None:
        return
    save(path, lists)
    print(name, len(ranked))


def save(path, lists):
    """Writes `lists` to the culture's file at `path`."""
    # The format first, then the rest in order, a list's decades after its list of all years.
    order = sorted(lists, key=lambda key: (key != "format", key))
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(f"[{key}]\n" + "".join(f"{line}\n" for line in lists[key]) for key in order))
