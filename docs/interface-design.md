# Interface design

Goal: a custom interface should be indistinguishable in style from a Jagex one.
[osrs.design](https://osrs.design) is the visual reference gallery; the working rules
are below so you rarely need to look things up.

## Borders and frames

- **Steel border** — the modern standard window frame: grey riveted metal edging,
  9-sliced (corner sprites + tiled edge sprites, never stretched). Use it for every
  new or reworked interface. Newer OSRS interfaces (collection log, settings,
  achievement diaries) all use it.
- **Iron border** — the darker legacy frame on old interfaces. Do not use it for new
  work; only keep it when editing an existing iron-framed interface where restyling
  is out of scope.
- **Thin border** — the 1px bevel box (light top/left, dark bottom/right inset look)
  for structure inside a window: list boxes, section panels, item wells, input
  areas. Reuse the shared `thin_border` components rather than drawing new rects.
- Border sprites are 9-slice sets: corners fixed, edges tiled. Layer order: frame on
  top, content clipped inside, background behind.

## Colors

- Orange `#FF981F` — static labels and titles (the classic interface orange).
- White `#FFFFFF` — dynamic values and body text.
- Yellow `#FFFF00` — hover state, selected options, clickable option text.
- Red `#FF0000` — warnings/errors/disabled destructive; green `#00FF00` — success or
  positive confirmation.
- Grey `#9F9F9F` — disabled/inactive text.
- All text over textured backgrounds gets the 1px black drop shadow (down-right);
  never plain unshadowed text on parchment/stone.
- Backgrounds come from the standard sprite fills (dark brown panel, parchment
  scroll) — reuse existing background sprites, never flat-color rectangles.

## Typography

- Cache fonts only: `p11` (small plain), `p12` (regular plain), `b12` (bold) and the
  quill fonts for quest/scroll styling. No custom fonts.
- Titles: centered at the top of the frame, orange, usually `b12`. Labels orange,
  values white, one size step apart at most. Don't mix more than two fonts per
  interface.

## Components — reuse, never rebuild

- **Interface frame**: reuse the standard frame component — its CS2 automatically
  builds the steel border, the centered title text AND the close button. Never add
  your own title text or close X on top of it; just supply the title string.
- **Buttons**: standard stone buttons (gold-to-yellow gradient text on stone) with
  hover state; primary/secondary/danger variants exist — copy from a shipped
  interface.
- **Scrollbar**: the standard 16px scrollbar (up/down arrows + draggable thumb) —
  wire the existing scrollbar component/CS2, don't build one.
- **Dropdowns**: reuse the existing dropdown layers (content layer + popup layer that
  overlays the list, as in the production interface's `category_dropdown_popup`).
- **Checkboxes/toggles, tabs, progress bars, tooltips**: all have standard sprites
  and scripts — lift them from bank/settings/collection log instead of inventing.
- **Sprites over rectangles**: build visuals from existing cache sprites that match
  the interface style (frames, fills, dividers, slot backgrounds, icons) instead of
  plain rectangle/filled-rect components. Rectangles are a last resort for things no
  sprite covers — and even then match the palette; a flat rect next to textured
  sprites reads instantly as custom.
- General rule: find the shipped interface closest to what you're building and copy
  its component structure, sprite ids and spacing, then change only the content.

## Standard sizes and metrics

Baseline numbers, taken from this repo's `SpawnInterface.kt` and the OSRS client:

- Full-screen modal window: **512x334** (the fixed-mode viewport; never exceed it —
  it must also be checked in resizable mode).
- Title bar strip: **36px** tall, title text centered in it.
- Content inset from the frame: **10px** on each side.
- Scrollbar: **16px** wide with a **2px** gap to the content it scrolls; flush right.
- Button rows: buttons ~**48px** wide on a **52px** pitch (4px gap), row height
  **20px** with 4px vertical padding.
- Interface gold/orange: **`0xff981f`** — same value everywhere (titles, labels,
  button text).

## Layout rules

- Title bar top-center and close button top-right come free from the frame
  component's CS2 — never place them manually. Keep consistent inner padding from
  the frame; scrollable lists sit flush against their scrollbar on the right.
- Express layout as named constants derived from each other (see
  `SpawnInterface.kt`: `CONTENT_W = WIDTH - INSET * 2`,
  `GRID_W = CONTENT_W - SCROLLBAR_W - SCROLLBAR_GAP`) so spacing stays consistent
  when sizes change.
- Verify with `screenshot {interfaceId}` + `dump_interface` against an existing
  Jagex interface of the same shape — bounds, spacing and colors should match.

## Reference dumps — how Jagex actually built things

- [Joshua-F/osrs-dumps](https://github.com/Joshua-F/osrs-dumps) holds raw dumps of
  the packed cache: decompiled CS2 under
  [`script/`](https://github.com/Joshua-F/osrs-dumps/tree/master/script) (one `.cs2`
  per clientscript, e.g.
  [`[clientscript,1v1arena_clear_opbutton].cs2`](https://github.com/Joshua-F/osrs-dumps/blob/master/script/%5Bclientscript%2C1v1arena_clear_opbutton%5D.cs2))
  and interface definitions under
  [`interface/`](https://github.com/Joshua-F/osrs-dumps/tree/master/interface)
  (one `.if3` per interface, e.g.
  [`1v1arena_results.if3`](https://github.com/Joshua-F/osrs-dumps/blob/master/interface/1v1arena_results.if3)).
- When building or debugging an interface or clientscript, look up the closest
  official equivalent there to see the real component tree, ops and script logic
  before writing your own.
