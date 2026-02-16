# Key UX improvements design

Full flavor only. Feature branch: `feature/key-ux`.

## 1. Key press preview

A single reusable `TextView` overlay inside the keyboard's `FrameLayout`. On touch down for character keys (letters + number row), it repositions above the pressed key showing the character at 28sp with a bounce animation (scale 0.8x to 1.0x over ~100ms). Hidden on touch up with a brief 50ms fade-out. Skipped for special keys, symbol layer, and the extra row.

**New file:** `KeyPreview.kt`
- Constructor takes the `FrameLayout` wrapper and `ThemeManager`
- `show(anchor: View, label: String)` — measures anchor position, repositions overlay, animates in
- `hide()` — fade out and set GONE
- Bubble: themed rounded rect using `ThemeManager.keyBg()` (slightly adjusted shade) + `keyText()` for the character

**Integration:** `QwertyKeyboard.createKeyView()` adds a touch listener on character/number keys. The touch listener calls `keyPreview.show()` on `ACTION_DOWN` and `keyPreview.hide()` on `ACTION_UP`/`ACTION_CANCEL`. The existing click listener still fires normally — the preview is purely visual.

## 2. Remove period key, widen spacebar

In `KeyboardLayout.kt`, change the `lowercase` and `uppercase` bottom rows from:

```
?123 (1.5) | , (1.0) | SPACE (3.5) | . (1.0) | QuickKey (1.0) | Enter (1.5)
```

to:

```
?123 (1.5) | , (1.0) | SPACE (4.5) | QuickKey (1.0) | Enter (1.5)
```

Total weight stays 9.5. Period remains available on the symbols layer and as a quick key option (it's already in the options list).

## 3. Extra row key customization

### Customizable slots

3 slots: the buttons currently showing ESC, Tab, Ctrl. The remaining 7 buttons (Clipboard, 4 arrows, Menu/gear, Mic) stay fixed.

### Available mappings

Predefined key actions:
- ESC, Tab, Ctrl (default assignments)
- Home, End, PgUp, PgDn, Insert, Delete

Predefined text characters:
- `|` `~` `` ` `` `\`

Custom text entry:
- User types any string up to 8 characters
- Button label shows the text (truncated visually if needed)
- Tap sends the text via `keySender.sendText()`

### Storage

`AppPrefs` with keys `extra_slot_0`, `extra_slot_1`, `extra_slot_2`.

Format: `"keycode:KEYCODE_ESCAPE"` for system keys, `"text:|"` for single-char text, `"text:custom string"` for custom text, `"ctrl"` for the Ctrl modifier (special case preserving 3-state machine).

Defaults: `extra_slot_0 = "keycode:KEYCODE_ESCAPE"`, `extra_slot_1 = "keycode:KEYCODE_TAB"`, `extra_slot_2 = "ctrl"`.

### ExtraRowManager changes

On init, read the 3 slot prefs. For each slot:
- If `"ctrl"`: wire the existing CtrlState behavior
- If `"keycode:*"`: wire a click listener that sends the key code
- If `"text:*"`: wire a click listener that sends the text string

Update button labels to match the configured action. Re-wire when prefs change (MenuPanel callback).

### Input validation

- Custom text: max 8 chars, strip control characters (< 0x20 except 0x09 tab), reject null bytes
- Text is sent through `InputConnection.commitText()` — standard Android text input API, no injection vector
- Button labels set via `TextView.setText()` — no HTML rendering
- SharedPreferences escapes XML automatically

## 4. Expanded quick key options

### New options list (24 + custom)

```
/ . , ? ! — ' " : ;              (existing 10)
| ~ ` \ @ # $ _ & - + = ^ %      (new 14 terminal chars)
[Custom...]                        (free text entry)
```

### UI changes

The current long-press AltKeyPopup can't fit 25+ options horizontally. Replace with a scrollable grid popup or add a "Quick key" row in MenuPanel's new key mapping section.

**MenuPanel approach:** Add a row under the key mapping section. Tapping shows a scrollable list of all options. The last item is "Custom..." which shows a simple text input dialog (an `EditText` in an `AlertDialog`). Long-press on QuickKey button still opens the same picker as a shortcut.

### Custom text validation

Same rules as extra row custom text: max 8 chars, strip control characters, reject null bytes.

## 5. MenuPanel additions

New section in MenuPanel: **Key mapping** (full flavor only).

```
--- Key mapping ---
Slot 1 (left):   ESC        [tap to change]
Slot 2 (center): Tab        [tap to change]
Slot 3 (right):  Ctrl       [tap to change]
Quick key:       /           [tap to change]
```

Each row shows current assignment. Tapping opens a picker dialog with the available options. The picker for extra row slots and quick key share the same dialog pattern but different option lists.

## Files to create

| File | Purpose |
|------|---------|
| `KeyPreview.kt` | Overlay preview bubble for key presses |

## Files to modify

| File | Change |
|------|--------|
| `KeyboardLayout.kt` | Remove period from bottom row, widen spacebar |
| `QwertyKeyboard.kt` | Wire KeyPreview touch listeners on character/number keys |
| `AppPrefs.kt` | Add extra row slot prefs, expand quick key options, add custom text validation |
| `ExtraRowManager.kt` | Read slot prefs, dynamically wire buttons based on config |
| `MenuPanel.kt` | Add key mapping section with slot pickers and quick key picker |
| `KeyJawnService.kt` | Create KeyPreview instance, pass to QwertyKeyboard |
| `keyboard_view.xml` | Add preview TextView to FrameLayout (or create programmatically) |

## Testing

- `KeyPreviewTest.kt` — show/hide behavior, skips special keys
- `KeyboardLayoutTest.kt` — verify bottom row no longer has period, spacebar weight is 4.5
- `AppPrefsTest.kt` — extra row slot storage, custom text validation, quick key expanded options
- `ExtraRowManagerTest.kt` — dynamic wiring based on slot prefs
- `MenuPanelTest.kt` — key mapping section renders, picker interaction
