# Notebook (Add to Notes v2)

Status: **built** · Date: 2026-10-13 · Stage: Capture engine (cross-stage)
Builds on: [add-to-notes.md](add-to-notes.md) (v1, built). Everything in v1 stays. This doc adds what v1 left out.

---

## 1. In one line

**Say or press once, from any screen → see that screen's text → pick words → add your own words and prompt → Save or Ask AI.** Everything you keep lives on one editable board, like the keyboard clipboard, and every chat is kept.

### The five promises

| # | Promise | How |
|---|---|---|
| P1 | **Callable anytime** | Assistant gesture (long-press power or home), tile, bubble, shortcut |
| P2 | **Reads the screen when called** | Android's assist data from the app in front, OCR when there is no text. Only on call. |
| P3 | **Your words + screen words + your prompt** | One input box with selected-word chips, a prompt row and a mic |
| P4 | **Board of entries, editable** | Pinned and recent tiles, like Gboard's clipboard. Tap to use, hold to edit or pin. |
| P5 | **Chats are stored** | Every Ask is a thread in Room, synced to Supabase |

---

## 2. Your request → the design

| You asked | Section |
|---|---|
| Call it anytime | §3 Summon |
| Called with voice | §3, §7 Voice |
| Read the screen when called | §4 Screen reading |
| Input box with selected words | §5 Panel |
| Manual prompt for the AI | §5.3 Prompt row |
| Responsive, few words, structured | §5.4 Layouts, §5.5 Copy rules |
| Past chats in the database | §6.2 Chats, §9 Data |
| Entries editable, like pinned keyboard copies | §6.1 Board |

---

## 3. Summon

| Trigger | Reads screen? | Starts listening? | Status |
|---|---|---|---|
| **Long-press power / home** (assistant gesture) | **Yes** | Yes | `LedgerSession` exists. It now opens the Notebook panel, not `QuickCaptureActivity` |
| Quick Settings tile "Notebook" | No (clipboard chip only) | Optional | Rename of `ClipTileService` |
| Floating bubble | No (clipboard chip only) | Tap = no, hold = yes | `ClipBubbleService` exists |
| Selection menu "Add to Notebook" | Uses the selected text | No | v1 `ClipCaptureActivity`, label rename |
| Launcher shortcut `notebook` | No | Yes | Opens the panel |
| Widget mic | No | Yes | Listens, then categorizes into suggested entries. Does not open the panel |

**Voice:** the assistant gesture *is* the voice trigger. Hold power, speak, release. A custom always-on wake word ("Hey Lifeline") is **not possible** for a Play app: the hotword API is limited to preinstalled assistants, and a mic that is always running drains the battery and keeps the privacy dot on. It is rejected.

