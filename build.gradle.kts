plugins {
    alias(libs.plugins.android.app) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply true
    alias(libs.plugins.kover) apply true
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

kover {
    // bundled IntelliJ agent hard-codes an exclude of `com.android.*`
    // to avoid instrumenting Android SDK stubs. KordX's production
    // code lives in `com.android.rockages.kordx.*` and is silently
    // filtered out by the agent — coverage is reported as 0% across
    // all KordX classes. The opt-out property
    // `kover.android.excludes.disable` was added in kover 0.9.0 but
    // is also reported broken in 0.9.8 (same issue #810).
    //
    // WORKAROUND STATUS: none available. The kover report
    // infrastructure (multi-module artifact aggregation, class-name
    // filters, CI task) is correct and the report generates without
    // errors. Coverage data will auto-populate once the upstream
    // kover fix lands (tracked in kotlinx-kover issue #810).
    //
    // See: https://github.com/Kotlin/kotlinx-kover/issues/810
    reports {
        filters {
            excludes {
                classes(
                    "com.android.rockages.kordx.i18n.Translation_gKt*",
                    "com.android.rockages.kordx.i18n.Translations_gKt*",
                    "com.android.rockages.kordx.infra.db.*_Impl*",
                    "*.BuildConfig",
                )
            }
        }
    }
}

// dependencies in the root (the "merging module") to wire coverage
// from the subprojects' `testDebugUnitTest` (JVM) tasks into the
// aggregated report. Without these, `:koverGenerateArtifact` runs as
// a no-op at the root and the HTML / XML reports show "No coverage
// information was found". See
// the investigation + the kover 0.8.3 multi-module Android contract.
// All 4 modules are listed so the report covers all production
// Kotlin code (modules with no tests — currently `:core` / `:infra`
// — still appear in the report with 0% line coverage, which is the
// honest signal).
dependencies {
    kover(project(":app"))
    kover(project(":core"))
    kover(project(":infra"))
    kover(project(":metaphony"))
}
