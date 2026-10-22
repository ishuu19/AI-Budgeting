# Ledger screens

The clickable screens are the Android activity `PaperActivity` (launcher label **Ledger screens**). They are Compose, on a phone, and they are not a website. `web/` is removed.

## What the search suggested, and what we kept

UI/UX Pro Max `--design-system` for a personal finance ledger returned glassmorphism, trust blue `#1E40AF`, profit green, Caveat and Quicksand. That is the generic fintech kit. It is recorded in `design-system/ledgerai/MASTER.md` only as the rejected pass.

The screens use the override in `design-system/ledgerai/pages/app.md`:

- Paper `#E4E8DF`, ink `#1A2820`, moss `#2F4A3C`, brass `#8C6239`, rule `#C5CBBE`, danger `#8E3B32`
- Dark page `#141A17`, ink `#E7EDE6`, brass `#D7B08A`
- Fraunces for the one safe-to-spend figure. Literata for everything else
- Spacing 6, 11, 18, 29, 47. The 48dp size is the Android hit target, not a spacing step

Brass is the mic fill and a 3dp mark on the Next row. It is not used as small text, because brass on paper is about 4.3:1.

## How a screen is read

Home answers "what needs me". The eye hits the date, then the Next row, then the one large amount, then Coming up, then the ask bar. The mic is the only filled control. Library rows show one status line. Money's first number is safe to spend.

## Skills

- `.agents/skills/frontend-design`
- `.agents/skills/ui-ux-pro-max` (touch targets, contrast, no emoji icons, reduced-motion friendly sheets)
- UX scan: one job per screen, one large amount, no fake text fields
