# SHAREit — Source-Grounded Design System and UI Specification

> **Source of truth:** This document is a faithful specification of the five supplied standalone HTML files: `index.html`, `send.html`, `receive.html`, `transfer.html`, and `invite.html`. It records what is explicitly present in those files. It does not introduce inferred brand rules, undocumented functionality, or additional design requirements. The exact Arabic UI strings are retained alongside English explanations so the implementation can reproduce the source accurately.

## What “system” means here

Merriam-Webster defines a system as “a regularly interacting or interdependent group of items forming a unified whole.” In the design-system context, the Nielsen Norman Group describes a complete set of standards using reusable components and patterns; Figma describes reusable building blocks and standards for consistency. [1] [2] [3] These sources clarify only the general term. **Every SHAREit-specific statement below—including tokens, components, page patterns, copy, states, and behavior—is derived from the five supplied HTML files alone.** Here, “system” means the evidenced set of shared visual rules, reusable UI elements, page compositions, and implemented interaction states found in those files. It does not imply that the archive contains a separately governed design-system product, component package, backend, or undocumented standards.

## 1. Product and experience at a glance

The supplied prototype presents a mobile-first, dark-mode, Arabic-language file-sharing experience branded **SHAREit** on the home screen. All five documents set the document language to Arabic (`lang="ar"`) and the text direction to right-to-left (`dir="rtl"`). The home screen describes sharing with nearby people without internet and at high speed. The user-facing flows represented are: home, selecting items to send, waiting to receive, viewing transfer progress, and inviting nearby friends.

The visual language is characterized by a nearly black canvas, bright turquoise/teal emphasis, dark translucent glass controls with fine luminous rims, large rounded pills and cards, system-font typography, thin outlined icons, compact secondary text, and a circular radar-like connection illustration. This description summarizes the actual rules in the HTML; it is not an instruction to add effects or functionality beyond them.

## 2. Source files and page map

| File | HTML title | Page role evidenced by the source | Main route relationships |
|---|---|---|---|
| `index.html` | `الرئيسية` (Home) | Home, nearby-sharing hero, send/receive actions, files entry, recent activity, bottom navigation | Send action → `send.html`; receive action → `receive.html`; home navigation item → `index.html` |
| `send.html` | `إرسال` (Send) | Three category tabs and selectable app/file-like entries, with a fixed send action | Header back → `index.html`; send action points to `transfer.html` and stores selected entries in `sessionStorage` |
| `receive.html` | `استلام` (Receive) | Waiting-for-sender state, then simulated SmartTV discovery and connection action | Header back → `index.html`; after the simulated discovery, action → `transfer.html` |
| `transfer.html` | `متصل` (Connected) | Transfer progress, per-item progress, pause/resume, completion | Close → `index.html`; finish/done actions → `index.html` |
| `invite.html` | `دعوة` (Invite) | Nearby-friends invite methods and sharing destinations | Header back → `index.html`; sharing destinations are anchors without destinations in the supplied source |

Each page is a complete HTML document with its own inline `<style>` block and repeated inline SVG symbol sprite. There are no additional project files, image assets, external stylesheets, external scripts, or linked fonts in the supplied archive. JavaScript behavior is embedded in each page.

## 3. Global document, viewport, and layout rules

