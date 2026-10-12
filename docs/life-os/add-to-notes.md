# Add to Notes (text capture from anywhere)

Status: **design, not built** · Author date: 2026-10-12 · Stage: Capture engine (cross-stage), text half

## 1. Summary

Select or copy text in any app, tap **Add to Notes**, and the text lands in Lifeline as a *clip*. Clips can be gathered into one note, kept as plain notes, or handed to the AI, which suggests where each clip belongs (task, event, job, shopping item, memory, bill). Profile gets an **Add to Notes** screen that holds the clip list, the "collecting into" note and the settings.

Why: today text from other apps only reaches the app through Share, and Share always assumes it is a job (`JobShareActivity`). Most of what people copy (an address, a recipe step, a confirmation number, a quote, a link) has nowhere to go. This feature gives every piece of copied text one door in, and lets the existing pipeline file it.

## 2. Goals and non-goals

Goals
1. Capture text from any app in **one tap from the selection menu**, with no app switch.
2. **Many clips into one note** ("collecting" mode) as well as one clip per note.
3. Every clip can go to **Notes** (keep as written) or to **AI** (suggest a category and an action), chosen by hand or by a setting.
4. Clips keep their source (app, time, link) so anything filed from them can be traced and corrected (rule 5).
5. Works offline. AI work queues and runs when the network is back (rule 7, rule 9).

Non-goals (v1)
- **Silent background clipboard history.** Not possible on Android 10+ without being the keyboard or abusing an AccessibilityService. Both are out: the roadmap rules out background screen reading, and Play policy restricts accessibility use. See section 4.
- Images or files from the clipboard. Shared images already go to `QuickCaptureActivity`.
- Household sharing of clips. Clips are private, like notes.
- Fetching link previews. That contacts third-party sites; it's left as an opt-in later idea (section 13).

## 3. Assumptions and open questions

Assumptions made in this spec. Correct any that are wrong before work starts.

| # | Assumption | If wrong |
|---|---|---|
| A1 | "Like copy, select all" means a menu item in the system text-selection toolbar. | If you meant a floating bubble on screen, that's Phase 3 (section 6.5). It still can't see copies on its own. |
| A2 | "Multinotes on the same note" means several clips appended to one note. | If you meant splitting one long clip into several notes, that's covered by AI "Split" (section 8.4). |
| A3 | "Add to AI" means: let the AI categorize and propose filing. It does not mean "start a chat". | A "Ask about this" chat shortcut is cheap to add (section 7.4). |
| A4 | "Profile" is the **Settings / You** screen opened from the avatar (`SettingsScreen.kt`). | |

Decisions (2026-10-12)
- D1. **AI filing is on by default, in Suggest mode.** Every clip saved through the sheet gets an AI suggestion when online. Nothing is filed until the user taps. "Auto for simple things" stays opt-in, and it still only applies Low-risk actions (rule 3).
- D2. **Rename the job share target to "Save as job".** "Add to Notes" becomes the general text share target (section 6.2).

## 4. Platform reality (why the design looks like this)

| Approach | Works on Android 10+? | Verdict |
|---|---|---|
| Listen to every copy in the background (`OnPrimaryClipChangedListener`) | No. Only the focused app or the default keyboard receives clipboard contents. | Rejected |
| AccessibilityService that watches copies | Technically, but Play restricts it to accessibility use, and it reads the whole screen. | Rejected (privacy, policy, roadmap risk table) |
| Custom keyboard (IME) | Yes, but replacing the user's keyboard is a large product of its own. | Rejected |
| **Text-selection menu (`ACTION_PROCESS_TEXT`)** | Yes, API 23+. Shows next to Copy / Select all / Share in Chrome, Messages, Gmail and most apps. | **Primary** |
| **Share sheet (`ACTION_SEND` text/plain)** | Yes. | **Secondary** |
| **Read clipboard when our own window has focus** (app open, tile, notification tap, bubble tap) | Yes, if read in `onWindowFocusChanged(true)`, not `onCreate`. Android 12+ shows a "pasted from clipboard" toast. | **Supporting** |

