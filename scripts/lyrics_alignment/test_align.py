import unittest
from align import clean_lyrics, validate

class AlignmentTests(unittest.TestCase):
    def test_sections_and_multilingual_text_are_preserved(self):
        for text in ['Hello', '你好', '您好', 'こんにちは', '안녕하세요', 'Olá', 'Hola', 'Hallo', 'Bonjour']:
            self.assertEqual(clean_lyrics('[Verse]\n' + text), text)
            result = validate({'segments': [{'text': text, 'words': [{'word': text, 'start': 1, 'end': 2, 'probability': .9}]}]}, text, 3)
            self.assertEqual(result['data']['alignedWords'][0]['word'], text + '\n')
    def test_does_not_invent_missing_or_zero_length_words(self):
        for start, end in [(1, 1), (2, 1), (1, 4)]:
            with self.assertRaises(ValueError):
                validate({'segments': [{'text': 'Hello', 'words': [{'word': 'Hello', 'start': start, 'end': end, 'probability': .9}]}]}, 'Hello', 3)
        with self.assertRaises(ValueError):
            validate({'segments': []}, 'Hello', 3)
    def test_low_confidence_is_rejected(self):
        with self.assertRaises(ValueError):
            validate({'segments': [{'text': 'Hello', 'words': [{'word': 'Hello', 'start': 1, 'end': 2, 'probability': .01}]}]}, 'Hello', 3)

if __name__ == '__main__':
    unittest.main()