**One-time setup** (the panel shows a card until it's done):
1. Settings → Apps → Default apps → Digital assistant → **Lifeline**
2. In the same screen: **Use text from screen** on, **Use screenshot** on

Check: `VoiceInteractionService.isActiveService(context, component)`.

---

## 4. Screen reading

### 4.1 How

```
assistant gesture
  └─ LedgerSession.onHandleAssist(state)
       ├─ AssistStructure has text   → text blocks (paragraphs, with on-screen order)   ✓ best
       ├─ no text, screenshot exists → ML Kit OCR on device → text blocks               ✓ fallback
       └─ neither (FLAG_SECURE app)  → "This app hides its screen." + type or paste    ✓ honest
```

- Walk `AssistStructure.ViewNode`s; keep `text` and `contentDescription`. Drop password fields (`inputType` password), empty nodes and nodes under 2 chars.
- Merge nodes into **blocks**, sorted top-to-bottom, left-to-right by `getVisibleRect`-style bounds.
- OCR uses `TextRecognition` (already used in `ScheduleImageRecognizer`). It runs on the phone. No image leaves it.
- Window title and package (`AssistStructure.activityComponent`) become the source chip: `Chrome · 14:02`.

### 4.2 Rules (privacy)

| Rule | Detail |
|---|---|
| **Only on call** | Data arrives only when the user triggers the assistant. No service watches the screen. |
| **Memory only** | Screen text and screenshot live in the panel's memory. Closing the panel drops them. |
| **Only picks are kept** | Only blocks or words you select are saved, as clips (`sourceKind = 'screen'`). |
| **Whole screen to AI needs a tap** | "Use whole screen" is an explicit chip. Default sends only the selection. |
| **Sensitive check first** | `ClipRules` (cards, OTPs, keys) runs on every selection before Save or Ask. |
| **No AccessibilityService** | v1's rejection stands. |

> Policy change: `privacy-security.md` says "No background screen reading". This stays true: reading is **foreground and user-triggered**, the same model as Google's "What's on my screen". Decision D1 confirms it (§13).

---

## 5. The panel

Opens over the current app. Translucent, `excludeFromRecents`, `adjustResize`.

### 5.1 Compact (phone, portrait)

```
┌────────────────────────────────────────┐
│ ━━━━                       Chrome · now│  ← drag handle · source
│ ┌ Screen ─────────────── Select all ┐ │
│ │ [Shinjuku Gyoen opens 9:00]        │ │  ← blocks: tap = select
│ │ [Closed Mondays. Entry ¥500.]      │ │     hold = pick single words
│ │ [Nearest station: Shinjuku-gyoemae]│ │
│ └────────────────────────── ▾ more ──┘ │
│                                        │
│ ╭ Shinjuku Gyoen…9:00 ✕ ╮ ╭ ¥500 ✕ ╮   │  ← selected chips
│ ┌────────────────────────────────────┐ │
│ │ Add a note or ask…             🎤 │ │  ← input box
│ └────────────────────────────────────┘ │
│ (Summarize)(Explain)(To-do)(Translate)+│  ← prompt row
│                                        │
│ 📌 Pinned  ▢ Visa    ▢ Address  ▢ …  › │  ← board strip
│                                        │
│ [   Save   ]              [ Ask AI ✦ ] │
└────────────────────────────────────────┘
```

Order top to bottom = order of use: **look → pick → write → send.**

### 5.2 Parts

| Part | Behaviour |
|---|---|
| **Screen card** | Blocks as tappable rows. Tap toggles a block. Hold enters word mode: tap words, or drag across a range. Collapses to 3 rows; **▾ more** expands. Hidden when nothing was read. |
| **Selected chips** | One chip per pick, in pick order. ✕ removes. Tap opens the full text. |
| **Input box** | Your own text. Grows to 5 lines, then scrolls. Mic dictates into it (offline STT first, as today). |
| **Prompt row** | See 5.3. |
| **Board strip** | Pinned entries, then recent. Tap = insert into the input box. **›** opens the Notebook screen. |
| **Save** | Saves chips + your text as **one entry**. Strip: *"Saved · Undo"* (3 s). |
| **Ask AI** | Saves the entry, opens the thread view inside the panel (§6.2), sends. |

### 5.3 Prompt row (manual prompt)

- Built-in chips: **Summarize · Explain · To-do · Translate · Reply**.
- **+** writes a custom prompt. Long-press a chip to edit, reorder or delete.
- One chip at a time. The chosen prompt shows in the input as a grey prefix: `Summarize ▸ your text`.
- Your own text without a chip = the prompt itself.
- What the AI receives: `prompt` + `selected text` + `your text` (+ the last 6 thread messages). Nothing else.

### 5.4 Layouts (responsive)

Driven by `WindowSizeClass`.

| Width | Layout |
|---|---|
| **Compact** (phone portrait) | Bottom sheet. Opens at 60 %, drags to full. Screen card collapses first when the keyboard opens. |
| **Medium** (foldable, small tablet, phone landscape) | Centred sheet, max 640 dp wide, full height. |
| **Expanded** (tablet, unfolded) | Two panes: screen on the left, compose + thread on the right. |

```
Expanded
┌──────────────────────────┬──────────────────────────┐
│ Screen · Chrome    All ▢ │ Thread: Shinjuku          │
│ [Shinjuku Gyoen opens…]  │  you  Summarize ▸ …      │
│ [Closed Mondays…]        │  ✦    Open 9–16:30, …    │
│ [Nearest station…]       │                          │
│                          │ ╭chip╮ ╭chip╮            │
│                          │ [Add a note or ask… 🎤]  │
│                          │ (Summ)(Expl)(To-do) +    │
│                          │ [Save]        [Ask AI ✦] │
└──────────────────────────┴──────────────────────────┘
```

- Targets ≥ 48 dp. Text scales with system font; nothing truncates actions.
- Uses `L.*` tokens, `LSheet`, `LChip`. AI marks use the one amber highlight. No new colours.

### 5.5 Copy rules (fewer words)

- Buttons: **one or two words**. `Save`, `Ask AI`, `Pin`, `Edit`, `Undo`.
- Status: **under 6 words**. `Saved · Undo`, `Waiting for network`, `Can't read this screen`.
- No explanations in the panel. Explanations live in the setup card only.

---

## 6. Notebook screen

Open from the panel's **›**, from Plan → Notes, or from Profile → Capture → **Notebook**. Two tabs.

```
← Notebook                          🔍  ⋯
   ( Board )   ( Chats )
```

### 6.1 Board (entries, like keyboard clipboard)

```
 📌 Pinned                          Edit
 ┌───────────────┐ ┌───────────────┐
 │ QX8-22K       │ │ 12 Baker St,  │
 │ booking ref   │ │ London NW1    │
 │ Gmail · 2d    │ │ Maps · 1w     │
 └───────────────┘ └───────────────┘
 Recent
 ┌───────────────┐ ┌───────────────┐
 │ Shinjuku Gyoen│ │ Summarize ▸   │
 │ opens 9:00 …  │ │ ✦ Open 9–16:30│
 │ Chrome · now  │ │ AI · now      │
 └───────────────┘ └───────────────┘
```

| Gesture | Action |
|---|---|
| **Tap** | Opens the editor (in the screen). Inserts into the input (in the panel). |
| **Hold** | Menu: **Pin · Edit · Copy · Add to note · Ask AI · Delete** |
| **Drag** (pinned only) | Reorder |
| **Edit** (top right) | Multi-select: Pin, Combine into one note, Delete |

- Grid: 2 columns on compact, 3 on medium, 4 on expanded. Tiles show 4 lines max.
- **Pinned never expire.** Recent follow the v1 retention setting.
- Filters under search: `All · Screen · Typed · Voice · AI` (from `sourceKind`).

**Editor**

```
← Entry                         📌  ⋯
┌──────────────────────────────────────┐
│ Shinjuku Gyoen opens 9:00, closed    │  ← editable
│ Mondays. Entry ¥500.                 │
└──────────────────────────────────────┘
 Chrome · 14:02 · Japan trip       Original ›
 [ Ask AI ]  [ Add to note ]  [ Copy ]
```

- Edits save as you type (debounced 500 ms).
- **Original ›** shows the captured text. Edits never overwrite it (rule 5): `editedText` holds your version.
- Filed records (event, task…) show as a chip: `Filed as event ›`.

### 6.2 Chats (stored threads)

```
 Today
 ✦ Shinjuku hours            Chrome · 14:02
   Open 9–16:30, closed Mon…
 ✦ Reply to Sam              Messages · 11:20
 Yesterday
 ✦ Translate menu            Camera · 19:05
```

- One thread per Ask session. Title = AI's 2–5 word title, editable.
- Open a thread → the same compose box as the panel, to continue.
- Each AI reply has **Pin · Copy · Save as entry**. Pinning an answer creates an entry linked to that message.
- If the AI proposes an action (create task, add event), it shows as the existing confirm card with its risk tier. **Nothing is done until the executor confirms** (rules 2 and 3).
- Swipe a thread left = delete (soft, with Undo).

---

## 7. Voice

The panel opens **listening** when called by the gesture. Speech goes into the input box. The first word can be a command:

| You say | Result |
|---|---|
| "**Save** … " | Text after the word + selection → Save |
| "**Ask** … " / "**Summarize** …" / "**Translate** …" | Picks that prompt chip, sends to AI |
| "**Pin** that" | Pins the last entry or AI answer |
| "**Read** it" | Reads the selection (or the AI answer) aloud with TTS |
| "**Select all**" | Selects every screen block |
| anything else | Stays in the input box for review |

- Matching is plain rules on the first word (`domain/notebook/NotebookVoice.kt`), offline, unit-tested. No AI call to parse commands.
- Silence for 2 s stops listening (same as the Home mic). Tap 🎤 to resume.

---

## 8. AI

```
Ask AI
  └─ save entry (Room)  ─► sensitive check ─► chat_messages(user, queued)
       └─ NotebookAskWorker (WorkManager, network)
            └─ ai-proxy fast model → { answer, title, actions? }
                 ├─ answer  → chat_messages(assistant, done)
                 └─ actions → PlanParser → Risk → confirm card → Executor
```

- Offline: the message waits with `Waiting for network`. It sends when online (rule 9).
- Output shape, validated like `ClipJson`:

```json
{"title":"2-5 words","answer":"text","actions":[ /* ActionPlan items, may be empty */ ]}
```

- System prompt adds: *"Use only the given text. If something is not in it, say you don't know."* (rule 4).
- Limits: 2,000 chars of selection, 2,000 chars of your text, last 6 messages. Daily cap shared with v1 (100 calls).

---

## 9. Data

### 9.1 Room v20 → v21 (`Migration20To21`)

**`clips`: new columns** (an entry *is* a clip, rule 1: no new table for entries)

| Column | Type | Why |
|---|---|---|
| `editedText` | TEXT NULL | Your edit. Display = `editedText ?: text`. Original stays in `text`. |
| `pinned` | INTEGER 0/1 | Board pin |
| `pinOrder` | INTEGER NULL | Drag order of pins |
| `prompt` | TEXT NULL | The prompt used, if any |
| `windowTitle` | TEXT NULL | Screen the text came from, ≤ 200 |
| `threadId` | TEXT NULL | Thread it started, if Ask AI |
| `messageId` | TEXT NULL | AI message it came from, if pinned from a chat |

`sourceKind` gains: `screen`, `ocr`, `typed`, `voice`, `ai`.

**New: `chat_threads`**

| Column | Type |
|---|---|
| `id` | TEXT PK (UUID) |
| `remoteId`, `userId` | TEXT NULL |
| `title` | TEXT |
| `origin` | TEXT: `notebook` (Home chat can adopt it later) |
| `sourceApp` | TEXT NULL |
| `createdAt`, `updatedAt`, `deletedAt` | INTEGER |

**New: `chat_messages`**

| Column | Type |
|---|---|
| `id` | TEXT PK (UUID) |
| `remoteId`, `userId` | TEXT NULL |
| `threadId` | TEXT, index |
| `role` | `user` \| `assistant` |
| `text` | TEXT (≤ 20,000) |
| `prompt` | TEXT NULL |
| `clipIds` | TEXT NULL (JSON array, the context sent) |
| `state` | `queued` \| `sent` \| `done` \| `failed` |
| `actionsJson` | TEXT NULL (proposed plan, all `inferred`) |
| `createdAt`, `updatedAt`, `deletedAt` | INTEGER |

Why not reuse `notes` for chats: a note is one editable body; a chat is ordered turns with state. Why not a notebook-only table: `chat_*` is generic so Home chat can store into it later, keeping one brain.

### 9.2 Supabase (`supabase/planned/migrations/021_notebook.sql`)

```sql
alter table public.clips
  add column if not exists edited_text text check (char_length(edited_text) <= 10000),
  add column if not exists pinned boolean not null default false,
  add column if not exists pin_order int,
  add column if not exists prompt text check (char_length(prompt) <= 2000),
  add column if not exists window_title text check (char_length(window_title) <= 200),
  add column if not exists thread_id uuid,
  add column if not exists message_id uuid;
alter table public.clips drop constraint if exists clips_source_kind_check;
alter table public.clips add constraint clips_source_kind_check check (source_kind in
  ('selection','share','clipboard','tile','bubble','screen','ocr','typed','voice','ai'));

create table if not exists public.chat_threads (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title text not null check (char_length(title) between 1 and 120),
  origin text not null default 'notebook' check (origin in ('notebook','home')),
  source_app text check (char_length(source_app) <= 200),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  thread_id uuid not null references public.chat_threads(id) on delete cascade,
  role text not null check (role in ('user','assistant')),
  text text not null check (char_length(text) between 1 and 20000),
  prompt text check (char_length(prompt) <= 2000),
  clip_ids jsonb,
  state text not null default 'done' check (state in ('queued','sent','done','failed')),
  actions_json jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
alter table public.chat_threads  enable row level security;
alter table public.chat_messages enable row level security;
create policy chat_threads_owner  on public.chat_threads
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy chat_messages_owner on public.chat_messages
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create index chat_threads_user_updated  on public.chat_threads  (user_id, updated_at);
create index chat_messages_thread_time  on public.chat_messages (thread_id, created_at);
```

- Sync: `data/sync/extra/ChatExtraSync.kt`, same `updated_at` cursor and soft delete as `ClipExtraSync`.
- **Sensitive clips and the messages that used them are not synced** (filtered in both handlers).
- Retention: chats default **Forever**. Setting: 30 days · 1 year · Forever. Pinned entries and threads with a pinned answer are kept.

---

## 10. Privacy summary

| Data | Stored? | Leaves the phone? |
|---|---|---|
| Screen text, screenshot | No (memory only) | No |
| Selected words you Save | Yes, as a clip | Synced, unless sensitive |
| Text sent to AI | Yes, as a message | To `ai-proxy`, then synced |
| Sensitive matches | Only with "Save anyway" | Never |

Owner-only RLS. No household sharing in v2 (rule 6).

---

## 11. Code layout

| Path | What |
|---|---|
| `service/AssistantServices.kt` | `LedgerSession`: `onHandleAssist`, `onHandleScreenshot` → start panel with blocks |
| `service/NotebookPanelActivity.kt` | Hosts the panel. Replaces the sheet in `ClipCaptureActivity` for gesture launches; v1 entry points keep working |
| `domain/notebook/ScreenBlocks.kt` | Pure: `ViewNode` list → ordered blocks, drops passwords (unit-tested) |
| `domain/notebook/NotebookVoice.kt` | Pure: first-word commands (unit-tested) |
| `domain/notebook/NotebookPrompts.kt` + `NotebookJson` | Prompt + validator (unit-tested) |
| `data/notebook/ScreenReader.kt` | Assist structure first, `TextRecognition` fallback |
| `data/chat/ChatEntities.kt`, `ChatDao.kt`, `ChatRepository.kt` | Threads and messages |
| `data/clips/ClipRepository.kt` | + pin, reorder, edit, `editedText` |
| `worker/NotebookAskWorker.kt` | AI send, offline queue |
| `data/sync/extra/ChatExtraSync.kt` | Sync |
| `presentation/screens/notebook/NotebookPanel.kt` | Panel, compact / medium / expanded |
| `presentation/screens/notebook/NotebookScreen.kt` + ViewModel | Board + Chats tabs, editor |

`AddToNotesScreen` becomes the Board tab. No new top-level layers.

---

## 12. Phases and checks

**Phase 1: Call, read, save** (prerequisites exist: assistant service, clips, ML Kit)
1. `ScreenBlocks` → verify: unit test with a fake node tree incl. a password field (dropped) and out-of-order nodes (sorted).
2. `LedgerSession` reads assist data → panel → verify on device: long-press power in Chrome, blocks appear in reading order.
3. Panel: chips, input, prompt row, Save → verify: pick 2 blocks + type a line → one entry with `sourceKind = 'screen'`.
4. `Migration20To21` (clip columns only) → verify: migration test like `Migration19To20Test`.
5. Board + editor + pin → verify: edit an entry, **Original** still shows the captured text; pinned stays after retention runs.
6. Layouts → verify: compact, landscape and a 840 dp emulator; no clipped buttons at 200 % font.
- Exit: from a banking app (FLAG_SECURE) the panel says `Can't read this screen`; from Chrome, two words + a note are saved in 3 taps.

**Phase 2: Ask and chats**
1. `chat_threads` / `chat_messages` in Room v21 → verify: DAO tests.
2. `NotebookPrompts` + `NotebookJson` + 20 golden cases → verify: no invented facts in answers to "what's the price?" on text with no price.
3. `NotebookAskWorker` via `ai-proxy` → verify: offline Ask shows `Waiting for network`, sends on reconnect.
4. Action proposals → confirm card → `Executor` → verify: "add this as an event" creates nothing until tapped.
5. `021_notebook.sql` planned + `ChatExtraSync` → verify: RLS test with two users; sensitive message absent on server.
- Exit: ask from a screen, close the app, reopen Notebook → Chats: the thread and answer are there, on a second device too.

**Phase 3: Voice and OCR**
1. `NotebookVoice` → verify: unit tests for each command word and a non-command sentence.
2. OCR fallback → verify: a game or image-only screen yields blocks.
3. Read aloud (TTS) → verify: "read it" speaks the selection.
- Exit: hold power, say "summarize this", release → answer appears and is stored, hands-free.

---

## 13. Decisions needed

| # | Decision | Proposed |
|---|---|---|
| D1 | Allow **user-triggered** screen reading via the assistant (not background) | **Yes.** Update `privacy-security.md` and the roadmap risk row when Phase 1 starts. |
| D2 | Rename "Add to Notes" → **"Add to Notebook"** in the text menu, tile and Profile | Yes (strings only) |
| D3 | Gesture opens Notebook panel instead of the voice capture sheet | Yes, with a **"Log it"** chip in the panel that hands off to the current voice pipeline, so expense logging by gesture still works |
| D4 | "Read the screens" = read the screen's **text into the panel** (and optionally aloud) | Both: text by default, aloud on "read it" |

---

## 14. Edge cases

| Case | Behaviour |
|---|---|
| Assistant not set as default | Gesture opens Google's assistant. Panel opened another way shows the setup card. |
| "Use text from screen" off | Structure is null → OCR if screenshot allowed → else `Can't read this screen` |
| Very long page | Blocks capped at 200; `▾ more` loads the rest from memory |
| WebView / Compose apps | Text comes through the structure; if empty, OCR |
| Lock screen | Assist data is empty; panel opens with input only |
| Same entry saved twice in 10 min | v1 dedupe by `textHash` |
| AI fails | Message `failed` with **Retry**. The entry is kept. |
| Delete a thread with a pinned answer | Asks: "Keep the pinned entry?" |

---

## 15. Docs to update when building
- `roadmap.md`: "Notebook: screen read on call, board, stored chats" under *Capture engine*; edit the screen-access risk row (D1).
- `privacy-security.md`: user-triggered screen reading rules (§4.2).
- `data-model.md`: `chat_threads`, `chat_messages`; new `clips` columns.
- `CHANGELOG.md`: one entry per phase.