The rule: **the user always makes one deliberate gesture per capture.** The app never reads the clipboard unless the user asked it to.

## 5. Concepts

- **Clip**: one captured piece of text plus where it came from. It is a *source* record, like `voice_history` or `media_assets`.
- **Note**: the existing `notes` row. A note can hold many clips.
- **Collecting note**: an optional note that every new clip appends to until you stop collecting. Example: start collecting "Japan trip", copy six things from six apps, and they all end up in that note.
- **Clip inbox**: clips that are not in any note yet (collecting is off and you chose "Save for later").
- **Filing**: turning a clip into a structured record (task, event, job…) through the `Executor`. The clip stays; the new record links back to it.

## 6. Entry points

### 6.1 Selection menu: "Add to Notes" (Phase 1)

New activity `service/ClipCaptureActivity`, translucent, `excludeFromRecents`, `taskAffinity=""` (same pattern as `QuickCaptureActivity`).

```xml
<activity
    android:name=".service.ClipCaptureActivity"
    android:exported="true"
    android:label="@string/add_to_notes"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:windowSoftInputMode="adjustResize">
    <intent-filter>
        <action android:name="android.intent.action.PROCESS_TEXT" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
    <intent-filter android:label="@string/add_to_notes">
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

- Read `Intent.EXTRA_PROCESS_TEXT` (a `CharSequence`). Convert to plain text; drop spans.
- **Never return text** (`setResult` is not called). The source app's text is never modified, even when `EXTRA_PROCESS_TEXT_READONLY` is false.
- Source app: `callingPackage` is usually null for `PROCESS_TEXT`, so try `referrer` and fall back to "Unknown app". Store the package name and resolve the label for display.
- `@string/add_to_notes` = "Add to Notes". Keep it short, because the toolbar truncates long labels into the overflow menu.

### 6.2 Share sheet (Phase 1)

The same activity handles `ACTION_SEND` text/plain, reading `EXTRA_TEXT` and `EXTRA_SUBJECT`.

Conflict: `JobShareActivity` already takes text/plain shares and shows the app name. Decision D2: give `JobShareActivity` the label **"Save as job"** (a label change only, no behaviour change) so the share sheet shows two clear targets. Also, when the rules see a job listing in a clip (`JobPasteParser.needsHelp == false`), the clip sheet offers **"Looks like a job · Track it"**, so both doors reach the job tracker.

### 6.3 "Paste what I copied" chip (Phase 1)

When the app or the Notes screen gains focus:
1. Read only `ClipboardManager.primaryClipDescription`. Reading the description does not show the Android 12 toast. Note: this behaviour should be checked on a real device in Phase 1.
2. If `description.timestamp` is newer than the last one we handled and the mime type is text, show a chip at the top of Notes and Add to Notes: **"Add what you copied"**.
3. Read `primaryClip` only when the chip is tapped.

There is a setting to turn the chip off.

### 6.4 Quick Settings tile and notification (Phase 2)

- **Tile "Add copied text"**: a second `TileService`. Tapping it opens `ClipCaptureActivity` in `mode=clipboard`. The activity reads the clipboard on `onWindowFocusChanged(true)`, saves it, shows the confirmation strip and closes. Total: copy, pull down the shade, tap.
- **Optional pinned notification** "Add to Notes · Tap to save what you copied". Its `PendingIntent.getActivity` goes straight to the activity, because Android 12 blocks notification trampolines. It's off by default and needs no foreground service.

### 6.5 Floating bubble (Phase 3, experimental)

This is the literal "on screen something". It needs `SYSTEM_ALERT_WINDOW` ("Display over other apps"). A small draggable dot sits at the screen edge, and tapping it does the same as the tile. **The bubble cannot detect copies on its own.** It never changes colour on its own, so it never implies it can see the clipboard. It ships behind an "Experimental" toggle and only if Phase 2 usage shows people want it.

## 7. UX

### 7.1 Clip sheet (shown after "Add to Notes")

A translucent bottom sheet over the source app, about 45% of the height.

```
┌──────────────────────────────────────────┐
│  Add to Notes                 from Chrome│
│ ┌──────────────────────────────────────┐ │
│ │ Shinjuku Gyoen opens 9:00, closed    │ │
│ │ Mondays. Entry ¥500.                 │ │
│ │                          Show all ▾  │ │
│ └──────────────────────────────────────┘ │
│  Add to                                  │
│  (● Japan trip · collecting) ( New note )│
│  ( Groceries ) ( Ideas ) ( Save for later)│
│                                          │
│  ✦ Looks like: place · opening hours     │  ← rules hint, only when a rule fired
│                                          │
│  [ Ask AI to file it ]        [  Save  ] │
└──────────────────────────────────────────┘
```

- Target chips: the collecting note (if set) is selected first, then **New note**, then the 3 most recently edited notes, then **Save for later** (the inbox).
- **Save** writes locally and closes. A confirmation strip shows for 3 s: *"Added to Japan trip · Undo"*. Undo soft-deletes the clip and removes its block from the note.
- **Ask AI to file it** saves the clip, then queues the AI pass (section 8). The strip says *"Saved · AI will suggest where it goes"*.
- Because Suggest is the default (D1), **Save** also queues the AI pass when a rule didn't already decide the kind. The difference is only in what the strip says: Save → *"Added to Japan trip"*, and the suggestion appears on the clip card later. Turning AI filing **Off** makes Save purely manual.
- **Fast mode** setting: skip the sheet. Tapping "Add to Notes" saves straight to the default target and shows only the strip with Undo.
- With **New note**, the title is the first line (max 60 chars), or the `NoteRules.suggestTags` top tag plus the date when the first line is a URL.

### 7.2 Profile → "Add to Notes" row

In `SettingsScreen`, add a new section **"Capture"** between *Preferences* and *Shortcuts*:

```
Capture
 ┌──────────────────────────────────────────┐
 │ 📋  Add to Notes                       › │
 │     Collecting into Japan trip · 3 new   │
 └──────────────────────────────────────────┘
