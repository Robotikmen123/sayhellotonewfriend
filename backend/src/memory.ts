// Long-term memory for the digital twin.
//
// On session end the client posts the transcript here. We summarize it with
// Haiku and embed the summary with Voyage. Retrieval at runtime is a simple
// cosine-similarity scan over the embedding index in KV — small N, no vector
// DB needed for a one-person system.

import Anthropic from "@anthropic-ai/sdk";
import type { MemoryFragment } from "./systemPrompt";

export interface Env {
    PERSONA_KV: KVNamespace;
    ANTHROPIC_API_KEY: string;
    VOYAGE_API_KEY: string;
    SUMMARY_MODEL: string;
    EMBED_MODEL: string;
}

interface MemoryIndexEntry {
    ts: string;
    embedding: number[];
    summary: string;
}

const INDEX_KEY = (user: string) => `memory_index:${user}`;
const CHUNK_KEY = (user: string, ts: string) => `memory:${user}:${ts}`;

/** Summarize a transcript into a single dense paragraph of "what happened". */
export async function summarizeTranscript(
    env: Env,
    transcript: string,
    userName: string,
): Promise<string> {
    const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY });
    const resp = await client.messages.create({
        model: env.SUMMARY_MODEL,
        max_tokens: 400,
        system:
            "Sen bir günlük tutucusun. Aşağıda " +
            userName +
            " ile dijital ikizi arasındaki sesli konuşmanın transcript'i var. " +
            "Bu konuşmadan akılda kalması gereken olguları (kim, ne, ne hissettiği, " +
            "hangi karara vardığı, hangi insanlar/yerler geçti) tek bir paragrafta " +
            "üçüncü tekil şahıs olarak yaz. 100-150 kelime. Selamlama-veda yok, " +
            "doğrudan içerik.",
        messages: [{ role: "user", content: transcript }],
    });
    const first = resp.content[0];
    return first.type === "text" ? first.text.trim() : "";
}

export async function embed(env: Env, text: string): Promise<number[]> {
    const r = await fetch("https://api.voyageai.com/v1/embeddings", {
        method: "POST",
        headers: {
            "content-type": "application/json",
            authorization: `Bearer ${env.VOYAGE_API_KEY}`,
        },
        body: JSON.stringify({
            model: env.EMBED_MODEL,
            input: [text],
            input_type: "document",
        }),
    });
    if (!r.ok) throw new Error(`voyage embed failed: ${r.status} ${await r.text()}`);
    const j = (await r.json()) as { data: { embedding: number[] }[] };
    return j.data[0].embedding;
}

export async function embedQuery(env: Env, text: string): Promise<number[]> {
    const r = await fetch("https://api.voyageai.com/v1/embeddings", {
        method: "POST",
        headers: {
            "content-type": "application/json",
            authorization: `Bearer ${env.VOYAGE_API_KEY}`,
        },
        body: JSON.stringify({
            model: env.EMBED_MODEL,
            input: [text],
            input_type: "query",
        }),
    });
    if (!r.ok) throw new Error(`voyage embed failed: ${r.status} ${await r.text()}`);
    const j = (await r.json()) as { data: { embedding: number[] }[] };
    return j.data[0].embedding;
}

export async function appendMemory(
    env: Env,
    user: string,
    transcript: string,
): Promise<MemoryFragment> {
    const ts = new Date().toISOString();
    const summary = await summarizeTranscript(env, transcript, user);
    const embedding = await embed(env, summary);

    await env.PERSONA_KV.put(
        CHUNK_KEY(user, ts),
        JSON.stringify({ ts, summary, transcript }),
    );

    const raw = await env.PERSONA_KV.get(INDEX_KEY(user));
    const index: MemoryIndexEntry[] = raw ? JSON.parse(raw) : [];
    index.push({ ts, embedding, summary });
    await env.PERSONA_KV.put(INDEX_KEY(user), JSON.stringify(index));

    return { ts, summary };
}

function cosine(a: number[], b: number[]): number {
    let dot = 0;
    let na = 0;
    let nb = 0;
    for (let i = 0; i < a.length; i++) {
        dot += a[i] * b[i];
        na += a[i] * a[i];
        nb += b[i] * b[i];
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9);
}

/** Return the top-K memories most relevant to `query`, plus the freshest 2. */
export async function retrieveMemories(
    env: Env,
    user: string,
    query: string,
    k: number = 6,
): Promise<MemoryFragment[]> {
    const raw = await env.PERSONA_KV.get(INDEX_KEY(user));
    if (!raw) return [];
    const index: MemoryIndexEntry[] = JSON.parse(raw);
    if (index.length === 0) return [];

    const q = await embedQuery(env, query);
    const scored = index.map((e) => ({ ...e, score: cosine(q, e.embedding) }));
    scored.sort((a, b) => b.score - a.score);
    const topK = scored.slice(0, k);

    // Always glue on the 2 most recent memories so "yesterday" stays warm.
    const recent = [...index].sort((a, b) => b.ts.localeCompare(a.ts)).slice(0, 2);

    const seen = new Set<string>();
    const merged: MemoryFragment[] = [];
    for (const m of [...topK, ...recent]) {
        if (seen.has(m.ts)) continue;
        seen.add(m.ts);
        merged.push({ ts: m.ts, summary: m.summary });
    }
    return merged;
}
