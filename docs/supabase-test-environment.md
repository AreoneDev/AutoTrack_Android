# Dedicated Supabase test environment

## Status and boundaries

Preparation is local only. No cloud project, Auth user, migration, CI/CD workflow or deployment has been created or changed by this work. Cloud acceptance is pending.

The Dashboard inspection on November 1, 2026 showed one existing project, `autotrack-next` (`ymafgxjevciiwkhtnzya`), marked production. Preserve it. The proposed separate project is `autotrack-test`, in the Areone organization, Canada (Central), using the Free plan if the creation screen permits it without an upgrade. Stop if creation requires a paid plan or new billing commitment.

The generator rejects the existing application project reference and malformed references. Its SQL also refuses any target with existing public tables. These checks do not prove the identity of a database: the operator must verify the Dashboard project name and reference immediately before running SQL.

## Reviewed inputs

`tools/supabase-test-env/base.sql` reconstructs the seven application tables from a read-only `information_schema.columns` and `pg_constraint` query against the existing project. No application records, passwords or tokens were read or copied. It preserves the observed scalar types, defaults, nullability, primary keys and foreign keys. It is an empty-project QA baseline, not a full database clone: existing indexes, triggers, extensions, sequence counters and identity-generation modes are not exported. Vehicle and notification identities use BY DEFAULT in this test baseline. Relationship IDs retain the observed integer types despite referencing bigint primary keys.

`ownership.sql` is copied from the local Web project's `supabase/migrations/20261025160000_per_user_data_access.sql`, with its fixed email replaced by a test-owner placeholder. `hardening.sql` reproduces the Web privilege correction. The generator then appends the Android repository's unchanged `supabase/migrations/20261101010000_offline_lead_sync.sql`.

All four inputs run in one transaction. A missing/ambiguous owner or migration error rolls back the new application schema. Supabase supplies the real Auth schema and roles; the cloud bundle never creates mock roles, JWT helpers or Auth records. Nothing is added to the automatically applied `supabase/migrations` chain for this QA bootstrap.

The generated bootstrap also enables RLS on `autotrack_sync.operations`, without adding client policies or grants. The security-definer function uses the table owner's privileges; do not force RLS on this table without revisiting the sync function. Local integration tests verify that the function can create and replay receipts while direct client access remains denied. Live Supabase behavior remains to be verified.

## Provisioning and operator confirmation

1. Review this runbook, the SQL inputs and the local test results below.
2. Obtain explicit confirmation to provision `autotrack-test` under the organization, region and cost boundary above. Create it separately from the existing project. Keep Data API enabled, disable automatic table exposure, and enable automatic RLS. Leave the optional GitHub repository unselected. The operator should enter and retain the new database password through the Dashboard; do not put it in chat, source control, application builds or generated examples.
3. Create two dedicated Supabase Auth users, A and B, with separate passwords and confirmed email status. Use accounts controlled for testing. Do not copy existing users, sessions or customer records. Keep the password login provider enabled. If testing email links, configure only the isolated Web instance's actual callback URL. A/B password handling remains operator-controlled.
4. Record the new Project Ref and A's email. Generate the concrete review bundle below; verify the URL and all SQL. Obtain explicit confirmation to apply that exact bundle to that exact project. Running migrations is a deployment under AGENTS.md.
5. Open the new project's SQL Editor, verify its Project Ref, and execute the generated `bootstrap.sql` as the database administrator. Verify successful COMMIT and the eight public application tables, private receipt schema, RLS and functions before connecting clients.

The generator performs no network calls and never deploys. It refuses to overwrite its output directory. Create a new output directory for each reviewed revision. Keep generated files outside source control because the bootstrap contains the test-owner email.

```powershell
node tools/supabase-test-env/prepare.mjs <test-project-ref> <test-owner-email> tools/supabase-test-env/generated
```

Do not run `supabase db push` against an empty target with only this Android repository's migration directory: the Web baseline and ownership prerequisites are missing from that automatic chain. Do not reset or drop the existing project.

## Client configuration

Copy the Publishable Key from the new project's Dashboard. Use the same new project URL in both clients. Secret keys and legacy service-role keys must never enter either client or APK.

