# Remedium Database Migration Policy

## Current Phase: Pre-Launch (Destructive Rebuilds Allowed)

Until the app ships with user-generated data, the database is treated as
**ship-only reference data**. Every schema change rebuilds the database
from `create_remedium_db.sql`. The on-device copy is deleted and replaced
when `DB_VERSION` increments.

This is acceptable because:
- The drugs/brand_products/warnings tables are read-only reference data
  shipped in the APK assets
- No user-generated data exists yet
- Schema is still evolving rapidly during development

## Rule: Adding User Data Will Trigger A Migration Architecture Change

The moment we add ANY of the following, the destructive rebuild policy
must be replaced with a real migration system:

- Scan history table
- User favorites
- Custom medicine notes
- Saved language preference (currently in-memory only)
- Any user-editable content

When that day comes, the architecture splits: