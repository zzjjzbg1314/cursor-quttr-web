"""识别结果只定位音频区间；最终逐词对齐始终使用用户原始歌词。"""
import difflib
import re


def normalized(text):
    return ''.join(c.lower() for c in text if c.isalnum())


def windows(text, recognized, duration):
    lines = text.splitlines()
    source = ''.join(normalized(line) for line in lines)
    heard, references = '', []
    for segment in recognized['segments']:
        for word in segment.get('words', []):
            token = normalized(word['word'])
            heard += token
            references.extend([word] * len(token))
    matched = {}
    for block in difflib.SequenceMatcher(None, source, heard, autojunk=False).get_matching_blocks():
        for i in range(block.size):
            matched[block.a + i] = references[block.b + i]
    if not source or len(matched) / len(source) < .8:
        raise ValueError('ALIGNMENT_ANCHORS_UNRELIABLE')
    result, offset = [], 0
    for line in lines:
        token = normalized(line)
        anchors = [matched[i] for i in range(offset, offset + len(token)) if i in matched]
        offset += len(token)
        if not token or len(anchors) / len(token) < .6:
            raise ValueError('ALIGNMENT_LINE_ANCHOR_MISSING')
        start, end = max(0, anchors[0]['start'] - .3), min(duration, anchors[-1]['end'] + .3)
        if not 0 < end - start <= 30:
            raise ValueError('ALIGNMENT_LINE_WINDOW_INVALID')
        result.append(dict(text=line, start=start, end=end))
    # 相邻搜索区间不重叠；此处是模型搜索范围，不作为最终歌词时间。
    for left, right in zip(result, result[1:]):
        if left['end'] > right['start']:
            boundary = (left['end'] + right['start']) / 2
            left['end'] = right['start'] = boundary
    return result


def english_tokens(line):
    words = re.findall(r'\s*\S+', line)
    normalized_words = []
    for word in words:
        value = word.upper().replace('’', "'")
        if any(c.isalnum() and c not in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' for c in value):
            raise ValueError('ALIGNMENT_CTC_UNSUPPORTED_TEXT')
        value = re.sub("[^A-Z']", '', value)
        if not value:
            raise ValueError('ALIGNMENT_CTC_UNSUPPORTED_TEXT')
        normalized_words.append(value)
    return words, normalized_words


def ctc_align(audio, segments):
    import torch
    import torchaudio
    from pathlib import Path
    bundle = torchaudio.pipelines.WAV2VEC2_ASR_BASE_960H
    checkpoint = Path(torch.hub.get_dir()) / 'checkpoints' / bundle._path
    if not checkpoint.is_file():
        raise ValueError('ALIGNMENT_CTC_MODEL_NOT_PREPARED')
    model = bundle.get_model().eval()
    labels = {value: index for index, value in enumerate(bundle.get_labels())}
    result = []
    with torch.inference_mode():
        for segment in segments:
            start = int(segment['start'] * 16000)
            end = int(segment['end'] * 16000)
            clip = torch.from_numpy(audio[start:end]).unsqueeze(0)
            emissions, _ = model(clip)
            words, values = english_tokens(segment['text'])
            tokens, offsets = [], []
            for value in values:
                offsets.append((len(tokens), len(value)))
                tokens.extend(labels[c] for c in value)
                tokens.append(labels['|'])
            aligned, scores = torchaudio.functional.forced_align(
                emissions.log_softmax(-1), torch.tensor([tokens]), blank=0)
            spans = torchaudio.functional.merge_tokens(aligned[0], scores[0].exp())
            if len(spans) != len(tokens):
                raise ValueError('ALIGNMENT_CTC_TOKEN_MISMATCH')
            ratio = (end - start) / emissions.shape[1] / 16000
            entries = []
            for word, (index, length) in zip(words, offsets):
                spans_for_word = spans[index:index + length]
                probability = sum(s.score * (s.end - s.start) for s in spans_for_word) / sum(s.end - s.start for s in spans_for_word)
                entries.append(dict(word=word, start=start / 16000 + spans_for_word[0].start * ratio,
                                    end=start / 16000 + spans_for_word[-1].end * ratio, probability=probability))
            result.append(dict(text=segment['text'], words=entries))
    return dict(segments=result)
