# Project language rule

Write all project documentation, notes, and code comments in English only. This applies to new and modified content throughout the repository. Preserve non-English application content and test data when needed to verify localization or Unicode behavior. User conversations may use the user's preferred language.

# Test-driven development

For new behavior and bug fixes, use a test-driven development cycle: first write a meaningful test that demonstrates the expected behavior and fails for the intended reason, then implement the smallest change that makes it pass, and refactor while keeping the relevant tests passing. Run appropriate regression checks and report their results. Preserve existing assertions; do not weaken tests merely to obtain a passing result. When a test cannot be written or run first, document the reason, the verification performed, and any remaining risk.

# Pull requests and release operations

Implement features and changes on a branch and create a pull request for merging into main. Do not merge directly into main unless the user explicitly requests an exception. If work is incomplete or still needs validation, use a draft pull request and clearly describe the remaining work.

Before creating, modifying, triggering, or approving CI/CD pipelines or performing a deployment, obtain explicit operator confirmation for the specific action. Prepare the proposed changes and verification details for review before requesting confirmation. Read-only inspection and local tests do not require this confirmation.
