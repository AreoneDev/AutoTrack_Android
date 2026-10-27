# AutoTrack web and Android quality audit

Audit date: October 26, 2026, America/Toronto.

The web application builds and its existing tests pass, but real data access is not ready for multiple accounts. Anonymous requests can read customer records, while creating a lead from the signed-in UI fails. Android builds successfully, but its unit tests do not compile and it has no cloud authentication or synchronization implementation. Live cross-device synchronization has not been established.

## Verification results (initial inspection)

| Check | Result | Scope |
| --- | --- | --- |
| Web `npm test -- --runInBand` | 12 suites and 77 tests passed | Domain objects, business services, and stub persistence; no Supabase RLS or browser workflow coverage |
| Web `npm run lint` | Passed | ESLint |
| Web `npm run build` | Passed | Next.js 16.2.10; Analytics and one repository test route are prerendered |
| Web signed-in UI | Leads, customer detail, Dashboard, Agenda, and Analytics render | Existing browser session; does not establish account isolation |
| Web create lead | Failed with `new row violates row-level security policy for table "leads"` | Synthetic record named QA-AutoTrack SyncCheck; no lead was created |
| Anonymous Supabase reads | Customer data accessible | Count-only checks: 101 leads, 1,080 tasks, 22 notifications, 9,896 vehicles, 2 interests, and 1 trade-in relationship |
| Supabase ownership column | Missing | Zero-row query for `leads.owner_id` returned PostgreSQL code `42703` |
| Web dependency audit | 11 affected packages | npm reported 2 critical, 8 high, and 1 moderate; this is a dependency advisory result, not proof every advisory is exploitable in this app |
| Android `assembleDebug` | Passed | `app/build/outputs/apk/debug/app-debug.apk` |
| Android `lintDebug` | Passed with 172 warnings | Detailed generated report: `app/build/reports/lint-results-debug.html` |
| Android `testDebugUnitTest` | Failed before tests ran | 19 Java compilation errors: tests called a removed 17-argument Lead constructor |
| Android runtime | Not yet tested | Existing Pixel 8 AVD is x86_64, but its referenced API 35 Google Play system image is missing |
| Web and Android round trip | Not tested | Auth, ownership, local sync metadata, and conflict handling are absent |

No existing customer records were deliberately edited or deleted. Dashboard and detail page rendering themselves call task insertion code; those existing read-triggered writes must be removed or moved to an explicit, idempotent workflow.

## Follow-up repairs and re-check (October 26, 2026)

- Web auth was moved to cookie-aware browser/server clients with a request-scoped server client, a session-refresh proxy, and an auth callback. Protected server pages now require the signed-in session and pass that session's client to repositories.
- Dashboard and lead-detail rendering no longer insert tasks as a side effect. Five database-mutating repository demo routes now return 404. Analytics is request-time rendered. Account sign-out and local date defaults were corrected.
- Web checks now pass: 15 suites / 84 tests, lint, and production build. Repository unit coverage includes a check that distinct session clients remain isolated. The changes are local and have not been deployed.
- The Supabase ownership migration is prepared and the user explicitly authorized it, but browser control timed out before its editor state or Run result could be read. Treat cloud migration status as **unknown** until checked in Supabase. No claim is made here that RLS has been fixed or that data counts were re-verified.
- Android test fixtures now construct the current `Lead` model, and test-source compilation succeeds. `assembleDebug` and `assembleDebugAndroidTest` pass. The unit suite runs 50 tests but 11 assertions fail, including stale Lead ID assumptions and persistence tests that rely on shared mutable stub state. These failures remain open; app behavior was not changed to mask them.
- Android `lintDebug` reports 172 warnings (66 HardcodedText, 23 SetTextI18n, 18 GradleDependency, 14 Autofill, 11 DefaultLocale, 9 ContentDescription, and others).
- Android app runtime and cross-device sync remain unverified. The x86_64 emulator image was installed outside the main SDK, but acceleration requires the Android Emulator Hypervisor Driver, a system-level driver that has not been installed. Android has no Supabase login or sync queue implementation yet.

The web code changes and migration SQL are not deployed, committed, or pushed. Supabase row counts and anonymous access must be rechecked after the migration is visibly confirmed as successful.

## Web findings

### P0 Customer reads are available without login

The browser login gate does not protect the Supabase API. Count-only requests using the public project key and no user session return customer, task, and notification counts. `owner_id` is absent, so the previously prepared per-user migration has not taken effect.

Before connecting Android, assign existing data to an explicitly selected account, enable RLS on every customer table, remove all permissive policies, and verify anonymous denial and isolation between two accounts. The owner cannot be inferred from a contact email inside a lead.

Relevant source: web `supabase/migrations/20261025160000_per_user_data_access.sql` and `components/auth/AuthGate.tsx`.

### P1 Signed-in users cannot create a lead

