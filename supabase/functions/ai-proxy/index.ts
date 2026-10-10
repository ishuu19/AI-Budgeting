/**
 * LedgerAI AI proxy — Supabase Edge Function (Deno).
 *
 * - Verifies Authorization Bearer JWT (Supabase Auth user) via anon key + getUser()
 * - In-memory per-user rate limit
 * - Calls Gemini (preferred) or OpenRouter with JSON responseSchema
 * - Holds provider keys as Edge secrets only — NO database credentials, NO service_role key
 * - Never performs direct Postgres queries or uses DB client beyond auth.getUser()
 *
 * Provider order: Gemini (free tier) -> OpenRouter FREE model (text only, best effort).
 * Exception: type fast_completion is one OpenRouter call only (no Gemini, no DB).
 * Voice: audio goes to Gemini (transcribe + parse in one call); no cloud backup for audio -
 * the app falls back to offline Vosk on the phone.
 * Secrets: GEMINI_API_KEY, OPENROUTER_API_KEY (optional, free models only; required for fast_completion),
 *          SUPABASE_URL, SUPABASE_ANON_KEY (auth verify only; never service_role).
 * fast_completion model: env AI_MODEL_FAST, or google/gemini-3.1-flash-lite.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

type AiType =
  | "transaction"
  | "task"
  | "alarm"
  | "insight"
  | "note_summary"
  | "chat"
  | "voice_intent"
  | "fast_completion";

/** Types that use the fixed response schemas. fast_completion does not. */
type SchemaType = Exclude<AiType, "fast_completion">;

interface ProxyRequest {
  type: AiType;
  system?: string;
  user?: string;
  /** Optional recorded audio (base64) for type=voice_intent. */
  audio?: { mimeType: string; data: string };
  /** Prefer flash-lite for parse; flash for chat/insights. */
  modelTier?: "flash" | "flash-lite";
}

interface RateBucket {
  count: number;
  windowStart: number;
}

const RATE_LIMIT = 30;
const RATE_WINDOW_MS = 60_000;
const rateBuckets = new Map<string, RateBucket>();

