---
title: SHAREit Developer Guide
document_type: developer_guide
language: English
project_source_of_truth:
  - index.html
  - send.html
  - receive.html
  - transfer.html
  - invite.html
companion_reference: system-design.md
source_policy: Project-specific facts and requirements must come only from the five listed HTML files.
---

# SHAREit Developer Guide

## 1. Purpose and source authority

This guide describes how to reproduce the five-page SHAREit prototype represented by the supplied HTML files. It is an implementation-oriented companion to the source-grounded [`system-design.md`](./system-design.md) Design System reference. It documents page composition, route relationships, interactive elements, state transitions, data, browser storage, timing, progress calculations, and observable limitations.

The only project sources used for this guide are the five files in the supplied archive:

- `index.html`
- `send.html`
- `receive.html`
- `transfer.html`
- `invite.html`

All project-specific requirements below are derived from those files. The source is a collection of independent, standalone HTML documents with inline CSS, SVG symbols, and page-specific JavaScript. The documents share the same CSS rules and SVG sprite. They do not include a backend, external script, external stylesheet, external font, packaged image, or real device/file-transfer integration.

This guide distinguishes three categories:

1. **Implemented behavior:** directly wired in the HTML or JavaScript, including state updates and local navigation.
2. **Simulation:** UI changes that imitate discovery or file transfer but are driven only by local JavaScript.
3. **Unspecified or inactive:** visible controls or product behavior for which the HTML supplies no handler, destination, network call, or implementation contract. Do not infer those behaviors from the visual affordance.

### Implementation contract for code-generation agents

- Treat the five listed HTML files as the complete and exclusive project-specific evidence available in this archive.
- Reproduce the documented strings, routes, data, initial states, transitions, and formulas; do not replace them with assumed product behavior.
- Keep locally simulated device discovery and transfer progress explicitly separate from real discovery or transfer functionality.
- Leave behavior marked “unspecified” unimplemented unless the user supplies a separate requirement. Do not claim that an inactive control works.
- Use `system-design.md` for visual tokens, geometry, shared CSS patterns, and icon treatment. This guide is the behavior/data companion; neither document establishes undocumented backend or platform contracts.

## 2. Application boundaries and runtime assumptions

Each source file is a complete document with `lang="ar"` and `dir="rtl"`. The app is designed as a mobile-width experience: body max width is `480px`, horizontally centered, with `min-height:100dvh`. The root and body backgrounds are black, and the document opts into dark color-scheme rendering. Safe-area insets are included in the top content/header and bottom bar positioning.

The prototype can be followed as ordinary HTML-page navigation:

```text
index.html ── Send ──> send.html ── Send ──> transfer.html
    │
    ├── Receive ──> receive.html ── after 4.5 s, Connect ──> transfer.html
    │
    └── Invite page is present as invite.html; no source link to it is wired from Home
```

Explicit links back to `index.html` are present on the send, receive, invite, and transfer pages. The Home-to-Invite entry point is not provided in the source. Do not treat the bottom-navigation Files or History labels, the Home files card, or any invite methods as functional routes unless a separate product requirement supplies their behavior.

All five pages declare the same inline stylesheet, with the same design tokens and component styles. Each page also repeats the same inline SVG sprite containing 16 symbols. Page-specific JavaScript initializes the shared top/bottom backdrop treatment, then performs only the interactions described in this guide.

## 3. Page and DOM contract

The IDs below are present in the HTML and are directly accessed by each page's script. They are useful for tracing the original implementation. They are not a requirement to use the same IDs in a different framework, but the associated behavior and displayed state are the source contract.

| Page | Important source IDs | Script responsibility |
|---|---|---|
| `index.html` | `rec` | Populate the Recent card from the fixed `D` data array |
| `send.html` | `seg`, `L`, `sm`, `go` | Toggle category appearance; render item rows; render selected count/size; store selected entries on send |
| `receive.html` | `h`, `sb`, `st`, `btn` | Update heading, subtitle, bottom-bar status and action after the discovery timer |
| `transfer.html` | `n`, `tm`, `fl`, `pc`, `sp`, `L`, `bx`, `ss`, `pz` | Read transfer selection; render item rows; update progress/time/status; pause/resume; replace controls on completion |
| `invite.html` | None beyond SVG symbol IDs | Apply shared backdrop blur layers; no invite action handler exists |

