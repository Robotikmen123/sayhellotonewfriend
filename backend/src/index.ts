// Cloudflare Worker entry. Three roles:
//
//   POST /persona/upload   — store the dossier + voice_id once, after building
//                            them locally with scripts/build_persona.py.
//   POST /memory/append    — called by the iOS app at end of call.
//   POST /llm/webhook      — OpenAI-compatible chat completions endpoint that
//                            ElevenLabs Conversational AI calls for every turn.
//                            We inject persona + retrieved memories into the
//                            system prompt and stream Claude back as SSE.

import Anthropic from "@anthropic-ai/sdk";
import { buildSystemPrompt } from "./systemPrompt";
import {
    appendMemory,
    retrieveMemories,
    type Env as MemoryEnv,
} from "./memory";

interface Env extends MemoryEnv {
    DEFAULT_MODEL: string;
    USER_NAME: string;
    ELEVENLABS_WEBHOOK_TOKEN: string;
}

interface OpenAIChatRequest {
    model?: string;
    messages: { role: "system" | "user" | "assistant"; content: string }[];
    stream?: boolean;
    temperature?: number;
    max_tokens?: number;
}

export default {
    async fetch(req: Request, env: Env): Promise<Response> {
        const url = new URL(req.url);
        try {
            if (url.pathname === "/persona/upload" && req.method === "POST") {
                return await handlePersonaUpload(req, env);
            }
            if (url.pathname === "/memory/append" && req.method === "POST") {
                return await handleMemoryAppend(req, env);
            }
            if (
                (url.pathname === "/llm/webhook" ||
                    url.pathname === "/v1/chat/completions") &&
                req.method === "POST"
            ) {
                return await handleLLMWebhook(req, env);
            }
            if (url.pathname === "/health") {
                return new Response("ok\n");
            }
            return new Response("not found", { status: 404 });
        } catch (err) {
            const msg = err instanceof Error ? err.message : String(err);
            console.error(msg);
            return new Response(`error: ${msg}`, { status: 500 });
        }
    },
} satisfies ExportedHandler<Env>;

function requireAdmin(req: Request, env: Env): void {
    const auth = req.headers.get("authorization") ?? "";
    const token = auth.replace(/^Bearer\s+/i, "");
    if (!env.ELEVENLABS_WEBHOOK_TOKEN || token !== env.ELEVENLABS_WEBHOOK_TOKEN) {
        throw new Response("unauthorized", { status: 401 });
    }
}

async function handlePersonaUpload(req: Request, env: Env): Promise<Response> {
    requireAdmin(req, env);
    const body = (await req.json()) as {
        user: string;
        dossier: string;
        voiceId: string;
    };
    if (!body.user || !body.dossier) {
        return new Response("missing fields", { status: 400 });
    }
    await env.PERSONA_KV.put(`persona:${body.user}`, body.dossier);
    if (body.voiceId) {
        await env.PERSONA_KV.put(`voice:${body.user}`, body.voiceId);
    }
    return Response.json({ ok: true });
}

async function handleMemoryAppend(req: Request, env: Env): Promise<Response> {
    requireAdmin(req, env);
    const body = (await req.json()) as { user?: string; transcript: string };
    const user = body.user ?? env.USER_NAME;
    if (!body.transcript) return new Response("missing transcript", { status: 400 });
    const fragment = await appendMemory(env, user, body.transcript);
    return Response.json(fragment);
}

