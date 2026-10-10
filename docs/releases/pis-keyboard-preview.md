# Keyboard preview

2026-10-09. Emulator preview.

Final preview version pis-v2-keyboard-preview includes all changes from pis-input-update.md. The entire 68dp feedback module shifts left by 4dp via end margin; text/translation/voice reserve a matching 72dp to avoid overlap. Three colored lamps and 24-dot grid keep their internal spacing and activity-only brightness behavior.

Glass edge gets a mild increase only: 0.8dp outline, low-alpha diagonal reflection and faint glint/lower edge. No thick refraction band, double rim, large white lamp core or colored outer glow is restored. The bar remains 64dp with larger output fonts; key letters stay at the existing height.

The old ComposingPinyin enum identifier was ineffective. Correct configured value is Composing pinyin, with signature embedded-pinyin-v2. Existing setComposingText handles chat editor composition, and candidate selection replaces it.

This position/glass adjustment was delivered as an emulator preview; the pis-v2-input-update ARM64 file belongs to the preceding stage. assembleDebug passed in 36s; installed on emulator-5556, version confirmed and input demo activity opened. Follow-up verification items: functional and UI interactions.

The emulator's persisted pinyin.conf contains PreeditMode="Composing pinyin", confirming the configured enum value was accepted. Typing and UI regression are follow-up verification items.
