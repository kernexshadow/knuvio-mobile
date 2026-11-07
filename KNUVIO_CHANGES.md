# knuvio mobile changes

Everything knuvio mobile adds on top of [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile). Each entry says
why the change exists, which files it touches, and how to remove it. Read this first when an upstream merge conflicts.

Files under `androidApp/src/full/res`, `androidApp/src/fullDebug/res`, `knuvio-branding/` and the knuvio workflow are
knuvio-only and cannot conflict. **Upstream files that knuvio edits** are the ones to watch:

| Upstream file | Changed by |
|---|---|
| `androidApp/build.gradle.kts` | [K-02](#k-02-application-ids-updater-and-source-link) |
| `composeApp/.../updater/AppUpdaterRepository.kt`, `composeApp/.../settings/LicensesAttributionsPage.kt` | [K-02](#k-02-application-ids-updater-and-source-link) |
| `composeApp/.../updater/VersionUtils.kt` | [K-05](#k-05-knuvio-revisions-in-the-updater) |

Upstream status: **knuvio only** = not meant for upstream; **upstream PR #N** = proposed; **in upstream since X** = the change
can be dropped from knuvio after merging X.

---

## K-02 Application IDs, updater and source link

- **Why:** knuvio mobile installs next to the official app, is signed with its own key, must update from knuvio mobile
  releases, and links to its own source (GPLv3).
- **What:** application ID `com.knuvio.mobile`, debug `com.knuvio.mobiledebug` (distinct from every knuvio TV ID). The
  in-app updater reads releases from `kernexshadow/knuvio-mobile`, and the licenses page links to this repository.
- **Files:** `androidApp/build.gradle.kts` (2 lines: `applicationId`, the debug `variant.applicationId`),
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/updater/AppUpdaterRepository.kt` (the releases URL),
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/LicensesAttributionsPage.kt` (`NuvioRepositoryUrl`).
- **Remove:** revert the commit. Installed knuvio mobile apps would then be replaced by, or conflict with, the official app.
- **Upstream status:** knuvio only.
- **Watch:** the deep link schemes stay upstream's (`nuvio://`, `stremio://`, `https://nuvio.tv`): with both apps installed,
  Android asks which app opens them, including the Trakt and Simkl login callbacks (`nuvio://auth/...`).

## K-05 knuvio revisions in the updater

- **Why:** manual knuvio builds of the same upstream version (`+k.1`, `+k.2`, …) must be offered as updates, while any newer
  upstream version still wins.
- **What:** `SemanticVersion` gets `knuvioRevision`, parsed from `+k` / `+k.N` build metadata and compared only when everything
  else is equal. Other build metadata stays ignored, so upstream's tests still pass. Same change as knuvio TV's K-05. The
  order is `0.6.9-beta+k < 0.6.9-beta+k.1 < 0.6.10-beta+k`. As upstream, a tag with a prerelease part (`-beta`) is offered
  only on the beta update channel.
- **Files:** `composeApp/src/commonMain/kotlin/com/nuvio/app/features/updater/VersionUtils.kt` (upstream file),
  `composeApp/src/commonTest/kotlin/com/nuvio/app/features/updater/KnuvioRevisionTest.kt` (knuvio-only tests).
- **Remove:** revert the commit and stop publishing `+k.N` builds (the workflow's manual mode depends on it).
- **Upstream status:** knuvio only.
- **Watch:** upstream edits to `VersionUtils.kt` will conflict. Keep the `knuvioRevision` comparison as the last tie-breaker
  and run the updater tests (see `.claude/CLAUDE.md`).
