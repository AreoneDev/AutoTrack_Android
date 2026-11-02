# Locale isolation and recreation investigation

## Reproduced causes

The API 35 instrumentation process retained Chinese after a localization test
closed its last activity. The previous cleanup called
`AppCompatDelegate.setApplicationLocales(en)` after `ActivityScenario.close()`.
In the cached AppCompat 1.7.0 source, `setApplicationLocales` on API 33+ obtains
`LocaleManager` through `getLocaleManagerForApplication()`. That method searches
active activity delegates and returns null when none remain. The setter then
does nothing. Changing the requested cleanup locale from the system default to
English cannot correct this missing context.

`LocaleIsolationTest.nextFixtureRestoresEnglishAfterLastActivityCloses` reproduces
the boundary between two fixtures in one instrumentation process. Before the
fix it failed with expected `ALL MY LEADS`, actual `所有客户` (`locale-baseline.log`).
After using the target application's `LocaleManager` directly on API 33+, the
same assertion passed. The fixture waits for the English resource configuration
and closes remaining activities before releasing the synthetic database.

The localization test also called `ActivityScenario.recreate()` immediately
after requesting a locale change. The framework already schedules recreation
for the configuration change. The competing requests could leave the scenario
in `DESTROYED` while the test attempted another recreation. The test now relies
on framework recreation and retains its original language and navigation checks.
The three-test root-cause run passed (`locale-fixed-targeted.log`).

## Additional review findings

- The 11sp stage badge used `#2196F3` on `#E3F2FD`, below 4.5:1 contrast.
  `BadgeContrastTest` reads the actual XML colors and failed before the text was
  changed to `#0D47A1`. The background remains unchanged.
- The French and Simplified Chinese resource sets lacked
  `account_session_description`; both translations were added.
- `PageAppearanceAuditTest` checks seven private pages in all six combinations
  of supported language and appearance, verifies the applied configuration and
  theme body-text contrast, and captures screenshots. Theme contrast is not a
  claim that every rendered text/background pair meets contrast requirements.
- An intermediate complete run encountered a calendar-to-account navigation
  assertion while the device logged activity lifecycle timeouts. The test now
  waits for the expected focused, resumed activity and applied configuration;
  this uses a bounded state check rather than a fixed delay or assertion retry.
- An intermediate narrow run also demonstrated that a new regression test can
  observe the old English root immediately after requesting Chinese. Waiting
  for main-thread idleness alone does not synchronize framework configuration
  delivery and window focus. All language-changing tests now wait for the
  expected resumed language configuration before retaining their text checks.
- Real toolbar colors failed the 3:1 large-title check in dark mode. Creation
  and detail toolbars now pair `colorPrimary` with `colorOnPrimary` for title
  text and control colors.
- A task workflow failed first because its completion icon had no accessible
  name. After adding localized action names and a 48dp target, the same workflow
  failed its real date-text contrast check. Replacing fixed green text with
  theme colors corrected that separate defect. Both completion and reopening
  action names are checked without removing the original persistence assertions.
- The detail edit menu used a literal English title. It now uses the existing
  translated `edit_lead` resource; the resource regression failed before the fix.

## Verification before final complete run

- Unit tests: 82 passed, zero failures (`final-review-build.log`).
- Root-cause and task UI regression: six passed (`final-review-targeted.log`).
- Intermediate normal complete run: 99 executed, 97 passed, two failed; two
  existing empty acceptance placeholders were ignored (`locale-full-suite.log`).
- Intermediate narrow complete run: 99 executed, 98 passed, one failed because
  the newly added reproduction asserted Chinese before configuration delivery;
  two existing placeholders were ignored (`locale-final-narrow-suite.log`).
- Normal and 320dp/130% font screenshots were captured for all seven private
  pages in all six locale/appearance combinations. Final captures and final
  complete-run results are recorded separately after validation.

## Scope and limits

Tests use a dedicated AVD, the `.accounttest` application, synthetic accounts,
blank cloud configuration, disposable local databases, and a loopback-only
synthetic synchronization bridge. No independent Supabase test project exists,
so these checks do not establish hosted profile persistence or real Auth/RLS
behavior. Screenshot and semantic checks do not replace manual TalkBack speech
and focus-order review. Final suite and display-matrix results must be recorded
before treating this work as ready for review.
