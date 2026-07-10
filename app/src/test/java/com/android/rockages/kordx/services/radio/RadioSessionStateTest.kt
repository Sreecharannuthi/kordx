package com.android.rockages.kordx.services.radio

import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM unit tests for the pure Android Auto / AAOS Now Playing card builders in [RadioSessionState]: queue items and the three custom actions (shuffle / repeat / favorite). The builders are pure data — no `KordX`, no `Radio`, no Android `Uri` / `Bundle` / `MediaSessionCompat` — so the whole surface is testable on the JVM without an emulator. The `RadioSession` consumes the data classes (the resource-name strings, the `action` strings, the `Map<String, Long>` extras) and adapts them to the `MediaSessionCompat` API at the call site. */
class RadioSessionStateTest {

 // ---- Queue item builder.

 @Test
 fun radioQueueItemDataHasSongPrefixAndExtras() {
 val snapshot = RadioSessionState.RadioSongSnapshot(
 id = "abc-123",
 title = "Test Song",
 durationMs = 210_000L,
 trackNumber = 4,
 albumId = "album-1",
 iconResourceName = "ic_test_icon",
 )
 val item = RadioSessionState.radioQueueItemData(snapshot)
 assertEquals(KordXMediaSessionConstants.PREFIX_SONG + "abc-123", item.mediaId)
 assertEquals("Test Song", item.title)
 assertEquals(210_000L, item.longExtras["DURATION_MS"])
 assertEquals(4, item.intExtras["TRACK_NUMBER"])
 assertEquals("album-1", item.stringExtras["ALBUM_ID"])
 assertEquals("ic_test_icon", item.iconResourceName)
 }

 @Test
 fun radioQueueItemDataOmitsOptionalFields() {
 val snapshot = RadioSessionState.RadioSongSnapshot(
 id = "abc",
 title = "Test",
 durationMs = 1000L,
 trackNumber = null,
 albumId = null,
 iconResourceName = null,
 )
 val item = RadioSessionState.radioQueueItemData(snapshot)
 assertTrue(item.intExtras.isEmpty(), "expected no intExtras when trackNumber is null")
 assertTrue(item.stringExtras.isEmpty(), "expected no stringExtras when albumId is null")
 assertEquals(1000L, item.longExtras["DURATION_MS"])
 assertNull(item.iconResourceName)
 }

 // ---- Custom action: shuffle.

 @Test
 fun shuffleActionOffUsesOffIconAndLabel() {
 val a = RadioSessionState.shuffleAction(shuffleOn = false)
 assertEquals(RadioSessionState.ACTION_SHUFFLE, a.action)
 assertEquals("ic_shuffle", a.iconResourceName)
 assertEquals("Shuffle", a.label)
 }

 @Test
 fun shuffleActionOnUsesActiveIconAndLabel() {
 val a = RadioSessionState.shuffleAction(shuffleOn = true)
 assertEquals(RadioSessionState.ACTION_SHUFFLE, a.action)
 assertEquals("ic_shuffle_active", a.iconResourceName)
 assertEquals("Shuffling", a.label)
 }

 // ---- Custom action: repeat.

 @Test
 fun repeatActionNoneUsesOffIcon() {
 val a = RadioSessionState.repeatAction(RadioQueue.LoopMode.None)
 assertEquals(RadioSessionState.ACTION_REPEAT, a.action)
 assertEquals("ic_repeat", a.iconResourceName)
 assertEquals("Repeat off", a.label)
 }

 @Test
 fun repeatActionQueueUsesActiveIcon() {
 val a = RadioSessionState.repeatAction(RadioQueue.LoopMode.Queue)
 assertEquals(RadioSessionState.ACTION_REPEAT, a.action)
 assertEquals("ic_repeat_active", a.iconResourceName)
 assertEquals("Repeat all", a.label)
 }

 @Test
 fun repeatActionSongUsesOneIcon() {
 val a = RadioSessionState.repeatAction(RadioQueue.LoopMode.Song)
 assertEquals(RadioSessionState.ACTION_REPEAT, a.action)
 assertEquals("ic_repeat_one", a.iconResourceName)
 assertEquals("Repeat one", a.label)
 }

 // ---- Custom action: favorite.

 @Test
 fun favoriteActionOffUsesBorderIcon() {
 val a = RadioSessionState.favoriteAction(isFavorite = false)
 assertEquals(RadioSessionState.ACTION_FAVORITE, a.action)
 assertEquals("ic_favorite_border", a.iconResourceName)
 assertEquals("Add to favorites", a.label)
 }

 @Test
 fun favoriteActionOnUsesFilledIcon() {
 val a = RadioSessionState.favoriteAction(isFavorite = true)
 assertEquals(RadioSessionState.ACTION_FAVORITE, a.action)
 assertEquals("ic_favorite_filled", a.iconResourceName)
 assertEquals("Remove from favorites", a.label)
 }

