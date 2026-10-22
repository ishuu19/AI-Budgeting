# LedgerAI phone screens

This file is the source of truth for every Ledger screen. `MASTER.md` is the rejected generated kit. Do not paint from it.

The screens live in `app/src/main/java/com/ledgerai/app/presentation/paper/`. Open the **Ledger screens** launcher. There is no web prototype.

## Why the generated master is not used

The search recommended glassmorphism, trust blue `#1E40AF`, profit green, and Caveat/Quicksand. That is the generic fintech kit. This product is a private pocket ledger: money, birthdays, passwords, appointments. Amounts and passwords need a book face.

## Tokens

| Role | Light | Dark |
|---|---|---|
| Paper | `#E4E8DF` | `#141A17` |
| Ink | `#1A2820` | `#E7EDE6` |
| Moss | `#2F4A3C` | `#B7C9BC` |
| Brass | `#8C6239` | `#D7B08A` |
| On brass | `#FFFBF5` | `#1A140C` |
| Rule | `#C5CBBE` | `#314038` |
| Danger | `#8E3B32` | `#E7A198` |
| Quiet text | `#3E5146` | `#C5D0C8` |

Display: Fraunces, the safe-to-spend figure only. Body: Literata. Sentence case.

Spacing is 6, 11, 18, 29, 47. Do not use 4, 8, 12, 16, 24, 32. Hit targets are 48dp because that is the Android minimum, and that size is not a spacing step.

Brass is not small text. On paper it is about 4.3:1, under the 4.5:1 text bar. Use it for the mic fill and the Next mark. White on the light brass fill is about 5.3:1.

## Principles

- One phone column, left aligned. No glass, blur, gradient, shadow, or emoji.
- Rows are separated by a hairline. A row with no action is not clickable.
- The memorable element is the safe-to-spend figure. Everything else stays quiet.
- Bottom bar: Home, Plan, Mic, Money, Library. Mic is the only filled control.
- Search on a tab is a button that opens the search screen. It does not look like a field you can type in.
- Buttons say the action: Save, Allow, File, Unlock.
- Sheets dim the page with a scrim. Close and the scrim both dismiss. A wish's spend draft, closed without Save, changes nothing.

## Scan

Home, top to bottom: date (opens the agenda), Next, safe to spend, coming up, ask bar. One Next row. One amount. Coming up is one line.
