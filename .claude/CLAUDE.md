# knuvio mobile

knuvio mobile is a personal build of [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile), the Nuvio phone
app, published at [kernexshadow/knuvio-mobile](https://github.com/kernexshadow/knuvio-mobile). It is the mobile sibling of
[kernexshadow/knuvio](https://github.com/kernexshadow/knuvio) (Android TV) and works the same way: it follows upstream
releases automatically and differs from upstream only in branding, application IDs, the in-app updater source, and an extra
revision number for its own builds. Everything knuvio mobile adds is listed in [KNUVIO_CHANGES.md](../KNUVIO_CHANGES.md).
Only the Android app is built; the iOS app is not.

NuvioMobile is GPLv3, so the repository stays public: every published APK must have its source available.

## Repositories and branches

| Remote | Repository | Role |
|---|---|---|
| `origin` | `kernexshadow/knuvio-mobile` | This repo. `main` is the only branch and the release branch. |
| `upstream` | `NuvioMedia/NuvioMobile` | Source of truth. Releases are tags on its `cmp-rewrite` branch. |

- `main` = an upstream **release tag** + the knuvio commits on top. Only release tags are merged, never upstream
  `cmp-rewrite` HEAD, so every knuvio mobile release matches an exact upstream version.
- Contributions to Nuvio itself are made from the separate NuvioMobile checkout and the `kernexshadow/NuvioMobile` fork, on
  branches created from `upstream/cmp-rewrite`. Never open PRs from this repo, and never let knuvio commits reach an
  upstream PR branch.
- A knuvio feature that should also go upstream: develop it in NuvioMobile, open the upstream PR there, then merge or
  cherry-pick it into knuvio mobile `main` and record it in KNUVIO_CHANGES.md with the PR number.

## Versions and releases

| Build | Release tag | APK `versionName` | GitHub release |
|---|---|---|---|
| Automatic, upstream `0.6.9-beta` | `0.6.9-beta+k` | `0.6.9-beta+k` | prerelease if upstream's is |
| Manual, revision 1 of that version | `0.6.9-beta+k.1` | `0.6.9-beta+k.1` | same |

- `+k` is semver build metadata. The updater (`composeApp/.../features/updater/VersionUtils.kt`) reads `+k.N` as a
  tie-breaker between builds of the same upstream version, so `0.6.9-beta+k < 0.6.9-beta+k.1 < 0.6.10-beta+k`. As upstream,
  a tag with a prerelease part (`-beta`) is offered only on the app's beta update channel.
- `versionName` is not in the repo: the workflow passes the release tag with `-Pnuvio.app.versionName`, as upstream's
  release workflow passes its tag. Without it, Gradle uses `MARKETING_VERSION` from `iosApp/Configuration/Version.xcconfig`.
- `versionCode` is upstream's `CURRENT_PROJECT_VERSION` from the same file. Revisions reuse it, which Android allows.

## Sync and release workflow

`.github/workflows/knuvio-sync-release.yml`:

- **Automatic**, every 6 hours (cron `47 */6 * * *`, UTC): finds the newest non-draft upstream release, prereleases
  included. If `<tag>+k` already exists it does nothing. Otherwise it merges the upstream tag into `main`, pushes, builds,
  and publishes.
- **Manual**: Actions → "Sync upstream and release" → Run workflow, or
  `gh workflow run knuvio-sync-release.yml -R kernexshadow/knuvio-mobile -f manual=true`. It builds the current `main` as
  the next `+k.N` of the newest upstream release that `main` contains, and the release notes list commits since the
  previous knuvio release. `-f tag=<upstream tag>` (without `manual`) builds a specific upstream release.
- Merge conflicts: the job fails with an error annotation and GitHub emails the owner. Resolve locally (see below).
- It disables every other workflow in the repo. Inherited upstream workflows must not run here; `update-store-source.yml`
  would react to every knuvio release.
- It runs the updater unit tests, then `:androidApp:assembleFullRelease`, and uploads the four APKs (arm64-v8a,
  armeabi-v7a, x86, x86_64; upstream builds no universal APK).
- Every `gh` call passes `-R`/an explicit repo: with an `upstream` remote present, `gh` defaults to NuvioMedia.
- To rebuild a version that already has a release, delete that release and its tag first, then run the workflow again.

Secrets used are referenced by name in the workflow; the signing secrets hold the same keystore as knuvio TV.
`KNUVIO_LOCAL_PROPERTIES_BASE64` is the app's `local.properties` without `sdk.dir` and `NUVIO_RELEASE_*` (the workflow adds
the signing entries, which the app reads from `local.properties` only). `SYNC_PAT` (fine-grained, Contents + Workflows
read/write on this repo) does the checkout and push, because `GITHUB_TOKEN` cannot push upstream changes to
`.github/workflows`.

## Resolving an upstream merge conflict

```bash
git fetch upstream --tags
git merge <upstream-tag>           # e.g. 0.6.10-beta
# resolve: KNUVIO_CHANGES.md says why each knuvio change exists and which files it touches
git push origin main
gh workflow run knuvio-sync-release.yml -R kernexshadow/knuvio-mobile   # publishes <tag>+k
```

## Rules for knuvio changes

1. **Prefer overlays to edits.** Android resources go in `androidApp/src/full/res` (flavor source set, overrides
   `composeApp`'s `androidMain` resources) or `androidApp/src/fullDebug/res`. They never conflict with upstream. Compose
   resources (`composeApp/src/commonMain/composeResources`) cannot be overridden that way; replace them in place only when
   there is no other way. Edit upstream code only when there is no other way, and keep the diff minimal.
2. **Record every change** in KNUVIO_CHANGES.md in the same commit: what, why, files, how to remove, upstream status.
3. Commit messages start with `knuvio:`. Code, comments and docs are in English.
4. When upstream adds a locale whose `composeApp/src/androidMain/res/values-*/strings.xml` defines `app_name`, add a matching
   `androidApp/src/full/res/values-<qualifier>/strings_knuvio.xml`, or that locale shows "Nuvio".
5. When upstream adds or renames a theme or launcher icon option, update and rerun the branding script (below).

## Branding assets

`knuvio-branding/` holds the source artwork (the same K as knuvio TV), a copy of upstream's original wordmark, and
`make_mobile.py`, which generates every knuvio mobile image (Python 3 with Pillow, NumPy, SciPy):

```bash
python knuvio-branding/make_mobile.py . <preview-dir>
```

It writes the launcher icons, adaptive icon layers and splash icons to `androidApp/src/full/res`, replaces the in-app
wordmarks and icon picker images in `composeApp/src/commonMain/composeResources/drawable`, and writes a preview image to
`<preview-dir>`; check it before committing. The adaptive icon background colour is `values/colors_knuvio.xml`.

## Local builds

- Same toolchain as NuvioMobile. Needs `local.properties` in the repo root (gitignored), with the same keys as knuvio TV's
  minus its Trakt redirect and TV-only build flags.
- `./gradlew :androidApp:assembleFullDebug` for a debug build; release builds need `NUVIO_RELEASE_*` in `local.properties`
  (pointing at the Android debug keystore is fine locally).
- Updater tests: `./gradlew :composeApp:testAndroidHostTest --tests 'com.nuvio.app.features.updater.*'`.
- Debug app ID `com.knuvio.mobiledebug`, label "knuvio Debug". Release app ID `com.knuvio.mobile`, label "knuvio". Both
  install next to the official Nuvio apps and next to knuvio TV.
