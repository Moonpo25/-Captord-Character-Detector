name: Pull Request

about: Use this template for every non-trivial change to Captord.
title: "<type>(<scope>): <subject>"
labels: []
assignees: []

---

## Summary

<!-- One or two sentences on what this PR does and why. -->

## Linked issues

<!-- `Fixes #123`, `Closes #456`, or `Refs planning-docs-kotlin/...`. Leave blank if none. -->

## Type of change

- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds behavior)
- [ ] Breaking change (fix or feature that would change existing behavior)
- [ ] Documentation / repo infra only

## Test plan

<!-- How did you verify this works? List commands and devices. -->

- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:lintDebug` passes (or only introduces known warnings)
- [ ] Manual test on a physical device (list model + Android version below)
- [ ] If UI change: Compose preview or screenshot attached

**Device(s) tested:**

## Privacy / security checklist

- [ ] No new permission added to `AndroidManifest.xml` (or justified below)
- [ ] No cloud upload / analytics / network call introduced
- [ ] No keystore, signing credential, or signing path committed
- [ ] Pending output still hidden until finalization succeeds (if touched recording lifecycle)

## Notes for reviewer

<!-- Anything non-obvious, follow-ups, or things that should NOT block merge. -->