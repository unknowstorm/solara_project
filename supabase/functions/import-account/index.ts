import { createClient } from "npm:@supabase/supabase-js@2";

const JAMENDO = "https://api.jamendo.com/v3.0";
const AUDIUS = "https://api.audius.co/v1";
const MAX_LIKES = 200, MAX_PLAYLISTS = 20, MAX_PER_PLAYLIST = 200;

type T = { provider: "JAMENDO" | "AUDIUS"; track_id: string; title: string; artist: string;
           artwork_url: string | null; duration_ms: number };

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
// Jamendo sometimes answers 200 with headers.status="failed" ("Internal Error") for a second or two:
// retry such answers and 5xx a few times before giving up
const j = async (url: string) => {
  let last = "";
  for (let attempt = 0; attempt < 4; attempt++) {
    if (attempt) await sleep(700 * attempt);
    try {
      const r = await fetch(url);
      if (r.status >= 500) { last = `${r.status} ${url.split("?")[0]}`; continue; }
      if (!r.ok) throw new Error(`${r.status} ${url.split("?")[0]}`);
      const d = await r.json();
      if (d?.headers?.status === "failed" && /internal/i.test(String(d.headers.error_message ?? ""))) {
        last = `jamendo: ${d.headers.error_message}`; continue;
      }
      return d;
    } catch (e) {
      if (attempt === 3) throw e;
      last = String(e);
    }
  }
  throw new Error(last || "fetch_failed");
};

