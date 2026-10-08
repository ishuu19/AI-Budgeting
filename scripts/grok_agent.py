#!/usr/bin/env python
"""Grok agent CLI - chat with Grok, which can read/search/edit files and run commands in this project.

Run:  python scripts/grok_agent.py      (or: scripts/grok-agent.sh)
Keys are read from secrets.properties (XAI_API_KEY, XAI_BASE_URL, AI_MODEL_XAI) and never printed.
Commands in chat: /exit  /clear  /auto (toggle auto-approve of edits & commands)  /model <name>
"""
import json, os, re, subprocess, sys, urllib.request, urllib.error

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MAX_OUT = 12000
SKIP_DIRS = {".git", ".gradle", ".idea", "build", ".kotlin", "node_modules"}


def load_secrets():
    cfg = {}
    with open(os.path.join(ROOT, "secrets.properties"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                cfg[k.strip()] = v.strip()
    return cfg


CFG = load_secrets()
KEY = CFG.get("XAI_API_KEY", "")
BASE = CFG.get("XAI_BASE_URL", "https://api.x.ai/v1").rstrip("/")
MODEL = os.environ.get("GROK_MODEL") or CFG.get("AI_MODEL_XAI", "grok-4")
AUTO = False


def safe_path(p):
    full = os.path.abspath(os.path.join(ROOT, p))
    if os.path.commonpath([full, ROOT]) != ROOT:
        raise ValueError("path is outside the project")
    return full


def clip(s):
    return s if len(s) <= MAX_OUT else s[:MAX_OUT] + f"\n...[truncated {len(s) - MAX_OUT} chars]"


def approve(what):
    if AUTO:
        return True
    return input(f"\n  Allow {what}? [y/N] ").strip().lower() in ("y", "yes")


# ---- tools ----------------------------------------------------------------
def t_list_dir(path="."):
    full = safe_path(path)
    out = []
    for name in sorted(os.listdir(full)):
        if name in SKIP_DIRS:
            continue
        out.append(name + ("/" if os.path.isdir(os.path.join(full, name)) else ""))
    return "\n".join(out) or "(empty)"


def t_read_file(path, start=1, end=400):
    with open(safe_path(path), encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    chunk = lines[max(start - 1, 0):end]
    return clip("\n".join(f"{i}\t{l}" for i, l in enumerate(chunk, start=max(start, 1)))) + \
        (f"\n[file has {len(lines)} lines]" if len(lines) > end else "")


def t_search(pattern, path="."):
    rx = re.compile(pattern, re.I)
    base = safe_path(path)
    hits = []
    for d, dirs, files in os.walk(base):
        dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
        for fn in files:
            fp = os.path.join(d, fn)
            if os.path.splitext(fn)[1].lower() in (".png", ".jpg", ".webp", ".jar", ".class", ".ttf", ".apk"):
                continue
            try:
                with open(fp, encoding="utf-8") as f:
                    for n, line in enumerate(f, 1):
                        if rx.search(line):
                            hits.append(f"{os.path.relpath(fp, ROOT)}:{n}: {line.strip()[:160]}")
                            if len(hits) >= 80:
                                return "\n".join(hits) + "\n[more matches omitted]"
            except (UnicodeDecodeError, OSError):
                pass
    return "\n".join(hits) or "no matches"


def t_write_file(path, content):
    full = safe_path(path)
    if os.path.basename(full) == "secrets.properties":
        return "refused: will not modify secrets.properties"
    if not approve(f"WRITE {path} ({len(content)} chars)"):
        return "user denied"
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    return f"wrote {path}"


def t_edit_file(path, old, new):
    full = safe_path(path)
    with open(full, encoding="utf-8") as f:
        text = f.read()
    if text.count(old) != 1:
        return f"error: 'old' must match exactly once (found {text.count(old)})"
    if not approve(f"EDIT {path}\n    - {old[:100]!r}\n    + {new[:100]!r}"):
        return "user denied"
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(text.replace(old, new))
    return f"edited {path}"


def t_run_command(command, timeout=300):
    if not approve(f"RUN: {command}"):
        return "user denied"
    try:
        r = subprocess.run(command, shell=True, cwd=ROOT, capture_output=True, text=True,
                           timeout=min(int(timeout), 600), encoding="utf-8", errors="replace")
        return clip(f"exit {r.returncode}\n{r.stdout}{r.stderr}")
    except subprocess.TimeoutExpired:
        return "error: command timed out"


TOOLS = {
    "list_dir": (t_list_dir, "List files in a project directory.", {"path": "string"}, []),
    "read_file": (t_read_file, "Read a text file with line numbers.", {"path": "string", "start": "integer", "end": "integer"}, ["path"]),
    "search": (t_search, "Regex search (case-insensitive) across project files.", {"pattern": "string", "path": "string"}, ["pattern"]),
    "write_file": (t_write_file, "Create/overwrite a file (asks user).", {"path": "string", "content": "string"}, ["path", "content"]),
    "edit_file": (t_edit_file, "Replace one exact occurrence of text in a file (asks user).", {"path": "string", "old": "string", "new": "string"}, ["path", "old", "new"]),
    "run_command": (t_run_command, "Run a shell command in the project root (asks user).", {"command": "string", "timeout": "integer"}, ["command"]),
}


def tool_specs():
    return [{"type": "function", "function": {
        "name": n, "description": d,
        "parameters": {"type": "object", "properties": {k: {"type": t} for k, t in props.items()}, "required": req}}}
        for n, (_, d, props, req) in TOOLS.items()]


def call_tool(name, args):
    try:
        return str(TOOLS[name][0](**args))
    except Exception as e:  # report back to the model
        return f"error: {type(e).__name__}: {e}"


# ---- API ------------------------------------------------------------------
def chat(messages):
    body = json.dumps({"model": MODEL, "messages": messages, "tools": tool_specs()}).encode()
    req = urllib.request.Request(f"{BASE}/chat/completions", data=body, headers={
        "Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            return json.load(r)["choices"][0]["message"]
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"API error {e.code}: {e.read().decode()[:400]}")


SYSTEM = (
    "You are a coding agent working inside the Android project at the current directory "
    "(LedgerAI: Kotlin, Compose, Room, Hilt; see PLAN.md for the roadmap). Use the tools to inspect "
    "before changing anything. Make small, correct edits, then verify (e.g. ./gradlew assembleDebug). "
    "Never touch secrets.properties and never print API keys. Be concise."
)


def main():
    global AUTO, MODEL
    if not KEY:
        sys.exit("XAI_API_KEY missing in secrets.properties")
    fresh = lambda: [{"role": "system", "content": SYSTEM}]
    messages = fresh()
    print(f"Grok agent | model={MODEL} | project={ROOT}\n/exit /clear /auto /model <name>\n")
    while True:
        try:
            user = input("you> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not user:
            continue
        if user == "/exit":
            break
        if user == "/clear":
            messages = fresh(); print("(history cleared)"); continue
        if user == "/auto":
            AUTO = not AUTO; print(f"(auto-approve {'ON' if AUTO else 'OFF'})"); continue
        if user.startswith("/model "):
            MODEL = user.split(None, 1)[1]; print(f"(model={MODEL})"); continue
        messages.append({"role": "user", "content": user})
        try:
            for _ in range(40):  # max tool rounds per message
                msg = chat(messages)
                messages.append({k: v for k, v in msg.items() if v is not None})
                if msg.get("content"):
                    print(f"\ngrok> {msg['content']}\n")
                calls = msg.get("tool_calls") or []
                if not calls:
                    break
                for c in calls:
                    fn = c["function"]
                    try:
                        args = json.loads(fn.get("arguments") or "{}")
                    except json.JSONDecodeError:
                        args = {}
                    print(f"  [tool] {fn['name']}({', '.join(f'{k}={str(v)[:50]!r}' for k, v in args.items())})")
                    messages.append({"role": "tool", "tool_call_id": c["id"], "content": call_tool(fn["name"], args)})
        except KeyboardInterrupt:
            print("\n(interrupted)")
        except Exception as e:
            print(f"\n[error] {e}\n")


if __name__ == "__main__":
    main()
