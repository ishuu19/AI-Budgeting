# UI design

Visual language (colors, type, components) stays with the existing system: see [../ui-brief.md](../ui-brief.md), [../ui-redesign.md](../ui-redesign.md) and mockups in `docs/screens/`. This doc defines structure, not styling.

## Principle
One home screen organized around what the person needs today, not ten dashboards. Everything is reachable from one assistant input.

## Navigation

Bottom bar (4 + center assistant):

| Tab | Purpose | Existing screens it absorbs |
|---|---|---|
| Home | Daily briefing, glance cards, quick actions | `dashboard`, `today` |
| Plan | Calendar, tasks, jobs, habits | `calendar`, `tasks`, `jobs`, `plan`, `focus` |
| **Ask** (center) | Universal input: voice, text, camera, upload | `ai`, `voice` |
| Life | Home & household, people, wardrobe | new `home`, `household`, `people`, `wardrobe` |
| Money | Spending, receipts, bills, renewals | `money`, `transactions`, `bills`, `budget`, `debts`, `goals`, `analytics`, `forecast` |

Settings, "What I remember", Activity and search sit in a profile sheet, with global search in the top bar (existing `search`).

## Home screen

```
Good afternoon, <name>
┌ Daily briefing ───────────────────────┐
│ 3 need attention · 4 groceries · 2 due │  → Review my day
└───────────────────────────────────────┘
┌ Ask anything ─────────────────[mic][cam]┐
Life at a glance (2x2 cards)
 Home        Money
 My day      My people
Suggestions (pending confirm cards)
```

Cards show one number and one next action. Tapping opens the module workspace.

## Core components (new)

| Component | Use |
|---|---|
| `AssistantBar` | Persistent input with mic, camera, attach |
| `ActionConfirmCard` | Shows the `ActionPlan`, risk badge, edit/approve/reject. Generalizes the finance confirm card |
| `ProvenanceChip` | "From receipt, 11 Oct" with link to source; marks inferred vs confirmed |
| `UndoSnackbar` | For low-risk applied actions |
| `EmptyState` | Explains what the module does and the fastest way to add data |

## Screen inventory by stage

- Stage 1: Ask sheet, Confirm card, Inventory list/detail, Shopping list, Receipt scan + review, Expiry alerts.
- Stage 2: Household create/invite, Members, Split & balances, Shopping turn, Budget per household.
- Stage 3: People list/detail, Pre-meeting brief, Document import, Job extraction review, Form-assist (share-to-app).
- Stage 4: Subscriptions, Wardrobe + outfit builder, Automation settings, Weekly review.
- Cross-cutting: What I remember, Activity, Notification controls, Privacy settings.

## Quality bars
- Every AI result is editable before commit and shows its source.
- Works offline for capture; queues and says so.
- Voice-first flows reachable in two taps from the widget.
- Accessibility: TalkBack labels, 48dp targets, no color-only status.
