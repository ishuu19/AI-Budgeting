# LedgerAI UI brief

Rebuild the screen from scratch. Keep every feature and ViewModel call. Replace the layout.

## Look
- Page background: `L.Page`. Cards: `L.Box` with a 1dp `L.Line` border. Text: `L.OnBox`, secondary `L.OnBoxMuted`.
- One filled action: `L.Accent` (the old name `L.Gold` is the same colour) with `L.OnAccent` text. The mic is `L.Highlight` with `L.OnHighlight`. Amounts use `L.Ink`. Income uses `L.Income` and a leading `+`. Over or overdue uses `L.Danger` on the amount only. Labels and row icons use `L.InkMuted`.
- Spacing: `L.Space` is 4 · 8 · 12 · 16 · 24 · 32. Page gutter 16dp. Card inset 16dp.
- Radius: 16dp (`L.Radius`) on cards, groups, inputs, and buttons. `L.RadiusSm` is 10dp. Pills (`L.RadiusPill`) for chips, segments, search, and the mic.
- One large number per screen at most (`LHero`). Sentence case. No uppercase eyebrows.

## Copy
- Titles: one word when possible (Spend, Budget, Bills, Tasks).
- No helper paragraphs, onboarding text, tips, "Tap to…", or explanations.
- Empty state: `LEmpty(icon, "No bills")` style, one short line.
- Buttons: verb only — Save, Delete, Done. Creating is "New note" or "New bill".
- Shared home is Household. The money segment is Budget. Money you can spend is Safe to spend. Photo actions are Camera and Gallery.
- Field labels: one or two words.
- No emojis.

## Components (only these, from `com.ledgerai.app.presentation.components`)
`LScreen`, `LHeader`, `LHero` (optional `delta` and `trend`), `LCard`, `LGroup` + `LGroupRow`, `LRow` for one standalone item,
`LSection`, `LFieldLabel`, `LEmpty`, `LButton(fill)`, `LGhostButton`, `LTextLink`, `LFab`, `LField`, `LFilterChip`, `LActionChip`,
`LSegments` for 2–4 exclusive options, `LSheet` (primary may be null), `LSearchBox`, `LMicButton`, `LAiBar`, `LSwitch`, `LSwitchRow`,
`LSnackbar`, `LConfirm`, `LProgress`, `LBarChart`, `LDonut`, `LSparkline`, `LGauge`, `LToolButton`, `money()`, `relativeDate()`, object `L`.

Read `app/src/main/java/com/ledgerai/app/presentation/components/Ledger.kt` first.

## Rules
- Add/edit flows use `LSheet`, not full screens or AlertDialogs (confirm-delete may use a small AlertDialog).
- Lists: `LGroup` and `LGroupRow`. `LRow` is only a single standalone item. Tap = edit. Delete goes through `LConfirm` and uses `L.Danger`.
- Sub-screens take `onBack: () -> Unit = {}` and pass it to `LScreen(onBack = onBack)`.
- Do not change repository or DAO code. Change a ViewModel only if the UI cannot work otherwise.
- Do not run gradle. Another process builds at the end.
