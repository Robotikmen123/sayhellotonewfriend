#!/usr/bin/env bash
# Build an ElevenLabs Instant Voice Clone from voice notes inside WhatsApp
# iOS exports.
#
# Pipeline:
#   1. Extract .m4a voice notes from every .zip in $EXPORTS_DIR
#   2. Drop clips < MIN_SECONDS
#   3. Concatenate the longest N into a single mono 44.1kHz WAV
#   4. Trim long silences, loudness-normalize, re-encode to MP3 192k
#   5. (Optional) Run through ElevenLabs Voice Isolator
#   6. Upload to ElevenLabs Instant Voice Cloning → print voice_id
#
# Required env:
#   ELEVENLABS_API_KEY
#
# Required tools on PATH:
#   ffmpeg, ffprobe, jq, curl, unzip
#
# Usage:
#   ELEVENLABS_API_KEY=... ./scripts/clone_from_whatsapp.sh \
#       --exports ./exports \
#       --name "Ali Yilmaz"

set -euo pipefail

EXPORTS_DIR=""
VOICE_NAME=""
MIN_SECONDS=8
MAX_CLIPS=20
TARGET_SECONDS=150
WORK_DIR="$(mktemp -d -t persona-clone-XXXXXX)"
USE_ISOLATOR=1

while [[ $# -gt 0 ]]; do
    case "$1" in
        --exports) EXPORTS_DIR="$2"; shift 2 ;;
        --name) VOICE_NAME="$2"; shift 2 ;;
        --min-seconds) MIN_SECONDS="$2"; shift 2 ;;
        --max-clips) MAX_CLIPS="$2"; shift 2 ;;
        --target-seconds) TARGET_SECONDS="$2"; shift 2 ;;
        --no-isolator) USE_ISOLATOR=0; shift ;;
        --work-dir) WORK_DIR="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,/^set/p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 1 ;;
    esac
done

if [[ -z "$EXPORTS_DIR" || -z "$VOICE_NAME" ]]; then
    echo "usage: $0 --exports DIR --name NAME [--min-seconds N] [--max-clips N] [--no-isolator]" >&2
    exit 1
fi

if [[ -z "${ELEVENLABS_API_KEY:-}" ]]; then
    echo "ELEVENLABS_API_KEY env var is required" >&2
    exit 1
fi

for bin in ffmpeg ffprobe jq curl unzip; do
    if ! command -v "$bin" >/dev/null; then
        echo "missing required tool: $bin" >&2
        exit 1
    fi
done

mkdir -p "$WORK_DIR/raw" "$WORK_DIR/cut"
echo "→ work dir: $WORK_DIR" >&2

# 1. Extract every .m4a / .opus voice note from the zips.
echo "→ extracting voice notes from $EXPORTS_DIR" >&2
i=0
for zip in "$EXPORTS_DIR"/*.zip; do
    [[ -f "$zip" ]] || continue
    while IFS= read -r entry; do
        ext="${entry##*.}"
        out="$WORK_DIR/raw/$(printf '%04d' $i).$ext"
        unzip -p "$zip" "$entry" > "$out" || continue
        i=$((i + 1))
    done < <(unzip -Z1 "$zip" | grep -iE '\.(m4a|opus)$' || true)
done
echo "→ extracted $i clip(s)" >&2
if [[ "$i" -eq 0 ]]; then
    echo "no voice notes found. did you export 'Attach Media' in WhatsApp?" >&2
    exit 1
fi

# 2. Probe duration, filter, and pick the longest MAX_CLIPS.
echo "→ filtering by duration ≥ ${MIN_SECONDS}s, keeping longest $MAX_CLIPS" >&2
durations="$WORK_DIR/durations.tsv"
: > "$durations"
for f in "$WORK_DIR"/raw/*; do
    dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f" 2>/dev/null || echo "0")
    awk -v d="$dur" -v m="$MIN_SECONDS" -v f="$f" \
        'BEGIN { if (d+0 >= m+0) printf "%s\t%s\n", d, f }' >> "$durations"
done
sort -rn "$durations" | head -n "$MAX_CLIPS" | cut -f2 > "$WORK_DIR/picked.txt"
count=$(wc -l < "$WORK_DIR/picked.txt" | tr -d ' ')
echo "→ picked $count clip(s)" >&2
if [[ "$count" -eq 0 ]]; then
    echo "no clips long enough. try lowering --min-seconds." >&2
    exit 1
fi

# 3. Concat to one mono 44.1kHz WAV.
list="$WORK_DIR/concat.txt"
: > "$list"
while IFS= read -r f; do
    echo "file '$f'" >> "$list"
done < "$WORK_DIR/picked.txt"
concat_wav="$WORK_DIR/concat.wav"
ffmpeg -hide_banner -loglevel error -y -f concat -safe 0 -i "$list" \
    -ac 1 -ar 44100 "$concat_wav"

# 4. Silence-trim + loudness-normalize, target ≈ TARGET_SECONDS.
trimmed_wav="$WORK_DIR/trimmed.wav"
ffmpeg -hide_banner -loglevel error -y -i "$concat_wav" \
    -af "silenceremove=stop_periods=-1:stop_duration=0.4:stop_threshold=-40dB,loudnorm=I=-16:TP=-1.5:LRA=11" \
    -t "$TARGET_SECONDS" "$trimmed_wav"

clean_mp3="$WORK_DIR/clean.mp3"
ffmpeg -hide_banner -loglevel error -y -i "$trimmed_wav" -b:a 192k "$clean_mp3"
final_dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$clean_mp3")
echo "→ cleaned sample: $clean_mp3 (${final_dur}s)" >&2

# 5. Optional: ElevenLabs Voice Isolator.
upload_path="$clean_mp3"
if [[ "$USE_ISOLATOR" -eq 1 ]]; then
    isolated="$WORK_DIR/isolated.mp3"
    echo "→ running ElevenLabs Voice Isolator" >&2
    http_code=$(curl -sS -o "$isolated" -w "%{http_code}" \
        -X POST "https://api.elevenlabs.io/v1/audio-isolation" \
        -H "xi-api-key: $ELEVENLABS_API_KEY" \
        -F "audio=@${clean_mp3}")
    if [[ "$http_code" != "200" ]]; then
        echo "voice isolator failed (HTTP $http_code), continuing without it" >&2
        cat "$isolated" >&2 || true
    else
        upload_path="$isolated"
        echo "→ isolated audio ready" >&2
    fi
fi

# 6. Upload to Instant Voice Cloning.
echo "→ creating ElevenLabs voice '$VOICE_NAME'" >&2
resp="$WORK_DIR/clone_response.json"
http_code=$(curl -sS -o "$resp" -w "%{http_code}" \
    -X POST "https://api.elevenlabs.io/v1/voices/add" \
    -H "xi-api-key: $ELEVENLABS_API_KEY" \
    -F "name=${VOICE_NAME}" \
    -F "description=Digital twin voice cloned from WhatsApp voice notes." \
    -F "files=@${upload_path}")

if [[ "$http_code" != "200" ]]; then
    echo "voice clone failed (HTTP $http_code)" >&2
    cat "$resp" >&2
    exit 1
fi

voice_id=$(jq -r '.voice_id' < "$resp")
echo "$voice_id"
echo "→ voice_id: $voice_id (saved sample at $upload_path)" >&2