- HTML doctype is `<!doctype html>`; character encoding is UTF-8.
- Viewport is `width=device-width,initial-scale=1,viewport-fit=cover`.
- Browser theme color is `#000000`.
- The root `html` background is `#000` and `color-scheme: dark`.
- The body has a maximum width of `480px`, is centered with `margin:auto`, and has `min-height:100dvh` and a `#000` background.
- Global box sizing is `border-box`; all margins and padding are reset to zero; tap highlight is disabled with `-webkit-tap-highlight-color:transparent`.
- Main content uses `padding: calc(env(safe-area-inset-top,0px) + 78px) 16px 132px`, leaving space for the fixed top region and bottom controls.
- Fixed top, bottom-bar, and backdrop elements are constrained to the same `480px` maximum width and centered. The top region is fixed at `top:0`, uses a space-between flex row with centered alignment, and has padding `calc(env(safe-area-inset-top,0px) + 10px) 16px 0`. The bottom bar is fixed at `calc(env(safe-area-inset-bottom,0px) + 14px)`, centers its child, and has horizontal padding `16px`.
- Top and bottom wrappers ignore pointer input (`pointer-events:none`), while their direct children re-enable pointer input (`pointer-events:auto`). The fixed backdrop layers themselves also do not intercept pointer input.
- The shared fixed-position rule initially declares `z-index:10` for `.top`, `.bar`, and `.pb`; `.pb` then overrides this to `z-index:5`, placing the header and bar above the backdrop strips.
- The `.row` utility is a flex row with an `8px` gap. The `a` reset sets inherited color and removes text decoration. The `button` reset sets inherited font and color, removes border and background, and uses a pointer cursor.
- There are no CSS media-query breakpoints for width. The maximum-width container and flexible layouts are the responsive behavior explicitly present.
- Logical properties such as `inset-inline-end` and `inset-inline` are used for RTL-aware positioning and separators.

## 4. Design tokens: exact values from `:root`

The following custom properties are declared identically in all five pages:

| Token | Exact value | Usage evidenced in the source |
|---|---|---|
| `--a` | `#4592FF` | Ocean blue accent, active navigation, radar accent dot, and small status dot |
| `--a-rgb` | `69 146 255` | RGB component for alpha-blended transparent layers: `rgb(var(--a-rgb) / .5)` |
| `--ag` | `linear-gradient(180deg,#3F8CFF,#1D63E6)` | Main Ocean gradient, primary actions, selected checkmarks, progress fills, and radar core |
| `--on` | `#FFFFFF` | Foreground/icon color on accent gradient surfaces |
| `--s` | `#0C0D0F` | Solid card background |
| `--t` | `#F5F5F7` | Main foreground text and selected-tab label color |
| `--t2` | `rgba(235,235,245,.6)` | Secondary text, inactive navigation, small labels, and inactive share labels |
| `--t3` | `rgba(235,235,245,.3)` | Low-emphasis dots and unselected check control border |
| `--h` | `rgba(255,255,255,.08)` | Hairline separators and radar-ring outlines |
| `--rim` | `1` | Opacity of the glass-surface rim pseudo-element |

Other exact color values used directly in CSS include: `#000`, `#1A1B1F`, `#1C1D21`, `#FF453A`, `#fff`, `rgba(60,62,68,.34)`, `rgba(255,255,255,.6)`, `rgba(255,255,255,.06)`, `rgba(255,255,255,.34)`, `rgba(255,255,255,.14)`, `rgba(255,255,255,.12)`, `rgba(255,255,255,.1)`, `rgba(0,0,0,.35)`, `rgba(0,0,0,.4)`, `rgba(0,0,0,.7)`, and `rgba(0,0,0,.85)`.

## 5. Typography

The body font declaration is exactly:

```css
font: 17px/1.35 -apple-system, "SF Arabic", "SF Pro Text", "Segoe UI", system-ui, Tahoma, sans-serif;
```

The body uses `-webkit-font-smoothing:antialiased`. No font file is packaged; rendering therefore relies on the platform/system font stack.

| Element / class | Exact styling |
|---|---|
| Body | `17px / 1.35`, system font stack above |
| `h1` | `30px`, `line-height:1.2`, weight `700`, letter spacing `-.01em`, centered |
| `.sub` | `15px`, centered, `var(--t2)`, top margin `6px` |
| `.h2` | `13px`, weight `600`, `var(--t2)`, margins `28px 8px 8px` |
| `.c` | `15px`, weight `600` |
| `.big` | `17px` |
| `.nav a` | `11px`, weight `600` |
| `.li b` | `16px`, weight `500`, block |
| `.li small` | `13px`, `var(--t2)` |
| `.bx small` | `12px`, regular weight, `var(--t2)` |
| `.seg button` | `14px`, weight `600` |
| `.sh a` | `12px`, `var(--t2)` |
| `.num` | `52px`, weight `700`, tabular numerals, line-height `1`, letter spacing `-.02em` |
| `.num2` | `28px`, weight `600`, tabular numerals, line-height `1`, `var(--t2)` |
| `.u` | `13px`, `var(--t2)`, top margin `8px` |
| `.meta` | `13px`, `var(--t2)`, tabular numerals |
| `.bd` | `11px/19px`, system UI, weight `700` |

