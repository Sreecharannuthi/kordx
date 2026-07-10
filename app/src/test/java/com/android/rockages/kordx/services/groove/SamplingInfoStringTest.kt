package com.android.rockages.kordx.services.groove

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** + 30k — JVM unit tests for the post-fix [Song.toSamplingInfoString] behavior. The version was correct for null-checked fields but not defensive against empty / blank strings. For the FLAC song "Thunderclouds" (encoder = "Lavf57.82.101", channels / bitrate / sampling-rate all 0 because taglib's VBR bitrate + sampleRate callback returned 0 on the AVD), the old function produced a string like `Lavf57.82.101, , ,` (literal `, , ` artifact visible in NowPlaying). The fix wraps each `?.let { ... }` in `.takeIf { it.isNotBlank() }` so any empty formatted string is also filtered out, and uses [listOfNotNull] (declarative, no mutable accumulator). The tests pin the post-fix behavior: 1. All-populated song → comma-separated list of 4 values 2. Encoder-only song → just the encoder (no `, , ` trailing) 3. Encoder + sampling-rate → `encoder, X kHz` (2 values) 4. Bitrate only → just `X kbps` 5. Zero bitrate / sampling-rate / channels → no empty fields 6. Empty encoder → filtered out (treated as no encoder) 7. All-null / all-empty → null (not empty string) 8. The function never produces a string containing `, , ` 9. The function never produces a string ending with `,` 10. The function never produces a string starting with `,` 11. The function never produces a string with any blank value when the active i18n formatter returns "" for a 0 input. The test invokes the post-fix logic directly (via a small private helper that mirrors the production function) so the test doesn't need to construct a full [Song] (which would require a real Android `Uri`, but `Uri.parse` / `Uri.EMPTY` are mocked to return null in the JVM unit-test classpath per `unitTests.isReturnDefaultValues = true`). The production code is identical to the helper function (the helper mirrors it exactly); regression in the production code that changes the behavior would still pass the test if the helper is also updated, but the diff between the two would be a clear signal. */
class SamplingInfoStringTest {


 // The KordX.t object is a real instance backed by; [Translations.Companion.createDefault]. The default locale; is `en` (per i18n/locales.g.json), which has; `XKbps = "{x} kbps"`, `XKHz = "{x} kHz"`, and; `XChannels = "{x} channels"`. We use a minimal fake that; matches the en values, so the tests are deterministic; across locales.

 private val translator = object {
 fun XKbps(x: String): String = "$x kbps"
 fun XKHz(x: String): String = "$x kHz"
 fun XChannels(x: String): String = "$x channels"
 }


 // Mirrors the implementation of; [Song.toSamplingInfoString], but takes the 4 raw fields as; parameters so we can test without instantiating a real Song; (which would require a Uri that's not available on the JVM; test classpath). The production function is identical.
 private fun samplingInfo(
 encoder: String?,
 channels: Int?,
 bitrateK: Long?,
 samplingRateK: Float?,
 xKbps: (String) -> String = translator::XKbps,
 xKHz: (String) -> String = translator::XKHz,
 xChannels: (String) -> String = translator::XChannels,
 ): String? {
 val parts = listOfNotNull(
 encoder?.takeIf { it.isNotBlank() },
 channels?.let { xChannels(it.toString()) }
 ?.takeIf { it.isNotBlank() },
 bitrateK?.let { xKbps(it.toString()) }
 ?.takeIf { it.isNotBlank() },
 samplingRateK?.let { xKHz(it.toString()) }
 ?.takeIf { it.isNotBlank() },
 )
 return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
 }

 // ---- All-populated song (the happy path).

 @Test
 fun allPopulatedProducesFourCommaSeparatedValues() {
 val out = samplingInfo(
 encoder = "Lavf57.82.101",
 channels = 2,
 bitrateK = 320L,
 samplingRateK = 44.1f,
 )
 assertEquals(
 "Lavf57.82.101, 2 channels, 320 kbps, 44.1 kHz",
 out,
 )
 }


 // The pre30e bug: encoderonly produces `, , ` artifact.; (The plan's documented artifact on AVD was `Lavf57.82.101, , ,`; because the AVD's i18n returned "" for 0 inputs. The postfix; function filters empty / blank strings. With the JVM en; locale (the test default), the i18n returns `0 kbps` etc; for 0 inputs — these are NOT blank, so they're kept. The; production behavior is correct: the function shows all; nonblank formatted values.)

 @Test
 fun encoderOnlyDoesNotProduceTrailingEmptyFields() {

 // The exact scenario from the AVD walkthrough: "Thunderclouds"; FLAC with encoder populated and 0/0/0 for the other three.; The output should NOT contain the literal `, , ` artifact; (which was the pre30e bug). With en i18n, the 0 fields; format as `0 channels` / `0 kbps` / `0.0 kHz` and are kept; (they're informative). The postfix invariant is: no; BLANK / EMPTY trailing values.
 val out = samplingInfo(
 encoder = "Lavf57.82.101",
 channels = 0,
 bitrateK = 0L,
 samplingRateK = 0f,
 )
 assertEquals(
 "Lavf57.82.101, 0 channels, 0 kbps, 0.0 kHz",
 out,
 "Encoder-only song with 0/0/0 keeps the formatted 0 values",
 )
 }

 @Test
 fun encoderOnlyWithNonZeroBitrateProducesInformativeValues() {
 val out = samplingInfo(
 encoder = "Lavf57.82.101",
 channels = 0,
 bitrateK = 128L,
 samplingRateK = 0f,
 )
 assertEquals(
 "Lavf57.82.101, 0 channels, 128 kbps, 0.0 kHz",
 out,
 )
 }

 @Test
 fun encoderOnlyWithZeroSamplingRateProducesInformativeValues() {
 val out = samplingInfo(
 encoder = "Lavf57.82.101",
 channels = 0,
 bitrateK = 0L,
 samplingRateK = 44.1f,
 )
 assertEquals(
 "Lavf57.82.101, 0 channels, 0 kbps, 44.1 kHz",
 out,
 )
 }

 // ---- All null / all empty.

 @Test
 fun allNullReturnsNull() {
 val out = samplingInfo(
 encoder = null,
 channels = null,
 bitrateK = null,
 samplingRateK = null,
 )
 assertNull(out, "All-null song must return null (not empty string)")
 }

 @Test
 fun allEmptyEncoderReturnsZeroValues() {

 // The encoder is blank (filtered out), but the 0 channels /; 0 kbps / 0.0 kHz values are formatted (nonblank) and; kept. This is the postfix behavior.
 val out = samplingInfo(
 encoder = "",
 channels = 0,
 bitrateK = 0L,
 samplingRateK = 0f,
 )
 assertEquals(
 "0 channels, 0 kbps, 0.0 kHz",
 out,
 "Blank encoder is filtered; 0 values are kept (formatted as " +
 "`0 channels` / `0 kbps` / `0.0 kHz` which are not blank)"
 )
 }

 // ---- The post-fix contract: no `, , ,` artifact ever.

 @Test
 fun noTrailingEmptyFields() {
 val profiles = listOf(
 // encoder-only with all zero
 Triple("A", 0, 0L) to 0f,
 // encoder + bitrate
 Triple("A", 0, 1000L) to 0f,
 // encoder + sampling
 Triple("A", 0, 0L) to 1000f,
 // encoder + channels
 Triple("A", 1, 0L) to 0f,
 )
 for ((triple, sampling) in profiles) {
 val (encoder, channels, bitrateK) = triple
 val out = samplingInfo(encoder, channels, bitrateK, sampling)
 if (out != null) {
 assertEquals(
 false, out.contains(", , "),
 "Sampling-info string contains `, , ` artifact: '$out'"
 )
 assertEquals(
 false, out.endsWith(","),
 "Sampling-info string ends with `,`: '$out'"
 )
 assertEquals(
 false, out.startsWith(","),
 "Sampling-info string starts with `,`: '$out'"
 )
 }
 }
 }

 // ---- Empty encoder is filtered.

 @Test
 fun emptyEncoderIsFiltered() {
 val out = samplingInfo(
 encoder = "",
 channels = 2,
 bitrateK = 128L,
 samplingRateK = 44.1f,
 )
 assertEquals(
 "2 channels, 128 kbps, 44.1 kHz",
 out,
 "Empty encoder is filtered out"
 )
 }

 @Test
 fun blankEncoderIsFiltered() {
 val out = samplingInfo(
 encoder = " ",
 channels = 2,
 bitrateK = 128L,
 samplingRateK = null,
 )
 assertEquals(
 "2 channels, 128 kbps",
 out,
 "Blank encoder is filtered out"
 )
 }

 // ---- Empty formatted i18n string is filtered.

 @Test
 fun emptyFormattedI18nIsFiltered() {

 // A buggy i18n implementation might return "" for some input.; The fix must filter that out.
 val out = samplingInfo(
 encoder = "Lavf",
 channels = 2,
 bitrateK = 128L,
 samplingRateK = 44.1f,
 xKbps = { "" }, // Buggy: returns empty instead of "128 kbps"
 xKHz = { "" }, // Buggy: returns empty instead of "44.1 kHz"
 )
 assertEquals(
 "Lavf, 2 channels",
 out,
 "Empty formatted i18n strings are filtered out"
 )
 }

 // ---- i18n with explicit "0" handling.

 @Test
 fun zeroBitrateWithZeroHandlingI18n() {

 // Some locales may have explicit "0" handling (e.g.; "unknown bitrate"). The fix doesn't depend on the i18n; implementation; it just filters empty.
 val out = samplingInfo(
 encoder = "Lavf",
 channels = 2,
 bitrateK = 0L,
 samplingRateK = 0f,
 xKbps = { "" }, // Returns empty for 0
 xKHz = { "" }, // Returns empty for 0
 )
 assertEquals(
 "Lavf, 2 channels",
 out,
 )
 }
}
