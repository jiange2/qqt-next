const BASE = "http://localhost:8001";
const PASSWORD = "fwoCR5B1LpKhD4Gf";

const login = await fetch(BASE + "/admin/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "admin", password: PASSWORD }),
}).then((r) => r.json());
if (!login.token) { console.error("LOGIN FAILED", login); process.exit(1); }
const H = { Authorization: "Bearer " + login.token };

async function devices(params) {
  const u = new URL(BASE + "/admin/stats/devices");
  for (const k in params) u.searchParams.set(k, String(params[k]));
  const res = await fetch(u, { headers: H });
  if (!res.ok) { console.error("HTTP", res.status, await res.text()); process.exit(1); }
  const d = await res.json();
  return { ids: d.items.map((x) => x.deviceId), total: d.total, items: d.items };
}

const DEFAULT = ["devC","devA","devG","devE","devB","devD","filler1","devF","filler2","devH","filler3","filler4","filler5"];
const cases = [
  ["default", {}, DEFAULT],
  ["budget desc", { sortBy: "allocatedStorage", sortOrder: "desc" }, ["devA","devE","devH","devB","devD","filler5","filler4","filler3","devF","filler2","filler1","devC","devG"]],
  ["budget asc (NULL sink)", { sortBy: "allocatedStorage", sortOrder: "asc" }, ["filler1","filler2","devF","filler3","filler4","filler5","devB","devD","devE","devH","devA","devC","devG"]],
  ["usage desc", { sortBy: "usedStorage", sortOrder: "desc" }, ["devB","devA","devH","devF","devD","filler5","filler4","filler3","filler2","devG","filler1","devC","devE"]],
  ["facts desc", { sortBy: "facts", sortOrder: "desc" }, ["devF","devD","devA","devE","devC","devG","devB","devH","filler1","filler2","filler3","filler4","filler5"]],
  ["firstSeen desc", { sortBy: "firstSeen", sortOrder: "desc" }, ["filler1","filler2","filler3","filler4","filler5","devE","devC","devH","devG","devB","devA","devD","devF"]],
  ["lastSeen desc", { sortBy: "lastSeen", sortOrder: "desc" }, ["devC","devA","devG","devE","devB","devD","filler1","devF","filler2","devH","filler3","filler4","filler5"]],
  ["lastSeen asc", { sortBy: "lastSeen", sortOrder: "asc" }, ["filler5","filler4","devH","filler3","devF","filler2","devD","filler1","devB","devE","devG","devA","devC"]],
  ["invalid sortBy -> default", { sortBy: "deviceId; DROP TABLE access_facts", sortOrder: "desc" }, DEFAULT],
  ["invalid sortOrder -> desc", { sortBy: "allocatedStorage", sortOrder: "bogus" }, ["devA","devE","devH","devB","devD","filler5","filler4","filler3","devF","filler2","filler1","devC","devG"]],
];

let pass = 0, fail = 0;
for (const [name, params, expected] of cases) {
  const { ids, total } = await devices({ size: 20, ...params });
  const ok = JSON.stringify(ids) === JSON.stringify(expected) && total === 13;
  console.log((ok ? "PASS" : "FAIL") + " | " + name + " | total=" + total);
  if (!ok) { console.log("  expected: " + expected.join(",")); console.log("  actual:   " + ids.join(",")); fail++; } else pass++;
}

// 分页不重不漏：budget desc, size 10, 两页
const p1 = await devices({ sortBy: "allocatedStorage", sortOrder: "desc", size: 10, page: 1 });
const p2 = await devices({ sortBy: "allocatedStorage", sortOrder: "desc", size: 10, page: 2 });
const union = [...p1.ids, ...p2.ids];
const noDup = new Set(union).size === 13;
const p1ok = JSON.stringify(p1.ids) === JSON.stringify(["devA","devE","devH","devB","devD","filler5","filler4","filler3","devF","filler2"]);
const p2ok = JSON.stringify(p2.ids) === JSON.stringify(["filler1","devC","devG"]);
const pageOk = noDup && p1ok && p2ok && p1.total === 13 && p2.total === 13;
console.log((pageOk ? "PASS" : "FAIL") + " | pagination budget desc size10 (no dup/skip, NULL sink on page2)");
if (!pageOk) { console.log("  p1: " + p1.ids.join(",")); console.log("  p2: " + p2.ids.join(",")); fail++; } else pass++;

// NULL 沉底直接核对：budget desc 末两位是 devC/devG（均 NULL），且 allocatedStorage 为 null
const bd = await devices({ sortBy: "allocatedStorage", sortOrder: "desc", size: 20 });
const tail = bd.items.slice(-2);
const nullOk = tail.every((x) => x.allocatedStorage === null) && JSON.stringify(tail.map(x=>x.deviceId)) === JSON.stringify(["devC","devG"]);
console.log((nullOk ? "PASS" : "FAIL") + " | NULL budget rows sink to bottom (devC,devG allocatedStorage=null)");
if (nullOk) pass++; else { fail++; console.log("  tail: " + JSON.stringify(tail.map(x=>[x.deviceId,x.allocatedStorage]))); }

console.log("\nSUMMARY: " + pass + " passed, " + fail + " failed");
process.exit(fail ? 1 : 0);