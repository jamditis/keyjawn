# Content curation pipeline design

**Goal:** Turn KeyJawn's social accounts into tastemaker accounts that discover, evaluate, and share interesting dev tools content -- with a bias toward open source, free, and indie projects. The content we post shouldn't be just self-serving. We want to become known as an account with taste that recognizes and supports good work.

**Architecture:** Source-first pipeline with three monitors (YouTube, Google News/RSS, Twitch) feeding into a four-stage AI evaluation funnel (keyword filter, Haiku quick-check, Gemini deep-dive, Sonnet judgment). Curated shares go through Telegram approval and post to Twitter/Bluesky as commentary + link or quote posts.

**Tech stack:** YouTube Data API v3, Twitch Helix API, Google News RSS, Claude Haiku/Sonnet, Gemini CLI

---

## Source monitors

### YouTube

- YouTube Data API v3 with API key (no OAuth for public searches)
- Scans every 6 hours
- Search keywords: `terminal keyboard android`, `mobile CLI tools`, `SSH mobile app`, `developer keyboard`, `indie dev tools`, `open source android`, `coding on phone`, etc.
- Also monitors specific channels from the boost list
- Quota budget: ~50 searches/day = 5,000 of 10,000 daily free units
- Returns: video ID, title, description, channel name, view count, publish date

### Google News / RSS

- Google Alerts RSS feeds -- no API key, no quota
- Pre-configured alerts: `"mobile developer tools"`, `"terminal keyboard"`, `"open source Android"`, `"indie developer"`, `"CLI tools"`, `"SSH mobile"`
- Also monitors: Hacker News RSS, dev.to RSS, lobste.rs RSS
- Scans every 6 hours
- Returns: title, snippet, source URL, publish date

### Twitch

- Twitch Helix API with OAuth client credentials
- Scans every 8 hours (live content is more ephemeral)
- Monitors clips from: "Science & Technology", "Software and Game Development"
- Keyword search on clip titles
- Returns: clip ID, title, creator, view count, category, clip URL

### TikTok (indirect)

- No direct API access (academic-only)
- Catch TikTok content when it surfaces in other sources (Google News, Twitter, YouTube)
- Future: yt-dlp can fetch TikTok metadata and video given a URL (see backlog)

### Common output format

All monitors normalize to `CurationCandidate`:

```python
@dataclass
class CurationCandidate:
    source: str          # "youtube", "google_news", "twitch"
    url: str
    title: str
    description: str
    author: str
    published: datetime
    metadata: dict       # source-specific (view count, channel, etc.)
```

---

## AI evaluation pipeline

Four-stage funnel. Each stage is cheaper/faster than the next, filtering aggressively early.

### Stage 1: Keyword filter (local, instant)

Fast local check with no API calls:
- Title + description keyword matching against dev tools vocabulary
- Open source / indie boost: +0.2 for signals like "open source", "free", "indie", "side project", "solo dev", "no VC", GitHub/GitLab links
- Negative signals: -0.3 for marketing language ("limited time", "discount", "sponsored"), corporate product launches from big companies, crypto/web3
- Drop candidates scoring below 0.3

### Stage 2: Haiku quick-check (~$0.001/call)

Binary classification with reasoning:
- Is this content about developer tools, terminal/CLI software, mobile dev tools, keyboards, or related indie tech?
- Would a developer interested in terminal tools and mobile coding find this useful?
- Classifies: open source / free / indie (boost), corporate launch (penalty), clickbait / low-effort (kill)
- Cost: ~100 candidates/day x $0.001 = ~$0.10/day

### Stage 3: Gemini CLI deep-dive (free)

Top ~20 candidates that pass Haiku get investigated using Gemini CLI (1,500 free req/day on AI Pro subscription):
- What is it about? (2 sentences)
- Is the project open source? Link to repo if found.
- Is the creator an indie dev, small team, or large company?
- Is the content good/useful, or is it hype?
- Would sharing this make us look like we have good taste?
- Quality + relevance score: 1-10

Gemini's Google Search grounding can look up repos, check if the project is real, and find related coverage.

### Stage 4: Sonnet judgment + draft post (~$0.01/call)

Top ~5 candidates (Gemini score 7+) get a Sonnet call that:
1. Makes the final share/skip decision
2. Drafts the post text (respecting platform character limits)
3. Decides format: commentary + link, or quote post (for same-platform content)
4. Provides reasoning for the Telegram approval message

Draft follows the same voice rules as self-promo: dev-to-dev, no hype, short sentences.

### Scoring weights

```python
SCORING_WEIGHTS = {
    "is_open_source": +0.25,
    "is_free": +0.15,
    "is_indie_dev": +0.20,
    "is_solo_dev": +0.15,
    "has_github_repo": +0.10,
    "is_corporate_launch": -0.15,
    "is_sponsored": -0.30,
    "gemini_quality_score": 0.0-1.0,  # normalized from 1-10
}
```

