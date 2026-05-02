import fs from 'node:fs';

const SUPPORTED_LANGUAGES = ['zh', 'en', 'ja', 'ko', 'de', 'fr', 'pt', 'es'];
const DEFAULT_START = '2026-04-01T00:00:00+08:00';

const startDateTime = process.argv[2] || DEFAULT_START;
const deepSeekApiKey = process.env.DEEPSEEK_API_KEY;
const deepSeekApiUrl = process.env.DEEPSEEK_API_URL || 'https://api.deepseek.com/chat/completions';
const deepSeekModel = process.env.DEEPSEEK_API_MODEL || 'deepseek-v4-flash';
const concurrency = Number(process.env.BACKFILL_CONCURRENCY || '3');

if (!deepSeekApiKey) {
  throw new Error('DEEPSEEK_API_KEY is required');
}

const applicationYml = fs.readFileSync('src/main/resources/application.yml', 'utf8');
const d1Config = {
  databaseId: matchConfig(/database-id:\s*([^\n]+)/),
  accountId: process.env.CLOUDFLARE_ACCOUNT_ID || matchConfig(/account-id:\s*\$\{CLOUDFLARE_ACCOUNT_ID:([^}]+)\}/),
  apiToken: process.env.CLOUDFLARE_API_TOKEN || matchConfig(/api-token:\s*\$\{CLOUDFLARE_API_TOKEN:([^}]+)\}/),
};
const d1Endpoint = `https://api.cloudflare.com/client/v4/accounts/${d1Config.accountId}/d1/database/${d1Config.databaseId}/query`;

let translatedPostCount = 0;
let translatedCommentCount = 0;
let failedPostCount = 0;
let failedCommentCount = 0;

console.log(`[backfill] start=${startDateTime}, model=${deepSeekModel}, concurrency=${concurrency}`);

const posts = await d1Query(
  `SELECT p.*, u.emojiCountry AS user_emojiCountry
   FROM posts p
   LEFT JOIN users u ON p.user_id = u.id
   WHERE p.created_at >= ?
     AND (p.translation_status IS NULL OR p.translation_status != 'completed')
   ORDER BY p.created_at ASC`,
  [startDateTime],
);

const comments = await d1Query(
  `SELECT c.*, u.emojiCountry AS user_emojiCountry
   FROM comments c
   LEFT JOIN users u ON c.user_id = u.id
   WHERE c.post_id IN (SELECT post_id FROM posts WHERE created_at >= ?)
     AND (c.translation_status IS NULL OR c.translation_status != 'completed')
   ORDER BY c.created_at ASC`,
  [startDateTime],
);

console.log(`[backfill] posts=${posts.length}, comments=${comments.length}`);

await processRows('post', posts, async post => {
  const id = post.post_id;
  try {
    await backfillRow('posts', 'post_id', id, post, 'post');
    translatedPostCount += 1;
    console.log(`[backfill] post completed ${translatedPostCount}/${posts.length}: ${id}`);
  } catch (error) {
    failedPostCount += 1;
    console.error(`[backfill] post failed ${id}: ${error.message}`);
    await markFailed('posts', 'post_id', id);
  }
});

await processRows('comment', comments, async comment => {
  const id = comment.comment_id;
  try {
    await backfillRow('comments', 'comment_id', id, comment, 'comment');
    translatedCommentCount += 1;
    console.log(`[backfill] comment completed ${translatedCommentCount}/${comments.length}: ${id}`);
  } catch (error) {
    failedCommentCount += 1;
    console.error(`[backfill] comment failed ${id}: ${error.message}`);
    await markFailed('comments', 'comment_id', id);
  }
});

console.log(JSON.stringify({
  startDateTime,
  translatedPostCount,
  translatedCommentCount,
  failedPostCount,
  failedCommentCount,
}, null, 2));

function matchConfig(pattern) {
  const matched = applicationYml.match(pattern);
  if (!matched) {
    throw new Error(`Missing config pattern: ${pattern}`);
  }
  return matched[1].trim();
}

async function processRows(label, rows, handler) {
  let index = 0;
  const workerCount = Math.max(1, Math.min(concurrency, rows.length));
  const workers = Array.from({ length: workerCount }, async (_, workerIndex) => {
    while (index < rows.length) {
      const currentIndex = index;
      index += 1;
      console.log(`[backfill] ${label} worker ${workerIndex + 1}/${workerCount} processing ${currentIndex + 1}/${rows.length}`);
      await handler(rows[currentIndex]);
    }
  });
  await Promise.all(workers);
}

