# LedgerAI UI brief

Rebuild the screen from scratch. Keep every feature and ViewModel call. Replace the layout.

## Look
- Page background: white (`L.Page`).
- Boxes, rows, cards: royal green (`L.Box`). Text on boxes: white (`L.OnBox`), secondary `L.OnBoxMuted`.
- Accent: gold (`L.Gold`) for labels, amounts, icons, primary buttons.
- Danger amounts on green: `L.Danger`.
- Spacing: 4 · 8 · 12 · 16 · 20 · 24. Page gutter 20dp. Gap between items 12dp.
- One large number per screen at most (`LHero`).

## Copy
- Titles: one word when possible (Spend, Budget, Bills, Tasks).
- No helper paragraphs, onboarding text, tips, "Tap to…", or explanations.
- Empty state: `LEmpty(icon, "No bills")` style, 2–3 words.
- Buttons: verb only — Save, Add, Delete, Done.
- Field labels: one or two words.
- No emojis.

## Components (only these, from `com.ledgerai.app.presentation.components`)
`LScreen(title, onBack, action, fab) { lazy items }`, `LHero`, `LCard`, `LStat`, `LRow`, `LSection`,
`LEmpty`, `LButton`, `LGhostButton`, `LFab`, `LField`, `LChip`, `LSheet`, `LProgress`, `LIconButton`, `LLogo`, `money()`, object `L`.

Read `app/src/main/java/com/ledgerai/app/presentation/components/Ledger.kt` first.

## Rules
- Add/edit flows use `LSheet`, not full screens or AlertDialogs (confirm-delete may use a small AlertDialog).
- Lists: `LRow` items. Tap = edit. Long actions go into the edit sheet (Delete as `LGhostButton`).
- Sub-screens take `onBack: () -> Unit = {}` and pass it to `LScreen(onBack = onBack)`.
- Do not change repository or DAO code. Change a ViewModel only if the UI cannot work otherwise.
- Do not run gradle. Another process builds at the end.