## 6. Shared visual components and exact geometry

### 6.1 Icons

The common `.i` SVG class is `22px × 22px`, has no fill, uses `currentColor` for a `1.8px` stroke, rounded line caps and joins, and `flex:none`. Selected icons override this size: the radar core icon is `36px × 36px`; the checkmark inside a selection circle is `16px × 16px` with a `2.6px` stroke.

A hidden inline SVG sprite is embedded in each HTML file. Each symbol has `viewBox="0 0 24 24"`. The 16 declared symbols are:

| Symbol ID | Source path / meaning evident from shape and usage |
|---|---|
| `bk` | Chevron/back arrow; used as the back control on secondary pages |
| `bell` | Bell; home notification action |
| `plus` | Plus sign; home action |
| `send` | Paper-plane/send; home and invite radar cores |
| `dl` | Download/receive; home receive action and receive radar core |
| `fold` | Folder; home files entry and bottom navigation |
| `srch` | Magnifying glass/search; send-page header action |
| `ck` | Checkmark; selectable rows on send page |
| `pa` | Pause; transfer action while running |
| `pl` | Play; substituted for pause icon while paused |
| `x` | Close; transfer-page header action |
| `qr` | QR pattern; receive header and invite QR method |
| `bt` | Bluetooth; invite method |
| `hs` | Wireless/hotspot arcs; invite method |
| `hm` | Home; home item in bottom navigation |
| `cl` | Clock/history; history item in bottom navigation |

The inline sprite also appears in pages where some symbols are unused. Do not remove symbols solely because they are not used on that particular page if reproducing the full supplied sprite.

### 6.2 Glass surfaces (`.g`)

The shared glass surface is positioned relatively and uses:

- Background `rgba(60,62,68,.34)`.
- `backdrop-filter: blur(22px) saturate(1.8)` with the prefixed WebKit equivalent.
- Shadow `0 8px 20px rgba(0,0,0,.4)`.
- Transform transition `.35s cubic-bezier(.3,1.5,.5,1)`.
- An `::after` overlay filling the surface, inheriting its border radius, with `1px` padding, no pointer events, and opacity `var(--rim)`.
- The rim gradient runs at `135deg` from `rgba(255,255,255,.6)` through `rgba(255,255,255,.06)` at 30% and 70%, to `rgba(255,255,255,.34)`. The mask/composite construction creates a thin edge rather than a full overlay.
- Active press state scales the surface to `.96`.

The `.p` primary action uses the shared gradient background and `var(--on)` foreground; it disables backdrop blur and uses shadow `0 6px 16px rgba(0,0,0,.35)`.

When `prefers-reduced-transparency:reduce` is active, `.g` changes to solid `#1C1D21` and disables both standard and WebKit backdrop filters. When `prefers-reduced-motion:reduce` is active, all animations and transitions are disabled with `!important`.

### 6.3 Buttons and pill controls

- `.c`: inline-flex, centered on both axes, `gap:8px`, height `44px`, horizontal padding `18px`, radius `22px`, weight `600`, font size `15px`.
- `.ci`: square icon-control variant, width `44px`, zero horizontal padding; its base height remains `44px`.
- `.big`: height `56px`, radius `28px`, font size `17px`, flexes to fill available width.
- `.bd`: notification badge positioned `3px` above and at logical inline-end `-3px`; minimum width `19px`, height `19px`, radius `10px`, red `#FF453A`, white text, `11px/19px` bold system UI, centered, horizontal padding `5px`.
- `.dot`: `8px × 8px` circle in `var(--a)`, non-shrinking.

### 6.4 Cards, rows, and row icons

