// Reusable Chrome DevTools Protocol harness — drives the running shadow-cljs
// app at http://localhost:8280/index.html in a headless Chrome instance.
//
// No third-party deps: uses Node 22+ native WebSocket + node:fs/child_process.
// Scenario scripts import the helpers exported here and stay terse.
//
// Connection: scenario receives the page-target ws URL on argv[2] (verify.sh
// finds it via curl localhost:9222/json). Each helper wraps one CDP method.
//
// Conventions:
//   cdp(method, params)    — promise of CDP result
//   evalJs(expr)           — Runtime.evaluate with returnByValue
//   shot(path)             — write a 390×844 mobile PNG
//   navigate(url)          — Page.navigate + wait for load + 700ms settle
//   kwExpr(ns?, name)      — build a `cljs.core.keyword.call(...)` JS literal
//   dispatchExpr(action, argsExpr?) — build `ceol.web.core.dispatch_action_BANG_(...)`
//
// Both helpers return raw JS expression strings — feed them to evalJs().

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";

const wsUrl = process.argv[2];
if (!wsUrl) {
  console.error("missing CDP page ws url (pass on argv[2])");
  process.exit(1);
}

const ws = new WebSocket(wsUrl);
let nextId = 0;
const pending = new Map();
const listeners = new Map();

function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}

function on(event, fn) {
  if (!listeners.has(event)) listeners.set(event, []);
  listeners.get(event).push(fn);
}

function once(event, predicate = () => true) {
  return new Promise((resolve) => {
    const fn = (params) => {
      if (predicate(params)) {
        const arr = listeners.get(event) || [];
        const i = arr.indexOf(fn);
        if (i >= 0) arr.splice(i, 1);
        resolve(params);
      }
    };
    on(event, fn);
  });
}

ws.addEventListener("message", (msg) => {
  const data = JSON.parse(msg.data);
  if (data.id != null) {
    const p = pending.get(data.id);
    pending.delete(data.id);
    if (data.error) p.reject(new Error(`${data.error.code} ${data.error.message}`));
    else p.resolve(data.result);
  } else if (data.method) {
    for (const fn of (listeners.get(data.method) || []).slice()) fn(data.params);
  }
});

await new Promise((res, rej) => {
  ws.addEventListener("open", res, { once: true });
  ws.addEventListener("error", rej, { once: true });
});

await send("Page.enable");
await send("Runtime.enable");
await send("Emulation.setDeviceMetricsOverride", {
  width: 390, height: 844, deviceScaleFactor: 2, mobile: true,
});

export const cdp = send;

export async function navigate(url, settleMs = 700) {
  const navP = once("Page.loadEventFired");
  await send("Page.navigate", { url });
  await navP;
  await sleep(settleMs);
}

export async function evalJs(expression, { awaitPromise = true } = {}) {
  const r = await send("Runtime.evaluate", {
    expression, returnByValue: true, awaitPromise,
  });
  if (r.exceptionDetails) {
    const txt = r.exceptionDetails.exception?.description ?? JSON.stringify(r.exceptionDetails);
    throw new Error(`JS exception: ${txt}`);
  }
  return r.result?.value;
}

export async function shot(path, { settleMs = 150 } = {}) {
  await sleep(settleMs);
  const { data } = await send("Page.captureScreenshot", { format: "png" });
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, Buffer.from(data, "base64"));
  console.log(`shot → ${path}`);
}

export function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

// Build a `cljs.core.keyword.call(null, ns, name)` JS expression. Single-arg
// form omits the namespace.
export function kwExpr(nsOrName, maybeName) {
  if (maybeName == null) return `cljs.core.keyword.call(null, ${JSON.stringify(nsOrName)})`;
  return `cljs.core.keyword.call(null, ${JSON.stringify(nsOrName)}, ${JSON.stringify(maybeName)})`;
}

// Build a `cljs.core.PersistentVector.fromArray([...])` expression. `items` is
// an array of JS expression strings (already-stringified literals).
export function vecExpr(items) {
  return `cljs.core.PersistentVector.fromArray([${items.join(", ")}])`;
}

// Build a `ceol.web.core.dispatch_action_BANG_(action, args)` expression.
// `action` and `args` are JS expression strings; pass `"null"` for no args.
export function dispatchExpr(actionExpr, argsExpr = "null") {
  return `ceol.web.core.dispatch_action_BANG_(${actionExpr}, ${argsExpr})`;
}

// Read a value out of the cljs app-state. Path is an array of keyword names
// (strings). Returns the value via cljs->js + JSON.
export function getInExpr(pathKwNames) {
  const path = pathKwNames.map(k => kwExpr(k));
  return `(function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var v = cljs.core.get_in.call(null, s, [${path.join(", ")}]);
    return v == null ? null : JSON.stringify(cljs.core.clj__GT_js(v));
  })()`;
}

// Convenience: dispatch with a single literal arg (string / number / kw expr)
// wrapped in a one-element vector.
export async function dispatch(actionKwExpr, argsExpr = "null") {
  await evalJs(dispatchExpr(actionKwExpr, argsExpr));
}

export function shutdown() {
  ws.close();
}