async function backfillRow(tableName, idColumn, id, row, contentType) {
  const content = String(row.content || '').trim();
  if (!content) {
    throw new Error('content is empty');
  }

  const originalLanguage = normalizeOriginalLanguage(row.original_language, content);
  const now = new Date().toISOString();
  await d1Execute(
    `UPDATE ${tableName} SET translation_status = ?, updated_at = ? WHERE ${idColumn} = ?`,
    ['processing', now, id],
  );

  const translations = {};
  for (const targetLanguage of SUPPORTED_LANGUAGES) {
    translations[`content_${targetLanguage}`] = targetLanguage === originalLanguage
      ? content
      : await translateCommunityText(content, contentType, targetLanguage);
  }

  const emojiCountry = firstNonBlank(row.emojiCountry, row.user_emojiCountry, defaultEmojiCountryByLanguage(originalLanguage));
  await d1Execute(
    `UPDATE ${tableName}
     SET original_language = ?,
         content_zh = ?,
         content_en = ?,
         content_ja = ?,
         content_ko = ?,
         content_de = ?,
         content_fr = ?,
         content_pt = ?,
         content_es = ?,
         translation_status = ?,
         translated_at = ?,
         updated_at = ?,
         emojiCountry = ?
     WHERE ${idColumn} = ?`,
    [
      originalLanguage,
      translations.content_zh,
      translations.content_en,
      translations.content_ja,
      translations.content_ko,
      translations.content_de,
      translations.content_fr,
      translations.content_pt,
      translations.content_es,
      'completed',
      now,
      now,
      emojiCountry,
      id,
    ],
  );
}

async function markFailed(tableName, idColumn, id) {
  await d1Execute(
    `UPDATE ${tableName} SET translation_status = ?, updated_at = ? WHERE ${idColumn} = ?`,
    ['failed', new Date().toISOString(), id],
  );
}

async function d1Query(sql, params = []) {
  const result = await d1Request(sql, params);
  return result?.[0]?.results || [];
}

async function d1Execute(sql, params = []) {
  await d1Request(sql, params);
}

async function d1Request(sql, params = []) {
  const response = await fetch(d1Endpoint, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${d1Config.apiToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ sql, params }),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`D1 HTTP ${response.status}: ${text}`);
  }
  const json = JSON.parse(text);
  if (!json.success) {
    throw new Error(`D1 error: ${JSON.stringify(json.errors)}`);
  }
  return json.result || [];
}

async function translateCommunityText(sourceText, contentType, targetLanguage) {
  const messages = [
    {
      role: 'system',
      content:
        `You are translating user-generated community content into natural ${languageName(targetLanguage)} for a recovery app. ` +
        'The topic is quitting pornography, recovery, relapse, self-control, and mutual support. ' +
        'Preserve the original meaning, emotional tone, and humility. ' +
        'Use safe, non-explicit language suitable for an app store community. ' +
        'Do not add facts, do not moralize, do not turn it into marketing copy, and return only the translation text.',
    },
    {
      role: 'user',
      content:
        `Translate the following ${contentType} into fluent, concise ${languageName(targetLanguage)}.\n` +
        `Source text:\n${sourceText}\n\nReturn only the translated text.`,
    },
  ];

  const response = await fetch(deepSeekApiUrl, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${deepSeekApiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model: deepSeekModel,
      messages,
      temperature: 0.2,
      max_tokens: 900,
      stream: false,
    }),
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`DeepSeek HTTP ${response.status}: ${text}`);
  }
  const json = JSON.parse(text);
  const translated = normalizeTranslatedContent(json.choices?.[0]?.message?.content);
  if (!translated) {
    throw new Error('DeepSeek translation is empty');
  }
  return translated;
}

function normalizeOriginalLanguage(originalLanguage, content) {
  if (originalLanguage && String(originalLanguage).trim()) {
    const lang = String(originalLanguage).trim().toLowerCase().replace('_', '-').split('-')[0];
    if (SUPPORTED_LANGUAGES.includes(lang)) {
      return lang;
    }
  }
  if (/\p{Script=Han}/u.test(content)) return 'zh';
  if (/\p{Script=Hiragana}|\p{Script=Katakana}/u.test(content)) return 'ja';
  if (/\p{Script=Hangul}/u.test(content)) return 'ko';
  return 'en';
}

function normalizeTranslatedContent(content) {
  if (!content) return '';
  let normalized = String(content).trim();
  if (normalized.startsWith('Translation:')) {
    normalized = normalized.slice('Translation:'.length).trim();
  }
  if (
    (normalized.startsWith('"') && normalized.endsWith('"')) ||
    (normalized.startsWith('\u201C') && normalized.endsWith('\u201D'))
  ) {
    normalized = normalized.slice(1, -1).trim();
  }
  return normalized;
}

function firstNonBlank(...values) {
  for (const value of values) {
    if (value != null && String(value).trim()) {
      return String(value).trim();
    }
  }
  return null;
}

function defaultEmojiCountryByLanguage(language) {
  switch (language) {
    case 'zh': return '\uD83C\uDDE8\uD83C\uDDF3';
    case 'en': return '\uD83C\uDDFA\uD83C\uDDF8';
    case 'ja': return '\uD83C\uDDEF\uD83C\uDDF5';
    case 'ko': return '\uD83C\uDDF0\uD83C\uDDF7';
    case 'de': return '\uD83C\uDDE9\uD83C\uDDEA';
    case 'fr': return '\uD83C\uDDEB\uD83C\uDDF7';
    case 'pt': return '\uD83C\uDDF5\uD83C\uDDF9';
    case 'es': return '\uD83C\uDDEA\uD83C\uDDF8';
    default: return null;
  }
}

function languageName(languageCode) {
  switch (languageCode) {
    case 'zh': return 'Simplified Chinese';
    case 'en': return 'English';
    case 'ja': return 'Japanese';
    case 'ko': return 'Korean';
    case 'de': return 'German';
    case 'fr': return 'French';
    case 'pt': return 'Portuguese';
    case 'es': return 'Spanish';
    default: return 'English';
  }
}
