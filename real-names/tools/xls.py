"""Reads the cells of a legacy Excel workbook (.xls, BIFF8), for the scripts whose sources publish only
that: its text and numbers, not its formats or formulas."""
import struct

FREE, END = 0xFFFFFFFF, 0xFFFFFFFE


def stream(data, wanted):
    """The stream named `wanted` in a compound file's bytes `data`."""
    shift, = struct.unpack_from("<H", data, 0x1E)
    size = 1 << shift
    first_directory, cutoff = struct.unpack_from("<I", data, 0x30)[0], struct.unpack_from("<I", data, 0x38)[0]
    difat_sector, difat_count = struct.unpack_from("<II", data, 0x44)
    fat_sectors = list(struct.unpack_from("<109I", data, 0x4C))
    for _ in range(difat_count):
        entries = struct.unpack_from(f"<{size // 4}I", data, size * (difat_sector + 1))
        fat_sectors += entries[:-1]
        difat_sector = entries[-1]
    fat = []
    for sector in fat_sectors:
        if sector not in (FREE, END):
            fat += struct.unpack_from(f"<{size // 4}I", data, size * (sector + 1))

    def chain(sector):
        out = bytearray()
        while sector != END:
            out += data[size * (sector + 1):size * (sector + 2)]
            sector = fat[sector]
        return bytes(out)

    directory = chain(first_directory)
    for at in range(0, len(directory), 128):
        length, = struct.unpack_from("<H", directory, at + 0x40)
        name = directory[at:at + max(length - 2, 0)].decode("utf-16-le")
        if name == wanted:
            start, length = struct.unpack_from("<II", directory, at + 0x74)
            if length < cutoff:
                raise ValueError(f"{wanted} is in the mini stream, which this does not read")
            return chain(start)[:length]
    raise ValueError(f"no {wanted} stream")


def records(data):
    """The workbook stream's records, as (offset, type, contents), each CONTINUE record's contents
    following its record's as a further part: (offset, type, [contents, continued, ...])."""
    at, last = 0, None
    while at + 4 <= len(data):
        kind, length = struct.unpack_from("<HH", data, at)
        body = data[at + 4:at + 4 + length]
        if kind == 0x003C and last is not None:
            last[2].append(body)
        else:
            if last is not None:
                yield last
            last = (at, kind, [body])
        at += 4 + length
    if last is not None:
        yield last


def strings(parts):
    """The shared strings of an SST record's parts. A string's characters may run on into the next
    part, which then starts with a byte saying whether they are one byte each or two."""
    part, at = 0, 8
    count, = struct.unpack_from("<I", parts[0], 4)
    out = []

    def take(n):
        nonlocal part, at
        got = bytearray()
        while n:
            if at == len(parts[part]):
                part, at = part + 1, 0
            piece = parts[part][at:at + n]
            got += piece
            at += len(piece)
            n -= len(piece)
        return bytes(got)

    for _ in range(count):
        if at == len(parts[part]):
            part, at = part + 1, 0
        chars, flags = struct.unpack("<HB", take(3))
        runs = struct.unpack("<H", take(2))[0] if flags & 8 else 0
        extra = struct.unpack("<I", take(4))[0] if flags & 4 else 0
        text, wide = [], flags & 1
        while chars:
            if at == len(parts[part]):
                part, at = part + 1, 0
                wide = parts[part][0] & 1
                at = 1
            n = min(chars, (len(parts[part]) - at) // (2 if wide else 1))
            raw = parts[part][at:at + n * (2 if wide else 1)]
            text.append(raw.decode("utf-16-le" if wide else "latin-1"))
            at += len(raw)
            chars -= n
        take(4 * runs + extra)
        out.append("".join(text))
    return out


def rk(value):
    """An RK number's value."""
    number = struct.unpack("<d", struct.pack("<Q", (value & 0xFFFFFFFC) << 32))[0] if not value & 2 else (value >> 2) - ((value & 0x80000000) >> 1)
    return number / 100 if value & 1 else number


def text(number):
    return str(int(number)) if number == int(number) else str(number)


def sheets(path):
    """Each sheet of the workbook at `path`, as (its name, its rows), a row a list of its cells' text."""
    with open(path, "rb") as f:
        data = stream(f.read(), "Workbook")
    shared, names, cells = [], {}, {}
    current = None
    for at, kind, parts in records(data):
        body = parts[0]
        if kind == 0x0085:
            offset, = struct.unpack_from("<I", body, 0)
            length, flags = body[6], body[7]
            names[offset] = body[8:8 + length * 2].decode("utf-16-le") if flags & 1 else body[8:8 + length].decode("latin-1")
        elif kind == 0x00FC:
            shared = strings(parts)
        elif kind == 0x0809:
            current = cells.setdefault(at, {}) if at in names else None
        elif current is None:
            continue
        elif kind == 0x00FD:
            row, column, _, index = struct.unpack_from("<HHHI", body)
            current[row, column] = shared[index]
        elif kind == 0x0203:
            row, column, _, number = struct.unpack_from("<HHHd", body)
            current[row, column] = text(number)
        elif kind == 0x027E:
            row, column, _, value = struct.unpack_from("<HHHI", body)
            current[row, column] = text(rk(value))
        elif kind == 0x00BD:
            row, column = struct.unpack_from("<HH", body)
            for i in range((len(body) - 6) // 6):
                current[row, column + i] = text(rk(struct.unpack_from("<I", body, 4 + 6 * i + 2)[0]))
        elif kind == 0x0204:
            row, column, _, length, flags = struct.unpack_from("<HHHHB", body)
            current[row, column] = body[9:9 + length * 2].decode("utf-16-le") if flags & 1 else body[9:9 + length].decode("latin-1")
        elif kind == 0x0006:
            row, column = struct.unpack_from("<HH", body)
            # A formula's result is a number unless its last two bytes are 0xFFFF.
            if body[12:14] != b"\xff\xff":
                current[row, column] = text(struct.unpack_from("<d", body, 6)[0])
    for offset, name in names.items():
        found = cells.get(offset, {})
        rows = [[] for _ in range(1 + max((row for row, _ in found), default=-1))]
        for (row, column), value in sorted(found.items()):
            rows[row] += [""] * (column - len(rows[row])) + [value]
        yield name, rows
