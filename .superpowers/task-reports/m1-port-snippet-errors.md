# M1 Port Report: SnippetText + ErrorMessages

Branch: `m1/phase0-fixtures-and-ports` (no git commands run per task instructions).

## Files ported

Donor (read-only, `com.tyleryancey.lightwiki.domain`) → target (`dev.tyler.wiki.pipeline`):

| Donor | Target |
|---|---|
| `app/src/main/kotlin/.../domain/SnippetText.kt` | `tool/src/main/kotlin/dev/tyler/wiki/pipeline/SnippetText.kt` |
| `app/src/main/kotlin/.../domain/ErrorMessages.kt` | `tool/src/main/kotlin/dev/tyler/wiki/pipeline/ErrorMessages.kt` |
| `app/src/test/kotlin/.../domain/SnippetTextTest.kt` | `tool/src/test/kotlin/dev/tyler/wiki/pipeline/SnippetTextTest.kt` |
| `app/src/test/kotlin/.../domain/ErrorMessagesTest.kt` | `tool/src/test/kotlin/dev/tyler/wiki/pipeline/ErrorMessagesTest.kt` |

## Assertion re-ordering

**Count: 0.** Every `assertEquals` call in both donor test files is the 2-arg
`(expected, actual)` form — there are no 3-arg message-first JUnit4 assertions
anywhere in the donor tests. Each call was checked individually
(SnippetTextTest: 17 assertEquals call sites across 12 tests [M1-review correction: an earlier draft said 18, counting the import line]; ErrorMessagesTest:
4 assertEquals calls across 4 tests). 2-arg `(expected, actual)` order is
identical between JUnit4 and kotlin.test, so all calls carried over verbatim;
only the imports changed:

- `org.junit.Assert.assertEquals` → `kotlin.test.assertEquals`
- `org.junit.Test` → `kotlin.test.Test`

No JUnit imports remain in the ported files.

## Semantic / non-mechanical decisions

- **None affecting behavior.** Production and test bodies are byte-for-byte
  identical to the donor apart from the `package` line and test-framework
  imports. All comments (including the "Task 9" section marker and the KDoc
  "Jsoup lands in M3" note) were kept verbatim.
- ErrorMessages references only `java.io.IOException`,
  `java.net.SocketTimeoutException`, `java.net.UnknownHostException` — all
  allow-listed JDK types. No Retrofit `HttpException` or other third-party
  exception types in the donor, so no STOP condition was hit.
- Pure-JVM check: no `android.*` / `androidx.*` imports in any ported file.
- Plugin-scan check: no banned tokens (`getSystemService(`, `startActivity(`,
  `contentResolver`, `.javaClass`, `Class.forName(`, `LocalContext`, etc.) in
  any donor code or string literal.
- Import ordering in ErrorMessagesTest was normalized to alphabetical
  (`java.*` before `kotlin.test.*`); purely cosmetic.

## Build dependency

`tool/build.gradle.kts` already had `testImplementation(libs.kotlin.test)`
(catalog: `org.jetbrains.kotlin:kotlin-test`, `gradle/libs.versions.toml:16`).
No build-file change needed.

## Verification

Command:

```
cd /Users/tyleryancey/Documents/lightphone/light-wiki
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :tool:testDebugUnitTest --tests "dev.tyler.wiki.*" 2>&1 | tail -30
```

Verbatim tail of the run:

```
> Task :sdk:ui:compileDebugJavaWithJavac NO-SOURCE
> Task :sdk:ui:processDebugJavaRes
> Task :sdk:ui:bundleLibRuntimeToJarDebug
> Task :sdk:ui:bundleLibCompileToJarDebug
> Task :sdk:client:kspDebugKotlin SKIPPED

> Task :sdk:client:compileDebugKotlin
w: file:///Users/tyleryancey/Documents/lightphone/light-wiki/sdk/client/src/main/kotlin/com/thelightphone/sdk/LightActivity.kt:204:40 'val characters: String!' is deprecated. Deprecated in Java.

> Task :sdk:client:compileDebugJavaWithJavac
> Task :sdk:client:processDebugJavaRes
> Task :sdk:client:bundleLibCompileToJarDebug
> Task :sdk:client:bundleLibRuntimeToJarDebug
> Task :tool:kspDebugKotlin
> Task :tool:compileDebugKotlin
> Task :tool:compileDebugJavaWithJavac NO-SOURCE
> Task :tool:processDebugJavaRes
> Task :tool:bundleDebugClassesToCompileJar
> Task :tool:bundleDebugClassesToRuntimeJar
> Task :tool:kspDebugUnitTestKotlin
> Task :tool:compileDebugUnitTestKotlin
> Task :tool:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :tool:processDebugUnitTestJavaRes
> Task :tool:testDebugUnitTest

[Incubating] Problems report is available at: file:///Users/tyleryancey/Documents/lightphone/light-wiki/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 14s
60 actionable tasks: 56 executed, 4 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.0.0/userguide/configuration_cache_enabling.html
```

## Test count confirmation

From `tool/build/test-results/testDebugUnitTest/` JUnit XML:

- `TEST-dev.tyler.wiki.pipeline.SnippetTextTest.xml`: tests="12", failures="0", errors="0", skipped="0"
- `TEST-dev.tyler.wiki.pipeline.ErrorMessagesTest.xml`: tests="4", failures="0", errors="0", skipped="0"

**Total: 16 tests, all passing.**