const SCHEMAS: Record<SchemaType, Record<string, unknown>> = {
  transaction: {
    type: "object",
    properties: {
      amount: { type: "number", nullable: true },
      category: {
        type: "string",
        enum: [
          "FOOD",
          "TRANSPORT",
          "SUBSCRIPTIONS",
          "ENTERTAINMENT",
          "SHOPPING",
          "HEALTH",
          "UTILITIES",
          "RENT",
          "SALARY",
          "OTHER",
        ],
      },
      merchant: { type: "string" },
      note: { type: "string" },
      type: { type: "string", enum: ["INCOME", "EXPENSE"] },
      confidence: { type: "number" },
    },
    required: ["category", "merchant", "note", "type", "confidence"],
  },
  task: {
    type: "object",
    properties: {
      title: { type: "string" },
      notes: { type: "string" },
      dueAt: { type: "string", nullable: true },
      reminders: {
        type: "array",
        items: {
          type: "object",
          properties: {
            label: { type: "string" },
            remindAt: { type: "string" },
          },
          required: ["label", "remindAt"],
        },
      },
    },
    required: ["title"],
  },
  alarm: {
    type: "object",
    properties: {
      hour: { type: "integer" },
      minute: { type: "integer" },
      label: { type: "string" },
      enabled: { type: "boolean" },
      repeatDays: { type: "integer" },
    },
    required: ["hour", "minute", "label", "enabled"],
  },
  insight: {
    type: "object",
    properties: {
      title: { type: "string" },
      body: { type: "string" },
      severity: { type: "string", enum: ["info", "watch", "alert"] },
      actions: { type: "array", items: { type: "string" } },
    },
    required: ["title", "body", "severity"],
  },
  note_summary: {
    type: "object",
    properties: {
      summary: { type: "string" },
      tags: { type: "array", items: { type: "string" } },
      highlights: { type: "array", items: { type: "string" } },
    },
    required: ["summary", "tags"],
  },
  chat: {
    type: "object",
    properties: {
      reply: { type: "string" },
    },
    required: ["reply"],
  },
  voice_intent: {
    type: "object",
    properties: {
      transcript: { type: "string" },
      intent: {
        type: "string",
        enum: ["TRANSACTION", "TASK", "REMINDER", "ALARM", "NOTE", "ROUTINE", "JOB"],
      },
      amount: { type: "number", nullable: true },
      category: {
        type: "string",
        enum: [
          "FOOD", "TRANSPORT", "SUBSCRIPTIONS", "ENTERTAINMENT", "SHOPPING",
          "HEALTH", "UTILITIES", "RENT", "SALARY", "OTHER",
        ],
      },
      merchant: { type: "string" },
      note: { type: "string" },
      type: { type: "string", enum: ["INCOME", "EXPENSE"] },
      confidence: { type: "number" },
      title: { type: "string" },
      body: { type: "string" },
      due_at: { type: "string", nullable: true },
      remind_at: { type: "string", nullable: true },
      label: { type: "string" },
      time: { type: "string", nullable: true },
      tags: { type: "array", items: { type: "string" } },
      repeat_rule: {
        type: "string",
        enum: ["DAILY", "WEEKLY", "WEEKDAYS", "CUSTOM"],
        nullable: true,
      },
      repeat_days: { type: "integer", nullable: true },
    },
    required: ["transcript", "intent", "confidence"],
  },
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

function allowRate(userId: string): boolean {
  const now = Date.now();
  const bucket = rateBuckets.get(userId);
  if (!bucket || now - bucket.windowStart >= RATE_WINDOW_MS) {
    rateBuckets.set(userId, { count: 1, windowStart: now });
    return true;
  }
  if (bucket.count >= RATE_LIMIT) return false;
  bucket.count += 1;
  return true;
}

const VOICE_SYSTEM = `You turn a spoken request into ONE structured action for a personal finance + planner app.
The user may speak English or Bangla, or mix the two. Understand both.
If audio is attached: first transcribe it faithfully into "transcript" (spoken numbers as digits, e.g. "fifty two dollars" -> 52), then interpret it. If only text is given, copy it into "transcript".
"intent": TRANSACTION (spent/paid/bought/earned/received money), TASK (to-do), REMINDER (remind me at a time), ALARM (wake/alarm at a clock time), NOTE (remember/write down), ROUTINE (repeating habit), JOB (a job application or interview; never a calendar event).
Rules:
- If the user spent, paid, bought, or received money, intent is TRANSACTION, never NOTE.
- TRANSACTION: amount is a positive number without currency symbols; type EXPENSE unless money was received/earned (INCOME); category from the enum; merchant is the specific shop or person name if one was said (Starbucks, Sarah, John). note is a short extra detail or empty. Never copy the full sentence into note, title, or body.
- TASK and NOTE titles should be the specific name or action, not the whole sentence.
- TASK: title; due_at ISO-8601 local datetime if a deadline is said.
- REMINDER: title/label; remind_at ISO-8601 local datetime (resolve "tomorrow", "in 2 hours", "Friday" using the current time given in the user message).
- ALARM: time as 24h "HH:mm"; label; repeat_days weekday bitmask Sun=1,Mon=2,Tue=4,Wed=8,Thu=16,Fri=32,Sat=64 (0 = one time, weekdays = 62, every day = 127).
- NOTE: title, body, up to 5 short tags.
- ROUTINE: title and repeat_rule DAILY|WEEKLY|WEEKDAYS|CUSTOM.
- JOB: name is only the company. title is only the spoken role, such as "Android engineer", never the company and never the word Role. label is APPLIED|SCREENING|INTERVIEW|OFFER|REJECTED|WITHDRAWN. start_at is the interview or follow-up. Do not use TASK, REMINDER, or EVENT for a job or interview.
confidence is 0..1. Never invent amounts or times that were not said; leave them null instead.`;

function defaultSystem(type: AiType): string {
  switch (type) {
    case "transaction":
      return "Extract one personal finance transaction. Use only the JSON schema fields.";
    case "task":
      return "Extract one task. Use ISO-8601 datetimes when present. Max 10 reminders.";
    case "alarm":
      return "Extract one alarm. hour 0-23, minute 0-59. repeatDays bitmask Sun=1..Sat=64.";
    case "insight":
      return "Produce one concise daily finance insight from the provided context only.";
    case "note_summary":
      return "Summarize the note; suggest 1-5 short tags and optional highlights.";
    case "chat":
      return "You are LedgerAI, a concise personal finance assistant. Do not invent balances.";
    case "voice_intent":
      return VOICE_SYSTEM;
    case "fast_completion":
      return "Reply with one JSON object. Leave unknown amount, merchant, and start_at null. Do not invent items.";
  }
}

async function callGeminiOnce(
  apiKey: string,
  system: string,
  user: string,
  schema: Record<string, unknown>,
  model: string,
  audio?: { mimeType: string; data: string },
): Promise<string> {
  const url =
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
  const parts: Record<string, unknown>[] = [];
  if (audio) parts.push({ inlineData: { mimeType: audio.mimeType, data: audio.data } });
  parts.push({ text: user });
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-goog-api-key": apiKey,
    },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: system }] },
      contents: [{ role: "user", parts }],
      generationConfig: {
        temperature: 0.3,
        maxOutputTokens: 4096,
        responseMimeType: "application/json",
        responseSchema: schema,
      },
    }),
  });
  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Gemini ${res.status}: ${errText.slice(0, 200)}`);
  }
  const data = await res.json();
  const text = data?.candidates?.[0]?.content?.parts
    ?.map((p: { text?: string }) => p.text ?? "")
    .join("")
    ?.trim();
  if (!text) throw new Error("Gemini returned empty content");
  return text;
}

/** Gemini with one retry on transient errors (429 / 5xx "high demand"). */
async function callGemini(
  apiKey: string,
  system: string,
  user: string,
  schema: Record<string, unknown>,
  model: string,
  audio?: { mimeType: string; data: string },
): Promise<string> {
  try {
    return await callGeminiOnce(apiKey, system, user, schema, model, audio);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (!/Gemini (429|5\d\d)/.test(msg)) throw e;
    await new Promise((r) => setTimeout(r, 1500));
    return await callGeminiOnce(apiKey, system, user, schema, model, audio);
  }
}

async function callOpenRouter(
  apiKey: string,
  system: string,
  user: string,
  schema: Record<string, unknown>,
  model: string,
  limits?: {
    temperature?: number;
    maxTokens?: number;
    /** OpenRouter provider.sort. Omitted unless set, so other types stay unchanged. */
    providerSort?: string;
    /** Default true. fast_completion sends the client system text with no schema appendix. */
    appendSchema?: boolean;
  },
): Promise<string> {
  const temperature = limits?.temperature ?? 0.2;
  const maxTokens = limits?.maxTokens ?? 1500;
  const appendSchema = limits?.appendSchema !== false;
  const systemContent = appendSchema
    ? `${system}\nReply with ONLY a JSON object matching this JSON schema (include all required fields; use null for unknown nullable fields):\n${JSON.stringify(schema)}`
    : system;
  const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model,
      temperature,
      max_tokens: maxTokens,
      response_format: { type: "json_object" },
      ...(limits?.providerSort ? { provider: { sort: limits.providerSort } } : {}),
      messages: [
        { role: "system", content: systemContent },
        { role: "user", content: user },
      ],
    }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || data?.error) {
    throw new Error(`OpenRouter ${res.status}: ${JSON.stringify(data?.error ?? data).slice(0, 200)}`);
  }
  const text = data?.choices?.[0]?.message?.content?.trim();
  if (!text) throw new Error("OpenRouter returned empty content");
  return text;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }
  if (req.method !== "POST") {
    return json(405, { error: "Method not allowed" });
  }

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader?.startsWith("Bearer ")) {
      return json(401, { error: "Missing Authorization Bearer JWT" });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseAnon = Deno.env.get("SUPABASE_ANON_KEY");
    if (!supabaseUrl || !supabaseAnon) {
      return json(500, { error: "Auth env not configured" });
    }
    // Hard guard: never accept a service_role key (would be a secret leak).
    if (supabaseAnon.includes("service_role") || supabaseAnon.length > 200) {
      return json(500, { error: "SUPABASE_ANON_KEY must be the publishable/anon key, never service_role" });
    }

    // Auth-only client — no service_role, no DB reads/writes, no postgrest usage for user data.
    const supabase = createClient(supabaseUrl, supabaseAnon, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: userData, error: userError } = await supabase.auth.getUser();
    if (userError || !userData?.user) {
      return json(401, { error: "Invalid or expired JWT" });
    }
    const userId = userData.user.id;

    if (!allowRate(userId)) {
      return json(429, { error: "Rate limit exceeded" });
    }

    const body = (await req.json()) as ProxyRequest;
    if (!body?.type) return json(400, { error: "Body requires type" });
    if (body.type !== "fast_completion" && !(body.type in SCHEMAS)) {
      return json(400, { error: `Unsupported type: ${body.type}` });
    }
    const audio = body.type === "voice_intent" ? body.audio : undefined;
    if (body.audio && (!audio || !audio.data || audio.data.length > 8_000_000)) {
      return json(400, { error: "audio invalid or too large (max ~6 MB)" });
    }
    const userText = (body.user ?? "").trim().slice(0, 8000);
    if (!userText && !audio) return json(400, { error: "Body requires user text or audio" });

    const system = (body.system?.trim() || defaultSystem(body.type)).slice(0, 4000);

    // One OpenRouter JSON completion. Client temperature, max tokens, model, and
    // response format are ignored. Provider key comes only from Edge secrets.
    if (body.type === "fast_completion") {
      const openRouterKey = Deno.env.get("OPENROUTER_API_KEY")?.trim();
      if (!openRouterKey) {
        return json(503, { error: "No AI provider keys configured on Edge" });
      }
      const model = Deno.env.get("AI_MODEL_FAST")?.trim() || "google/gemini-3.1-flash-lite";
      let rawJson: string;
      try {
        rawJson = await callOpenRouter(openRouterKey, system, userText, {}, model, {
          temperature: 0,
          maxTokens: 600,
          providerSort: "latency",
          appendSchema: false,
        });
      } catch (e) {
        const message = e instanceof Error ? e.message : String(e);
        return json(502, { error: message.slice(0, 600) || "All providers failed" });
      }
      let data: unknown;
      try {
        data = JSON.parse(rawJson);
        if (typeof data === "string") data = JSON.parse(data);
      } catch {
        return json(502, { error: "Provider returned non-JSON", raw: rawJson.slice(0, 500) });
      }
      if (data === null || typeof data !== "object") {
        return json(502, { error: "Provider returned non-JSON", raw: rawJson.slice(0, 500) });
      }
      return json(200, { type: body.type, data, provider: "openrouter" });
    }

    const schema = SCHEMAS[body.type];
    const lite = body.modelTier === "flash-lite" && !audio;
    const geminiModel = lite
      ? (Deno.env.get("GEMINI_MODEL_LITE") ?? "gemini-flash-lite-latest")
      : (Deno.env.get("GEMINI_MODEL") ?? "gemini-flash-latest");
    const openRouterModel = Deno.env.get("OPENROUTER_MODEL") ??
      "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free";

    const geminiKey = Deno.env.get("GEMINI_API_KEY")?.trim();
    const openRouterKey = Deno.env.get("OPENROUTER_API_KEY")?.trim();
    if (!geminiKey && !openRouterKey) {
      return json(503, { error: "No AI provider keys configured on Edge" });
    }

    const errors: string[] = [];
    let rawJson: string | undefined;
    let provider = "";

    // 1) Gemini (free tier) - with audio when provided (transcribe + parse in one call).
    if (geminiKey) {
      try {
        rawJson = await callGemini(
          geminiKey, system,
          userText || "Transcribe the attached audio and interpret it.",
          schema, geminiModel, audio,
        );
        provider = audio ? "gemini-audio" : "gemini";
      } catch (e) {
        errors.push(String(e instanceof Error ? e.message : e));
      }
    }

    // 2) Backup (text only): OpenRouter free model. Audio has no cloud backup (app uses Vosk).
    if (!rawJson && openRouterKey && !audio) {
      try {
        rawJson = await callOpenRouter(openRouterKey, system, userText, schema, openRouterModel);
        provider = "openrouter";
      } catch (e) {
        errors.push(String(e instanceof Error ? e.message : e));
      }
    }

    if (!rawJson) return json(502, { error: errors.join(" | ").slice(0, 600) || "All providers failed" });

    let data: unknown;
    try {
      data = JSON.parse(rawJson);
    } catch {
      return json(502, { error: "Provider returned non-JSON", raw: rawJson.slice(0, 500) });
    }

    return json(200, { type: body.type, data, provider });
  } catch (e) {
    const message = e instanceof Error ? e.message : "Unknown error";
    return json(502, { error: message });
  }
});
