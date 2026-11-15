# SignPath Windows signing (planned)


User decided (2026-10-15) to add Windows code signing via **SignPath Foundation** (free OSS signing),
after the winget/Scoop/Homebrew/Flathub rollout with 2.5.0 — not as a prerequisite for winget.
Plan (the local plan file no longer exists): application + code-signing-policy page by the user,
then SignPath GitHub action with deep-signing artifact configurations for MSI and app-image ZIP.

**Why:** SmartScreen warnings, Smart App Control blocks unsigned apps, fewer Defender false positives in winget validation.

**How to apply:** once the user reports SignPath approval (secret `SIGNPATH_API_TOKEN`), implement Phase 1 of the plan.
Related: flatpak-testbuild-after-2-5-0.
