# Home CI runners

Windows workflows use `CI_WINDOWS_RUNS_ON` with `[self-hosted, Windows, X64,
adler-white-idea, adler-white-ephemeral]`. White coordinates GitHub admission and
JIT registration and runs the disposable Windows Server container under
`ContainerUser` using its existing Docker engine and Hyper-V isolation. The
retired Black Windows image is not recreated; no separate Windows VM is needed.
No runner is installed on the owner's physical Windows desktop.

## Routing and trust

Only the canonical repository's owner may start or rerun home jobs. Pull requests
must also be authored by the owner, originate in this repository and not be forks.
Other authors, bots, forks and non-owner reruns are skipped before checkout.
There is no GitHub-hosted or laptop fallback.

`CI_WINDOWS_RUNS_ON` must retain Windows/X64 and the White repository/admission
labels. Older workflow snapshots can still request `adler-black-ephemeral`; the
operator maps that compatibility label only to the White container backend.
Direct Linux checks use `arc-prod-adler-idea-mte`;
`CI_LINUX_RUNS_ON` must select that same repository-scoped pool. Release and
Marketplace/Maven jobs retain their original main/tag, confirmation, environment,
signing and publication gates. A migration check does not authorize publication.

Linux actionlint, dependency review, Java CodeQL and native Scorecard use Direct
mode without a Docker socket. Native Scorecard 5.5.0 retains the upstream policy,
default-branch and exact-head SARIF scans, JSON validation, artifacts and uploads.
A separate owner-only main job uses `arc-prod-adler-docker-idea-mte` and the pinned
original Scorecard action to preserve OIDC-signed public results. Its isolated
container runtime has a local socket, a non-root worker and bounded resources.
Both pools must be admitted in ProdOps and verified live before enabling jobs.

Windows CI needs Temurin JDK 17, the checked-in Gradle 9.8.0 wrapper, Git Bash,
PowerShell and a current runner supporting Node 24 actions. Codecov receives the
installed Git `bin` directory through `GITHUB_PATH`; no privileged installation
is performed in a job. Provisioning and admission are documented in VpnOps
`ops/ci-windows-white`. The worker is limited to 12 GiB, six CPUs and 64 GiB of
writable storage, with disk and memory admission checks before launch. Operator
credentials stay on White; the guest receives
only the short-lived JIT configuration and normal per-job GitHub token.

`CI_WINDOWS_JAVA_OPTIONS` is passed as `JAVA_TOOL_OPTIONS` in trusted Windows jobs
when the approved LAN proxy is required for complete JetBrains CDN downloads.
Keep localhost/LAN exclusions and TLS verification; do not change host routing.

The repository's external-contributor approval policy is
`all_external_contributors` (read back on 2026-10-04). Workflow trust predicates
are admission rules, not a sandbox for arbitrary changes to workflow YAML.

## Acceptance before merge

1. Require final-head Windows CI in a White container: wrapper validation, clean
   `check`, packaged metadata, unit tests, 70% JaCoCo coverage, core performance,
   `buildPlugin`, ZIP artifact and baseline IDEA `verifyPlugin`.
2. Dispatch the existing Runner self-test on the reviewed branch. Require clean
   tests, performance, Plugin Verifier and packaging without publication.
3. Require final-head Linux lint, dependency review and Java CodeQL on the actual
   repository-scoped Direct pool; verify native Scorecard and its SARIF output.
4. After merge, require main CI and signed Scorecard publication on the admitted
   Docker pool. A queued, skipped or prepared job does not satisfy acceptance.
5. Keep the migration open while a pool is unavailable, downloads stall, disk/RAM
   admission fails, a required check fails or Windows acceptance is incomplete.
   Do not relax release checksums, SBOM, provenance or publication gates.