On all pages, JavaScript prepends five generated blur/mask `<i>` layers to each `.pb` backdrop element. It chooses the mask gradient direction based on whether the backdrop is top or bottom, with blur radii `0.7px`, `1.4px`, `2.8px`, `5.6px`, and `11.2px`. This effect is presentational and does not represent device scanning or app logic.

## 4. User-visible copy and localization

The document language and base direction are Arabic and RTL. Preserve the exact Arabic strings below when reproducing the visible prototype. English app names and methods are also present in source and should remain as written.

| Screen/state | Exact source text | Meaning for implementation reference |
|---|---|---|
| Home heading | `شارك مع من حولك` | Share with people around you |
| Home subtitle | `بدون إنترنت، وبسرعة عالية` | Without internet, at high speed |
| Home primary action | `إرسال` | Send |
| Home secondary action | `استلام` | Receive |
| Home files card | `الملفات` / `تصفّح ملفات جهازك` | Files / Browse your device files |
| Home recent label | `الأخيرة` | Recent |
| Home navigation | `الرئيسية` / `الملفات` / `السجل` | Home / Files / History |
| Send header | `إرسال` | Send |
| Send categories | `تطبيقات` / `صور` / `ملفات` | Apps / Photos / Files |
| Send selection count suffix | `عناصر` | Items |
| Send action | `إرسال` | Send |
| Receive initial heading | `في انتظار المرسل` | Waiting for sender |
| Receive initial subtitle | `افتح الإرسال على الجهاز الآخر` | Open Send on the other device |
| Receive initial status | `جارٍ البحث…` | Searching… |
| Receive initial action | `مسح QR` | Scan QR |
| Receive found heading | `تم العثور على SmartTV` | SmartTV found |
| Receive found subtitle | `اضغط اتصال للبدء` | Tap Connect to start |
| Receive found status | `SmartTV` | Discovered device name |
| Receive found action | `اتصال` | Connect |
| Transfer header | `متصل · SmartTV` | Connected · SmartTV |
| Transfer amount label | `ميغابايت مُرسَل` | Megabytes sent |
| Transfer elapsed-time label | `الوقت` | Time |
| Transfer file section | `الملفات` | Files |
| Transfer running status | `جارٍ الإرسال` | Sending |
| Transfer paused status | `متوقف مؤقتًا` | Paused |
| Transfer initial bottom action | `إنهاء` | Finish |
| Transfer complete status | `اكتمل الإرسال` | Transfer complete |
| Transfer complete action | `تم` | Done |
| Invite heading | `شارك مع الأصدقاء القريبين` | Share with nearby friends |
| Invite subtitle | `بدون إنترنت` | Without internet |
| Invite hotspot action | `نقطة الاتصال` | Hotspot |
| Invite methods heading | `المزيد من طرق المشاركة` | More sharing methods |
| Invite QR method | `رمز QR` | QR code |
| Invite final method | `المزيد` | More |

Other exact visible strings include brand `SHAREit`, identity `user79109045681`, device `SmartTV`, methods `Bluetooth`, `WhatsApp`, `Facebook`, `MB`, `MB/s`, the transfer speed dash `—`, and notification badge `4`.

## 5. Navigation and action map