Creating the synthetic QA record through the normal form failed with an RLS error. Read access and write access are inconsistent. After correcting the policies, test create, update, delete, task creation, vehicle relationships, and notification creation using a dedicated account and synthetic data.

Relevant source: web `components/leads/LeadTable.tsx` and `lib/persistence/real/supabase/LeadRepo.ts`.

### P1 Server queries do not carry the browser session

Dashboard, Analytics, and customer detail are Server Components, but the repositories import a shared `createClient` instance configured without request cookies. They therefore query without the browser user's identity. A client-side AuthGate does not stop the server from executing children. With proper RLS these pages will lose data; permissive RLS currently hides this integration defect.

Use request-scoped server clients and cookie-based session refresh, verify identity before loading protected data, and pass the client to repositories. Browser components should use the corresponding browser client. Never substitute a service-role key for the missing user session.

Relevant source: web `app/dashboard/page.tsx`, `app/analytics/page.tsx`, `app/leads/[id]/page.tsx`, and `lib/supabase/client.ts`.

### P1 Development routes mutate the live database during rendering

Routes under `app/test-*-repo` run insert, update, or delete operations on render. The production build includes these routes and prerenders `/test-lead-repo`. Whether a write succeeds depends on current policies, but rendering or building the application must not attempt these mutations.

Move these checks into isolated integration tests with a test database. Remove or disable the runtime routes before granting authenticated write access.

### P1 Analytics can be stale and uses a different score definition

The production build identifies `/analytics` as static. Its database queries can therefore run at build time rather than for the signed-in user. Analytics also defines hot leads using a threshold of 80 and `Math.max(storedScore, calculatedScore)`, while the current scoring service uses different thresholds. The inspected UI showed 27 hot leads in Analytics and zero high-priority leads in Dashboard; these are different metrics and are currently easy to interpret as contradictory.

Make the page user-specific and dynamic. Define the intended score/priority metric once, distinguish historical scores from current scores, and test the same fixture across list, Dashboard, and Analytics.

Relevant source: web `domain/business/AnalyticsService.ts` and `domain/business/ScoringService.ts`.

### P1 Calendar dates and timestamps need a consistent contract

The UI displayed October 26 locally while the create lead and detail task forms defaulted to October 27. Several defaults use `new Date().toISOString().split("T")[0]`, which selects the UTC calendar day. TaskRepo separately parses and formats timestamps as local wall-clock components, discarding timezone information.

Use local calendar formatting for date-only form defaults. Define date-only fields separately from UTC instants, migrate existing values deliberately, and test midnight, DST, and device timezone changes before synchronization.

### P1 Some task operations combine distinct appointments

TaskRepo treats the same lead, title, and calendar date as one logical task. Two same-title appointments at different times on the same day can be collapsed. Completion updates also update every matching task within that date range. A check followed by an insert is not safe against simultaneous requests from web and Android.

Use a stable task identifier and a server-enforced idempotency key for generated tasks. Completing a manually created task should address that task only.

Relevant source: web `lib/persistence/real/supabase/TaskRepo.ts`.

### P2 Error states can look like empty data

Real repositories commonly return `[]` or `null` on network and permission errors. The UI cannot distinguish an empty account from a failed request. Some load flows have no catch path and can remain loading after a thrown error. Propagate typed failures and expose retry states; test expired sessions, blocked requests, and offline transitions.

### P2 Account and workflow controls are incomplete

The sidebar and top bar display a hardcoded identity, there is no visible sign-out control, and AuthGate does not subscribe to sign-out/session changes. Add actual account identity, sign-out, and authentication error handling. Also align sales stage options across creation, filtering, and scoring: the existing data includes `APPOINTMENT` and `DELIVERED`, while the creation form offers a different set including `CLOSED`.

Analytics explicitly lacks completion timestamps and delivery discount data for two metrics. Present these as unavailable rather than implying the displayed zero is measured performance. Package scripts also reference a missing `scripts` directory.

### P2 Data loading and editing need stronger integration tests

The lead list loads per-lead vehicle relationships, producing additional requests for every lead; it has no pagination. A lead detail save updates the lead and then two vehicle relationships in separate operations, so later failures leave a partial save. Add paginated/joined reads and a transactional save for related edits.

The existing per-user migration also treats `vehicles` as private, while the UI uses it as a roughly 9,896-row vehicle catalogue. Separate shared reference data from account-owned records before applying ownership rules. Do not assign the entire shared catalogue to one user by accident.

## Android findings

### P1 Authentication and synchronization do not exist yet

The manifest has no INTERNET permission. There is no Supabase login, token refresh, account-scoped SQLite storage, upload queue, download cursor, retry policy, or conflict resolution. Local autoincrement IDs cannot safely identify the same record across independent installations and the cloud.

### P1 The current test sources no longer compile

