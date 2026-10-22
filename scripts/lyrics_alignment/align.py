"""本地强制对齐：只输出输入歌词的真实模型时间，不补造缺失时间。"""
import argparse
import hashlib
import json
import math
import os
import re
from pathlib import Path


def clean_lyrics(text):
    lines = [re.sub(r"\[[^\]\n]*\]", "", line).strip() for line in text.splitlines()]
    return "\n".join(line for line in lines if line)


def compact(text):
    return "".join(text.split())


def validate(result, text, duration):
    segments = result["segments"]
    if compact("".join(s["text"] for s in segments)) != compact(text):
        raise ValueError("ALIGNMENT_TEXT_MISMATCH")
    expected = text.splitlines()
    if len(segments) != len(expected):
        raise ValueError("ALIGNMENT_LINES_MISMATCH")
    words, previous, low, count = [], 0.0, 0, 0
    for line, segment in zip(expected, segments):
        entries = segment.get("words", [])
        if not entries or compact("".join(w["word"] for w in entries)) != compact(line):
            raise ValueError("ALIGNMENT_WORDS_MISMATCH")
        for i, word in enumerate(entries):
            start, end = float(word["start"]), float(word["end"])
            probability = float(word.get("probability", 0))
            if not all(math.isfinite(v) for v in (start, end, probability)) or start < previous - .001 or end <= start or end > duration + .05:
                raise ValueError("ALIGNMENT_INVALID_TIMING")
            previous = end
            count += 1
            low += probability < .15
            words.append({"word": word["word"] + ("\n" if i == len(entries) - 1 else ""),
                          "startS": start, "endS": end, "success": True,
                          "probability": probability})
    if not count or low / count > .2:
        raise ValueError("ALIGNMENT_LOW_CONFIDENCE")
    return {"code": 200, "data": {"alignedWords": words},
            "quality": {"wordCount": count, "lowConfidenceWords": low, "textCoverage": 1.0}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--audio", required=True)
    parser.add_argument("--lyrics", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--model", default="small")
    parser.add_argument("--language", default="auto")
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()
    import torch
    import whisper
    import stable_whisper
    torch.set_num_threads(max(1, min(args.threads, 4)))
    text = clean_lyrics(Path(args.lyrics).read_text())
    if not text or len(text) > 50000:
        raise ValueError("ALIGNMENT_INVALID_LYRICS")
    audio = whisper.load_audio(args.audio)
    duration = len(audio) / 16000
    if not 0 < duration <= 600:
        raise ValueError("ALIGNMENT_INVALID_DURATION")
    model_url = whisper._MODELS.get(args.model)
    if not model_url:
        raise ValueError("ALIGNMENT_UNKNOWN_MODEL")
    cache = Path(os.getenv("XDG_CACHE_HOME", str(Path.home() / ".cache"))) / "whisper"
    checkpoint = cache / model_url.rsplit("/", 1)[-1]
    if not checkpoint.is_file():
        raise ValueError("ALIGNMENT_MODEL_NOT_PREPARED")
    with checkpoint.open("rb") as handle:
        if hashlib.file_digest(handle, "sha256").hexdigest() != model_url.split("/")[-2]:
            raise ValueError("ALIGNMENT_MODEL_CHECKSUM_FAILED")
    model = stable_whisper.load_model(args.model, device="cpu", download_root=str(cache))
    language = args.language
    if language == "auto":
        mel = whisper.log_mel_spectrogram(whisper.pad_or_trim(audio), n_mels=model.dims.n_mels)
        _, probabilities = model.detect_language(mel)
        language = max(probabilities, key=probabilities.get)
    from anchored import windows, ctc_align
    recognized = model.transcribe(audio, language=language, regroup=False, verbose=None, fp16=False)
    segments = windows(text, recognized.to_dict(), duration)
    if language == "en":
        raw = ctc_align(audio, segments)
        engine = "whisper-anchors+wav2vec2-ctc"
    else:
        aligned = model.align_words(audio, segments, language=language, regroup=False, verbose=None)
        raw = aligned.to_dict()
        engine = "whisper-anchors+stable-ts"
    Path(args.output + ".diagnostic.json").write_text(json.dumps(raw, ensure_ascii=False))
    result = validate(raw, text, duration)
    result.update({"source": "forced-alignment", "engine": engine, "model": args.model,
                   "language": language, "durationSeconds": duration,
                   "lyricsSha256": hashlib.sha256(text.encode()).hexdigest()})
    target = Path(args.output)
    temporary = target.with_suffix(".partial")
    temporary.write_text(json.dumps(result, ensure_ascii=False))
    temporary.replace(target)


if __name__ == "__main__":
    main()