// ---------- Jamendo ----------
// Jamendo answers HTTP 200 even on errors: the real status is in headers.status
const jcheck = (what: string, d: any) => {
  if (d?.headers?.status === "failed") throw new Error(`jamendo_api(${what}): ${d.headers.error_message ?? d.headers.code}`);
};
const cid = () => Deno.env.get("JAMENDO_CLIENT_ID") ?? "";
// Jamendo returns HTML-escaped names ("&amp;"): decode them
const dec = (s: unknown) => String(s ?? "").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
  .replace(/&quot;/g, '"').replace(/&#0?39;/g, "'").replace(/&amp;/g, "&");
// By default the API returns only album tracks: singles must be requested explicitly
const TT = "track_type=single+albumtrack";

async function jamendoResolve(input: string) {
  if (!cid()) throw new Error("jamendo_client_id_missing");
  const d = await j(`${JAMENDO}/users/?client_id=${cid()}&format=json&name=${encodeURIComponent(input)}`);
  jcheck("users", d);
  const u = d.results?.[0];
  if (!u) {
    if (/^\d+$/.test(input)) return { id: input, name: null }; // numeric id typed
    throw new Error("not_found");
  }
  return { id: String(u.id), name: u.name ?? null };
}
const jTrack = (t: any): T => ({
  provider: "JAMENDO", track_id: String(t.id), title: dec(t.name), artist: dec(t.artist_name),
  artwork_url: t.image ?? t.album_image ?? null, duration_ms: (Number(t.duration) || 0) * 1000,
});
async function jamendoLikes(id: string): Promise<T[]> {
  // the heart on the site is relation=favorite; "like" is requested too and merged; newest first
  const out: T[] = [];
  for (const rel of ["favorite", "like"]) {
    const d = await j(`${JAMENDO}/users/tracks/?client_id=${cid()}&format=json&id=${id}&relation=${rel}&${TT}&order=updatedate_desc&limit=${MAX_LIKES}`);
    jcheck(`users/tracks ${rel}`, d);
    out.push(...(d.results ?? []).flatMap((r: any) => r.tracks ?? []).map(jTrack));
  }
  return out;
}
async function jamendoPlaylists(id: string) {
  // one call: the user's playlists with their tracks inside
  const d = await j(`${JAMENDO}/playlists/tracks/?client_id=${cid()}&format=json&user_id=${id}&${TT}&limit=${MAX_PLAYLISTS}`);
  jcheck("playlists/tracks", d);
  return (d.results ?? []).map((p: any) => ({
    external_id: String(p.id), name: dec(p.name),
    // the API does not guarantee the order: sort by the position inside the playlist
    tracks: [...(p.tracks ?? [])].sort((a: any, b: any) => Number(a.position) - Number(b.position))
      .slice(0, MAX_PER_PLAYLIST).map(jTrack),
  }));
}

// ---------- Audius ----------
const app = "app_name=Solara";
async function audiusResolve(input: string) {
  const handle = input.replace(/^.*audius\.co\//, "").replace(/^@/, "").split(/[/?]/)[0];
  const d = await j(`${AUDIUS}/users/handle/${encodeURIComponent(handle)}?${app}`);
  if (!d.data?.id) throw new Error("not_found");
  return { id: String(d.data.id), name: d.data.name ?? handle };
}
const aTrack = (t: any): T => ({
  provider: "AUDIUS", track_id: String(t.id), title: t.title, artist: t.user?.name ?? "",
  artwork_url: t.artwork?.["480x480"] ?? t.artwork?.["150x150"] ?? null, duration_ms: (Number(t.duration) || 0) * 1000,
});
async function audiusLikes(id: string): Promise<T[]> {
  const d = await j(`${AUDIUS}/users/${id}/favorites?${app}`);
  const tracks: T[] = [], ids: string[] = [];
  for (const f of d.data ?? []) {
    if (f.item_type && f.item_type !== "track") continue;
    if (f.item) tracks.push(aTrack(f.item));
    else if (f.favorite_item_id ?? f.item_id) ids.push(String(f.favorite_item_id ?? f.item_id));
  }
  for (let i = 0; i < ids.length && tracks.length < MAX_LIKES; i += 50) {
    const q = ids.slice(i, i + 50).map((x) => `id=${x}`).join("&");
    const r = await j(`${AUDIUS}/tracks?${q}&${app}`);
    tracks.push(...(r.data ?? []).map(aTrack));
  }
  return tracks.slice(0, MAX_LIKES);
}
async function audiusPlaylists(id: string) {
  const d = await j(`${AUDIUS}/users/${id}/playlists?${app}`);
  const out = [];
  for (const p of (d.data ?? []).slice(0, MAX_PLAYLISTS)) {
    const pid = p.id ?? p.playlist_id;
    const tr = await j(`${AUDIUS}/playlists/${pid}/tracks?${app}`);
    out.push({ external_id: String(pid), name: String(p.playlist_name ?? p.name ?? "Audius"),
              tracks: (tr.data ?? []).slice(0, MAX_PER_PLAYLIST).map(aTrack) });
  }
  return out;
}

// ---------- handler ----------
Deno.serve(async (req) => {
  const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type" };
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  const reply = (b: unknown, s = 200) =>
    new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });

  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
  const { data: u } = await sb.auth.getUser();
  if (!u.user) return reply({ error: "unauthorized" }, 401);

  const { provider, account } = await req.json().catch(() => ({}));
  if (provider !== "JAMENDO" && provider !== "AUDIUS") return reply({ error: "bad_provider" }, 400);

  try {
    // reuse the saved id on re-sync, otherwise take the one the user typed
    const saved = await sb.from("linked_accounts").select("external_id,display_name").eq("provider", provider).maybeSingle();
    const who = account
      ? await (provider === "JAMENDO" ? jamendoResolve(String(account).trim()) : audiusResolve(String(account).trim()))
      : saved.data ? { id: saved.data.external_id, name: saved.data.display_name } : null;
    if (!who) return reply({ error: "account_required" }, 400);

    const [likes, lists] = provider === "JAMENDO"
      ? await Promise.all([jamendoLikes(who.id), jamendoPlaylists(who.id)])
      : await Promise.all([audiusLikes(who.id), audiusPlaylists(who.id)]);
    console.log("import", provider, "likes=" + likes.length, "playlists=" + lists.length,
      "tracks_per_playlist=" + lists.map((p) => p.tracks.length).join(","));

    // likes: only new ones; liked_at keeps the source order (first = newest)
    const have = await sb.from("likes").select("track_id").eq("provider", provider);
    const haveIds = new Set((have.data ?? []).map((r) => r.track_id));
    const base = Date.now();
    const newLikes = [...new Map(likes.map((t) => [t.track_id, t])).values()]
      .filter((t) => !haveIds.has(t.track_id))
      .map((t, i) => ({ ...t, imported_from: provider, liked_at: new Date(base - i * 1000).toISOString() }));
    if (newLikes.length) {
      const { error } = await sb.from("likes").insert(newLikes);
      if (error) throw error;
    }

    // playlists: skip those already imported, new ones get their items
    const doneP = await sb.from("playlists").select("external_id").eq("imported_from", provider);
    const doneIds = new Set((doneP.data ?? []).map((r) => r.external_id));
    let addedPlaylists = 0;
    for (const p of lists.filter((p) => !doneIds.has(p.external_id) && p.tracks.length)) {
      const { data: row, error } = await sb.from("playlists")
        .insert({ name: p.name.slice(0, 100), imported_from: provider, external_id: p.external_id })
        .select("id").single();
      if (error) throw error;
      const items = p.tracks.map((t, i) => ({ playlist_id: row.id, position: i, provider,
        track_id: t.track_id, title: t.title, artist: t.artist, artwork_url: t.artwork_url, duration_ms: t.duration_ms }));
      const ins = await sb.from("playlist_items").insert(items);
      if (ins.error) { await sb.from("playlists").delete().eq("id", row.id); throw ins.error; }
      addedPlaylists++;
    }

    const up = await sb.from("linked_accounts").upsert({
      user_id: u.user.id, provider, external_id: who.id, display_name: who.name, last_import_at: new Date().toISOString(),
    }, { onConflict: "user_id,provider" });
    if (up.error) throw up.error;
    return reply({ likesAdded: newLikes.length, playlistsAdded: addedPlaylists, account: who.name ?? who.id });
  } catch (e) {
    const msg = e instanceof Error ? e.message : JSON.stringify(e);
    console.error("import failed", provider, msg);
    return reply({ error: msg === "not_found" ? "not_found" : "import_failed" }, 502);
  }
});