| Source control | Destination or effect | Implemented? |
|---|---|---|
| Home `إرسال` pill | `send.html` | Yes; ordinary anchor navigation |
| Home `استلام` pill | `receive.html` | Yes; ordinary anchor navigation |
| Send back icon | `index.html` | Yes |
| Send category button | Adds `.on` to clicked category and removes it from the other category buttons | Yes; visual state only |
| Send row | Toggles that entry in the selection set and redraws all rows and summary | Yes |
| Send search icon | No `href`, click handler, or search operation | No behavior supplied |
| Send `إرسال` action | Attempts to store selected entries in `sessionStorage.sel`; anchor destination is `transfer.html` | Yes, while pointer interaction is allowed by current CSS |
| Receive back icon | `index.html` | Yes |
| Receive QR header icon | Anchor without `href` or handler | No behavior supplied |
| Receive bottom action before discovery | `href="#"`, label `مسح QR` | No QR scanning behavior supplied |
| Receive bottom action after discovery | Changes to `href="transfer.html"`, label `اتصال` | Yes; link is activated by local timer state change |
| Invite back icon | `index.html` | Yes |
| Invite Bluetooth and Hotspot actions | Anchors with no destination or click handler | No behavior supplied |
| Invite QR/WhatsApp/Facebook/More options | Anchors with no destination or click handler | No behavior supplied |
| Transfer close icon | `index.html` | Yes |
| Transfer `إنهاء` link | `index.html` | Yes |
| Transfer pause control | Toggles running/paused state and icon/status | Yes |
| Transfer completion `تم` link | `index.html` | Yes |
| Home notification, plus, Files and Files/History bottom-nav items | No `href`/handler in source | No behavior supplied |

The send button has an `href` even while empty selection causes JavaScript to set `pointer-events:none` and opacity `.4`. This is a CSS interaction treatment rather than native disabled semantics. Do not claim the anchor is semantically disabled: the source does not set a `disabled` attribute, `aria-disabled`, or remove its `href`.

## 6. Send screen: state and data behavior

### 6.1 Item dataset

The source defines an array `A` in this order. Numeric sizes are megabytes and are stored as JavaScript numbers.

| Index | Name | Size in MB | Default selected? |
|---:|---|---:|---|
| 0 | DeskLink LAN | 46.9 | Yes |
| 1 | Velo | 10.4 | Yes |
| 2 | Files Pro | 22.1 | No |
| 3 | Notes | 8.2 | No |
| 4 | Clip | 3.4 | No |
| 5 | Terminal | 31.5 | No |
| 6 | Radio | 12.8 | No |
| 7 | Maps Lite | 54.0 | No |

The selection state is a JavaScript `Set` initialized with indexes `{0,1}`. The UI does not query the device's installed applications or files; these eight records are hard-coded demonstration data.

The array source writes the final size as the numeric literal `54.0`, but JavaScript stores it as the number `54`. The row template interpolates the raw number, so that entry appears as `54 MB`, not `54.0 MB`. The selected-total summary uses `toFixed(1)` and therefore always displays one decimal place.

### 6.2 Initial state and render contract

On page load:

1. `تطبيقات` is the initially active segment; `صور` and `ملفات` are inactive.
2. The source renders all eight `A` entries, regardless of the selected segment.
3. Each row contains an icon tile made from the first two name characters, the full name, a size line formatted from the number plus ` MB`, and a circular check control.
4. Rows at selected indexes receive `.sel`; the check icon is visible through the selected CSS state.
5. The bottom summary is populated as selected count plus ` عناصر`, and the total size to one decimal followed by ` MB`.
6. The send action has opacity `1` and pointer events enabled because the initial selection is non-empty.

The initial set summary therefore reads two items with a combined total of `57.3 MB` (`46.9 + 10.4`), formatted by `toFixed(1)`. The summary's exact structure is dynamic: `${S.size} عناصر` followed by a `<small>` containing `${t.toFixed(1)} MB`.

### 6.3 Row selection transition

Clicking any descendant of a list row is handled by `L.onclick`. The handler finds the nearest `.li`, reads its `data-i`, toggles that index in `S`, then calls `r()` to replace the list markup and refresh summary/action styling.

The action state is calculated from whether `S.size` is truthy:

- Non-empty: opacity `1`, pointer events `auto`.
- Empty: opacity `.4`, pointer events `none`.

There is no maximum selection count, category-specific filtering, confirmation dialog, or selection error state in the source.

### 6.4 Category segment transition

Clicking a direct `<button>` child of `#seg` toggles `.on` so only that button is active. This does not modify `A`, `S`, the row list, or the summary. The tab labels are UI-only categories in this source, not separate data filters.

### 6.5 Selection persistence and route transition

Clicking `#go` runs:

```js
sessionStorage.sel = JSON.stringify([...S].map(i => A[i]))
```