Build Android with explicit Gradle properties, preserving the isolated application ID:

```powershell
.\gradlew.bat assembleDebug `
  '-PtestApplicationSuffix=.syncqa' `
  '-PSUPABASE_URL=https://<test-project-ref>.supabase.co' `
  '-PSUPABASE_PUBLISHABLE_KEY=<test-project-publishable-key>'
```

The current Gradle precedence is project property, environment variable, then `local.properties`. Explicit properties prevent a previously configured development URL from winning. `.syncqa` isolates application data; it does not itself select a backend. The APK is `app/build/outputs/apk/debug/app-debug.apk`. Install and run only this QA APK for the acceptance session.

For the `autotrack-next` Web app, supply these variables to a separate local instance and restart it:

```dotenv
NEXT_PUBLIC_SUPABASE_URL=https://<test-project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=<test-project-publishable-key>
```

Use an isolated browser profile/context. Do not modify the deployed Web environment. Retain all server-only Web configuration required by that application; only the Supabase public connection settings change for this instance.

## Local verification

```powershell
node --test tools/supabase-test-env/prepare.test.mjs
npm.cmd ci --prefix tools/sync-tests --offline --no-audit --no-fund
node --test tools/sync-tests/test-env.mjs
node tools/sync-tests/contract.mjs
```

If the locked PGlite package is not cached, omit `--offline` for the installation. The generator tests first failed because `prepare.mjs` did not exist, then passed after implementation. They cover target rejection, SQL quoting, transaction composition, matching public configuration and non-overwriting CLI output.

The bootstrap tests run the complete generated SQL in an in-memory PostgreSQL engine with simulated Supabase Auth. They cover missing-owner rollback, refusal of populated targets, real baseline defaults/catalogue columns, authenticated inserts, exact sync replay, cross-account denial, anonymous denial, child deletion/tombstones and removal of TRUNCATE/private-receipt access. An initially incomplete vehicle test payload failed against the observed NOT NULL constraints; the suite retains rejection-and-rollback coverage and uses complete Android-shaped vehicle snapshots for success cases. Numeric query results are cast explicitly when testing defaults.

PGlite does not verify real Auth, JWT validation, PostgREST, parallel database sessions, browser UI or Android runtime. Final local results: all four generator tests, both bootstrap integration tests and the existing sync contract passed. Existing contract tests remain unchanged. This preparation does not change application behavior, so no Android build regression was required here.

## Cloud acceptance and evidence

After separately approved provisioning and migration, record the new project reference, source commit, applied bootstrap hash, QA APK build configuration and the following results. Use unique synthetic names such as `QA-SYNC-<run-id>`.

| Check | Expected result |
| --- | --- |
| A signs into Android and Web | Both sessions belong to the same new project and user UUID |
| Android creates a customer with both vehicle snapshots | Web shows the customer and vehicle fields |
| Web updates that customer's fields | Android reconciles the changes |
| Android edits offline and restarts | Pending work survives; reconnect replays it once |
| Both clients edit stale versions | Android reports a conflict and both resolution choices are checked |
| Delete with child tasks/notifications | Other client removes the customer; tombstone prevents resurrection |
| Session refresh and account switching | Refresh works; B never inherits A's session, queue or local data |
| B requests A's known IDs directly through the API | No private customer, task, notification, relationship or vehicle is returned or changed |
| Anonymous direct API access | Private tables and sync RPC are denied |
| Administrative SQL checks | All eight public tables have RLS; client roles lack TRUNCATE and private receipt access |

The existing Web scalar UPDATE is unversioned: it can overwrite a newer Android change from a stale form. Record this limitation; do not claim symmetric conflict protection until the Web client adopts the versioned RPC. Independent task/notification editing and the standalone vehicle catalogue are outside Android's current synchronization scope.

Leave cloud rows and evidence in the dedicated test project for review. Cleanup or project deletion is a separate explicit action; this package does not automate it. Do not add CI/CD or connect the new project to GitHub automatically.

References: [Supabase environments](https://supabase.com/docs/guides/deployment/managing-environments), [API keys](https://supabase.com/docs/guides/getting-started/api-keys), [Android sync protocol](../ANDROID_SYNC.md).
