# Credential rotation: Render + Supabase

Status update, 26 October 2026: the owner confirmed that the PostgreSQL password
was changed, the Render connection was updated, and the service works. This is
owner confirmation, not independent provider-side verification. Rotation of other
credentials is not established by that confirmation. Historical private database
configuration is excluded from the sanitized publication history. The original
private recovery bundle must never be uploaded or made public.
Do not paste secrets into chat, reports, Git or screenshots.
Store new values in a password manager and the intended service's secret settings.

Private application.properties/application-local.properties are excluded from
Maven resources/JARs. Do a clean build to remove stale copies from target/classes.
They remain on disk for reference; supply runtime values through environment.
The user has confirmed production currently contains disposable test data. A
reset can be considered if useful, but is not needed for the implemented schema
paths and never replaces revocation of exposed credentials.

## Preserve encrypted history

Before changing anything, retain a secure copy of the CURRENT production
MESSAGE_ENCRYPTION_KEY and take a database backup. Do not regenerate this key:
existing ciphertext needs it. A compromised encryption key requires a separate
versioned-key/data re-encryption design; replacing an environment variable is not
a safe migration. JWT_SECRET is independent of this key and of Supabase Auth's JWT.

## Supabase storage API key

The backend now accepts a legacy service_role JWT or a new sb_secret_ key in
SUPABASE_SERVICE_ROLE_KEY (name retained for deployment compatibility).
Both avatar and attachment requests share the same header handling: modern keys
use apikey only; legacy JWT keys also use Authorization: Bearer.

1. Deploy/test this compatible code before changing the key.
2. In Supabase Settings > API Keys, create a secret API key for this backend.
3. Set SUPABASE_SERVICE_ROLE_KEY to the new secret in Render. Apply it with a
   deployment; do not assume a service restart picks up newly saved values.
4. Check avatar upload/download, audio/attachment upload and signed download.
5. Only after checking all consumers, revoke the exposed secret, or disable
   legacy API keys if the leaked key was service_role. New and legacy keys can
   coexist during migration, but leaving the old one enabled leaves the leak open.
   If Supabase's legacy JWT signing secret itself leaked, also follow its signing
   key migration/revocation procedure; disabling API keys alone is insufficient.

Sources: [Supabase migration guide](https://supabase.com/docs/guides/getting-started/migrating-to-new-api-keys),
[legacy rotation warning](https://supabase.com/docs/guides/troubleshooting/rotating-anon-service-and-jwt-secrets-1Jq6yd),
[Render environment changes](https://render.com/docs/configure-environment-variables).

## Database password

Identify the database role actually used by Render (DB_USERNAME), including any
pooler suffix, and other consumers of that role. Coordinate a short maintenance
window. Reset its password in Supabase, immediately update DB_PASSWORD in Render
and deploy. Check startup, database queries and authentication. Also update local
private configuration; do not print it. Existing pooled connections may mask an
incorrect password until reconnection. Do not revert to an exposed password.

Source: [Supabase password reset](https://supabase.com/docs/guides/troubleshooting/how-do-i-reset-my-supabase-database-password-oTs5sB).

## Application JWT secret and sessions

Generate a cryptographically random secret of at least 32 bytes (64 random bytes
encoded as Base64 is suitable here). Set JWT_SECRET in Render and deploy.
This invalidates previously signed access tokens; users may need to log in again.
However refresh tokens are separate database sessions, so rotating JWT_SECRET
alone does NOT revoke them. If compromise warrants a forced logout, explicitly
coordinate revocation of active auth_sessions and increment users.token_version
in a transaction, after checking the live schema and impact. This is intentionally
not an automatic application migration or a change applied by these tests.

## Completion evidence

Record only dates, key identifiers (never values), deployment ID and test outcomes.
Verify old keys are disabled. Review provider logs for suspicious use. Removing
historical secrets from Git does not revoke them; rotation comes first. Private
recovery copies and old AI reports remain sensitive even while ignored by Git.