```

The subtitle states the current mode: "Off", "On · 3 new clips", or "Collecting into X · 3 new". Tapping opens the **Add to Notes screen**. It is a full screen, not an `LSheet`, because it holds a list.

### 7.3 Add to Notes screen

```
← Add to Notes                              (avatar)
┌──────────────────────────────────────────────────┐
│ Collecting into                                  │
│ Japan trip · 6 clips              [Stop] [Change]│
└──────────────────────────────────────────────────┘
[ Add what you copied ]          ← chip from 6.3, only when there's a new clip

 Search clips…                              🎤

 New (3)        In notes        Filed        All     ← segment chips

 Today
 ┌──────────────────────────────────────────────┐
 │ Shinjuku Gyoen opens 9:00, closed Mondays…   │
 │ Chrome · 14:02 · Japan trip                  │
 │ ✦ Suggest: Add event "Shinjuku Gyoen"  [Add] │  ← AI suggestion, one tap (medium risk)
 └──────────────────────────────────────────────┘
 ┌──────────────────────────────────────────────┐
 │ Booking ref QX8-22K, check-in 15:00 12 Nov   │
 │ Gmail · 13:40 · inbox                        │
 │ [Add to note]  [Ask AI]  [⋯]                 │
 └──────────────────────────────────────────────┘
 Yesterday …

 ─────────────── Settings ───────────────
 Show "Add to Notes" in the text menu      [on]
 Skip the sheet (fast mode)                [off]
 Offer to add what I copied                [on]
 AI filing          (Off) (● Suggest) (Auto for simple things)
 Quick Settings tile                    How to add ›
 Pinned notification                       [off]
 Keep clips for     (30 days) (● 1 year) (Forever)
