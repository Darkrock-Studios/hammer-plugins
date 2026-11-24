"""A culture's lists, all in resources/names/<culture>.txt: each a line "[<list>]", such as
"[feminine-1950]", then its lines, a name and its count on each, a tab between them."""
import os


def read(path):
    """The lists in the culture's file at `path`, each as its lines, by name; none when there is no file."""
    lists, lines = {}, None
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            for line in f.read().splitlines():
                if line.startswith("[") and line.endswith("]"):
                    lines = lists[line[1:-1]] = []
                elif line:
                    if lines is None:
                        raise ValueError(f"{path} has lines before its first list")
                    lines.append(line)
    return lists


def write(path, name, ranked):
    """Sets the list `name` in the culture's file at `path` to `ranked`, (name, count) pairs, keeping its other lists."""
    lists = read(path)
    lists[name] = [f"{each}\t{count}" for each, count in ranked]
    # The format first, then the rest in order, a list's decades after its list of all years.
    order = sorted(lists, key=lambda key: (key != "format", key))
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(f"[{key}]\n" + "".join(f"{line}\n" for line in lists[key]) for key in order))
    print(name, len(ranked))
