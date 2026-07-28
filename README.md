# Wiki

TODO: one paragraph, in your own words — what this tool does and who it's for. See `docs/README-CHECKLIST.md` in light-workspace for what makes this section good.

## Screenshots

TODO: device/emulator captures of the actual states a user sees.

## Install (sideload)

TODO: download the latest release APK from [Releases](https://github.com/tyleryancey/light-wiki/releases) and sideload it via Android Studio / adb, per the LightOS developer docs.

## LP3 / LightOS compatibility

TODO: state what was actually tested — emulator vs. physical Light Phone III, LightOS version. Tool ID: `dev.tyler.wiki`.

## Build from source

```
git clone https://github.com/tyleryancey/light-wiki.git
cd $(basename tyleryancey/light-wiki)
./gradlew :tool:assembleRelease
```

Building against the SDK's GitHub Packages artifacts requires a GitHub token with `read:packages` scope, set as `GH_PACKAGES_USER`/`GH_PACKAGES_TOKEN` env vars or `gpr.user`/`gpr.key` in `local.properties`.

## Attribution

Built on [lightphone/light-sdk](https://github.com/lightphone/light-sdk). This repo inherits its MIT license.