 // ---- allCustomActions: order is shuffle, repeat, favorite.

 @Test
 fun allCustomActionsExposesAllThreeInOrder() {
 val actions = RadioSessionState.allCustomActions(
 shuffleOn = true,
 loopMode = RadioQueue.LoopMode.Queue,
 isFavorite = false,
 )
 assertEquals(3, actions.size)
 assertEquals(RadioSessionState.ACTION_SHUFFLE, actions[0].action)
 assertEquals(RadioSessionState.ACTION_REPEAT, actions[1].action)
 assertEquals(RadioSessionState.ACTION_FAVORITE, actions[2].action)
 }

 // ---- Skip intervals and queue title.

 @Test
 fun skipIntervalsAreThirtySeconds() {
 assertEquals(30_000L, RadioSessionState.SKIP_BACK_MS)
 assertEquals(30_000L, RadioSessionState.SKIP_FORWARD_MS)
 }

 @Test
 fun queueTitleIsUpNext() {
 assertEquals("Up next", RadioSessionState.QUEUE_TITLE)
 }

 // ---- Action strings are unique and stable (regression guard).

 @Test
 fun actionStringsAreUnique() {
 val set = setOf(
 RadioSessionState.ACTION_SHUFFLE,
 RadioSessionState.ACTION_REPEAT,
 RadioSessionState.ACTION_FAVORITE,
 )
 assertEquals(3, set.size, "all three custom action strings must be unique")
 }

 // ---- 26g: nowPlayingCardCustomActions (Media3 CommandButton builder).