The stored value is therefore a JSON array of `[name, size]` pairs in the iteration order of the selected `Set`. It is written to the session-scoped browser storage key `sel`. A `try/catch` suppresses storage errors. The event handler does not cancel default anchor navigation; the `href="transfer.html"` continues to provide the route.

If this storage write fails or is unavailable, navigation still follows the anchor when activated; the transfer page then uses its default dataset. The source does not remove `sessionStorage.sel` after reading or completing a transfer.

## 7. Receive screen: states and timing

The receive page starts in a waiting state and changes once after a timer. The timer is exactly `4500` milliseconds.

| Field | Initial state | State after 4,500 ms |
|---|---|---|
| Heading `#h` | `في انتظار المرسل` | `تم العثور على SmartTV` |
| Subtitle `#sb` | `افتح الإرسال على الجهاز الآخر` | `اضغط اتصال للبدء` |
| Bottom status `#st` | `جارٍ البحث…` | `SmartTV` |
| Bottom action `#btn` label | `مسح QR` | `اتصال` |
| Bottom action `href` | `#` | `transfer.html` |
| Identity pill | `user79109045681` | Unchanged |

The receive page always renders the same radar illustration with a download icon and the same identity pill. No receiver identifier generation, scanner, camera permission, QR decoder, Bluetooth scan, local network search, connection handshake, timeout, error state, cancellation, or retry behavior is implemented. “SmartTV found” is a timer-driven presentation state only.

The action becomes a real local route to `transfer.html` after the timer. No recipient or selected content is passed to transfer from this page. On transfer-page load, the transfer script reads the existing session storage key if available, otherwise uses the default transfer list.

## 8. Transfer screen: data input and lifecycle

### 8.1 Startup source and fallback behavior

The transfer script initializes `F` to:

```text
[DeskLink LAN, 46.9], [Velo, 10.4], [Files Pro, 22.1]
```

It then attempts to parse `sessionStorage.sel`. If the parsed value is truthy and has a nonzero `.length`, it replaces `F`. Parse/storage exceptions are caught and leave the default intact. An empty parsed array also leaves the fallback intact.

The script does not validate the shape or numeric values of the parsed array before using it. The source's intended saved data is the two-element `[name,size]` format created by `send.html`. A parseable but wrongly shaped nonempty value is not covered by the JSON/storage `try/catch` and can fail later when transfer code assumes array methods and numeric sizes.

### 8.2 Initial display

At initialization:

- Receiver header reads `متصل · SmartTV` and a separate glass control displays `2`.
- Sent amount is `0.0`; elapsed time is `0:00`; total progress is `0%`; speed is `0 MB/s`.
- Bottom status is `جارٍ الإرسال`.
- Each current item is rendered in a row with a two-character name tile, name, thin row-progress track initialized at zero width, and `0%` status.
- The pause control displays the pause symbol; the adjacent `إنهاء` link routes to `index.html`.
- The transfer starts running immediately. The source does not include a separate ready-to-start state.

### 8.3 Runtime state variables

| Variable | Initial value | Meaning evidenced by use |
|---|---:|---|
| `F` | Saved nonempty selection, otherwise three defaults | Ordered transfer demonstration items `[name,size]` |
| `TT` | Sum of `F[i][1]` | Total displayed transfer size |
| `sent` | `0` | Simulated megabytes sent |
| `el` | `0` | Simulated elapsed seconds |
| `run` | `1` | Whether simulation values may advance |
| `fin` | `0` | Completion guard |
| `last` | `performance.now()` | Timestamp used to calculate frame delta |
| `R` | Snapshot of rendered row children | Per-item DOM rows updated by the animation loop |

### 8.4 Progress and time calculations

The script starts a `requestAnimationFrame` loop. On each callback:

1. Compute `d = min((t - last) / 1000, .1)` seconds.
2. Set `last = t` whether running or paused.
3. If `run` is true and `fin` is false, add `d` to `el`.
4. Calculate the displayed simulated speed:

   ```text
   v = 6 + 2.5 × sin(el × 1.7) + 1.2 × sin(el × 5.3)
   ```

