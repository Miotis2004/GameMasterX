# Authored Adventure Aggregate

This document describes the Authored Adventure aggregate: its structure, its
building blocks, and where it lives in the codebase.

## What an Adventure Is

An **Authored Adventure** is the authored-content aggregate that holds everything
a game master needs to run a session. It is the narrative and mechanical skeleton
of a campaign: the chapters and scenes to run, the places they happen in, the
people and monsters present, the goals the players pursue, the branches that model
player choice, and the rewards, secrets, lore and metadata that surround them.

The aggregate is persisted as a single MongoDB document (collection `adventures`)
and is modeled with the same conventions as the Campaign and Character aggregates:

- A **stable identifier** (`id`).
- A **schema version** (`schemaVersion`) that is bumped on backwards-incompatible
  shape changes.
- A **revision** counter (`revision`) used for optimistic concurrency control
  (managed automatically by Spring Data MongoDB via `@Version`).
- **Created/updated timestamps** (`createdAt` / `updatedAt`), refreshed by
  `Adventure#touch(Instant)`.

## Structure

The Adventure root exposes the following top-level fields:

- `title`, `description`, `gameSystem`, `recommendedPlayerCount`, `status`
- `chapters` - ordered list of chapter objects, each holding an ordered list of
  scene objects. This is the primary playback structure.
- `locations` - the places scenes take place at.
- `npcs` - non-player characters authored into the adventure.
- `creatures` - creature encounters with a difficulty tier and rating.
- `objectives` - goals the players work towards.
- `branches` - points of divergence driven by choice or conditions.
- `rewards` - XP, items and currency granted for success.
- `secretNotes` - GM-only notes gated behind reveal conditions.
- `worldFacts` - authored lore anchoring the setting.
- `tags` - classification tags with an optional category.

### Chapters and Scenes

Chapters order the adventure into acts. Each chapter has an `order` and a list of
scenes, and each scene has an `order` within its chapter. A scene is the smallest
playable unit and references the shared NPC, creature, objective and collections
by **stable ID**:

- `locationId` - where the scene takes place.
- `npcIds` / `creatureIds` - who is present.
- `objectiveId` - the objective that scene drives.
- `branchIds` - the branches the scene can spawn.

### Cross-References

Instead of embedding full NPC, creature, objective or branch definitions inside a
scene, the scene stores their stable IDs. This keeps each entity defined once and
reused across scenes, and makes the aggregate internally consistent. Branches in
turn reference the scenes they diverge from (`fromSceneIds`) and the scene they
lead to (`consequenceSceneId`).

## Where It Lives

| Concern                 | Location                                                        |
| ----------------------- | --------------------------------------------------------------- |
| Shared client/server types | `contracts/adventure.ts`                                     |
| Aggregate root + value objects | `server/.../adventure/model/Adventure.java`              |
| API-facing projection   | `server/.../adventure/model/AdventureDto.java`                  |
| Persistence             | `server/.../adventure/repository/AdventureRepository.java`      |

## Schema Version

The document begins at schema version `1` (see `Adventure.SCHEMA_VERSION`). Bump this
constant when the persisted shape of the aggregate changes in a backwards
incompatible way.