---

## Boost list

Configurable list of trusted sources that get +0.15 priority in keyword filter stage. Still must pass the full pipeline.

```python
BOOST_CHANNELS = {
    "youtube": [
        "ThePrimeagen", "Fireship", "NetworkChuck",
        "TechHut", "Dreams of Code", "typecraft",
    ],
    "twitch": [
        "ThePrimeagen", "teaboraxofficial",
    ],
    "news_domains": [
        "github.com/trending", "lobste.rs", "news.ycombinator.com",
    ],
}
```

---

## Database additions

```sql
CREATE TABLE curation_candidates (
    id INTEGER PRIMARY KEY,
    source TEXT NOT NULL,
    url TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    author TEXT,
    description TEXT,
    published_at TEXT,
    metadata TEXT,                  -- JSON
    keyword_score REAL,
    haiku_pass INTEGER,             -- 0/1
    haiku_reasoning TEXT,
    gemini_score REAL,
    gemini_analysis TEXT,
    sonnet_pass INTEGER,            -- 0/1
    sonnet_draft TEXT,
    sonnet_reasoning TEXT,
    final_score REAL,
    status TEXT DEFAULT 'new',      -- new, evaluating, approved, rejected, posted, skipped
    created_at TEXT DEFAULT (datetime('now')),
    evaluated_at TEXT,
    posted_at TEXT
);
```

---

## Integration with existing worker

### Budget

Separate from self-promotion:
- 3 self-promo actions/day (unchanged)
- 2 curated shares/day (new, configurable via `max_curated_shares_per_day`)

### Schedule

```
Existing (unchanged):
  Morning:  Monitor scan (Twitter, Bluesky, Product Hunt)
  Evening:  Action session (calendar posts, replies)

New:
  Every 6h: YouTube + Google News/RSS scan -> evaluation pipeline
  Every 8h: Twitch scan -> evaluation pipeline
  Evening:  Approved curated shares added to action session
```

### Action picker changes

`ActionPicker.pick_actions()` extended to include curated shares after calendar posts and finding-based replies. Curated shares always use `BUTTONS` escalation tier.

### Telegram approval format

```
[CURATE] Share recommendation

Source: YouTube - Dreams of Code
Title: "Building a terminal file manager in Rust"
Score: 0.87 (open source, indie dev, high quality)

AI reasoning: Rust CLI tool tutorial from a solo dev.
Open source repo on GitHub. Well-produced, practical content
that matches our audience's interests.

Draft post (Twitter, 247 chars):
"Solid walkthrough of building a terminal file manager in
Rust from scratch. Open source, clean code, worth watching
if you're into CLI tools. youtube.com/watch?v=..."

[Approve] [Edit] [Skip]
```

### Post format

- External content (YouTube, news articles, Twitch clips): commentary post with link
- Same-platform content (tweet about a project, bsky post): quote post with our take
- Voice: dev-to-dev, no hype, short sentences, same rules as self-promo content

---

## New files

```
worker/worker/
  curation/
    __init__.py
    models.py          # CurationCandidate dataclass
    sources/
      __init__.py
      youtube.py       # YouTube Data API v3 client
      news.py          # Google News RSS + HN/lobsters/dev.to
      twitch.py        # Twitch Helix API client
    pipeline.py        # Four-stage evaluation pipeline
    boost.py           # Boost list management
    monitor.py         # CurationMonitor orchestrator
```

---

## API keys needed

| Service | Key type | Storage |
|---------|----------|---------|
| YouTube Data API v3 | API key | `pass claude/api/youtube` |
| Twitch Helix | Client ID + Secret | `pass claude/api/twitch` |
| Google News RSS | None | N/A |
| Anthropic (Haiku/Sonnet) | API key | `pass claude/api/anthropic` (existing) |
| Gemini CLI | OAuth | Already configured via amditisjunk@gmail.com |

---

## Cost estimate

| Component | Daily cost |
|-----------|-----------|
| YouTube API | Free (5,000 of 10,000 quota units) |
| Google News RSS | Free |
| Twitch API | Free |
| Haiku (~100 calls) | ~$0.10 |
| Gemini CLI (~20 calls) | Free (AI Pro subscription) |
| Sonnet (~5 calls) | ~$0.15 |
| **Total** | **~$0.25/day** |

---

## Future additions (backlog)

- yt-dlp video clip downloading for native video posts (better engagement than links)
- TikTok content via yt-dlp when URLs surface from other sources
- Auto-post mode for high-confidence candidates (score > 0.9) once the pipeline is trusted
- Weekly curation digest (email or blog post summarizing shared content)