```

Interactions
- **Tap a clip** opens it in full with Copy, Edit text, Open link, Delete, and "Filed as… ›" when filed.
- **Long press** starts multi-select. The action bar offers **Combine into one note**, **Add to note…**, **Ask AI**, **Delete**. "Combine" covers "many clips → one note" after the fact.
- **Swipe right** adds the clip to the collecting note (or the last used note). **Swipe left** dismisses it. Both offer Undo.
- **Collecting card**: *Start collecting* asks for a note (pick an existing one or name a new one). *Stop* clears it. While collecting, the pinned notification (if on) reads "Collecting into Japan trip".
- Empty state, in the existing "Say: …" style: *"Select text in any app and tap **Add to Notes**."* with a 3-frame illustration of the selection toolbar.

### 7.4 Notes screen changes

- A note built from clips shows each clip as a block with a small **source chip** (`Chrome · 14:02`). Tapping the chip opens the clip. The note text itself stays editable as before.
- Note overflow menu: **Collect into this note**, **Ask AI to sort this note**. The second runs the AI pass over each clip in the note.
- Optional (A3): **Ask about this** opens Chat with the note as context.

### 7.5 Accessibility and look
- Uses existing `L.*` tokens, `LScreen`, `LGroupRow`, `LChip` and `LSheet`. No new colours. The AI hint uses the single amber highlight.
- Every action has a `contentDescription`. Swipe actions are also in the ⋯ menu.
- The sheet works with the keyboard open (`adjustResize`) and in landscape.

## 8. AI filing

### 8.1 Flow

```
clip saved (Room) ─► local rules ─► (AI mode != Off; default Suggest; skipped if sensitive or a rule decided)
                                     ─► ClipFileWorker (WorkManager, network)
                                        ─► ai-proxy fast_completion → typed ActionPlan
                                           ─► validate ─► Risk check ─► Executor
                                                                  ├─ low + Auto: apply, log, Undo
                                                                  └─ else: suggestion on the clip card
```

This follows rule 2: the model proposes, only the `Executor` writes. AI calls go through `ai-proxy`, like everything else since the Hong Kong fix.

### 8.2 Rules first (free, offline)

These run on save, before any AI call. They reuse what exists where possible.

| Detector | Source | Result on the clip |
|---|---|---|
| Job listing | `JobPasteParser` | Hint "Looks like a job" + Track it |
| Date / time | `DateRules` | Hint "Has a date: 12 Nov 15:00" |
| To-do sentence | `NoteRules.actionItems` | Hint "Looks like a task" |
| Tags | `NoteRules.suggestTags` | Pre-filled tags on the note |
| URL, email, phone | regex | Open / Call / Email buttons |
| Amount + currency | existing amount parsing in `QuickParse` | Hint only. Money is high risk, so never auto-filed. |
| **Sensitive** | see 9.1 | Blocked or masked, never sent to AI |

When a rule already gives a confident kind, the AI is not called unless the user taps **Ask AI**.

### 8.3 Categories and risk

| AI kind | Executor action | Risk (rule 3) | Auto allowed? |
|---|---|---|---|
| `note` | append to note / set tags | Low | Yes |
| `shopping` | add shopping item | Low | Yes |
| `task` | create task | Medium | No, one tap |
| `event` | create calendar event | Medium | No, one tap |
| `job` | create job application | Medium | No, one tap |
| `memory` | create `memories` row (Stage 3) | Medium | No, one tap |
| `place` | save to note with map link | Low | Yes |
| `bill` / `transaction` / `debt` | draft only | **High** | Never. Explicit confirm screen. |
| `other` | stay in Notes | — | — |

"Auto for simple things" only ever applies **Low** actions. Everything else becomes a suggestion row on the clip card.

### 8.4 Prompt and output (sketch, in the style of `CapturePrompts`)

```
You sort text a user copied on their phone for a personal life-organizer app.
Reply with ONLY one JSON object:
{"kind":"note|shopping|task|event|job|memory|place|bill|other",
 "title":"2-6 words",
 "tags":["..."],
 "split":[ "...", "..." ],          // only if the text holds several unrelated items, else []
 "fields":{"date":null,"time":null,"place":null,"amount":null,"currency":null,
           "company":null,"role":null,"person":null,"item":null},
 "confidence":"high|low"}
