# GameMasterX Documentation

This directory contains project documentation, architecture decisions, and milestone planning.

## Contents

- `MILESTONE_SCOPE.md` - Current and planned milestones with acceptance criteria
- `ARCHITECTURE.md` - High-level architecture overview and design principles
- `CAMPAIGN_INVITES.md` - Invite and join-code mechanics
- `MEMBERSHIP.md` - Membership lifecycle: acceptance, revocation, and role changes
- `AUTHORIZATION.md` - Backend role-based authorization enforcement boundary
- `CLIENT_AUTHORIZATION.md` - Client-side role-based UI visibility and why visibility is not enforcement
- `CHARACTER_SHEET_VALIDATION.md` - Deterministic SRD 5.x validation of character sheets
- `ADVENTURE.md` - The Authored Adventure aggregate: chapters, scenes, locations, NPCs, creatures, objectives, branches, rewards, secret notes, world facts, and tags
- `ADVENTURE_IMPORT.md` - Validated import of local adventure packages: structure/identifier validation, collision, path-traversal, oversized-input and overwrite safeguards
- `CAMPAIGN_DASHBOARD.md` - The campaign dashboard endpoint: aggregated members, characters, and selected adventure with per-role authorization
- `DICE.md` - Auditable random dice generation and seeded deterministic rolling
- `GAMEPLAY_RULES.md` - Backend-owned ability checks, skill checks and saving throws: deterministic modifier application and auditable results
- `ATTACKS.md` - Backend-owned attack resolution: to-hit comparison against armor class, crit/miss classification and turn ownership
- `CONTRIBUTING.md` - Development guidelines and workflow

## Navigation

Start with `MILESTONE_SCOPE.md` for current project status and roadmap.
