"""
Generate the Clean Bharat location directory from the supplied LGD workbook.

The workbook (Ulb_By_State_<date>.xlsx) is the single authoritative source. This
script reads it with the standard library only - an xlsx is a zip of XML - and
writes a deterministic JSON directory that the backend serves and validates
against. Districts, ULBs and types come only from the workbook. A documented
state-only supplement lists Lakshadweep without inventing municipal areas.

Run from the backend project root:

    python -I scripts/build_location_directory.py

Re-run this whenever a new workbook is supplied. The output is sorted, so a diff
between two versions shows exactly what changed.
"""

import collections
import hashlib
import json
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"

BACKEND = Path(__file__).resolve().parent.parent
SOURCE = BACKEND / "src/main/resources/locations/source/Ulb_By_State_2026-10-18_16-34-45.xlsx"
OUTPUT = BACKEND / "src/main/resources/locations/india-ulb-directory.json"

# The header the workbook is expected to carry, in order. Checked rather than
# assumed, so a reshaped export fails loudly instead of producing a silently
# wrong directory.
EXPECTED_HEADER = [
    "S.No.",
    "State Code",
    "State Name (In English)",
    "District Code",
    "District Name(In English)",
    "ULB Code",
    "ULB Name",
    "ULB Type",
]


def cell_text(cell):
    """Text of one cell, handling the workbook's inline strings and numbers."""
    if cell.get("t") == "inlineStr":
        return "".join(t.text or "" for t in cell.iter(NS + "t")).strip()
    value = cell.find(NS + "v")
    return value.text.strip() if value is not None and value.text else ""


def code(value):
    """LGD codes arrive as '35.0' because the cells are numeric - store '35'."""
    text = value.strip()
    if text.endswith(".0"):
        text = text[:-2]
    if not text.isdigit() and text:
        raise ValueError(f"unexpected non-numeric code: {value!r}")
    return text


# Spellings that differ only in capitalisation, folded onto the one that
# dominates the workbook. Listed explicitly rather than case-folded blindly:
# 'Municipality(Nagar Palika)' is a genuinely different type from 'Municipality'
# and must survive untouched.
TYPE_CANONICAL = {
    "municipal council": "Municipal Council",
    "notified area council": "Notified Area Council",
}

# States/UTs of the LGD master list that this ULB export carries no rows for.
# The export's silence says nothing about whether the place exists - it only
# means this workbook, an urban-local-body export, lists no ULB under it. The
# state still belongs in the selector, with an empty district list rather than
# invented records. Lakshadweep (LGD state code 31) is the only such state in
# the 2026-10-18 export; Ladakh (37) IS covered by workbook rows.
STATES_WITHOUT_ULB_ROWS = [
    {"code": "31", "name": "Lakshadweep"},
]


def canonical_type(value):
    """
    Normalise spelling variants of the same ULB type.

    The workbook carries 'MUNICIPALITY' and 'Municipality', 'MUNICIPAL
    CORPORATION' and 'Municipal Corporation', and a stray trailing tab in
    'Nagar Panchayat\t'. Those are the same type, so they are folded together.
    Anything not folded keeps its exact wording.
    """
    value = " ".join(value.split())  # collapse runs of whitespace, drop tabs
    if value and value.isupper():
        return value.title()
    return TYPE_CANONICAL.get(value.lower(), value)


def read_rows(workbook):
    with zipfile.ZipFile(workbook) as archive:

        # Only one worksheet is expected. More than one means the parser is
        # guessing which sheet is authoritative, so refuse instead.
        workbook_xml = ET.fromstring(archive.read("xl/workbook.xml"))
        sheets = workbook_xml.findall(f".//{NS}sheet")
        if len(sheets) != 1:
            raise ValueError(f"expected exactly one worksheet, found {len(sheets)}")

        sheet = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
        rows = [r for r in sheet.findall(f".//{NS}sheetData/{NS}row")]

        # Row 1 is the title banner, row 2 the header.
        header = [cell_text(c) for c in rows[1].findall(NS + "c")]
        if header != EXPECTED_HEADER:
            raise ValueError(f"unexpected header:\n  expected {EXPECTED_HEADER}\n  found    {header}")

        for row in rows[2:]:
            values = [cell_text(c) for c in row.findall(NS + "c")]
            if len(values) != len(EXPECTED_HEADER):
                raise ValueError(f"row {row.get('r')} has {len(values)} columns")
            yield row.get("r"), values