The application has a 14-argument Lead constructor, while tests use the earlier 17-argument constructor that also accepted stage and dates. Update fixtures to the current model contract, then rerun object, business, persistence, and instrumentation tests. Do not claim a pass by removing the failing cases.

### P1 SQLite omits fields required for a faithful round trip

Lead persistence does not store last interaction date or last interaction actor. Dates are reduced to `dd/MM/yyyy`; timestamps lose their time component. Android Task has no separate task type or notes fields, although the web model does. A naive upload could erase richer web data or change scoring after a restart.

Add versioned local migrations, preserve currently unknown remote fields, and test reopen/round-trip equality before enabling upload.

Relevant source: Android `persistence/DataAccessObject.java`, `objects/Lead.java`, and `objects/Task.java`.

### P1 Encryption failures silently save plaintext

`CryptoManager.encrypt` returns the original plaintext after any exception. `decrypt` similarly returns unverified input if decryption fails. A missing or invalid key can therefore be hidden from the application. Backup is enabled without explicit database exclusions even though Keystore keys are device-bound.

Make new encryption failures explicit, version the encrypted format, and define a compatible migration/recovery strategy for existing rows. Exclude device-bound encrypted database and session material from unsupported backup/restore paths. Keep local encryption at rest; upload data over HTTPS using the signed-in user's session, not the device's Keystore ciphertext.

### P2 Local lifecycle and relation handling need repair

The DAO seeds demo leads whenever the table is empty, which would recreate records after a valid delete-all or empty-account sync. Seed only an explicit demo database. `deleteLead` deletes only the lead row; foreign key enforcement is not enabled and notifications have no declared foreign key. Implement and test a transactional relationship policy and deletion tombstones for sync.

Database initialization also opens through Services and then opens the resolved database path again. Review this lifecycle before background sync introduces concurrent access.

## Synchronization implementation requirements

Both applications should continue using the same Supabase project. Android keeps SQLite as its local working database and persists pending changes until the server acknowledges them. No additional repository is required.

| Information | Android today | Web and cloud today | Required alignment |
| --- | --- | --- | --- |
| Customer name | FirstName and LastName, encrypted locally | first_name and last_name | Map decrypted domain values; retain local encryption |
| Phone | PhoneNumber, encrypted locally | phone | Normalize mapping without changing the user's value |
| Email | Email, encrypted locally | lead_email | Use actual schema name, not an assumed `email` field |
| Customer identity | Local LeadID | Numeric lead_id | Add stable sync UUID and preserve local/cloud ID mapping |
| Record owner | None | None deployed | Auth user ID plus account-scoped local database |
| Follow-up date | dd/MM/yyyy text | ISO date/time value | Decide date-only versus instant and migrate consistently |
| Last interaction | Model only | last_interaction_date and last_interaction_by | Persist on Android and preserve both directions |
| Vehicle relationships | Embedded VI and TI columns | Separate relationships and vehicle catalogue | Match by stable catalogue identity, not local numeric ID |
| Task details | Title, timestamp, completed | Title, task_date, completed, task_type, notes | Preserve richer fields on Android |
| Change tracking | None | No shared sync protocol | Server revision, outbox, idempotency, and delete history |

Implementation order:

1. Confirm which account owns the existing customer records; preserve a database backup and counts.
2. Separate shared vehicle reference data from private data, remove permissive policies, and enforce same-owner relationships.
3. Repair web server session handling and remove render-triggered mutation routes before enabling writes.
4. Restore Android tests and persistence fields, then add Auth and per-account local storage.
5. Add a persistent encrypted outbox, UUIDs, server revisions, deletion history, and explicit conflict handling. A stale upload must not silently replace a newer server value.
6. Implement paginated pull and idempotent push. Commit pulled data and its cursor in one local transaction; do not acknowledge queued writes before the server commits them.
7. Verify the acceptance cases below on a test account before migrating real data.

## Synchronization acceptance cases

| Scenario | Expected result |
| --- | --- |
| Web creates a synthetic customer, Android syncs | Exactly one matching customer with all supported fields |
| Android edits the customer offline, app restarts, network returns | Edit survives restart and reaches web once |
| Web changes a record while Android has an older pending edit | Explicit conflict; no silent loss |
| A request succeeds remotely but its response is lost | Retry does not create a duplicate |
| A customer or task is deleted on either client | Deletion propagates and old offline data does not resurrect it |
| User A changes to User B | No records or pending writes cross accounts |
| Anonymous and second-account requests | No access to the first account's customer data |
| Task timezone and date-only follow-up round trip | Same intended day/time after sync and restart |
| Pull is interrupted between pages or before local commit | Resume without skipping or duplicating changes |
| Migration on a copy of the current database | Counts, relationships, and customer fields are preserved |

Reference guidance: [Supabase server and browser clients](https://supabase.com/docs/guides/auth/server-side/creating-a-client), [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), and [database function security](https://supabase.com/docs/guides/database/functions).
