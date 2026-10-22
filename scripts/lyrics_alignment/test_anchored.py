import unittest
from anchored import windows, english_tokens

class AnchoredTests(unittest.TestCase):
    def test_repeated_chorus_uses_ordered_audio_positions(self):
        raw = {'segments': [{'words': [
            {'word': 'Hello', 'start': 1, 'end': 2}, {'word': 'world', 'start': 2, 'end': 3},
            {'word': 'Again', 'start': 8, 'end': 9},
            {'word': 'Hello', 'start': 15, 'end': 16}, {'word': 'world', 'start': 16, 'end': 17}]}]}
        out = windows('Hello world\nAgain\nHello world', raw, 20)
        self.assertEqual([s['text'] for s in out], ['Hello world', 'Again', 'Hello world'])
        self.assertLess(out[0]['end'], out[1]['start'])
        self.assertGreater(out[2]['start'], 14)
    def test_unrelated_audio_is_rejected(self):
        with self.assertRaises(ValueError):
            windows('Original lyrics', {'segments': [{'words': [{'word': 'Different', 'start': 1, 'end': 2}]}]}, 3)
    def test_unicode_original_is_not_translated(self):
        for text in ['你好世界','您好世界','こんにちは','안녕하세요','Olá mundo','Hola mundo','Guten Tag','Bonjour monde']:
            out=windows(text,{'segments':[{'words':[{'word':text,'start':1,'end':2}]}]},3)
            self.assertEqual(out[0]['text'],text)
    def test_ctc_preserves_original_punctuation_but_rejects_unspoken_numbers(self):
        words,tokens=english_tokens("We don’t leave.")
        self.assertEqual(''.join(words),"We don’t leave.")
        self.assertEqual(tokens,['WE',"DON'T",'LEAVE'])
        with self.assertRaises(ValueError): english_tokens('Year 2026')

if __name__ == '__main__': unittest.main()