5. Increase `sent` by `v × d`, capped at `TT`.
6. Redraw aggregate and per-item progress.
7. If `sent >= TT`, run completion handling.
8. Request the next animation frame.

Aggregate values are updated as follows:

- Overall percent `p = sent / TT × 100`.
- Overall progress fill width is `p%`.
- Amount is `sent.toFixed(1)`.
- Percent text is `Math.floor(p) + "%"`.
- Speed text is `v.toFixed(1) + " MB/s"`.
- Elapsed time is `floor(el/60) + ":" + floor(el%60)` with seconds padded to two digits.

Per-file progress walks the current list in order with cumulative offset `o`, initially zero. For each file size `f[i][1]`:

```text
q = max(0, min(1, (sent - o) / f[i][1]))
o = o + f[i][1]
```

The file's compact progress bar is set to `q × 100%`. Its status is a checkmark `✓` when `q >= 1`; otherwise it is the floored integer `q × 100` followed by `%`. Completed rows receive `.ok`, which colors status with the teal accent.

This is a visual progress calculation over numeric demo sizes. It does not read a file, send bytes, measure a real network, report a device transfer speed, or confirm that a recipient received data.

### 8.5 Pause and resume

Clicking `#pz` toggles `run` between truthy and falsey values. The same event handler:

- switches the inline SVG symbol to `#pa` when running, or `#pl` when paused;
- sets status to `جارٍ الإرسال` when resumed, or `متوقف مؤقتًا` when paused.

While paused, the frame callback continues to execute and update `last`, but it does not increment `el` or `sent`, redraw progress, or update speed. Updating `last` while paused prevents the next resumed frame from including the full paused duration in its delta. The source does not change the aggregate speed label when paused; it remains the last rendered speed until the next running update.

### 8.6 Completion state

When `sent >= TT`, the `done()` function sets `fin=1`, changes speed to an em dash `—`, sets status to `اكتمل الإرسال`, and replaces the contents of the bottom control row with a single primary `تم` anchor to `index.html`. Completed per-file rows display `✓` and use `.ok`.

The loop still schedules future animation frames, but the `run && !fin` guard prevents further simulation updates. No completion sound, system notification, receipt/confirmation from a device, retry option, or transfer record persistence is present in source.

## 9. Home screen: content and fixed data

Home consists of:

1. A fixed top header with `SHAREit`, a teal dot, notification badge `4`, and plus control.
2. A radar illustration centered around the send icon.
3. Heading `شارك مع من حولك` and subtitle `بدون إنترنت، وبسرعة عالية`.
4. A primary send route and secondary receive route.
5. A files card with folder icon and subtitle.
6. A Recent section and list.
7. A fixed three-item bottom navigation bar.

The Recent list is generated from fixed array `D` in this order:

| Name | Displayed size | Status |
|---|---:|---|
| DeskLink LAN | 46.9 MB | `أُرسل` |
| Velo | 10.4 MB | `أُرسل` |
| Files Pro | 22.1 MB | `استُلم` |
| Notes | 8.2 MB | `استُلم` |
| Clip | 3.4 MB | `أُرسل` |
| Terminal | 31.5 MB | `استُلم` |

Each recent icon tile is text containing the first two characters of its name. The status and amount are joined using ` · `. There are no timestamps, sorting rules, query, or persisted recent-history source.

The plus and notification controls have no handlers. The Files card has no `href`. Only the Home item in bottom navigation has an `href` (`index.html`); Files and History are anchors without destinations.

## 10. Invite screen: available and unavailable behavior

The invite page has a back link, centered `دعوة` title, and an empty `44px` spacer in the opposite header position. Main content is radar/send icon, heading `شارك مع الأصدقاء القريبين`, subtitle `بدون إنترنت`, two stacked methods, a centered more-methods heading, and four sharing options.

The two large controls are Bluetooth and Hotspot. The four smaller options are QR code, WhatsApp, Facebook, and More. The icons are the inline SVG symbols for Bluetooth, hotspot/wireless, and QR; WhatsApp/Facebook/More are literal glyphs `W`, `f`, and `···`. All these action-like anchors lack `href` values and click handlers. The source does not invoke a native share sheet, build a QR code, open external apps, broadcast an invitation, or establish Bluetooth/hotspot communication.

