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
| `androidApp/build.gradle.kts`, `composeApp/build.gradle.kts`, `LicensesAttributionsPage.kt`, stream, player, addon manager and settings files listed in K-07 | [K-07](#k-07-built-in-usenet-upstream-pr-2725) |

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
  The build caps the Gradle heap at 6 GB and the Kotlin daemon at 3 GB in the runner's user-home `gradle.properties`:
  the repo's 12 GB + 8 GB is more than the 16 GB hosted runner has, and the runner was lost mid-build ("lost communication
  with the server").
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

## K-07 Built-in Usenet (upstream PR #2725)

- **Why:** use the same on-device Usenet streaming as knuvio TV (its K-07) on the phone before upstream merges it.
- **What:** [NuvioMedia/NuvioMobile#2725](https://github.com/NuvioMedia/NuvioMobile/pull/2725) by kernexshadow (branch
  `feat/built-in-usenet` on kernexshadow/NuvioMobile), a port of NuvioTV's built-in Usenet
  ([NuvioMedia/NuvioTV#4679](https://github.com/NuvioMedia/NuvioTV/pull/4679)) to the Android app:
  - Settings → Integrations → Usenet: NNTP providers (with Test provider and NuvioTV's connection tuner) and Newznab indexers,
    grouped under "Built-in Usenet sources", plus search filters, sorting presets, a custom sorting editor and engine options
    (automatic fallback, prefetch, pre-warm, performance profile). Sources are stored per profile, encrypted with an Android
    Keystore key, and never synced.
  - Results form a "Built-in Usenet" group on the stream screen and in the player's sources panel; the addon manager lists
    it among the addons, and it counts as an addon for auto-play, next-episode auto-play and source fallback. Addon streams
    with an `nzbUrl` play through the same engine.
  - Diagnostics: last startup timings and per-indexer/per-provider source statistics, kept on the device.
  - The engine is the same pinned [NuvioMedia/nuvio-usenet-engine](https://github.com/NuvioMedia/nuvio-usenet-engine)
    release as the TV PR (`usenet-native-b6ca59925e184dce`, source `e4b0717ae`). It runs as a child process, packaged as
    `libnuvio_usenet.so`. The binaries are not in Git: `native/usenet/android.gradle.kts` downloads the four ABIs named in
    `native/usenet/prebuilt/manifest.json`, checks their SHA-256 and caches them, so the first build needs network access.
  - Imported at PR head `2a7f9575d` as cherry-picks of its 18 commits onto `main` (1.1.0-beta.2+k.N), because the branch is
    based on `cmp-rewrite` past 1.1.0-beta.2 and `main` takes only release tags. The patch is identical to the PR's; only the
    context lines next to K-02's edits differ.
- **Files:** new `native/usenet/**` (Gradle adapter, pinned release, licenses), `composeApp/src/*/kotlin/com/nuvio/app/
  features/usenet/**` and their tests, `composeApp/.../settings/Usenet*.kt`,
  `composeResources/values/strings_usenet.xml`. Upstream files edited: `androidApp/build.gradle.kts` (engine jniLibs,
  license assets, debug symbols, the adapter), `composeApp/build.gradle.kts`, `MainActivity.kt`, `MainAppContent.kt`,
  `StreamDestination.kt`, `StreamLoadingScreen.kt`, `AppFeaturePolicy*.kt` (`usenetEnabled`), `LocalAccountDataCleaner.kt`,
  `AddonsScreen.kt`, the `Player*` runtime, engine and error files, `ProfileRepository.kt`, `PlaybackSettingsDialogs.kt`
  (scrolling choice dialogs), the settings models, screen,
  search, Integrations, playback and licenses pages, and the stream models, parser, card, badge, fetch, cache, search
  session, auto-play and repository files.
- **Remove:** revert the 18 commits (or merge an upstream release that contains #2725 and keep upstream's side).
- **Upstream status:** upstream PR #2725 (open).
- **Watch:** `LicensesAttributionsPage.kt` and `androidApp/build.gradle.kts` are also edited by K-02/K-06; keep both sides.
  When #2725 gets new commits, cherry-pick them the same way and update the head above. When an upstream release contains
  #2725, the merge brings the same changes: resolve conflicts in favour of upstream. Check with
  `./gradlew :composeApp:testAndroidHostTest --tests 'com.nuvio.app.features.usenet.*' --tests
  'com.nuvio.app.features.streams.StreamAutoPlaySelectorTest'`.