- `.card`: background `var(--s)`, radius `26px`, overflow hidden.
- `.li`: flex row, vertically centered, `gap:14px`, padding `12px 16px`, relatively positioned.
- Consecutive `.li` rows receive an upper separator positioned from logical inline offset `78px` to the other inline edge, height `.5px`, color `var(--h)`.
- `.ic`: `46px × 46px`, radius `12px`, background `#1A1B1F`, centered grid content, weight `700`, `14px`, color `var(--t2)`, does not shrink. In the data lists it contains the first two characters of the entry name, not a packaged image/logo.
- `.li b`: block, `16px`, weight `500`.
- `.li small`: `13px`, `var(--t2)`.
- `.gr`: flexible (`flex:1`) and `min-width:0`.

### 6.5 Navigation and segmented selection

- `.nav`: flex, `gap:2px`, padding `6px`, radius `32px`.
- `.nav a`: column layout, centered, `gap:3px`, width `86px`, height `52px`, radius `26px`, font size `11px`, weight `600`, inactive color `var(--t2)`.
- `.nav .on`: background `rgba(255,255,255,.12)` and color `var(--a)`.
- `.seg`: flex with `gap:2px`, padding `4px`, height `48px`, radius `24px`, bottom margin `14px`.
- `.seg button`: equal flexible width, radius `20px`, weight `600`, `14px`, inactive color `var(--t2)`.
- `.seg .on`: background `rgba(255,255,255,.14)`, text `var(--t)`.

### 6.6 Selection, progress, numeric, and sharing styles

- `.ck`: `26px × 26px` circular control, `1.5px` solid border `var(--t3)`, centered grid, initially transparent text/icon, non-shrinking, `.25s` transition. Its internal icon is `16px × 16px`, stroke width `2.6`.
- `.sel .ck`: gradient background `var(--ag)`, transparent border, foreground `var(--on)`. `.li[data-i]` has a pointer cursor.
- `.tr`: progress track height `10px`, radius `5px`, background `rgba(255,255,255,.1)`, overflow hidden. Its child `i` is block, full track height, starts with width `0`, radius `5px`, and gradient fill `var(--ag)`. `.tr.s` is the compact row version: height `4px`, top margin `8px`.
- `.num` and `.num2` are specified in the typography table above; both use tabular numerals.
- `.u`: block label beneath number; `.meta`: horizontally distributed progress metadata with `12px` top margin.
- `.st`: minimum width `44px`, end-aligned, tabular numerals. `.st.ok` uses `var(--a)`.
- `.sh`: flex, `space-around`, top margin `4px`. `.sh a`: grid, centered items, `gap:8px`, `12px`, `var(--t2)`. `.sh .ci`: `56px × 56px`, radius `28px`, font size `20px`, weight `700`, foreground `var(--t)`.
- `.row`: flex with `gap:8px`.
- `.col`: grid with `gap:12px`, top margin `28px`.

## 7. Shared background treatment

Every page includes two fixed backdrop strips, `.pb` at the top and `.pb.b` at the bottom. The top strip is `calc(env(safe-area-inset-top,0px) + 96px)` high. The bottom strip is `150px` high and fixed to the bottom. Both sit at z-index `5`, are non-interactive, and the shared fixed header/bar/backdrop positioning is declared at z-index `10` for `.top`, `.bar`, and `.pb` (the `.pb` rule then specifically sets z-index `5`).

The CSS adds a dark linear-gradient veil via `::after`: top overlay uses `linear-gradient(rgba(0,0,0,.7),transparent)`; bottom overlay uses `linear-gradient(to top,rgba(0,0,0,.85),transparent)`. Inline JavaScript creates five absolute `<i>` blur/mask layers inside each strip. For layer index `k=0..4`, blur radius is `.7 * 2^k` pixels (formatted to one decimal: `0.7px`, `1.4px`, `2.8px`, `5.6px`, `11.2px`); mask stop `n` is `100 - 17k` percent, with its opaque start at `n - 35` percent and transparent end at `n`. The gradient direction is selected according to whether the strip is the top or bottom strip.

