# Dedicated Supabase QA verification — 2026-11-01

The isolated project `autotrack-test` was used with two dedicated accounts and synthetic customers. Android ran as `com.areonedev.autotrack.cloudqa` on emulator-5554; the Web instance ran from a separate source copy on localhost:3317. The original Web checkout and its environment file were not modified. The project URL and publishable key were configured for the QA instances; credentials and access tokens are not recorded here.

## Observed outcomes

- The real Web client created a synthetic customer. Android authenticated snapshot retrieval succeeded, but the initial application failed on offset-less PostgreSQL timestamps. The transaction rolled back and no local customer was written.
- A focused unit test first failed on the observed timestamp format. The parser now accepts offset-less timestamps using the configured business timezone, while continuing to honor explicit offsets and reject invalid dates. Both the real authenticated snapshot and Android persistence diagnostics passed after the fix. A subsequent app restart automatically downloaded a newly created synthetic customer.
- An Android-created synthetic customer was queued through a simulated network failure, then replayed to the real test project. The actual Web customer list displayed it. A repeated committed delete returned the same versioned receipt. The offline condition was simulated in the transport; this was not a physical radio-disconnect test.
- Android retained a pending local edit after the real Web client changed the same record. Synchronization produced a conflict with both versions intact. Accepting the server version cleared the conflict.
- A Web deletion propagated to Android; an Android deletion propagated to the real Web list. A later server-side deletion was automatically consumed by the reopened Android app.
- B's Web list and direct detail URL did not expose A's synthetic customer. A's authenticated Android snapshot contained its own marker but not B's. An A-authenticated DELETE against B's synthetic customer affected zero rows, and B's Web list still showed that customer. This did not separately exercise cross-account INSERT or UPDATE.
- The reported stale synchronization status was reproduced by a failing device test. Lifecycle-scoped status notifications now update the visible screen; the unchanged UI expectations passed after the fix.

All synthetic customer rows created during this verification were deleted through normal application APIs. Deletion history and operation receipts remain by design. No other accounts or customer records were removed. The temporary account-specific instrumentation test is archived outside the repository under the local `cloudqa` test directory and must not be added to the ordinary test suite: it accesses a live test project and includes destructive synthetic-record cleanup.

## Regression and remaining limitations

The full 77-test JVM suite, lint, and 22 selected device tests passed after both fixes. The test APK was updated without clearing the QA account session. The date-only and timestamp contract still needs an explicit cross-client timezone decision: a timestamp without an offset cannot itself establish the original instant, and clients in different timezones may interpret it differently. DST ambiguous local times remain a product/data-contract issue.

The current Web scalar-save path still has no expected-version condition; the validated conflict direction protects a pending Android edit against a newer Web change, but a stale Web form may overwrite a newer Android write. Cross-client version protection therefore remains incomplete. No cloud schema changes, CI/CD operations, production deployment, or remote Git push were performed in this verification.
