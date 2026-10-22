/**
 * media-sign — Supabase Edge Function (Deno). Stays in supabase/planned/ until promoted.
 *
 * Gives the Android app short-lived presigned URLs for Cloudflare R2 (S3-compatible).
 * The R2 keys live only here as Edge secrets; the APK never holds them.
 *
 * - Verifies the Authorization Bearer JWT with the anon key + getUser() (no service_role, no DB).
 * - The object key is always `{user.id}/{id}.{ext}`, built here. The client cannot choose another folder.
 *   `ext` is optional and must be on the allowlist below; it defaults to jpg (photos, as before).
 * - Fixed request/response shapes.
 *
 * Request:  { "action": "put" | "get", "id": "<uuid>", "ext"?: "jpg" | "png" | "heic" | "pdf" | "txt" | "docx" | "xlsx" | "zip" }
 * Response: { "url": "<presigned https url>", "path": "<user.id>/<id>.<ext>", "expiresIn": 300, "contentType": "<type>" }
 * Error:    { "error": "<message>" } with a 4xx/5xx status.
 *
 * Secrets: R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET,
 *          SUPABASE_URL, SUPABASE_ANON_KEY.
 * The PUT is signed for the Content-Type that matches `ext`; the client must send exactly that header.
 * Vault files are capped at 25 MB by the client and by the media_assets.size_bytes check. Locked and
 * device-only folders never call this function.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { AwsClient } from "https://esm.sh/aws4fetch@1.0.20";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const EXPIRES_IN = 300;

// Allowlist: extension -> Content-Type. Anything else is rejected, so the key cannot carry an arbitrary suffix.
const TYPES: Record<string, string> = {
  jpg: "image/jpeg",
  png: "image/png",
  heic: "image/heic",
  pdf: "application/pdf",
  txt: "text/plain",
  docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  zip: "application/zip",
};
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json(405, { error: "POST only" });

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return json(401, { error: "Missing Authorization Bearer JWT" });

  const env = (k: string) => Deno.env.get(k) ?? "";
  const supabaseUrl = env("SUPABASE_URL");
  const anon = env("SUPABASE_ANON_KEY");
  const account = env("R2_ACCOUNT_ID");
  const accessKeyId = env("R2_ACCESS_KEY_ID");
  const secretAccessKey = env("R2_SECRET_ACCESS_KEY");
  const bucket = env("R2_BUCKET");
  if (!supabaseUrl || !anon || !account || !accessKeyId || !secretAccessKey || !bucket) {
    return json(500, { error: "Server is not configured" });
  }

  const supabase = createClient(supabaseUrl, anon, { global: { headers: { Authorization: authHeader } } });
  const { data: userData, error: userError } = await supabase.auth.getUser();
  if (userError || !userData.user) return json(401, { error: "Invalid JWT" });

  let body: { action?: string; id?: string; ext?: string };
  try {
    body = await req.json();
  } catch {
    return json(400, { error: "Invalid JSON" });
  }
  if (body.action !== "put" && body.action !== "get") return json(400, { error: "action must be put or get" });
  if (!body.id || !UUID.test(body.id)) return json(400, { error: "id must be a uuid" });

  const ext = (body.ext ?? "jpg").toLowerCase();
  const contentType = TYPES[ext];
  if (!contentType) return json(400, { error: "ext is not allowed" });

  const path = `${userData.user.id}/${body.id.toLowerCase()}.${ext}`;
  const r2 = new AwsClient({ accessKeyId, secretAccessKey, service: "s3", region: "auto" });
  const url = new URL(`https://${account}.r2.cloudflarestorage.com/${bucket}/${path}`);
  url.searchParams.set("X-Amz-Expires", String(EXPIRES_IN));

  const signed = await r2.sign(
    new Request(url, {
      method: body.action === "put" ? "PUT" : "GET",
      headers: body.action === "put" ? { "Content-Type": contentType } : {},
    }),
    { aws: { signQuery: true } },
  );

  return json(200, { url: signed.url, path, expiresIn: EXPIRES_IN, contentType });
});