## 8. Radar / nearby-device illustration

The `.rd` illustration appears on home, invite, and receive pages. It is a relative, centered grid of height `300px`, with margins `8px 0 12px`. It contains three ring elements and a rotating orbital layer around an `84px` teal-gradient circular core.

- Ring outlines use `1px solid var(--h)`, circular radius, and `pl` pulse animation lasting `5s`, ease-in-out, infinite.
- Ring sizes are `130px`, `210px`, and `290px`; the second and third rings have animation delays of `-1.6s` and `-3.2s`.
- The orbital layer `.o` is `290px × 290px`, absolutely positioned and rotates linearly once per `28s`, infinitely.
- Three orbital dots are `12px × 12px` circles. The first is at `top:-6px;left:50%` and uses accent `var(--a)`; the second is at `top:74%;left:92%`; the third at `top:70%;left:2%`; the last two use `var(--t3)`.
- Core is `84px × 84px`, circular, with `var(--ag)`, grid-centered icon and `var(--on)` icon color. Core icon is `36px × 36px`.
- `@keyframes pl`: at 50%, scale to `1.05` and opacity `.45`.
- `@keyframes sp`: rotate to `1turn`.

On home and invite, the core uses the send icon. On receive, it uses the download icon.

## 9. Page specifications

### 9.1 Home — `index.html`

**Header:** Fixed top header with a glass `SHAREit` pill at inline-start. It contains a teal dot before the brand. At the other side is a row of two glass circular controls: notification bell with red badge `4`, and a plus icon. The header sits over the top backdrop.

**Main content order:**

1. Radar illustration with send icon in its center.
2. Centered heading: `شارك مع من حولك` (“Share with people around you”).
3. Centered subtitle: `بدون إنترنت، وبسرعة عالية` (“Without internet, at high speed”).
4. Two equal, large pill actions in one row with `margin-top:28px` and `gap:8px`: gradient primary `إرسال` (“Send”) with send icon, linking to `send.html`; glass secondary `استلام` (“Receive”) with download icon, linking to `receive.html`.
5. A dark card row at `margin-top:12px`, with folder icon, title `الملفات` (“Files”), subtitle `تصفّح ملفات جهازك` (“Browse your device files”). No `href` is provided for this row.
6. Section heading `الأخيرة` (“Recent”) using `.h2`, followed by a dark card populated by JavaScript.

**Recent data, exact order:**

| Name | Displayed size | Status text |
|---|---:|---|
| DeskLink LAN | 46.9 MB | `أُرسل` (sent) |
| Velo | 10.4 MB | `أُرسل` (sent) |
| Files Pro | 22.1 MB | `استُلم` (received) |
| Notes | 8.2 MB | `استُلم` (received) |
| Clip | 3.4 MB | `أُرسل` (sent) |
| Terminal | 31.5 MB | `استُلم` (received) |

Each recent row shows the first two characters of the name in `.ic`, the name in a `16px`, weight-`500` row label, and a small line formatted as `<size> · <Arabic status>`.

**Bottom navigation:** Fixed glass pill with three items: active `الرئيسية` (home, `hm` icon), `الملفات` (folder icon), and `السجل` (history/clock icon). The home item links to `index.html` and is marked `.on`; the other anchors have no `href` in the supplied HTML.

### 9.2 Send — `send.html`

**Header:** Fixed glass back icon linking to `index.html`, centered glass title `إرسال`, and a glass circular search icon at the opposite side. The search anchor has no `href` and no search behavior is implemented.

**Category selector:** Glass segmented control with three buttons, in source order: `تطبيقات` (“Apps”), `صور` (“Photos”), and `ملفات` (“Files”). `تطبيقات` is initially active. Clicking a button toggles `.on` among the three. The selected tab changes visually only; the available data/list does not change by tab in this prototype.

**Selectable list data, exact order:**

