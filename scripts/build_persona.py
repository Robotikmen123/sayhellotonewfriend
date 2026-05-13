#!/usr/bin/env python3
"""
Persona dossier builder.

Takes a JSONL stream of messages produced by extract_whatsapp.py and asks
Claude Opus to write a dense persona dossier in Turkish that captures HOW
the user speaks: vocabulary, rhythm, humor, opinions, life facts.

The dossier is later injected as the system prompt for the digital twin.

Usage:
    export ANTHROPIC_API_KEY=...
    python scripts/build_persona.py \
        --in data/messages.jsonl \
        --name "Ali" \
        --out persona_doc.md
"""
from __future__ import annotations

import argparse
import json
import os
import random
import sys
import textwrap
from pathlib import Path

import anthropic


MODEL = "claude-opus-4-7"

PROMPT_TEMPLATE = """Aşağıda {name} adlı bir kişinin WhatsApp sohbetlerinden alınmış
mesajları var. Senin işin: {name}'in DIGITAL TWIN'ini (dijital ikizini)
kuracak başka bir modelin SYSTEM PROMPT'una koyulacak bir
"persona dossier" yazmak.

Bu dossier {name} olarak rol yapacak modeli besleyecek — yani başkasının
{name} gibi konuşmasını sağlayacak. Olabildiğince zengin, somut, taklit
edilebilir olmalı.

Dossier şu bölümleri içersin (her bölüm Türkçe, kısa başlıklarla):

1. **Kimlik temel:** Yaş aralığı (tahmin), şehir/ortam, iş/öğrenim, ana ilişkiler
   (aile, partner, en yakın arkadaşlar — isimleriyle).
2. **Konuşma tarzı (mikro detay):**
   - Ortalama mesaj uzunluğu (kısa/orta/uzun)
   - Noktalama alışkanlığı (nokta koyar mı, virgül, üç nokta, soru işareti?)
   - Büyük/küçük harf (hep küçük mü, normal mi?)
   - Emoji kullanır mı, hangi emojiler?
   - Yazım hataları, kısaltmalar (knk, slm, mrb, kib vs.)
   - Argo/küfür seviyesi (örnekle)
3. **Sık kullanılan kalıplar:** En az 20 adet, her biri tırnak içinde gerçek bir
   örnek olacak şekilde. (Örnek: "ya hadi ya", "valla bilmem", "kanka napıyon")
4. **Mizah / ironi:** Nasıl şaka yapar, neye güler, sarkastik mi?
5. **Görüşler ve takıntılar:** Sevdiği müzik, dizi, takım, yemek, nefret ettiği
   şeyler — mesajlardan çıkarabildiğin kadar somut.
6. **Hayat olayları ("anılar"):** Mesajlarda geçen önemli olaylar — iş değişikliği,
   ayrılık, taşınma, hastalık, doğum günü vb. Tarihleriyle birlikte.
7. **Birebir örnek mesajlar:** En az 30 adet, gerçek mesajlarından, üslup
   yelpazesini gösteren çeşitlilikte. Madde işareti olmadan, her satıra bir
   mesaj. Bunlar few-shot olarak kullanılacak — orijinaliyle birebir aynı
   olmaları kritik.
8. **ASLA şöyle konuşma:** {name}'in tarzına aykırı düşen yapay-zeka klişeleri.
   Bu bölüm her digital twin için ortak ama özelleştir:
   - "Yapay zeka", "yardımcı olabilirim", "tabii ki", "anlıyorum", "harika"
   - Liste halinde madde madde cevap (sohbette böyle konuşulmaz)
   - Aşırı resmi/nazik dil
   - "X ister misin? Y de yapabilirim" tarzı assistant tonu
   - Emoji yağmuru, başlıklar, kalın yazı
   - Hiç bilmediği bir şeyi olduğundan emin gibi anlatmak

Dossier MARKDOWN formatında olsun. Mümkün olduğunca somut ve örnekli yaz.
{name} olmayan birinin okuyup "{name} gibi" konuşmasını mümkün kılacak kadar
detaylı olsun. 2000-3000 kelime hedefle.

---

İşte mesajlar (sadece {name}'in kendi mesajları, en zengin tarz örneği için):

{my_messages}

---

Karşı taraflardan da bazı örnekler (kime nasıl yazdığını anlaman için):

{context_messages}

---

Şimdi dossier'i yaz. Doğrudan markdown ile başla, ön söz/açıklama yazma.
"""


def load_jsonl(path: Path) -> list[dict]:
    out = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out


def sample_messages(msgs: list[dict], n: int, is_me: bool) -> list[dict]:
    pool = [m for m in msgs if m["is_me"] == is_me]
    if len(pool) <= n:
        return pool
    return random.sample(pool, n)


def format_messages(msgs: list[dict], with_chat: bool = False) -> str:
    lines = []
    for m in msgs:
        prefix = f"[{m['chat']}] " if with_chat else ""
        lines.append(f"{prefix}{m['sender']}: {m['body']}")
    return "\n".join(lines)


def build_dossier(my_msgs: list[dict], context_msgs: list[dict], name: str) -> str:
    prompt = PROMPT_TEMPLATE.format(
        name=name,
        my_messages=format_messages(my_msgs),
        context_messages=format_messages(context_msgs, with_chat=True),
    )

    client = anthropic.Anthropic()
    resp = client.messages.create(
        model=MODEL,
        max_tokens=8000,
        temperature=0.4,
        system=(
            "Sen kişilik analizi yapan ve digital-twin persona dossier'ları "
            "yazan bir Türkçe yazardın. Çıktın doğrudan başka bir LLM'in "
            "system prompt'una konulacak — net, somut, taklit-edilebilir yaz."
        ),
        messages=[{"role": "user", "content": prompt}],
    )
    return resp.content[0].text


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--in", dest="in_path", required=True, type=Path)
    ap.add_argument("--name", required=True, help="Kullanıcının ön adı")
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--my-samples", type=int, default=400,
                    help="How many of the user's messages to include in the prompt")
    ap.add_argument("--context-samples", type=int, default=80,
                    help="How many counterpart messages to include for context")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    if "ANTHROPIC_API_KEY" not in os.environ:
        print("ANTHROPIC_API_KEY env var is required", file=sys.stderr)
        return 1

    random.seed(args.seed)
    msgs = load_jsonl(args.in_path)
    my_msgs = sample_messages(msgs, args.my_samples, is_me=True)
    ctx_msgs = sample_messages(msgs, args.context_samples, is_me=False)
    if not my_msgs:
        print("no 'is_me' messages in input; check extract_whatsapp.py --me", file=sys.stderr)
        return 1

    print(
        f"building dossier from {len(my_msgs)} of your msgs + {len(ctx_msgs)} context msgs",
        file=sys.stderr,
    )
    dossier = build_dossier(my_msgs, ctx_msgs, args.name)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(dossier, encoding="utf-8")
    print(f"wrote {args.out} ({len(dossier)} chars)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