async function handleLLMWebhook(req: Request, env: Env): Promise<Response> {
    // ElevenLabs sends an OpenAI-compatible POST. The bearer token is whatever
    // we set in the agent's "Custom LLM" config. We accept either our shared
    // admin token or trust this path is otherwise locked down at the
    // ElevenLabs side; missing token = 401.
    requireAdmin(req, env);

    const body = (await req.json()) as OpenAIChatRequest;
    const user = env.USER_NAME;

    // Last user turn drives memory retrieval.
    const lastUser = [...body.messages].reverse().find((m) => m.role === "user");
    const query = lastUser?.content ?? "";

    const [dossierRaw, memories] = await Promise.all([
        env.PERSONA_KV.get(`persona:${user}`),
        query ? retrieveMemories(env, user, query, 6) : Promise.resolve([]),
    ]);
    if (!dossierRaw) {
        return new Response("persona not loaded — POST /persona/upload first", {
            status: 503,
        });
    }

    const systemPrompt = buildSystemPrompt({
        userName: user,
        personaDossier: dossierRaw,
        relevantMemories: memories,
        nowIso: new Date().toISOString(),
    });

    // Drop any system message ElevenLabs added — we always use ours.
    const turns = body.messages
        .filter((m) => m.role !== "system")
        .map((m) => ({ role: m.role as "user" | "assistant", content: m.content }));

    const model = body.model || env.DEFAULT_MODEL;
    const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY });

    if (body.stream) {
        return streamAnthropicAsOpenAI(client, model, systemPrompt, turns, body);
    }

    const resp = await client.messages.create({
        model,
        max_tokens: body.max_tokens ?? 400,
        temperature: body.temperature ?? 0.9,
        system: systemPrompt,
        messages: turns,
    });
    const text = resp.content
        .map((b) => (b.type === "text" ? b.text : ""))
        .join("");
    return Response.json({
        id: resp.id,
        object: "chat.completion",
        created: Math.floor(Date.now() / 1000),
        model,
        choices: [
            {
                index: 0,
                message: { role: "assistant", content: text },
                finish_reason: "stop",
            },
        ],
        usage: {
            prompt_tokens: resp.usage.input_tokens,
            completion_tokens: resp.usage.output_tokens,
            total_tokens: resp.usage.input_tokens + resp.usage.output_tokens,
        },
    });
}

function streamAnthropicAsOpenAI(
    client: Anthropic,
    model: string,
    system: string,
    turns: { role: "user" | "assistant"; content: string }[],
    body: OpenAIChatRequest,
): Response {
    const encoder = new TextEncoder();
    const id = `chatcmpl-${crypto.randomUUID()}`;
    const created = Math.floor(Date.now() / 1000);

    const stream = new ReadableStream({
        async start(controller) {
            const write = (obj: unknown) => {
                controller.enqueue(
                    encoder.encode(`data: ${JSON.stringify(obj)}\n\n`),
                );
            };
            try {
                const anthropicStream = client.messages.stream({
                    model,
                    max_tokens: body.max_tokens ?? 400,
                    temperature: body.temperature ?? 0.9,
                    system,
                    messages: turns,
                });

                // Initial role chunk so OpenAI-compatible clients see the
                // role on the first delta.
                write({
                    id,
                    object: "chat.completion.chunk",
                    created,
                    model,
                    choices: [
                        { index: 0, delta: { role: "assistant" }, finish_reason: null },
                    ],
                });

                for await (const event of anthropicStream) {
                    if (
                        event.type === "content_block_delta" &&
                        event.delta.type === "text_delta"
                    ) {
                        write({
                            id,
                            object: "chat.completion.chunk",
                            created,
                            model,
                            choices: [
                                {
                                    index: 0,
                                    delta: { content: event.delta.text },
                                    finish_reason: null,
                                },
                            ],
                        });
                    }
                }

                write({
                    id,
                    object: "chat.completion.chunk",
                    created,
                    model,
                    choices: [{ index: 0, delta: {}, finish_reason: "stop" }],
                });
                controller.enqueue(encoder.encode("data: [DONE]\n\n"));
            } catch (err) {
                const msg = err instanceof Error ? err.message : String(err);
                controller.enqueue(
                    encoder.encode(`data: {"error": ${JSON.stringify(msg)}}\n\n`),
                );
            } finally {
                controller.close();
            }
        },
    });

    return new Response(stream, {
        headers: {
            "content-type": "text/event-stream",
            "cache-control": "no-cache",
            connection: "keep-alive",
        },
    });
}
