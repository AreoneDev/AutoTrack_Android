# Offline customer synchronization

## Scope and integration

The `codex/offline-sync` branch builds on the committed authentication and customer mapping work. Development uses a managed isolated worktree; the shared checkout, its branch, its original untracked `SyncConflict` files, and other chats' edits are not moved or stashed.

This implementation synchronizes customers, including scalar fields and vehicle-interest/trade-in snapshots. Customer deletion also removes associated tasks and notifications and records their server tombstones through the existing migration. Independent task/notification edits and the standalone vehicle catalogue are not queued by Android in this change. Unclaimed legacy databases are never imported automatically.

Account-scoped DAO writes atomically update the customer and an encrypted journal. Requests have stable UUID operation IDs and freeze once exposed to the transport. Unsent edits coalesce; edits made during a request retain a later local revision. An acknowledgement only clears the revision it actually uploaded. Both journal payloads and conflict responses use authenticated encryption with a per-account Android Keystore key. Account customer PII fails closed on encryption/decryption errors; legacy unscoped DAO behavior is retained for an explicit future import policy.

Each network request runs outside the session monitor. Applying its response runs inside that monitor and checks the account generation, then uses the Services facade's account boundary. The scheduler never acquires Services while holding its own monitor. This avoids a scheduling/account-write lock inversion encountered during device regression testing. No credentials or server response bodies are logged.

An application resume or local customer write requests reconciliation. One process-wide worker serializes runs. While the process remains alive and an account is active it reconciles every 60 seconds; transient errors leave immutable requests on disk for the next attempt. Reopening the app restores the queue after process death. This is not an OS background-work guarantee. Empty public Supabase configuration disables automatic network work without disabling local queue writes.

Customers can open **Synchronization** from the customer list, inspect conflicts, and accept the server copy or retain local intent. Retaining a local deletion queues deletion against the reviewed server version. A remotely deleted customer cannot be resurrected under the same UUID; accept deletion or explicitly create a new customer. The server may change again before resolution uploads, producing a new conflict.

## Server migration and web compatibility

Apply `supabase/migrations/20261101010000_offline_lead_sync.sql` only after the web ownership migration, initially in an isolated test project. This branch does not apply any cloud migrations.

- `autotrack_sync_lead` atomically checks the expected version, writes the aggregate, and stores an owner-scoped immutable operation receipt. A response-loss replay returns the original receipt; changing a payload under an existing operation ID is rejected. Deleted UUIDs cannot be silently reused by this protocol.
- The receipt table is private and inaccessible to client roles. The fixed-schema definer write function explicitly validates `auth.uid()`, owner, identifiers and writable fields; anonymous execution is revoked. Read helpers use invoker permissions and existing RLS.
- `autotrack_sync_snapshot` returns customers and deletion history from one MVCC statement. Android applies the complete response in one SQLite transaction. This avoids missed late commits from timestamp-only cursors. Responses are limited to 16 MiB; larger accounts fail safely and require a future paged consistent-snapshot protocol. Local batches contain at most 100 upload requests per run.
- Relationship changes and edits of linked vehicle records advance the customer aggregate version. Existing web direct deletion gains atomic child cleanup. Private vehicle snapshots are inserted without modifying shared catalogue entries; superseded vehicle rows are retained rather than deleting potentially referenced data.

The existing web repository can continue reading and writing its current tables; those writes become visible to Android. Its current unversioned scalar UPDATE can still overwrite a newer Android change if a stale web form saves later. Both clients must adopt the versioned RPC to guarantee conflict protection in both directions. That web UI/repository change is not silently applied to the separate web checkout by this Android branch.

Receipts and tombstones have no automatic retention or pruning. A retention policy needs a device-resynchronization contract before deleting either. Production concurrency, real Supabase Auth/refresh, PostgREST deployment permissions, large-account behavior, and browser UI interactions require separate live validation.

## Tests and reproduction

Use synthetic records and disposable account identities only. No real customer data or real credentials are required by the local harness.

```powershell
$env:JAVA_HOME = 'C:/Users/Lenovo/.jdks/openjdk-21.0.1'
$env:ANDROID_HOME = 'C:/Users/Lenovo/AppData/Local/Android/Sdk'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug '-PtestApplicationSuffix=.syncqa' --offline
npm.cmd ci --prefix tools/sync-tests --no-audit --no-fund
node tools/sync-tests/contract.mjs
node tools/sync-tests/bridge.mjs
```

Run the bridge in a separate terminal. It binds only to `127.0.0.1:18765`, uses a new in-memory PGlite database, and accepts only two fixed synthetic bearer IDs. These IDs simulate account selection; they do not validate Supabase Auth or JWT verification. The ownership-migration fixture is copied from the reviewed web migration with a synthetic email substituted; the base schema fixture is a test model, not proof of the deployed schema. PGlite serializes statements and does not prove independent-session race behavior.

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adb -s emulator-5554 reverse tcp:18765 tcp:18765
& $adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5554 shell am instrument -w -e class com.areonedev.autotrack.sync.OfflineSyncDatabaseTest,com.areonedev.autotrack.sync.LocalCrossClientSyncTest,com.areonedev.autotrack.sync.SyncConflictWorkflowTest com.areonedev.autotrack.syncqa.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 reverse --remove tcp:18765
```

The `.syncqa` application ID isolates these installs from `.debug` and release application data. Database fixtures have synthetic owner UUIDs and are deleted by their own tests. Debug-only network configuration allows cleartext exclusively to loopback for the test bridge; release networking stays HTTPS-only. Cross-client tests exchange real Android SQLite operations with the PostgreSQL functions and web-style SQL operations over loopback. They do not execute the actual Next.js UI or its Supabase repository module.

TDD evidence: engine and transport tests first failed because their classes were absent. Seven initial SQLite tests failed against unsupported sync methods. Additional tests reproduced deleted-local conflict resolution failures, missing first-sync queueing for previously mapped rows, corrupt-PII upload, web direct deletion with foreign keys, and missing aggregate versions on vehicle edits. The corresponding implementations were corrected without removing assertions. UI integration tests are run after protocol implementation; the reachable-list navigation test first exposed the missing entry point in the NoActionBar layout. No separate failing visual-layout test preceded the initial programmatic conflict-screen composition; Espresso checks actual navigation and resolution, while broader accessibility/layout review remains pending.

See `docs/sync-verification.md` for the final execution results and remaining live-test prerequisites.
