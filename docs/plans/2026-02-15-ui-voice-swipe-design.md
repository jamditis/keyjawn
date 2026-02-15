# KeyJawn UI/UX redesign, swipe gestures, and voice input improvements

## Part 1: Visual style -- clean material dark

### Color palette

| Token | Hex | Usage |
|-------|-----|-------|
| `keyboard_bg` | `#1B1B1F` | Keyboard container background |
| `key_bg` | `#2B2B30` | Standard key surface |
| `key_bg_pressed` | `#3A3A40` | Key pressed/ripple state |
| `key_text` | `#E8E8EC` | Key label text |
| `extra_row_bg` | `#161619` | Extra row and number row background |
| `qwerty_bg` | `#222226` | QWERTY grid area |
| `key_special_bg` | `#333338` | Shift, Del, Enter, ?123, spacebar |
| `accent` | `#6C9BF2` | Active states (shift single, ctrl armed) |
| `accent_locked` | `#E86C5A` | Locked states (caps lock, ctrl locked) |
| `divider` | `#38383E` | Row separators, popup borders |

### Key styling

- Corner radius: 6dp (up from 4dp)
- New `key_bg_special.xml` drawable for modifier and special keys
- 1dp bottom inset shadow (`#00000040`) on all keys for depth
- Spacebar uses `key_special_bg` to visually anchor the bottom row

### Typography

- Letter keys: monospace 18sp (unchanged)
- Special keys (Shift, Del, Enter, ?123, abc): sans-serif medium 13sp
- Number row: 15sp
- Extra row labels: 11sp

### Popups (alt keys, clipboard, slash commands)

- 8dp rounded corners
- Background `#2B2B30`
- 1dp border `#38383E`
- Drop shadow for elevation

## Part 2: Swipe gestures

New class `SwipeGestureDetector` that wraps `OnTouchListener` on the QWERTY container.

### Gesture mapping

| Gesture | Action |
|---------|--------|
| Swipe left on key area | Delete word (Ctrl+Backspace equivalent) |
| Swipe right on key area | Space |
| Swipe up on spacebar | Switch to symbols layer |
| Swipe down on spacebar | Switch back to letters |

### Detection parameters

- Minimum swipe distance: 60dp
- Minimum velocity: 200dp/s
- Falls through to normal key handling if thresholds not met

### Integration

Wired in `QwertyKeyboard` alongside existing touch handlers. The detector sits on the container and intercepts qualifying swipes before individual key handlers fire.

## Part 3: Voice input -- inline bar with streaming text

### Behavior

When mic is tapped:

1. Extra row hides, replaced by a voice bar (same 42dp height)
2. Voice bar contains: waveform indicator | streaming text | stop button
3. `onPartialResults` streams partial recognition text to the bar in real time
4. `onRmsChanged` feeds amplitude values to the waveform animation
5. Tapping stop (or `onResults`/`onError`) commits final text, voice bar hides, extra row returns

### Waveform indicator

`VoiceWaveformView` -- custom View, ~40 lines:

- 5 thin vertical bars
- Bar height scales with current RMS dB value
- Animated via `ValueAnimator` on each `onRmsChanged` callback
- Bar color: accent blue `#6C9BF2`

### Layout: `voice_bar.xml`

Horizontal LinearLayout matching extra row dimensions:

- `VoiceWaveformView` (48dp width) on the left
- Horizontally scrolling `TextView` for partial results in the middle (weight 1)
- Stop `ImageButton` (36dp) on the right

### VoiceInputHandler changes

- Wire `onPartialResults` to stream text to a UI callback
- Wire `onRmsChanged` to feed amplitude to waveform view
- Add callback interface (`VoiceInputListener`) for UI updates
- Remove direct mic button manipulation; delegate to the listener

## File summary

| File | Action |
|------|--------|
| `colors.xml` | Update palette |
| `key_bg.xml` | Update radius to 6dp |
| `key_bg_active.xml` | Update to accent color |
| `key_bg_locked.xml` | Update to accent_locked color |
| `key_bg_special.xml` | New drawable for modifier keys |
| `key_bg_special_shadow.xml` | New drawable with bottom shadow |
| `themes.xml` | Update NumberKey style |
| `keyboard_view.xml` | Update background colors |
| `extra_row.xml` | Update background, text sizes |
| `number_row.xml` | Update background |
| `QwertyKeyboard.kt` | Apply special key styling, wire swipe detector |
| `SwipeGestureDetector.kt` | New -- directional swipe detection |
| `voice_bar.xml` | New layout for voice recording bar |
| `VoiceWaveformView.kt` | New custom View for amplitude bars |
| `VoiceInputHandler.kt` | Add streaming callbacks, listener interface |
| `ExtraRowManager.kt` | Toggle between extra row and voice bar |
| `KeyJawnService.kt` | Wire voice bar into view hierarchy |
| `clipboard_popup.xml` | Update border/shadow styling |
| `AltKeyPopup.kt` | Update popup background styling |