## 11. Shared implementation behavior and accessibility observations

### 11.1 Shared overlays and motion preferences

Every page adds five dynamically generated masked blur layers to each top/bottom `.pb` element. The page-level fixed headers/bars appear above the backdrop. The root CSS is dark and RTL throughout.

The stylesheet includes:

- `prefers-reduced-transparency:reduce`: `.g` backgrounds become solid `#1C1D21`, with standard and WebKit backdrop filters disabled.
- `prefers-reduced-motion:reduce`: all CSS animations and transitions are disabled using `!important`.

### 11.2 Semantic and accessibility facts present in the source

The HTML uses semantic elements such as `header`, `main`, `nav`, `h1`, `p`, `button`, `a`, `b`, and `small`; it specifies Arabic language and RTL direction. SVGs use stroked paths. The source does not provide `aria-label` attributes or explicit accessible names on icon-only controls. Some visual controls are anchors with no `href`; a real assistive-technology interaction model is not specified by these files. These are observations about the source, not evidence of additional accessibility requirements.

## 12. Source-backed acceptance tests

Use these checks to verify a reproduction of the behavior encoded in the HTML. Tests describe only source-backed outcomes; they do not assert a real transfer or device scan.

### Home

- Opening `index.html` shows Arabic RTL content, the SHAREit header, badge `4`, radar with send icon, headline/subtitle, send/receive actions, files card, six recent rows in the documented order, and three navigation labels.
- Activating `إرسال` navigates to `send.html`.
- Activating `استلام` navigates to `receive.html`.
- Do not expect notification, plus, Files card, Files navigation, or History navigation to perform an action.

### Send

- Opening `send.html` shows Apps active and all eight hard-coded rows; indexes `0` and `1` are selected.
- The initial summary count is `2` and total is `57.3 MB`.
- Selecting an unselected row marks it selected and updates count/total; selecting it again removes it and updates both.
- Clearing every selection changes opacity to `.4` and disables pointer interaction using `pointer-events:none`; source does not implement native disabled semantics.
- Clicking Photos or Files changes active-segment styling but leaves the same eight rows and current selection visible.
- With selected entries, activating Send stores those `[name,size]` pairs in `sessionStorage.sel` and follows `transfer.html`.
- If the storage write throws, the click handler suppresses the error; the anchor destination remains `transfer.html`.

### Receive

- Immediately after load, show the waiting heading, initial subtitle, identifier, searching status, and `مسح QR` action with `href="#"`.
- Before the 4.5-second timer fires, no “found” copy is shown.
- After 4.5 seconds, show SmartTV found/connect copy and update the bottom action to `اتصال` with `href="transfer.html"`.
- Do not expect camera access, QR decoding, radio scan, actual device discovery, or failure/retry UI.

### Transfer

- With no valid nonempty stored selection, render the three fallback entries (46.9, 10.4, 22.1 MB).
- With a nonempty valid `sessionStorage.sel` array created by Send, render those entries instead.
- On start, aggregate amount, timer, percentage and each row begin at zero; state reads Sending and pause icon is visible.
- During running simulation, totals, percent, speed, timer and per-item progress update using the source calculations.
- Pause halts time/progress advancement, changes status to paused and icon to play; resume restarts advancement and restores status/icon.
- When total progress reaches 100%, show completed state, speed dash, checkmarks for completed rows, and a single Done action to Home.
- Close and Finish actions navigate to `index.html`.
- Do not assert that bytes were read or transferred, or that the displayed speed is a measured network speed.

### Invite

- Opening `invite.html` shows the two large methods and four additional share options in the stated order.
- Back navigates to `index.html`.
- Bluetooth, Hotspot, QR, WhatsApp, Facebook and More are presentational controls with no wired destination/action in this source.

## 13. Explicitly unspecified: do not invent from the HTML

The files do not define:

