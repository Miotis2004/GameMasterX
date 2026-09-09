# Adventure Package Import

This document describes the validated import of local adventure packages and the
safeguards enforced to keep the operation safe and predictable.

## What Is Imported

A **local adventure package** is a ZIP archive whose manifest is a JSON document
(by default the entry `adventure.json`) that mirrors the `Adventure` aggregate
(see `ADVENTURE.md`). The package may also carry referenced asset files, which
are staged alongside the manifest.

Import is exposed as:

```
POST /api/adventures/import
Content-Type: multipart/form-data

package=<zip archive>          # required
overwrite=false                # optional; default false
```

The caller must be authenticated. Successful import returns `201 Created` with
the imported `AdventureDto`.

## Safeguards

Import proceeds in stages, each of which can reject the package *before* any
mutation happens:

1. **Structure & identifier validation** — the manifest is parsed and validated
   by `AdventurePackageValidator`. It enforces required root fields, a stable
   import-safe identifier (`id`: 1–128 chars, lower-case letters, digits,
   hyphens and underscores), well-formed enumerations, non-negative orders and
   counts, a positive recommended player count when set, and non-empty
   secret-note identifiers.

2. **Identifier collisions within the package** — every identifier across the
   whole aggregate (chapters, scenes, locations, NPCs, creatures, objectives,
   branches, rewards, secret notes, world facts, tags) must be unique. Duplicate
   identifiers are rejected. Scene/branch cross-references must resolve to
   entities that exist in the same package (referential integrity).

3. **Unsafe file paths** — every archive entry is resolved against the staging
   directory and normalized. Any entry that would escape the staging root
   (traversal via `../`, absolute paths, drive letters, etc.) is rejected with a
   `400 BAD_REQUEST`.

4. **Oversized input** — the archive size, any single entry size, and the total
   number of entries are bounded. Oversized input is rejected up front, before
   the archive is fully read. Limits and the staging directory are configured in
   `application.properties`:
   - `game.master.x.adventure.import.max-archive-bytes` (default 20 MiB)
   - `game.master.x.adventure.import.max-entry-bytes` (default 5 MiB)
   - `game.master.x.adventure.import.max-entries` (default 2000)
   - `game.master.x.adventure.import.staging-dir` (default `./tmp/adventure-imports`)
   - `spring.servlet.multipart.max-file-size` / `max-request-size` (20 MB)

5. **Silent overwrites prevented** — import is create-by-default. Importing a
   package whose `id` already exists is rejected with a `409 Conflict`. Supply
   `overwrite=true` to replace an existing adventure with the same `id`; the
   update is audited through the revision counter and updated timestamp.

## Error Responses

| Condition                                   | Status | Error code    |
| ------------------------------------------- | ------ | ------------- |
| Missing/empty package, bad structure, invalid identifiers, unsafe path, oversized input, malformed manifest | `400`  | `VALIDATION_ERROR` |
| `id` collision and `overwrite=false`        | `409`  | `CONFLICT`    |
| Unauthenticated caller                      | `401`  | `UNAUTHORIZED` |
| Insufficient membership role                | `403`  | `FORBIDDEN`   |

The offending package location (field, entry name, or `id`) is included in the
response `fieldErrors` when known.

## Implementation

| Concern                          | Location                                                        |
| -------------------------------- | --------------------------------------------------------------- |
| Package validation rules         | `server/.../adventure/AdventurePackageValidator.java`           |
| Import orchestration             | `server/.../adventure/service/AdventureImporter.java`           |
| HTTP endpoint                    | `server/.../adventure/controller/AdventureController.java`      |
| Exception types                  | `AdventurePackageImportException`, `AdventureImportConflictException` |