| Index | Entry name | Size |
|---:|---|---:|
| 0 | DeskLink LAN | 46.9 MB |
| 1 | Velo | 10.4 MB |
| 2 | Files Pro | 22.1 MB |
| 3 | Notes | 8.2 MB |
| 4 | Clip | 3.4 MB |
| 5 | Terminal | 31.5 MB |
| 6 | Radio | 12.8 MB |
| 7 | Maps Lite | 54.0 MB |

Rows are created dynamically inside a `.card`. Each row has a two-character text tile, entry name, size in a `<small>`, and a circular selection control. Initial selected set is `{0,1}` (DeskLink LAN and Velo). Clicking a row toggles its membership in the selected set and redraws the list.

**Fixed bottom send bar:** A glass `.bx` contains a summary on one side and a send action on the other. Summary is `<selected count> عناصر` (items) and a second line with the sum of selected sizes to one decimal followed by `MB`. The primary send action is labeled `إرسال` and links to `transfer.html`. When the selection is empty, its opacity is set to `.4` and pointer events are disabled; when non-empty, opacity is `1` and pointer events are enabled.

When clicked, the send link attempts to save the selected objects to `sessionStorage.sel` as JSON. The stored array contains `[name, size]` tuples in selected-set iteration order. Storage exceptions are caught and ignored. The click handler does not cancel the anchor's navigation.

### 9.3 Receive — `receive.html`

**Header:** Fixed glass back icon linking to `index.html`, centered title `استلام`, and glass QR icon action on the other side. The QR anchor has no `href` or click behavior in the source.

**Initial state:** Radar illustration with download icon. Heading `في انتظار المرسل` (“Waiting for sender”), subtitle `افتح الإرسال على الجهاز الآخر` (“Open Send on the other device”). Beneath it, centered glass identity pill with a teal dot and exact identifier `user79109045681`.

**Fixed bottom connection bar:** Glass `.bx` initially displays `جارٍ البحث…` (“Searching…”) and a glass gradient button labeled `مسح QR` (“Scan QR”), whose initial `href` is `#`.

**Simulated discovery transition:** After exactly `4500` milliseconds, JavaScript changes the heading to `تم العثور على SmartTV` (“SmartTV found”), subtitle to `اضغط اتصال للبدء` (“Tap Connect to start”), status to `SmartTV`, action text to `اتصال` (“Connect”), and action link to `transfer.html`. This is a local timer-driven UI change; no discovery/network API is present in the supplied code.

### 9.4 Transfer — `transfer.html`

**Header:** Fixed glass close (`x`) icon linking to `index.html`; centered glass connection pill with teal dot and exact label `متصل · SmartTV` (“Connected · SmartTV”); glass circular count/control displaying `2`. The displayed `2` has no further behavior in the source.

**Summary card:** Dark `.card` with inline padding `22px`. Its upper content is a horizontal row aligned to the bottom. Because the document direction is RTL, the first child is on the right and the second child is on the left:

- Right side: large `0.0` transfer amount (`.num`) with label `ميغابايت مُرسَل` (“Megabytes sent”).
- Left side: large timer `0:00` (`.num2`) with label `الوقت` (“Time”).
- Below: 10px progress track, top margin `22px`, filled with the gradient as progress advances.
- Metadata line: percentage is the first flex child and therefore appears on the right in RTL; speed is the second child and appears on the left (`0%` and `0 MB/s` initially).

**Transfer file list:** Section label `الملفات` (“Files”) followed by a dark `.card`. Each row contains the two-character name tile, file/app name, thin per-row progress track, and a status label aligned to logical inline-end. Since the document is RTL, this is the physical left edge; the initial status is `0%`.

**Initial transfer dataset:** The default is the first three entries:

| Name | Size |
|---|---:|
| DeskLink LAN | 46.9 MB |
| Velo | 10.4 MB |
| Files Pro | 22.1 MB |

On page load, if `sessionStorage.sel` parses successfully and contains a non-empty array, that array replaces the default list. Empty, invalid, unavailable, or inaccessible storage leaves the default data in use. The code catches storage/parse errors.

