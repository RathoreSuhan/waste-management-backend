# Location Directory

The bundled `india-ulb-directory.json` is the single source of places a new
account can choose from. Nothing at registration time asks a third party.
The workbook below is the only source of district/ULB records; a documented
state-only supplement adds Lakshadweep (31), with no invented municipal areas.
`IndianLocationService` validates every sign-up against this directory, and
`GET /api/locations` serves it to the sign-up form.

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
| 36 | states and Union Territories |
| 752 | ULB-linked district records in the supplied export |
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

Within that scope, the workbook is a *ULB* export covering 35 States/UTs.
**Lakshadweep (state code 31)** is included separately in the selector with
`districts: []`: this means the supplied workbook contains no ULB-linked
district records for it, not that Lakshadweep has no administrative district.
The form says, "No supported municipal areas are available for this State/UT
in the current directory." No district or ULB records have been fabricated.
Ladakh remains state code **37**, with its two workbook district/ULB rows.
A future export covering Lakshadweep uses its real rows automatically; a
conflicting code/name fails generation rather than silently overwriting it.

## Municipal routing compatibility

Since the location refactor, routing is **per report**. A report that carries
a directory ULB code (`garbage_reports.ulb_code`) is routed by that code and
only by that code - `MunicipalCorporationRepository.findByUlbCode` - and a
miss is a loud HTTP 404 that names the municipal area and states that the
report was not saved. It is never redirected to another corporation and never
matched by a city alias.

A legacy report without a code resolves its destination from the stored city
value exactly as before: the workbook's official ULB name run through
`LocationUtil` (for example ULB `248325`, `S.A.S.Nagar - Mohali`, stores as
`S.a.s.nagar - Mohali`), matched case-insensitively against
`municipal_corporations.city`. That legacy column is therefore preserved
verbatim even when an office is later mapped to its ULB code - the T3
preflight mapping writes `ulb_code` and never rewrites `city`, so legacy
reports keep reaching their destination while new ones route by code.

LGD ULB records do not create Clean Bharat municipal accounts or contacts.
An administrator must configure the destination separately - through the
admin form's directory cascade, which stores the canonical ULB name and code
and guards one corporation per ULB. A missing match returns HTTP 404 naming
the report city; it is not redirected elsewhere. Report creation and
default-assignment creation share a transaction, so the exception rolls back
the report insert before any assignment is saved. The report service also
attempts to delete the uploaded image; external Cloudinary deletion is
best-effort, not a database-transaction guarantee.

Before the code-backed routing contract goes live, the deployment preflight
maps and audits every existing corporation (unique `ulb_code`, coverage of
recent reports' locations). The preflight covers the explicitly audited
locations only - a directory ULB nobody has onboarded is refused with the
"not onboarded yet" error until an administrator maps it.

The files live under `classpath:locations`, not Spring's default `static`,
`public`, `resources` or `META-INF/resources` web roots. The JSON is exposed
intentionally by `/api/locations`; the source workbook is not a public asset.

## Behaviour when it is missing or malformed

The application still starts — nothing else depends on this file — but every
lookup answers `503` through `LocationDataUnavailableException`, and the
sign-up form refuses to submit. A broken dataset must never quietly mean "no
location rules today".
