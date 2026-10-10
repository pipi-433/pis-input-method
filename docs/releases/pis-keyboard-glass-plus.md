# Glass preview refinement

2026-10-09. Emulator-only version pis-v2-keyboard-glass-plus.

This version slightly increases the glass texture strength. The thin edge stays single-layer: stroke 0.8dp -> 0.9dp; rim/glint/lower-edge alpha increase approximately 25%. No thick refraction band, inner double rim or outside lamp glow is added. Bar center color, text positions and 4dp feedback inset remain unchanged.

Includes all keyboard-preview changes: correct embedded pinyin enum, 64dp bar and larger output fonts, input hot-path reductions, gloss.db hints, repeated-delete wrap and activity-only light boosts. This refinement was delivered as an emulator preview. assembleDebug passed in 38s; installed on emulator-5556, version confirmed and input activity opened. Follow-up verification item: UI interactions.
