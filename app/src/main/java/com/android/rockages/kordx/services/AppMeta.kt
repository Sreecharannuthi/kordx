package com.android.rockages.kordx.services

import com.android.rockages.kordx.BuildConfig

/** KordX application metadata. Update-check methods were removed. */
@Suppress("ConstPropertyName")
object AppMeta {
 const val appName = "KordX"
 const val author = "KordX Contributors"
 const val githubRepositoryOwner = "Sreecharannuthi"
 const val githubRepositoryName = "kordx"
 const val githubProfileUrl = "https://github.com/$githubRepositoryOwner"
 const val githubRepositoryUrl =
 "https://github.com/$githubRepositoryOwner/$githubRepositoryName"

 const val version = "v${BuildConfig.VERSION_NAME}"
 const val githubLatestReleaseUrl = "$githubRepositoryUrl/releases/latest"
 const val githubIssuesUrl = "$githubRepositoryUrl/issues"
 const val contributingUrl = "$githubRepositoryUrl#contributing"

 const val packageName = "com.android.rockages.kordx"
 const val izzyOnDroidUrl = "https://apt.izzysoft.de/fdroid/index/apk/$packageName"
 const val fdroidUrl = "https://f-droid.org/en/packages/$packageName"
 const val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName"

 fun isNightlyBuild() = version.contains("-nightly")
}