**Fixed bottom transfer controls:** Glass `.bx` initially reads `جارٍ الإرسال` (“Sending”). It contains a glass icon button showing pause and a glass `إنهاء` (“Finish”) link to `index.html`. On pause, the button icon switches to play, status becomes `متوقف مؤقتًا` (“Paused”), and progress/time stop advancing. On resume, icon switches back to pause and status returns to `جارٍ الإرسال`.

**Progress simulation as implemented:**

- Total size is the sum of all current row sizes (`TT`). Initial sent amount `sent=0`; elapsed time `el=0`; `run=1`; `fin=0`; last frame time is `performance.now()`.
- An animation-frame loop advances only while `run` is true and `fin` is false.
- Delta time is `(t-last)/1000`, capped at `.1` seconds per frame.
- Simulated speed is `6 + 2.5*sin(el*1.7) + 1.2*sin(el*5.3)` MB/s.
- Sent size increases by `speed * delta`, capped at total size.
- Main progress width is `sent/TT * 100%`; amount is shown to one decimal; overall percentage is floored to integer; speed is shown to one decimal plus ` MB/s`; elapsed time uses whole minutes and whole seconds formatted `m:ss`.
- Each row's progress is computed as the portion of the cumulative sent amount within that file's size, clamped from 0 to 1. Per-file progress bar width is that portion times 100. Status is a checkmark once a file reaches 100%, otherwise the floored integer percentage. Completed rows receive `.ok` and the teal accent color.
- At total completion, the speed is replaced with an em dash `—`, status becomes `اكتمل الإرسال` (“Transfer complete”), and the pause/finish row is replaced with one primary `تم` (“Done”) link to `index.html`.
- This is a front-end simulation; the code contains no file transfer, networking, recipient protocol, or backend request.

### 9.5 Invite — `invite.html`

**Header:** Fixed glass back control linking to `index.html`, centered glass title `دعوة` (“Invite”), and an empty width spacer of `44px` on the opposite side to balance the header.

**Main content:** Radar with send icon, centered heading `شارك مع الأصدقاء القريبين` (“Share with nearby friends”), subtitle `بدون إنترنت` (“Without internet”). Two vertically stacked `.big` controls, with `.col` spacing, are:

1. Gradient primary Bluetooth control labeled `Bluetooth`, with Bluetooth symbol.
2. Glass control labeled `نقطة الاتصال` (“Hotspot”), with wireless/hotspot symbol.

Both are anchors without `href` values in the supplied HTML.

A centered section label `المزيد من طرق المشاركة` (“More sharing methods”) is followed by a four-item sharing row, in this order:

1. QR icon and label `رمز QR` (“QR code”).
2. Text glyph `W` and label `WhatsApp`.
3. Text glyph `f` and label `Facebook`.
4. Text glyph `···` and label `المزيد` (“More”).

Each uses the glass circular share-control style. These anchors have no destination or share integration in the supplied source.

## 10. Interaction and navigation inventory

| Element | Implemented behavior |
|---|---|
| Home `إرسال` | Navigates to `send.html` |
| Home `استلام` | Navigates to `receive.html` |
| Home notifications / plus | No click behavior defined |
| Home files row | No `href` or click behavior defined |
| Home bottom navigation | Only home anchor explicitly routes to `index.html`; files and history have no `href` |
| Send back | Navigates to `index.html` |
| Send category tabs | Toggles active visual state only |
| Send selectable rows | Toggles selection and updates summary/list visuals |
| Send search | No implemented search behavior |
| Send action | Writes selected `[name,size]` pairs to session storage, then navigates to `transfer.html` |
| Receive QR action | No implemented click behavior |
| Receive discovery | After 4.5 seconds, local timer changes text and makes action link to transfer page |
| Invite back | Navigates to `index.html` |
| Invite Bluetooth/hotspot/share methods | No `href` or share behavior defined |
| Transfer close / finish / done | Navigates to `index.html` |
| Transfer pause/resume | Stops or restarts the simulated animation loop and switches icon/status |

## 11. Shared CSS coverage and page instantiation