- application framework, package layout, API contract, server, database, authentication, deployment target, or native bridge;
- real nearby-device discovery, Bluetooth protocol, Wi-Fi Direct, hotspot credentials, pairing, connection security, transfer negotiation, or disconnection handling;
- real file browsing, permissions, file selection, file metadata, app installation discovery, category-specific datasets, or content validation;
- actual reading/writing/transmission of file bytes, measured speed, integrity checks, resume checkpoints, cancellation semantics, retry, failure recovery, or transfer receipt;
- QR camera permission, QR content, QR generation, scanning, or URL/deep-link behavior;
- notification behavior, plus action, file browser route, history route, search behavior, or invite/share integration;
- input validation; parseable but malformed storage shapes are not validated, while storage-access/JSON-parse exceptions are caught and leave the default dataset in place; data expiry, storage clearing, user identity generation, or localization beyond the literal text in the files.

If a future implementation requires any of these, obtain a separate product/technical specification. Do not present a guessed implementation as something derived from the HTML.

## 14. Implementation fidelity checklist

Before considering a reproduction faithful to the source, confirm that it:

- contains the five mapped page roles and only the source-backed route links;
- retains Arabic document language, RTL direction, displayed copy, ordering, hard-coded names/sizes, identity string, and initial states;
- implements send selection and the exact `sessionStorage.sel` `[name,size]` data flow;
- implements the 4,500 ms receive-copy transition as a local UI timer, not as evidence of real discovery;
- implements the documented transfer formulas, pause/resume status/icon, per-item completion calculation, and completion control replacement as a demonstration;
- preserves the distinction between anchors with destinations and visible anchors without destinations;
- does not claim that a network transfer, device search, QR scan, operating-system share, or file-system browse works when the supplied files do not implement it;
- uses the accompanying Design System document for exact visual tokens, geometry, icons, and shared component styling rather than inventing new visual values.

## 15. Source inventory

| Source file | Title text | JavaScript behavior documented here |
|---|---|---|
| `index.html` | `الرئيسية` | Generate shared blur layers; populate static Recent entries |
| `send.html` | `إرسال` | Generate shared blur layers; render list; toggle selection and categories; compute summary; save selection |
| `receive.html` | `استلام` | Generate shared blur layers; update discovery copy/action after 4,500 ms |
| `transfer.html` | `متصل` | Generate shared blur layers; load selection/defaults; animate simulated progress; pause/resume; complete |
| `invite.html` | `دعوة` | Generate shared blur layers; no additional event behavior |

## 16. Developer glossary

These terms describe concepts as they are used in the supplied implementation.

| Term | Meaning in this guide |
|---|---|
| DOM | The document elements that page scripts query or update, such as `#L`, `#h`, and `#pz`. |
| RTL | Right-to-left document direction, declared on each HTML root as `dir="rtl"`. |
| Anchor / `href` | An `<a>` element and its navigation destination. Some visible anchors have no destination in these files. |
| `sessionStorage` | Browser storage used by the Send page to pass the selected `[name,size]` tuples to the Transfer page within the browser session. |
| Selection `Set` | The Send page's set of selected numeric indexes into its hard-coded item array. |
| `requestAnimationFrame` | The browser callback loop used to update the simulated transfer display. |
| Frame delta | The elapsed time between two animation-frame timestamps, converted to seconds and capped at `0.1` in the transfer script. |
| `TT` | The sum of the sizes of the current transfer entries; it is the denominator for overall progress. |
| `sent` | The simulated number of megabytes sent; it increases according to the demonstration speed and frame delta. |
| Per-item ratio `q` | A value from `0` to `1` representing the amount of the current file reached by the sequential simulated progress. |
| Fallback dataset | The three transfer entries used when storage is inaccessible, invalid, or contains an empty array. |
| Simulation | A UI state or number generated locally by JavaScript rather than by actual device discovery or byte transfer. |
| CSS `pointer-events` | The CSS property used to prevent pointer interaction with an empty-selection Send link; it is not the same as a native disabled control. |
| SVG symbol sprite | The set of inline `<symbol>` definitions referenced by SVG `<use>` elements for the outlined UI icons. |
| Active class `.on` | The visual selector applied to the active Home navigation item or Send category button. |
| Completion class `.ok` | The Transfer status class added when an item reaches its simulated complete ratio. |
