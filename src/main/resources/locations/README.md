# Location Directory

The bundled `india-ulb-directory.json` is the single source of places a new
account can choose from. Nothing at registration time asks a third party and
no list is maintained by hand: this directory, generated from the official
workbook below, is what `IndianLocationService` validates every sign-up
against and what `GET /api/locations` serves to the sign-up form.

## Source

| | |
|---|---|
| File | `source/Ulb_By_State_2026-10-18_16-34-45.xlsx` |
| Vintage | Local Government Directory export dated **2026-10-18 16:34** |
| SHA-256 | `4a61471effef08232b6cf182297c2121c6e75803e5970de743a74c133a7b8b5a` |

The hash is also embedded in the generated JSON (`source.sha256`), so a
directory and the workbook it claims to come from can be checked against each
other:

```bash
sha256sum src/main/resources/locations/source/Ulb_By_State_2026-10-18_16-34-45.xlsx
```

## Regenerating

From the backend root, with nothing but a standard Python 3 (the script reads
the workbook as a zip of XML — no openpyxl, no other dependency):

```bash
python -I scripts/build_location_directory.py
```

The generator is deterministic: every level is sorted by code and ULB-type
spellings are canonicalised, so regenerating from the same workbook produces a
byte-identical file and a clean diff. It prints the counts below; they are
also pinned by `IndianLocationServiceTest`, so a truncated regeneration fails
the suite rather than shipping quietly.

## What is in it

| Count | |
|---|---|
| 35 | states and Union Territories |
| 752 | districts |
| 5,059 | unique urban local bodies |
| 5,094 | district/ULB rows — a ULB the workbook lists under several districts stays valid under each |
| 18 | ULB types (Municipal Corporation, Municipal Council, Nagar Panchayat, …) |

Shape: `states[] → { code, name, districts[] } → { code, name, ulbs[] } →
{ code, name, type }`. Codes are strings and are the only thing registration
submits; names and types are lookups, never inputs.

## Coverage limitation

The directory contains **urban local bodies only, as the LGD workbook lists
them**. A place without an urban local body — a rural village, a census town
with no ULB — is not in the workbook and therefore cannot be selected at
registration. That is a deliberate trade-off: the alternative was accepting
free text again, which is what let `Punjab + Gaya` through. When a newer LGD
export needs to be picked up, replace the workbook in `source/`, rerun the
generator, and update the counts pinned in `IndianLocationServiceTest`.

## Behaviour when it is missing or malformed

The application still starts — nothing else depends on this file — but every
lookup answers `503` through `LocationDataUnavailableException`, and the
sign-up form refuses to submit. A broken dataset must never quietly mean "no
location rules today".
