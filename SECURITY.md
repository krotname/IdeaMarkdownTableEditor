# Security Policy

## Supported versions

Security fixes are handled on the default branch and the latest public release line.

## Reporting vulnerabilities

Do not open a public issue for suspected vulnerabilities, exploit details, or unsafe plugin behavior.

Report vulnerabilities through GitHub private vulnerability reporting:
https://github.com/krotname/IdeaMarkdownTableEditor/security/advisories/new

Include:

- affected version or commit,
- IDE and plugin versions,
- reproduction steps,
- impact assessment,
- suggested mitigation if available.

The maintainer aims to acknowledge valid reports within 48 hours and provide a remediation timeline after the impact is confirmed.

## Supply-chain controls

- Release packages include the plugin ZIP, `MARKETPLACE_SUBMISSION.md`, `SHA256SUMS.txt`, CycloneDX SBOM, and GitHub attestations.
- GitHub Actions are pinned by immutable commit SHA.
- Dependency Review, CodeQL, Scorecard, and actionlint run as repository quality gates.