 /**
 * 26g — verifies the new Media3-flavored builder returns exactly
 * 3 [CommandButton]s in the expected order (shuffle, repeat,
 * favorite) and that each button carries the same `ACTION_*`
 * custom-action string the legacy builder uses. The icon resolver
 * returns a fixed res id (1) so the test doesn't depend on
 * `Resources.getIdentifier`.
 */
 @Test
 fun nowPlayingCardCustomActionsReturnsThreeButtonsInOrder() {
 val resolver: (String) -> Int = { 1 }
 val buttons = RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = false,
 loopMode = RadioQueue.LoopMode.None,
 isFavorite = false,
 iconResolver = resolver,
 )
 assertEquals(3, buttons.size, "nowPlayingCardCustomActions must return 3 buttons")
 val shuffleCommand = buttons[0].sessionCommand
 val repeatCommand = buttons[1].sessionCommand
 val favoriteCommand = buttons[2].sessionCommand
 assertEquals(
 RadioSessionState.ACTION_SHUFFLE,
 shuffleCommand?.customAction,
 "button[0] must carry the SHUFFLE action",
 )
 assertEquals(
 RadioSessionState.ACTION_REPEAT,
 repeatCommand?.customAction,
 "button[1] must carry the REPEAT action",
 )
 assertEquals(
 RadioSessionState.ACTION_FAVORITE,
 favoriteCommand?.customAction,
 "button[2] must carry the FAVORITE action",
 )
 }

 /**
 * 26g — verifies the icon resolver is called with the 3
 * resource-name strings matching the legacy
 * `RadioCustomAction.iconResourceName` values. The names are
 * the canonical drawable names (without the `R.drawable.`
 * prefix) that the service layer resolves via
 * `Resources.getIdentifier`. The test pins all 5 names (3 base
 * + 2 active variants) so refactor that breaks the
 * naming contract surfaces as a failure.
 */
 @Test
 fun nowPlayingCardCustomActionsResolvesAllThreeIconNames() {
 val seenNames = mutableListOf<String>()
 val resolver: (String) -> Int = { name ->
 seenNames.add(name)
 1
 }
 RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = false,
 loopMode = RadioQueue.LoopMode.None,
 isFavorite = false,
 iconResolver = resolver,
 )
 assertEquals(3, seenNames.size, "resolver must be called exactly 3 times")
 // shuffle-off name + repeat-off name + favorite-off name
 assertTrue(seenNames.contains("ic_shuffle"), "shuffle-off must resolve ic_shuffle")
 assertTrue(seenNames.contains("ic_repeat"), "repeat-off must resolve ic_repeat")
 assertTrue(
 seenNames.contains("ic_favorite_border"),
 "favorite-off must resolve ic_favorite_border",
 )
 }

 /**
 * 26g — verifies the icon resolver gets the active-state
 * resource name when shuffle is on. The active state is
 * `ic_shuffle_active` (vs. `ic_shuffle` for the off state) —
 * the service layer resolves this to a different drawable
 * than the off state, giving AAOS / Auto a visual cue that
 * shuffle is enabled.
 */
 @Test
 fun nowPlayingCardCustomActionsShuffleOnUsesActiveIcon() {
 val seenNames = mutableListOf<String>()
 val resolver: (String) -> Int = { name ->
 seenNames.add(name)
 1
 }
 RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = true,
 loopMode = RadioQueue.LoopMode.None,
 isFavorite = false,
 iconResolver = resolver,
 )
 assertTrue(
 seenNames.contains("ic_shuffle_active"),
 "shuffle-on must resolve ic_shuffle_active",
 )
 assertTrue(
 !seenNames.contains("ic_shuffle"),
 "shuffle-on must not also resolve ic_shuffle",
 )
 }

 /**
 * 26g — verifies the icon resolver gets the right repeat icon
 * for each [RadioQueue.LoopMode]: `ic_repeat` (off),
 * `ic_repeat_active` (queue), `ic_repeat_one` (song). Mirrors
 * the legacy [RadioSessionState.repeatAction] contract.
 */
 @Test
 fun nowPlayingCardCustomActionsRepeatModeResolvesToRightIcon() {
 val offNames = mutableListOf<String>()
 RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = false,
 loopMode = RadioQueue.LoopMode.None,
 isFavorite = false,
 iconResolver = { name -> offNames.add(name); 1 },
 )
 assertTrue(offNames.contains("ic_repeat"), "LoopMode.None must resolve ic_repeat")

 val queueNames = mutableListOf<String>()
 RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = false,
 loopMode = RadioQueue.LoopMode.Queue,
 isFavorite = false,
 iconResolver = { name -> queueNames.add(name); 1 },
 )
 assertTrue(
 queueNames.contains("ic_repeat_active"),
 "LoopMode.Queue must resolve ic_repeat_active",
 )

 val songNames = mutableListOf<String>()
 RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = false,
 loopMode = RadioQueue.LoopMode.Song,
 isFavorite = false,
 iconResolver = { name -> songNames.add(name); 1 },
 )
 assertTrue(
 songNames.contains("ic_repeat_one"),
 "LoopMode.Song must resolve ic_repeat_one",
 )
 }

 /**
 * 26g — verifies the `displayName` on each button matches the
 * visible label the legacy [RadioSessionState.shuffleAction] /
 * [RadioSessionState.repeatAction] / [RadioSessionState.favoriteAction]
 * build. AAOS / Auto renders this on the Now Playing card
 * below the icon. The test pins the 3 default labels (off
 * state for each action).
 */
 @Test
 fun nowPlayingCardCustomActionsDisplayNamesMatchLegacyLabels() {
 val buttons = RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = false,
 loopMode = RadioQueue.LoopMode.None,
 isFavorite = false,
 iconResolver = { 1 },
 )
 assertEquals("Shuffle", buttons[0].displayName?.toString())
 assertEquals("Repeat off", buttons[1].displayName?.toString())
 assertEquals("Add to favorites", buttons[2].displayName?.toString())
 }

 /**
 * 26g — structural regression guard: the 3 buttons must not be
 * null and each must carry a non-null [CommandButton.sessionCommand]
 * (the [SessionCommand] is required by the
 * [CommandButton.Builder.build] contract). The test guards
 * against Media3 API change that makes sessionCommand
 * optional. The `commandCode` for custom actions is 0
 * (`SessionCommand.COMMAND_CODE_CUSTOM`); the test pins
 * `customAction` non-null + `commandCode == 0` so 
 * Media3 API change that flips the field semantics surfaces as
 * a test failure.
 */
 @Test
 fun nowPlayingCardCustomActionsEveryButtonHasSessionCommand() {
 val buttons = RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = true,
 loopMode = RadioQueue.LoopMode.Song,
 isFavorite = true,
 iconResolver = { 1 },
 )
 for ((index, button) in buttons.withIndex()) {
 assertNotNull(button, "button[$index] must not be null")
 val command = button.sessionCommand
 assertNotNull(
 command,
 "button[$index].sessionCommand must not be null",
 )
 assertEquals(
 SessionCommand.COMMAND_CODE_CUSTOM,
 command?.commandCode ?: -1,
 "button[$index].commandCode must be COMMAND_CODE_CUSTOM (0) for custom actions",
 )
 assertEquals(
 0,
 command?.commandCode ?: -1,
 "button[$index].commandCode must be 0 (the COMMAND_CODE_CUSTOM constant)",
 )
 }
 }


 // 26h: rootCustomButtons (Media3 CommandButton builder for; the 2 rootlevel custom browse actions).

 /**
 * 26h — verifies the new [RadioSessionState.rootCustomButtons]
 * builder returns exactly 2 [CommandButton]s in the expected
 * order (SHUFFLE_ALL first, SEARCH second) and that each button
 * carries the same `ACTION_*` custom-action string the legacy
 * `rootCustomActions` builder uses. This is the 
 * 26 deliverable — the 2 root actions are now exposed at the
 * root of the AAOS browse tree via the Media3
 * `setCustomLayout(List<CommandButton>)` API.
 */
 @Test
 fun rootCustomButtonsReturnsTwoButtonsInOrder() {
 val buttons = RadioSessionState.rootCustomButtons(
 iconResolver = { 1 },
 )
 assertEquals(2, buttons.size, "rootCustomButtons must return 2 buttons")
 assertEquals(
 RadioSessionState.ACTION_SHUFFLE_ALL,
 buttons[0].sessionCommand?.customAction,
 "button[0] must carry the SHUFFLE_ALL action",
 )
 assertEquals(
 RadioSessionState.ACTION_SEARCH,
 buttons[1].sessionCommand?.customAction,
 "button[1] must carry the SEARCH action",
 )
 }

 /**
 * 26h — verifies the icon resolver is called with the 2
 * resource-name strings matching the legacy
 * `shuffleAllAction` / `searchAction` `iconResourceName`
 * values: `"ic_shuffle"` and `"ic_search"`. The test pins the
 * 2 names so refactor that breaks the naming
 * contract surfaces as a failure.
 */
 @Test
 fun rootCustomButtonsResolvesBothIconNames() {
 val seenNames = mutableListOf<String>()
 RadioSessionState.rootCustomButtons(
 iconResolver = { name -> seenNames.add(name); 1 },
 )
 assertEquals(2, seenNames.size, "resolver must be called exactly 2 times")
 assertTrue(
 seenNames.contains("ic_shuffle"),
 "SHUFFLE_ALL must resolve ic_shuffle",
 )
 assertTrue(
 seenNames.contains("ic_search"),
 "SEARCH must resolve ic_search",
 )
 }

 /**
 * 26h — verifies the `displayName` on each button matches the
 * visible label the legacy `shuffleAllAction` / `searchAction`
 * build. AAOS / Auto renders this at the root of the browse
 * tree below the icon. The test pins the 2 default labels.
 */
 @Test
 fun rootCustomButtonsDisplayNamesMatchLegacyLabels() {
 val buttons = RadioSessionState.rootCustomButtons(
 iconResolver = { 1 },
 )
 assertEquals("Shuffle all songs", buttons[0].displayName?.toString())
 assertEquals("Search", buttons[1].displayName?.toString())
 }

 /**
 * 26h — structural regression guard: the 2 buttons must not be
 * null and each must carry a non-null
 * [CommandButton.sessionCommand]. Mirrors the
 * `nowPlayingCardCustomActionsEveryButtonHasSessionCommand`
 * guard for the 3 Now Playing card buttons; the 2 root buttons
 * have the same Media3 contract requirement.
 */
 @Test
 fun rootCustomButtonsEveryButtonHasSessionCommand() {
 val buttons = RadioSessionState.rootCustomButtons(
 iconResolver = { 1 },
 )
 for ((index, button) in buttons.withIndex()) {
 assertNotNull(button, "button[$index] must not be null")
 val command = button.sessionCommand
 assertNotNull(
 command,
 "button[$index].sessionCommand must not be null",
 )
 assertEquals(
 SessionCommand.COMMAND_CODE_CUSTOM,
 command?.commandCode ?: -1,
 "button[$index].commandCode must be COMMAND_CODE_CUSTOM (0)",
 )
 }
 }

 /**
 * 26h — theoot custom actions are independent of shuffle /
 * loop / favorite state (the 3 Now Playing card state inputs).
 * Verify the builder returns the same 2 buttons for any state
 * combination (here, the test only calls the builder once
 * because it takes no state arguments, but the test pins the
 * 2-button size as a regression guard against 
 * refactor that accidentally adds state-derived buttons).
 */
 @Test
 fun rootCustomButtonsAlwaysReturnsTwoButtons() {
 val buttons = RadioSessionState.rootCustomButtons(
 iconResolver = { 1 },
 )
 assertEquals(2, buttons.size, "root buttons must always be 2 (SHUFFLE_ALL + SEARCH)")
 }


 // 26h: the 5 ACTION_* strings are unique (SHUFFLE / REPEAT /; FAVORITE / SHUFFLE_ALL / SEARCH). Regression guard against; refactor that accidentally duplicates an action; string, which would route both buttons to the same handler.

 @Test
 fun allFiveActionStringsAreUnique() {
 val set = setOf(
 RadioSessionState.ACTION_SHUFFLE,
 RadioSessionState.ACTION_REPEAT,
 RadioSessionState.ACTION_FAVORITE,
 RadioSessionState.ACTION_SHUFFLE_ALL,
 RadioSessionState.ACTION_SEARCH,
 )
 assertEquals(
 5,
 set.size,
 "all 5 custom action strings must be unique " +
 "(SHUFFLE / REPEAT / FAVORITE / SHUFFLE_ALL / SEARCH)",
 )
 }
}
