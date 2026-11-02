# Offline synchronization verification — 2026-11-01

## Baseline and isolation

Authentication (`89dc39d`), customer mapping (`a812a6a`) and authentication loading (`170cc97`) are integrated into this branch. Feature development took place in `C:/Users/Lenovo/.codex/worktrees/offline-sync/AutoTrack` on `codex/offline-sync`. During feature development the original checkout stayed on its independently managed branch, and its untracked sync source and tests were preserved. No cloud schema, customer data, web checkout, credentials or remote branches were changed.

Build/runtime: OpenJDK 21.0.1, Gradle wrapper, Android emulator `emulator-5554`, model `sdk_gphone64_x86_64`, Android 15/API 35. APKs use `com.areonedev.autotrack.syncqa` and `.syncqa.test`, separate from the other chat's debug app and release data. Synthetic device databases are scoped to generated account UUIDs or disposable cache directories.

## Executed checks

| Check | Result |
| --- | --- |
| Full JVM suite | 76 tests, zero failures/errors |
| Debug application and instrumentation APKs | Built successfully |
| Android lint | Zero errors, 159 warnings; no baseline or error suppression added |
| Local PostgreSQL contract | Passed creation, exact replay, changed-payload rejection, stale version conflict, vehicle relationships, linked-vehicle updates, A/B isolation, delete replay, tombstone protection, direct web-style child deletion, transaction rollback, private receipts, anonymous denial and migration replay |
| Selected device regressions | 36 tests passed, including local cross-client round trips and conflict UI |
| Git whitespace checks | Passed |

The original three untracked `SyncConflictTest` tests were not copied into or counted as tests in the feature branch. The later user-authorized integration archived those early files outside the repository before removing them from the main checkout.

The device selection includes 14 offline database tests, one Android/PostgreSQL cross-client test, one conflict-navigation/resolution UI test, six existing mapping tests, one account database test, five login-loading tests, three storage tests, three Keystore tests and two retention tests. Existing mapping assertions were retained. Regression failures exposed by automatic identity reservation and strict legacy reading were fixed in production code, rather than weakening those assertions.

An initial combined run was interrupted after identifying a scheduler/Services lock-order risk. A later combined run completed with two mapping failures, followed by an additional strict-foreign-key deletion failure and an exposed-identity immutability failure. Those failures led to separate strict upload validation, transactional unexposed identity binding, durable identity pins and child-before-parent deletion. Only the final post-fix run counts as acceptance evidence.

## Boundaries and remaining work

The loopback bridge executes PostgreSQL functions and web-style SQL writes. It does not execute the actual Next.js module/UI, Supabase Auth, JWT verification or live PostgREST. PGlite is a single-connection engine; independent PostgreSQL-session races remain unverified. No dedicated cloud test account configuration was supplied during this run, and no live cloud write or migration was attempted.

The existing web scalar-save path still lacks expected-version checking. Android protects its own writes and retains conflicts, but a later stale web form may overwrite a newer value. Both clients need the versioned RPC before claiming complete cross-client conflict protection. Independent task/notification synchronization, expired-session offline unlocking, guaranteed OS background execution, consistent paging beyond the 16 MiB snapshot bound and receipt/tombstone retention policies remain outside this implementation.

Live acceptance requires a disposable Supabase project with the ownership migration plus this branch's migration, two isolated accounts, matching public configuration, an Android QA build and an isolated browser context. Verify both directions using synthetic customers, vehicle changes, offline replay, token refresh, cross-account denial, conflicts and deletion. Never substitute a customer account or production project.

The protocol and reproduction commands are in [ANDROID_SYNC.md](../ANDROID_SYNC.md). Read the official [Supabase function security guidance](https://supabase.com/docs/guides/database/functions) and [PGlite limitations](https://pglite.dev/docs/) when reviewing the deployment and test boundaries.

## User-authorized local main integration

The user explicitly requested direct integration into local `main`. Main fast-forwarded from `170cc97` to `787f9c6`, retaining both feature commits. The unrelated, uncommitted `AGENTS.md` change was preserved byte-for-byte and was not included in a commit.

The unused early `SyncConflict.java` and `SyncConflictTest.java` files were untracked. Their exact originals were backed up under `C:/Users/Lenovo/.codex/visualizations/2026/11/01/01a19832-99fb-7250-a1d7-6dca87c09369/early-sync-backup`, hash-verified and then removed from the main checkout. No committed production code or test assertions were removed.

Post-merge verification on main passed all 76 JVM tests, both QA APK builds, lint (zero errors, 159 warnings), the local PostgreSQL contract and all 36 selected device tests. The first device attempt had two UI lifecycle failures while the emulator was asleep (`mWakefulness=Asleep`). After waking and dismissing the test keyguard, the entire unchanged 36-test selection passed in 77.72 seconds. No production or test code was changed to obtain that pass.

The local build explicitly overrides `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` with empty values and uses the separate `.syncqa` application ID. Device output is in `app/build/reports/sync-post-merge-device-tests.txt` (first attempt) and `app/build/reports/sync-post-merge-device-tests-awake.txt` (passing rerun). The temporary loopback bridge and adb port mapping were removed, and the original emulator screen-timeout setting was restored. No cloud writes, pushes, deployments or CI/CD operations were performed.