Rules:
- Never guess. A date, amount, name or place that is not written in the text is null.
- "person" comes only from the text, never invented.
- Dates are YYYY-MM-DD, times HH:mm.
```

- Input: the clip text (capped at 2,000 chars), source app label, today's date. **No other personal data** in v1. `ContextRetriever` can add known merchants and people later.
- Parsed with a validator like `CaptureJson`. Unknown kind → `other`.
- Every value the model returns is stored as `inferred`. A value becomes `confirmed` when the user taps Add or edits it (rule 4).
- **Split**: when `split` has two or more items, the card offers *"Split into 3 notes"*. This is the second reading of "multinotes".

### 8.5 Cost and limits
- One fast-model call per clip. With Suggest on by default this means most clips are sent once, so the rules-first step and batching below matter.
- Batch: "Ask AI" on a multi-select sends up to 10 clips in one call.
- Daily cap of 100 AI clip calls per user. Over the cap, clips stay as notes and the card says so.

## 9. Privacy and safety

### 9.1 Sensitive text
- If the clip's `ClipDescription` extras carry `android.content.extra.IS_SENSITIVE` (set by password managers; constant `ClipDescription.EXTRA_IS_SENSITIVE` on API 33+): **do not save**. Show "Looks like a password, not saved."
- Text matching a card number (passes the Luhn check), a one-time code ("code is 482913", 4–8 digits next to OTP words), an IBAN, or a private key header: save only if the user taps **Save anyway**, and **never send to AI**. The clip is marked `sensitive = true`.
- Clip previews are never shown in the pinned notification.

### 9.2 Data
- Clips are private (`user_id` only), with no household column in v1 (rule 6). RLS: owner only.
- Clips sync like notes. Local-only accounts keep them on the phone.
- Retention setting (30 days / 1 year / forever) is enforced by a daily `ClipCleanupWorker`. It soft-deletes **inbox** clips older than the limit. Clips inside notes or linked to filed records are kept, so sources don't disappear (rule 5).
- Deleting a clip that something was filed from asks first: "This clip is the source of 1 event. Delete the clip only, or both?"

## 10. Data model

### 10.1 Room (v19 → v20, `Migration19To20`)

```kotlin
@Entity(
    tableName = "clips",
    indices = [Index("noteId"), Index("status"), Index("remoteId"), Index("updatedAt"), Index("textHash")]
)
data class ClipEntity(
    @PrimaryKey val id: String,              // UUID, like media_assets
    val remoteId: String? = null,
    val userId: String? = null,
    val text: String,                        // plain text, max 10_000 chars
    val textHash: String,                    // sha-256 of trimmed text, for dedupe
    val sourceKind: String,                  // selection | share | clipboard | tile | bubble
    val sourceApp: String? = null,           // package name
    val url: String? = null,                 // first URL in text, or EXTRA_SUBJECT link
    val noteId: Long? = null,                // note this clip is in, if any
    val status: String = "inbox",            // inbox | in_note | filed | dismissed
    val sensitive: Boolean = false,
    val aiState: String = "none",            // none | queued | done | failed | skipped
    val aiKind: String? = null,
    val aiJson: String? = null,              // validated suggestion, all values inferred
    val filedType: String? = null,           // task | calendar_event | job | item | memory …
    val filedId: String? = null,
    val capturedAt: Long,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)
