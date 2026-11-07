# knuvio mobile changes

Everything knuvio mobile adds on top of [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile). Each entry says
why the change exists, which files it touches, and how to remove it. Read this first when an upstream merge conflicts.

Files under `androidApp/src/full/res`, `androidApp/src/fullDebug/res`, `knuvio-branding/` and the knuvio workflow are
knuvio-only and cannot conflict. **Upstream files that knuvio edits** are the ones to watch:

| Upstream file | Changed by |
|---|---|
| `composeApp/src/commonMain/composeResources/drawable/app_icon_*.png`, `app_logo_wordmark*.png` | [K-01](#k-01-branding-icons-splash-app-name-wordmarks) |
| `androidApp/build.gradle.kts` | [K-02](#k-02-application-ids-updater-and-source-link) |
| `composeApp/.../updater/AppUpdaterRepository.kt`, `composeApp/.../settings/LicensesAttributionsPage.kt` | [K-02](#k-02-application-ids-updater-and-source-link) |
| `composeApp/.../updater/VersionUtils.kt` | [K-05](#k-05-knuvio-revisions-in-the-updater) |

Upstream status: **knuvio only** = not meant for upstream; **upstream PR #N** = proposed; **in upstream since X** = the change
can be dropped from knuvio after merging X.

---

## K-01 Branding: icons, splash, app name, wordmarks

- **Why:** knuvio mobile must be recognisable next to the official app on the same phone.
- **What:**
  - Launcher icons with the K: adaptive icon foreground, monochrome layer and background colour, legacy square and round
    icons, for the default icon and the five alternative ones (arctic blue, emerald, rose gold, copper, graphite).
  - Splash icons (Android 12+) for the same six.
  - App name "knuvio" in every locale that defines `app_name`; debug builds show "knuvio Debug".
  - In-app wordmarks (`app_logo_wordmark`, `_original` and six themes) and the in-app icon picker images (`app_icon_*`).
    These are Compose resources, which a flavor cannot override, so they are replaced in place: the only upstream files
    K-01 edits. The wordmarks are pixel-identical to knuvio TV's for the themes both apps share.
- **Files:** `androidApp/src/full/res/**` (`mipmap-*`, `drawable-nodpi/`, `values*/strings_knuvio.xml`,
  `values/colors_knuvio.xml`), `androidApp/src/fullDebug/res/values/strings_knuvio.xml`,
  `composeApp/src/commonMain/composeResources/drawable/{app_icon_*,app_logo_wordmark*}.png` (upstream files),
  `knuvio-branding/**` (source artwork, upstream's original wordmark and `make_mobile.py`).
- **Remove:** delete the overlays and restore the Compose images from the upstream tag
  (`git checkout <tag> -- composeApp/src/commonMain/composeResources/drawable`).
- **Upstream status:** knuvio only.
- **Watch:** a merge that changes one of the replaced Compose images conflicts (binary): take either side, then rerun
  `make_mobile.py`. If upstream redraws its wordmark, refresh `knuvio-branding/source/upstream-wordmark.png` from the tag
  first. A new upstream locale whose `strings.xml` in `composeApp/src/androidMain/res` defines `app_name` needs its own
  `strings_knuvio.xml`; a new theme or icon option needs the script updated and rerun. An overlay only replaces an upstream
  resource with the same name and qualifiers.

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
