# Lifeline: Life OS plan

Source brief: [../full-plan.md](../full-plan.md). This folder turns it into an executable plan for **this** repo (the existing LedgerAI Android app).

| Doc | What it answers |
|---|---|
| [architecture.md](architecture.md) | Layers, modules, the assistant pipeline, where each piece lives in the codebase |
| [data-model.md](data-model.md) | Shared entities, what is reused from today's schema, what is new |
| [assistant-pipeline.md](assistant-pipeline.md) | Capture → understand → retrieve → propose → confirm → execute, action schema, risk tiers |
| [privacy-security.md](privacy-security.md) | Personal vs household data, RLS, encryption, memory review |
| [ui-design.md](ui-design.md) | Information architecture, home screen, navigation, screen inventory |
| [roadmap.md](roadmap.md) | Stages, milestones, acceptance checks, risks |
| [adr/0001-evolve-existing-android.md](adr/0001-evolve-existing-android.md) | Why we extend the Kotlin app instead of moving to Expo/Next/Nest |

## One-paragraph summary

A single data layer (people, items, money, events, memory, documents) with one universal assistant on top. Any input (voice, text, photo, document, shared screen, authorized event) goes through the same pipeline and yields a typed action plan. A risk-aware executor applies it and updates every affected module. Build order: shared layer and assistant first, then Buy Never Twice, then households and money, then calendar/people/career, then proactive automation.

## Where this fits what already exists

Already built (see `README.md`, `PLAN.md`): voice capture and offline STT, multi-intent routing, finance, calendar events (tasks/reminders/alarms merged), notes, job applications, life log, habits, plans, quotes, widget, Supabase sync, `ai-proxy` Edge Function. Lifeline reuses these and adds Home/Inventory, Households, Receipts, People, Wardrobe, Subscriptions, Memory and the action/audit layer.