```

- **Dedupe:** the same `textHash` captured within 10 minutes bumps `capturedAt` instead of inserting a new row.
- **Note body:** adding a clip to a note appends a block to `notes.body` (a blank line, then the clip text) and sets `clips.noteId`. The note stays the readable, editable record. The clip stays the source. Editing the note does not change the clip.
- **Filed records:** created through the `Executor` with `source_type = 'clip'` and `source_id = clips.id` where the target table has those columns (the planned Stage 1/3 tables do). For older tables without them (`tasks`, `calendar_events`), the link lives on the clip (`filedType`, `filedId`) until those tables gain provenance columns.
- **Collecting note id** and settings live in DataStore with the other preferences, not in Room.
- **Alternative considered:** a generic `captures` table shared with `media_assets`. Rejected for now because `media_assets` is photo- and upload-specific. Revisit if a third input type appears.

### 10.2 Supabase (`supabase/planned/migrations/020_clips.sql`)

```sql
create table if not exists public.clips (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  text text not null check (char_length(text) between 1 and 10000),
  text_hash text not null check (char_length(text_hash) = 64),
  source_kind text not null check (source_kind in ('selection','share','clipboard','tile','bubble')),
  source_app text check (char_length(source_app) <= 200),
  url text check (char_length(url) <= 2000),
  note_id uuid references public.notes(id) on delete set null,
  status text not null default 'inbox' check (status in ('inbox','in_note','filed','dismissed')),
  sensitive boolean not null default false,
  ai_kind text check (char_length(ai_kind) <= 20),
  ai_json jsonb,
  filed_type text check (char_length(filed_type) <= 40),
  filed_id text check (char_length(filed_id) <= 64),
  captured_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
alter table public.clips enable row level security;
create policy clips_owner on public.clips
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create index clips_user_updated on public.clips (user_id, updated_at);
```

`notes.id` is a uuid on the server (`002_tasks_reminders_alarms_notes.sql`), while Room uses a local `Long`. The sync handler maps `clips.noteId` to the note's `remoteId`, the same way other rows link to notes. **Sensitive clips are not synced** (filtered in the sync handler), so they never leave the phone.

Sync: `data/sync/extra/ClipExtraSync.kt`, following `MediaAssetExtraSync` (`updated_at` cursor, soft delete).

## 11. Code layout

| Path | What |
|---|---|
| `service/ClipCaptureActivity.kt` | PROCESS_TEXT / SEND / clipboard modes, hosts the clip sheet |
| `service/ClipTileService.kt` | Phase 2 tile |
| `data/clips/ClipEntities.kt`, `ClipDao.kt` | Room |
| `data/clips/ClipRepository.kt` | save, dedupe, add to note, combine, retention |
| `domain/clips/ClipRules.kt` | pure detectors: sensitive, URL, phone, kind hints (unit-tested) |
| `domain/clips/ClipPrompts.kt` + `ClipJson` | prompt + validator (unit-tested, like `CaptureJsonTest`) |
| `data/clips/ClipActions.kt` | actions registered with the `ActionRegistry`, with risk tiers |
| `worker/ClipFileWorker.kt`, `worker/ClipCleanupWorker.kt` | AI pass, retention |
| `presentation/screens/clips/AddToNotesScreen.kt` + ViewModel | the list screen |
| `presentation/screens/clips/ClipSheet.kt` | the sheet |
| `presentation/screens/settings/SettingsScreen.kt` | new "Capture" section row |
| `presentation/screens/notes/NotesScreen.kt` | clip blocks, "collect into this note", copied-text chip |

No new top-level layers, per the conventions in CLAUDE.md.

## 12. Phases and checks

**Phase 1: Capture and notes (no AI).** Prerequisites already exist (notes, Room, sync pattern).
1. `clips` entity + `Migration19To20` → verify: migration test like `Migration17To18`, open v19 DB, upgrade, insert a clip.
2. `ClipRules` sensitive/URL detectors → verify: unit tests with a card number, an OTP SMS, a password-manager flag, a normal sentence.
3. `ClipCaptureActivity` for PROCESS_TEXT + SEND, plus the clip sheet → verify on device: select text in Chrome, Messages and Gmail, see "Add to Notes" in the menu, save, see it in the note.
4. Collecting note + Combine + Undo → verify: collect 3 clips from 3 apps into one note, undo the last one, and the note body and clip rows agree.
5. Profile row + Add to Notes screen → verify: the row subtitle updates as clips arrive.
6. "Add what you copied" chip → verify on Android 12+ that showing the chip does **not** trigger the system clipboard toast, and that tapping it does.
7. Label `JobShareActivity` "Save as job" → verify: the share sheet shows both targets.
- Exit: from three different apps, three clips end up in one note, each with its source chip, fully offline.

**Phase 2: AI filing, tile, notification.** Needs the `Executor` path already used by capture.
1. `ClipPrompts`/`ClipJson` + golden set of 30 clips (dates, jobs, addresses, recipes, bills, OTPs) → verify: validator tests; no invented fields in golden outputs.
2. `ClipFileWorker` through `ai-proxy` + `Executor` → verify: Ask AI on an event-like clip shows "Add event", one tap creates it, and the event links back to the clip.
3. Auto mode applies low risk only → verify: a test that a bill clip in Auto mode creates no transaction.
4. Tile + pinned notification → verify on device: copy, tap the tile, the clip is saved.
5. Sync handler + promote `020_clips.sql` → verify: RLS test with two users; sensitive clip absent on the server.
- Exit: copy a booking email line, Ask AI, one tap → calendar event that opens its source clip.

**Phase 3: Bubble, recall, digest (after Stage 3 memory exists).**
- Floating bubble (experimental toggle).
- Clips as assistant context: "what was that restaurant I copied last week?" SQL search first, then `memory_chunks` vectors for fuzzy recall (rule 8).
- Daily digest via WorkManager: "You saved 9 clips today: 2 tasks, 1 event waiting."

## 13. More ways to use it (later ideas, not committed)
- **Research mode**: collecting into a note, then "Summarize this note" using `NoteRules.summarize` offline or the AI online.
- **Shopping from recipes**: copy an ingredient list, and it splits into shopping items checked against the pantry ("you already have onions").
- **People memory**: copying a line from a chat with "Sam's birthday is 3 May" suggests a memory on Sam (Stage 3).
- **Global search** includes clips.
- **Widget**: "3 new clips" with one-tap Ask AI.
- **Link titles**: opt-in, fetched by an Edge Function so the phone doesn't hit arbitrary sites directly.

## 14. Edge cases

| Case | Behaviour |
|---|---|
| Empty or whitespace-only selection | Sheet does not open; toast "Nothing selected" |
| Text over 10,000 chars | Save the first 10,000 and mark the clip "trimmed" |
| Rich text / HTML | Converted to plain text; links kept as URLs |
| Same text again within 10 min | Bumped, not duplicated |
| Collecting note deleted elsewhere | Collecting turns off; clip goes to the inbox with a notice |
| Signed out / local account | Everything works locally; no sync, AI only if the local key path is available |
| Offline + Ask AI | `aiState = queued`; card shows "Waiting for network" (same wording as capture cards) |
| AI returns junk or times out | `aiState = failed`, card offers Retry; the clip is untouched |
| App selection menu missing in some apps | Some apps (games, custom views) don't use the system toolbar; Share or the tile cover them |

## 15. Docs to update when building
- `docs/life-os/roadmap.md`: add "Text capture (Add to Notes)" under *Capture engine*.
- `docs/life-os/data-model.md`: add `clips` under *Notes & log* and `source_type = 'clip'` under Provenance.
- `docs/life-os/privacy-security.md`: sensitive-clip rules.
- `docs/life-os/CHANGELOG.md`: an entry per phase.
