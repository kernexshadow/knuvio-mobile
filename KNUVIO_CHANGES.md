# knuvio mobile changes

Everything knuvio mobile adds on top of [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile). Each entry says
why the change exists, which files it touches, and how to remove it. Read this first when an upstream merge conflicts.

Files under `androidApp/src/full/res`, `androidApp/src/fullDebug/res`, `knuvio-branding/` and the knuvio workflow are
knuvio-only and cannot conflict. **Upstream files that knuvio edits** are the ones to watch:

| Upstream file | Changed by |
|---|---|
| `androidApp/build.gradle.kts` | [K-02](#k-02-application-ids-updater-and-source-link) |
| `composeApp/.../updater/AppUpdaterRepository.kt`, `composeApp/.../settings/LicensesAttributionsPage.kt` | [K-02](#k-02-application-ids-updater-and-source-link) |

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