def main():
    if not SOURCE.exists():
        sys.exit(f"source workbook not found: {SOURCE}")

    states = collections.OrderedDict()       # stateCode -> {code, name, districts}
    districts = collections.OrderedDict()    # (stateCode, districtCode) -> {code, name, ulbs}
    seen = set()                             # every (state, district, ulb) triple
    duplicate_triples = []

    for row_ref, values in read_rows(SOURCE):
        _, state_code, state_name, district_code, district_name, ulb_code, ulb_name, ulb_type = values

        state_code, district_code, ulb_code = code(state_code), code(district_code), code(ulb_code)
        if not (state_name and district_name and ulb_name and ulb_type):
            raise ValueError(f"row {row_ref} has a blank value: {values}")

        key = (state_code, district_code, ulb_code)

        # A ULB appearing under several districts is kept under each of them -
        # the source is the authority on district association, not this script.
        # An exact repeated triple is a duplicated line, recorded and skipped.
        if key in seen:
            duplicate_triples.append(key)
            continue
        seen.add(key)

        state = states.setdefault(state_code, {"code": state_code, "name": state_name, "districts": []})
        if state["name"] != state_name:
            raise ValueError(f"state {state_code} named both {state['name']!r} and {state_name!r}")

        district = districts.setdefault(
            (state_code, district_code),
            {"code": district_code, "name": district_name, "ulbs": []},
        )
        if district["name"] != district_name:
            raise ValueError(
                f"district {state_code}/{district_code} named both "
                f"{district['name']!r} and {district_name!r}"
            )

        district["ulbs"].append({
            "code": ulb_code,
            "name": ulb_name,
            "type": canonical_type(ulb_type),
        })

    # Complete the state/UT enumeration. A code the workbook does cover is
    # already keyed by its rows and wins; a name disagreement between the
    # workbook and the supplement means one of them is wrong, so refuse.
    for entry in STATES_WITHOUT_ULB_ROWS:
        existing = states.get(entry["code"])
        if existing is None:
            states[entry["code"]] = {"code": entry["code"], "name": entry["name"], "districts": []}
        elif existing["name"] != entry["name"]:
            raise ValueError(
                f"state {entry['code']} is {existing['name']!r} in the workbook "
                f"but {entry['name']!r} in the supplement"
            )

    # Attach districts to states, then sort each level by code so the file is
    # stable across runs regardless of workbook ordering.
    for (state_code, _), district in districts.items():
        states[state_code]["districts"].append(district)

    for state in states.values():
        state["districts"].sort(key=lambda d: d["code"])
        for district in state["districts"]:
            district["ulbs"].sort(key=lambda u: (u["code"], u["name"]))

    ordered_states = sorted(states.values(), key=lambda s: s["code"])

    ulb_codes = {u["code"] for s in ordered_states for d in s["districts"] for u in d["ulbs"]}
    types = sorted({u["type"] for s in ordered_states for d in s["districts"] for u in d["ulbs"]})
    total_rows = sum(len(d["ulbs"]) for s in ordered_states for d in s["districts"])

    document = {
        "version": SOURCE.stem,
        "source": {
            "file": SOURCE.name,
            "sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
            "description": "Local Government Directory export: state and district wise urban local bodies",
        },
        "counts": {
            "states": len(ordered_states),
            "districts": sum(len(s["districts"]) for s in ordered_states),
            "ulbs": len(ulb_codes),
            "districtUlbRows": total_rows,
        },
        "states": ordered_states,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(document, ensure_ascii=False, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )

    print(f"wrote {OUTPUT}")
    print(f"  states                 {len(ordered_states)}")
    print(f"  districts              {document['counts']['districts']}")
    print(f"  unique ULB codes       {len(ulb_codes)}")
    print(f"  district/ULB rows      {total_rows}")
    print(f"  exact duplicate triples skipped  {len(duplicate_triples)}")
    print(f"  ULB types ({len(types)}): {', '.join(types)}")


if __name__ == "__main__":
    main()
