package com.android.rockages.kordx.core.groove

import kotlin.time.Duration

data class Album(
 val id: String,
 val name: String,
 val artists: MutableSet<String>,
 var startYear: Int?,
 var endYear: Int?,
 var numberOfTracks: Int,
 var duration: Duration,
)
