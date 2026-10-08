"""Reads the cells of an Excel workbook, for the scripts whose sources publish one."""
import re
import zipfile
import xml.etree.ElementTree as ET

N = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def sheets(path):
    """Each sheet of the workbook at `path`, as (its name, its rows), a row a list of its cells' text."""
    with zipfile.ZipFile(path) as z:
        strings = []
        if "xl/sharedStrings.xml" in z.namelist():
            strings = ["".join(t.text or "" for t in item.iter(N + "t")) for item in ET.parse(z.open("xl/sharedStrings.xml")).getroot()]
        names = [sheet.get("name") for sheet in ET.parse(z.open("xl/workbook.xml")).getroot().iter(N + "sheet")]
        files = [name for name in z.namelist() if re.fullmatch(r"xl/worksheets/sheet\d+\.xml", name)]
        # In the workbook's order where its sheets' files are numbered in it, as they are when written whole.
        for name, file in zip(names, sorted(files, key=lambda file: int(re.search(r"\d+", file).group()))):
            rows = []
            for row in ET.parse(z.open(file)).getroot().iter(N + "row"):
                cells = []
                for cell in row.iter(N + "c"):
                    # A row leaves out its empty cells, so each goes where its reference's letters say.
                    column = 0
                    for letter in re.match(r"[A-Z]*", cell.get("r", "")).group():
                        column = column * 26 + ord(letter) - ord("A") + 1
                    cells += [""] * (column - 1 - len(cells))
                    value, inline = cell.find(N + "v"), cell.find(N + "is")
                    if value is not None:
                        cells.append(strings[int(value.text)] if cell.get("t") == "s" else value.text)
                    else:
                        cells.append("".join(t.text or "" for t in inline.iter(N + "t")) if inline is not None else "")
                rows.append(cells)
            yield name, rows
