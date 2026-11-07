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
| `androidApp/build.gradle.kts`, updater, banner, about page, manifest and `file_paths.xml` files listed in K-06 | [K-06](#k-06-automatic-updates-with-dex-metadata-from-nuviotv) |

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

## K-03 Upstream sync and release workflow

- **Why:** follow upstream releases without manual work, and publish signed APKs.
- **What:** `.github/workflows/knuvio-sync-release.yml`: scheduled merge of upstream release tags, manual `+k.N` revision
  builds, the release tag passed as `versionName`, disabling inherited workflows, signed release build and GitHub release.
  Adapted from knuvio TV's workflow. Details in `.claude/CLAUDE.md`.
- **Files:** `.github/workflows/knuvio-sync-release.yml`.
- **Remove:** delete the workflow; releases then have to be built by hand.
- **Upstream status:** knuvio only.

## K-04 Project notes

- **Why:** keep how knuvio mobile works, and why each change exists, next to the code.
- **Files:** `.claude/CLAUDE.md`, `KNUVIO_CHANGES.md`.
- **Upstream status:** knuvio only.

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

## K-06 Automatic updates with dex metadata (from NuvioTV)

- **Why:** the same in-app updater as NuvioTV and knuvio TV: updates compiled with their baseline profile at install time,
  downloaded in the background, and installed without a confirmation screen when the user opts in.
- **What:** a port of NuvioTV's updater work by kernexshadow
  ([#4121](https://github.com/NuvioMedia/NuvioTV/pull/4121) background downloads, silent installs, dex metadata and the
  "updated" banner; [#4239](https://github.com/NuvioMedia/NuvioTV/pull/4239) installs on adopted storage;
  [#4344](https://github.com/NuvioMedia/NuvioTV/pull/4344) no dex metadata for debuggable builds), adapted to the mobile
  app's Kotlin Multiplatform updater:
  - Every update installs through a PackageInstaller session that carries the APK's own `.dm` (Android 9+), so ART
    compiles the new version during the install. The release build embeds AGP's per-API `.dm` files under
    `assets/updater_dm`. A failed session retries without the `.dm`, then falls back to the system installer.
  - Settings → About → "Install updates automatically" (Android 12+, off by default): a job checks every 12 hours and
    downloads a new release, and another installs it while the device is idle, without asking. "Notify after automatic
    updates" (on by default) shows the "updated" banner, with a green check and the release notes, on the next launch;
    it closes by itself after 20 seconds.
  - Differences from NuvioTV: background checks and downloads wait for an unmetered network and run only while automatic
    installs are on (the mobile app has no update banner setting, and phones may be on mobile data); updates the user
    starts from the banner are downloaded to the same place, so a background download is reused.
- **Files:** new `composeApp/src/commonMain/.../updater/{AutoUpdate,AutoUpdateBanner}.kt`,
  `composeApp/src/commonMain/.../settings/AutoUpdateSettings.kt`, `composeApp/src/androidFull/.../updater/{ApkInstaller,
  AndroidAutoUpdatePlatform,UpdateDexMetadata,UpdateDownloadStore,UpdateInstallResultReceiver,UpdateJobService}.kt`,
  `composeResources/values{,-pt}/strings_knuvio.xml`, `UpdateDexMetadataTest.kt`. Upstream files edited:
  `androidApp/build.gradle.kts` (the `.dm` embed task), `androidApp/src/full/AndroidManifest.xml` (permission, job
  service, result receiver), `composeApp/src/androidMain/res/xml/file_paths.xml` (downloads in `filesDir/updates`),
  `composeApp/src/androidFull/.../updater/AndroidAppUpdaterPlatform.kt` (registers the platform, session installs),
  `composeApp/src/commonMain/.../updater/{AppUpdater,AppUpdaterBanner}.kt`, `composeApp/src/commonMain/.../settings/
  AboutSettingsPage.kt` (one line). Every edit is marked `knuvio`.
- **Remove:** revert the commit.
- **Upstream status:** knuvio only. The NuvioTV originals are in NuvioTV releases: #4121 since 1.1.0-beta.8, #4239 and
  #4344 since 1.1.0-beta.9.
- **Watch:** upstream changes to the updater files above may conflict; keep the `knuvio` hooks. `composeApp` builds only
  the Kotlin code of `src/androidFull`: its manifest and resources there are not part of the build, so Android
  declarations go in `androidApp/src/full`. If NuvioMobile adds its own
  background updates, drop this entry in favour of upstream's. Check with the updater tests (see `.claude/CLAUDE.md`);
  on a device, `adb shell cmd jobscheduler run -f com.knuvio.mobile 10020` runs the check and download job and
  `... 10021` the idle install job.