The complete inline stylesheet is repeated identically in all five documents. This is one shared rule set in content, not five different visual systems. A rule's presence in every page's `<style>` block does **not** mean every page renders every component. The actual page-level instantiation evidenced by markup and inline JavaScript is:

| Page | Components and patterns actually rendered or created by its script |
|---|---|
| `index.html` | Fixed brand/header controls and badge; radar; hero title/subtitle; send/receive actions; files row; recent card rows; bottom navigation; shared top/bottom backdrops |
| `send.html` | Back/title/search header; segmented categories; selectable list and check state; selection summary/send bottom bar; shared top/bottom backdrops |
| `receive.html` | Back/title/QR header; radar; waiting/connected text states; identity pill; receive bottom bar; shared top/bottom backdrops |
| `transfer.html` | Close/connection/count header; numeric summary and overall progress; per-item progress rows; pause/resume and completion bottom-bar states; shared top/bottom backdrops |
| `invite.html` | Back/title/spacer header; radar; Bluetooth and hotspot actions; four sharing options; shared top/bottom backdrops |

Across the five-page set, the shared CSS rules are represented by rendered markup or by a state created in the page's JavaScript. Important state selectors include `.on` (active navigation/tab), `.sel` and `.ck` (selected send row), `.st.ok` (completed transfer item), and the dynamic play/pause icon replacement. The notification badge is instantiated only on home. `.rd`/`.o`/`.core` appear on home, invite, and receive; `.seg` appears on send; `.nav` appears on home; `.sh` appears on invite; `.bx` appears on send, receive, and transfer. Transfer-specific data/progress classes include `.num`, `.num2`, `.tr`, `.meta`, `.st`, and `.u`. This section records scope and state; it does not label shared rules as unused merely because each individual page does not instantiate every component.

## 12. Accessibility and implementation facts visible in the HTML

- Document language and direction are explicitly Arabic/RTL.
- The interface supports safe-area insets through `env(safe-area-inset-top,0px)` and `env(safe-area-inset-bottom,0px)` and uses `100dvh` for body minimum height.
- There are explicit reduced-transparency and reduced-motion media preferences.
- Anchors and buttons inherit the page font/color; buttons have border and background reset and a pointer cursor.
- The source uses SVG strokes for icons and semantic elements including `header`, `main`, `nav`, `h1`, `p`, `button`, `a`, `b`, and `small`.
- The visible SVG use elements do not include accessible labels or `aria-label` attributes in the supplied source. Icon-only controls are present without additional accessible names.
- Several visually action-like anchors have no `href`; their functionality is not implemented in this prototype. Do not describe them as working features.

## 13. Fidelity rules for an implementation based on these files

1. Preserve the Arabic strings, RTL direction, document-level dark mode, exact color values, geometry, typography, content order, and initial states documented above.
2. Treat all five HTML pages as one visual system: their CSS rules and root design tokens are repeated identically in the source.
3. Preserve the distinction between actual link destinations and decorative/action-like controls without implemented destinations.
4. Do not turn the simulated receive discovery or transfer progress into claims of real device discovery or real file transfer. The supplied behavior is client-side demonstration logic only.
5. Keep literal data (names, sizes, notification count, identity string, and status labels) as shown unless a separate source explicitly changes it.
6. No logos, screenshots, photos, font assets, external packages, or backend services are included in the archive. The two-character list icons and the `W`, `f`, and `···` share glyphs are text, not imported assets.

## 14. Audit scope and fidelity note

This specification was prepared by reviewing the full contents of all five HTML files in the archive. Shared CSS, SVG symbol definitions, page markup, inline JavaScript, literal copy, data arrays, navigation URLs, state transitions, and timing/progress calculations were included. Where the source does not specify a behavior, this document records that it is not implemented instead of filling in a presumed design decision.

## References for the general term only

[1]: https://www.merriam-webster.com/dictionary/system "Merriam-Webster: System"
[2]: https://www.nngroup.com/articles/design-systems-101/ "Nielsen Norman Group: Design Systems 101"
[3]: https://www.figma.com/blog/design-systems-101-what-is-a-design-system/ "Figma: Design System 101"
