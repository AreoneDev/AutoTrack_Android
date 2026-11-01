# Android Test Strategy and Traceability

This strategy applies risk-based, layered testing and lightweight traceability using the [ISTQB CTFL v4.0.1 syllabus](https://istqb.org/?download_id=3345&sdm_process_download=1). It does not claim project certification. Use synthetic data and isolated databases.

## Product basis and open questions

The README, activity validation, and DAO are the current test basis. New leads have ID 0; SQLite assigns IDs on insertion. Persisted leads are queried by ID. The DAO also has a name/phone search branch whose encrypted-column comparison is suspect. The model accepts one name, while the creation screen requires first name, last name, and phone. Product confirmation is needed for that difference, phone/email formats, and duplicate handling.

## Risk-based checklist

| Risk | Priority | Conditions and techniques | Status |
|---|---|---|---|
| ID assignment and stale assertions | P0 | Unsaved ID 0; inserted IDs positive and distinct | JVM passed; SQLite not executed |
| Data loss or plaintext at rest | P0 | Unicode and optional-field partitions; CRUD, reopen, inspect ciphertext | Instrumentation compiled; not executed |
| IV reuse or ciphertext tampering | P0 | Repeated encryption, cross-instance decryption, altered ciphertext | Instrumentation compiled; not executed |
| Missed follow-up | P1 | Stage and keyword decision table; 3/4/7/8-day boundaries; DST | JVM: DST regression fails |
| Missing names | P1 | First/last name combinations; null and whitespace partitions | JVM passed; UI not executed |
| Task state and ordering | P1 | Create, complete, reopen, restore, edit, delete; date ordering | Instrumentation compiled; not executed |
| Customer workflow | P1 | Create, list, search, filter, edit, cancel and confirm deletion | Instrumentation compiled; not executed |
| Lifecycle and navigation | P1 | Recreate draft; next/previous week and return | Instrumentation compiled; not executed |
| Permissions and external apps | P1 | Denied permissions and missing intent handlers | Manual exploration pending |
| Retention and recovery | P0 | Delete all and reopen, schema upgrade, backup and missing key | Pending |

## Execution and exit criteria

The baseline on 2026-10-31 Toronto was 50 executions and 11 failures, including duplicated suite runs. Four root causes were an outdated constructor ID expectation, an incorrect inserted name, a floating-point assertion overload, and an inverted nonempty assertion. A cleanup method also lacked `@After`. Local tests under the integration package use a stub and are not evidence of real SQLite execution.

Release exit requires all independent JVM tests passing, P0/P1 device tests actually passing on isolated devices, no unresolved data-loss or plaintext-storage defects, and manual exploration of critical navigation. Compilation alone does not satisfy device execution. These criteria are not met.

## Results and traceability (2026-10-31 Toronto)

| ID | Test conditions and cases | Result |
|---|---|---|
| R-ID | `LeadTest.testLeadIDGeneration`; `StorageContractTest.leadCrudAndReopenPreserveIdentityAndUnicode` | JVM passed; SQLite not executed |
| R-NAME | `LeadValidationTest`: invalid and valid name partitions and edit transitions | 3 passed |
| R-SCORE | `ScoringRulesTest`: stage/keyword table, silence boundaries, interaction reset | 3 passed in UTC |
| R-FILTER | `LeadFilteringTest`: search partitions, combined filters, status and absent vehicle | 4 passed |
| R-DATE | `MissionCalendarTest.gratitudeOnNextCalendarDayAcrossSpringDst` | Failed: BUG-01 |
| R-PII | `StorageContractTest.piiColumnsContainAuthenticatedCiphertext`; `KeystoreContractTest` | Compiled; device not executed |
| R-TASK | `StorageContractTest.taskCompletionTransitionsSurviveReopenAndDeletion`; `TaskWorkflowTest` | Compiled; device not executed |
| R-UI | `LeadWorkflowTest`: validation, draft recreation, create/search/reset, edit, delete and calendar | Compiled; device not executed |
| R-SEAM | Existing business and persistence instrumentation tests with isolated databases | Compiled; device not executed |
| R-STATIC | `lintDebug` | Completed: 0 errors, 153 warnings |

Final JVM results: 32 independent tests, 31 passed, 1 failed, none skipped. Suite entry points remain available in the IDE but are excluded from Gradle discovery to avoid duplicates. Three existing placeholder tests are empty and do not represent meaningful business coverage. Eleven new JVM tests yielded ten passes and one genuine regression failure. Existing CRUD scenarios were retained and corrected for actual DAO behavior and isolation; production code was not changed.

```powershell
$env:JAVA_HOME = 'D:\Apps\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --offline
.\gradlew.bat compileDebugAndroidTestJavaWithJavac --offline
.\gradlew.bat connectedDebugAndroidTest lintDebug --continue --offline
```

Evidence: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest/TEST-com.areonedev.autotrack.business.MissionCalendarTest.xml`, and `app/build/reports/lint-results-debug.html` and `.xml`. Build outputs can be overwritten by later runs.

No device was connected. The existing Pixel_8_API_35 emulator could not start because its `system-images/android-35/google_apis_playstore/x86_64` image was missing. The offline connected test task also failed before execution because `com.android.tools.utp:android-test-plugin-host-additional-test-output:31.7.2` was not cached. The test APK builds, but no device tests have passed. Install the missing image and dependency or use a dedicated isolated test device, then rerun.

The original splash smoke test is ignored because MainActivity initializes the customer database and the existing injection mechanism cannot isolate it safely. New UI tests start after injecting a separate DAO. Restore splash coverage using a dedicated test application or flavor. Each isolated test database uses a UUID in cache and is closed and deleted afterward; the original Main path is restored. Tests do not delete Keystore keys or access Supabase. Shared Services tests must run sequentially.

## Defects and risks

| ID | Priority and evidence | Impact and status |
|---|---|---|
| BUG-01 | P1; reproduced by JUnit | In Toronto, a lead created March 8, 2026 queried March 9 should receive the Day 1 Gratitude mission but receives Standard Follow-up. Milliseconds divided by 24 hours truncate the 23-hour DST day. Regression remains failing; consider local calendar-date arithmetic. |
| SEC-01 | P0; static review of `CryptoManager.encrypt` | Encryption failure returns plaintext, potentially storing sensitive data unencrypted. Add injectable failure tests and a fail-closed design; failure injection remains pending. |
| BUG-02 | P1; static review of `LeadDetailsActivity.handleUpdate` | Save sets edit mode false and then toggles it true. A UI regression expects view mode; not device verified. |
| BUG-03 | P1; static review of `DataAccessObject.getLeadRandom` | ID 0 queries compare plaintext names and phone against AES/GCM ciphertext, potentially breaking duplicate lookup. Device reproduction and product duplicate policy remain pending. |
| DATA-01 | P1; static review of `DataAccessObject.open` | An empty lead table is seeded with four demo leads, so deleting all leads and reopening can reintroduce demo data. Confirm release seeding policy and verify on device. |
| DATA-02 | P1; schema review | Cascade behavior and task/notification retention after lead deletion are unclear. Backup restore with a missing Keystore key can return undecrypted input. Recovery testing pending. |
| UI-01 | P1; layout review | The edit container lacks a scrolling parent; save/delete may be inaccessible on narrow screens or with large fonts. Device verification pending. |

The lint report includes 66 HardcodedText, 23 SetTextI18n, 14 Autofill, 11 DefaultLocale, and 9 ContentDescription warnings, plus performance and other warnings. Prioritize accessibility and localization. No coverage percentage, performance measurement, or migration pass is claimed.

## Further testing

- Run Android lint and triage findings; manually explore TalkBack labels, focus order, touch targets, 200% font size, contrast, and usability.
- Test API 24/34/35, narrow screens and tablets, portrait/landscape, dark mode, and English/Chinese locales; record actual devices and versions.
- Measure startup, search, filtering, memory and scrolling with isolated synthetic datasets of 1,000 and 10,000 leads/tasks. Agree product SLAs before setting thresholds.
- Test prior-schema fixtures, empty and fully deleted databases, upgrades, process death, backup restore, and missing Keystore keys.
- Implement or run selected-filter UI combinations, back stack, rotation, denied permissions and missing intent handlers. The task UI workflow is implemented but not executed.
- Use branch and statement coverage for scoring and validation when tooling is available; do not invent coverage percentages. Keep exploratory and usability tests manual.

## Future Supabase tests: planned only

Use an isolated test project and synthetic accounts. Cover API field/UUID/version/updated_at/sync_deleted contracts, cross-account RLS denial, authentication/session refresh/logout, Android-to-web end-to-end workflows, offline retry and idempotency, conflicts, deletion propagation, time zones and DST, and first migration of existing local data. These features were not implemented or verified by this test effort. Never use real customer data.

## Pending execution protocols and future synchronization cases

Each case requires synthetic data, a dedicated disposable device or emulator, recorded app build, device model, Android version, locale, and timestamp. Record pass/fail, observed values, artifacts and defect ID; an unrun case remains pending.

| ID | Setup and action | Expected result | Status |
|---|---|---|---|
| UI-FILTER | Create one active and one lost synthetic customer, search their shared prefix, select Lost and New, then reset | One lost match; reset restores all customers | Automated; compiled successfully, device not run |
| DATA-EMPTY | Delete all customers in isolated database, close and reopen | No deleted customers or replacement demo records appear | Automated; device not run; may expose DATA-01 |
| LIFE-BACK | Open customer details from list, return, navigate calendar and return | Correct screen and selected data; no duplicate activity or data loss | Manual pending |
| LIFE-DEATH | Enter unsaved draft, background app, terminate its process via device controls, restore | Document whether draft recovery is a confirmed requirement; no persisted partial customer or crash | Manual pending; draft recovery policy unresolved |
| LIFE-ROTATE | Enter draft, rotate portrait/landscape twice | Draft remains intact, no duplicate record | Manual pending |
| PERM-DENY | On isolated device deny phone/SMS permissions; open customer and calendar, invoke available contact actions | App remains usable; no crash or unauthorized action | Manual pending |
| PERM-NO-HANDLER | Use isolated emulator without a handler for an external contact intent | App handles unavailable action without losing data or crashing | Manual pending |
| A11Y | TalkBack on, 200% font, keyboard navigation and contrast inspection | Controls named, focused logically, reachable and readable; record specific failures | Manual pending |
| COMPAT | Repeat P0 paths on API 24/34/35, narrow and large screens, light/dark and English/Chinese | No crash, clipping or lost data; compare to approved behavior | Manual pending; device matrix unavailable |
| LOAD | Populate isolated store with 1,000 then 10,000 synthetic customers/tasks; record startup, search, scroll latency and memory | Correct counts and results; compare measured times with product-approved SLA once defined | Manual pending; no SLA asserted |
| MIGRATE | Start with versioned old database fixtures, open updated application and inspect all records | Approved migration preserves IDs, relationships, dates and readable sensitive fields | Pending: versioned fixture and migration specification required |
| RESTORE | Restore synthetic database backup with original key, then independently with key unavailable | Original key restores data; missing key must produce a clear failure and never overwrite data | Pending: restore policy and isolated device setup required |
| SYNC-CONTRACT | In isolated Supabase project create/read/update/delete each Android entity, validate owner_id, sync_id, version, updated_at, sync_deleted and server responses | Contract matches agreed schema and invalid input is rejected | Planned; Android sync not implemented |
| SYNC-RLS | Create accounts A/B and records for each; attempt cross-account select/insert/update/delete | Cross-account access denied and original records unchanged | Planned; isolated environment required |
| SYNC-AUTH | Expire/refresh session, sign out and reopen with synthetic accounts | Valid refresh succeeds; signed-out state cannot access prior account data | Planned; Android sync not implemented |
| SYNC-E2E | Create/edit/delete in Android and web in both directions | Correct owner, values and deletion converge on both clients | Planned; Android sync not implemented |
| SYNC-OFFLINE | Disconnect during writes, replay after reconnect, repeat identical request, race conflicting edits and deletion | No duplicate logical records; documented conflict policy and deletion converge | Planned; conflict policy needed |
| SYNC-DATE | Exchange dates around midnight, UTC offsets and DST boundaries | Semantics match agreed date/time contract without day shift | Planned; date contract needed |
| SYNC-FIRST | Import representative old local fixture twice into isolated account | No data loss or duplicates; ownership and migration checkpoints correct | Planned; migration design needed |

