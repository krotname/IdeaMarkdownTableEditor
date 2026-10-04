# White Windows CI runner

Owner CI, release builds/publishing, the runner self-test and Maven Central /
Marketplace workflows target `[self-hosted, Windows, X64, adler-white-idea]`.
This is preparation, not a completed deployment: on 2026-10-04 the White host was
unavailable and the repository had no registered runner. Do not merge the
migration until the PR has successful White CI.

## Routing and trust

| Event | Route |
| --- | --- |
| Canonical repository; original actor and rerun actor are the owner | White Windows |
| PR also authored by the owner, from this repository, not a fork | White Windows |
| Other PR author, fork (including the owner's fork), bot or non-owner rerun | Public GitHub-hosted Windows/Linux |
| Release dispatch/tag | Owner only; `main` or `v*` tag; all jobs depend on the gated version job |
| Runner self-test | Owner only; a reviewed branch, including the migration branch |
| Maven Central and Marketplace, including dry run | Owner only; `main`; existing confirmation/secrets/environment gates retained |

The optional repository variable `CI_WINDOWS_RUNS_ON` is a JSON array of labels.
Leave it unset for the default route. An override must retain `self-hosted`,
`Windows`, `X64` and this repository's unique label; it must not target a hosted,
shared, organisation-wide or unrelated runner. The old `adler-lenovo` selector is
not a fallback for an offline White runner.

Linux actionlint, dependency review, Java CodeQL and native Scorecard target the
repository-scoped Direct pool `arc-prod-adler-idea-mte`. The exact
`CI_LINUX_RUNS_ON` JSON override is `["arc-prod-adler-idea-mte"]`; set it only after
the canonical pool is live. Unset overrides select this same pool rather than
skip the checks. External/untrusted PRs retain public hosted Linux. Both actors,
the PR author and the same-repository/non-fork checks guard every home route.
Java CodeQL uses JDK 17 and the validated Gradle wrapper for an explicit traced
build of plugin and core classes. Full tests, performance and Plugin Verifier
remain required in Windows CI.

Actionlint 1.7.12 and Scorecard 5.5.0 are downloaded with pinned SHA256 checks.
Actionlint keeps shellcheck and pyflakes (installed into an unprivileged venv).
Scorecard uses native `--format sarif`, enabled by `ENABLE_SARIF=true`, retaining
the SARIF artifacts and GitHub code-scanning uploads. The upstream v2.4.4 policy
is preserved in `.github/scorecard-policy.yml`: it is required for native SARIF.
The default-branch scan retains all original checks; a separate exact-head scan
adds the commit-supported checks without reducing the default scan. Both output
files are parsed before upload; CLI exit zero alone is not proof of valid SARIF.
The CLI does not publish to
Scorecard's external REST dataset. No job needs sudo, apt, pipx or a Docker socket.
Manual dependency-review dispatch requires exact base/head commit inputs.

These predicates enforce the reviewed workflows' routing; they are not a sandbox
for arbitrary modified workflow YAML. Before bringing a public-repository runner
online, restrict repository write access and require approval for workflows from
all outside collaborators in Actions settings. Review the current PR head and
workflow changes before any such approval. Do not approve a workflow that removes
the routing guards or directly requests a home runner.
The repository API policy was set and read back as `all_external_contributors`
on 2026-10-04 before admitting home public-PR jobs.

## Install on ADLER-WHITE-W1

1. Restore access to the physical host and confirm its identity and Windows x64
   operating system. Use a dedicated low-privilege CI account, isolated from
   personal browser profiles, credentials and the Npp runner's work/tool caches.
2. Install current Git for Windows (including Git Bash), PowerShell 7 (`pwsh`)
   and GitHub CLI (`gh`) on the runner account's PATH. Bash release steps require
   `mapfile`, `find`, `sort`, `sha256sum`, `tr` and `head`. Do not persist a personal
   GitHub CLI login for the worker; release jobs use the per-job `GITHUB_TOKEN`.
3. Provide Temurin JDK 17 to the runner account. Keep the checked-in Gradle wrapper
   (9.8.0 and its pinned SHA256); no system Gradle is needed. The account must be
   able to write its own Gradle/JDK/tool caches. Start with at least 16 GiB available
   RAM and 30 GiB free disk for Gradle, IDEA and Plugin Verifier downloads; monitor
   actual peak usage before adding parallel workers.
4. Verify downloads from the runner account to Maven Central, Gradle services and
   plugin portal, JetBrains repositories/cache redirector/download hosts, GitHub
   Actions artifact/OIDC endpoints and Codecov. White previously stalled on the
   CloudFront-backed IntelliJ Platform download. A successful download/build on
   the laptop does not close that host-specific blocker: require full downloads
   and baseline Plugin Verifier completion on White. Do not silently switch to a
   shared runner or hosted Windows if it stalls.
5. Download the current official `actions/runner` Windows x64 release, verify its
   published SHA256 and extract it into a dedicated IDEA runner directory. Use a
   current runner supporting the pinned Node 24 actions (at least 2.327.1), with
   automatic updates enabled.
6. Register at **repository scope**, using the short-lived registration token from
   `krotname/IdeaMarkdownTableEditor` Actions settings via the interactive prompt:

   ```powershell
   .\config.cmd --url https://github.com/krotname/IdeaMarkdownTableEditor --name adler-white-idea --labels adler-white-idea --work _work
   ```

   Do not put the token in a command line, repository file or log. Check that the
   registered labels include the automatic `self-hosted`, `Windows`, `X64` labels
   plus `adler-white-idea`.
7. Start one worker under the dedicated CI account, as a Windows service or via
   `run.cmd`. Current CI uses headless tests and Plugin Verifier; the optional
   local `ideaPlaybackSmoke` task is not substituted for those checks. A future
   live GUI test would require an interactive desktop.
8. Retain the existing GitHub Maven/signing secrets and the protected `marketplace`
   environment with its reviewer and branch policies. Do not copy publishing
   secrets into local Gradle properties. Publishing is not needed to validate
   this runner migration.

## Acceptance before merge

1. Rerun the PR checks as the owner after the runner is online. Inspect logs for
   the actual runner name and confirm it is ADLER-WHITE-W1.
2. Require `Tests / IDEA plugin`: wrapper validation, clean `check`, packaged
   license/release metadata checks, core unit tests, JaCoCo coverage (minimum
   70%), core performance benchmarks, `buildPlugin`, ZIP artifact upload and
   baseline IDEA `verifyPlugin`. Preserve the no-cache/rerun flags.
3. Dispatch **Runner self-test** on the reviewed migration branch as the owner.
   It runs clean check, performance, baseline Plugin Verifier and plugin packaging
   without publishing. Require the reported host to be White, not the laptop.
4. After a real Linux pool is configured, rerun actionlint, dependency review and
   Java CodeQL; verify Scorecard separately. Skipped checks are not evidence of
   successful CI.
5. Keep the PR open while White is unavailable, JetBrains downloads stall or any
   required check fails. Release checksums, SBOM, provenance, Marketplace and
   Maven publication checks remain in the workflows; do not publish to validate
   this migration.
