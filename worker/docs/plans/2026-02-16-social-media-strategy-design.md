# @KeyJawn social media strategy

## Account identity

@KeyJawn is a developer tools curation account that also makes a keyboard. 80% of content is curated shares and engagement with the dev tools community. 20% is about KeyJawn itself.

### Voice rules

- Developer-to-developer. Talk like you're in a group chat with other devs.
- Short sentences. Contractions. Casual but not sloppy.
- Zero emojis. Zero hashtag spam. Zero exclamation marks.
- No hype words (BANNED_WORDS list in content.py).
- Hard NO on "It's not just X — it's Y" and all variations. No "more than just a", "not your average", "X, but better", or any structure that builds up by contrasting with a strawman. State the thing directly.
- When there's nothing useful to say, don't say anything. A like or straight repost is fine.
- Credit creators by name or handle when sharing their work.

### Content biases

- Open source over closed source
- Free tools over paid
- Indie/solo devs over corporate launches
- Terminal/CLI tools over GUI apps
- Practical utility over flashy demos

## Action types and approval tiers

| Action | Description | Approval | Platforms |
|--------|-------------|----------|-----------|
| Curated share | AI-drafted post sharing someone else's content with a brief take | Telegram buttons | Twitter + Bluesky |
| Quote repost | Repost with added context (when there's something to add) | Telegram buttons | Twitter + Bluesky |
| Straight repost | Boost without commentary (content speaks for itself) | Telegram buttons | Twitter + Bluesky |
| Like | Signal approval of good content | Auto-approved | Twitter + Bluesky |
| Follow | Follow interesting accounts in the space | Auto-approved | Twitter + Bluesky |
| Reply | Respond to mentions or join relevant conversations | Telegram buttons | Twitter + Bluesky |
| Original post | KeyJawn product content (features, updates, tips) | Telegram buttons | Twitter + Bluesky |

Rule: anything that shows up on the timeline (posts, reposts, quotes, replies) needs Telegram approval. Likes and follows are silent and auto-approved.

## Daily cadence

Target: 2-4 actions per day, weekdays only.

### Daily flow

1. **Curation scan** runs every 6 hours. Evaluates YouTube, RSS, Twitch candidates.
2. **On-platform scan** runs once daily. Searches Twitter/Bluesky for keyword matches and checks the curated account list for good posts to engage with.
3. **Action session** runs once per evening (7pm ET). Picks from:
   - Approved curated shares (if any)
   - Engagement opportunities (likes, reposts, follows from on-platform scan)
   - Calendar-scheduled original posts (KeyJawn product content, ~1/week)

### Fallback behavior

No forced output. Silence is better than a bad post.

- Good day: 1 curated share + 1-2 likes/reposts = 2-3 actions
- Engagement-only day: 0 posts, 2-3 likes + maybe a straight repost = 2-3 actions
- Quiet day: nothing worth engaging with = 0-1 actions

### Weekly original content (the 20%)

1 original post per week about KeyJawn, scheduled via the calendar generator. Examples:
- Feature highlight ("KeyJawn's ctrl key works like a real terminal modifier — armed on tap, locked on hold")
- Use case ("wrote a whole PR review from my phone over SSH last week")
- Open source mention ("we use ratatui for the settings TUI — good library")

Drafted by Claude Opus, always Telegram-approved.

## Content discovery

### Off-platform (for curated shares)

The existing curation pipeline: YouTube Data API, RSS feeds (Hacker News, lobste.rs, dev.to), Twitch clips. Keyword filter then Claude-based AI evaluation.

### On-platform (for engagement)

Two discovery methods:
1. **Curated account list** — a maintained list of accounts to follow and engage with (tool creators, indie devs, terminal enthusiasts). Reliable, consistent.
2. **Keyword search** — search Twitter/Bluesky for terms like "terminal", "CLI tool", "open source", "indie dev". Broader discovery of new accounts and content.

## Copy generation

Claude Opus (`claude -p --model opus`) for all public-facing text. The anti-slop rules and voice guidelines are baked into every prompt.

Gemini CLI stays available for internal-only tasks (calendar planning, research, analysis). Nothing from Gemini gets posted publicly.

## Telegram approval UX

### Batch drafts

Instead of sending 1 draft at a time, each approval message contains 4 draft variants in a single Telegram message. The user picks the best one or rejects all.

### Message format

```
[CURATE] youtube — Dreams of Code
Terminal file manager in Rust (score: 0.87)

A: Terminal file managers are underrated. This one's
built in Rust with ratatui, full walkthrough from
@dreamsofcode. youtube.com/watch?v=abc

B: Solid Rust TUI project — ratatui-based file manager
with vi bindings. Worth a look if you work in the
terminal. youtube.com/watch?v=abc

C: Clean walkthrough of building a terminal file manager.
Open source, Rust, ratatui. youtube.com/watch?v=abc

D: ratatui keeps producing good projects. This terminal
file manager from @dreamsofcode is a nice example.
youtube.com/watch?v=abc
```

### Buttons

```
[ A ] [ B ] [ C ] [ D ]
[  Deny  ] [ Backlog ] [ Rethink ]
```

- **A/B/C/D** — approve that specific draft and post it
- **Deny** — kill it, don't post
- **Backlog** — save for later
- **Rethink** — send back to Claude Opus for a fresh set of 4 drafts

For straight reposts: show the target post, offer Approve/Deny only (no draft variants needed).

## What needs to change from current implementation

1. **Draft generation** — switch from single draft to 4 variants per candidate, use Claude Opus instead of Sonnet for drafts
2. **Telegram message format** — update to batch format with A/B/C/D buttons
3. **Telegram callback handler** — handle A/B/C/D button presses, map to the correct draft variant
4. **On-platform discovery** — new module for searching Twitter/Bluesky and engaging (likes, follows, reposts)
5. **Account list** — config-driven list of accounts to follow/monitor on each platform
6. **Curated shares** — post to both Twitter and Bluesky (currently hardcoded to Twitter only)
7. **Engagement actions** — implement like, follow, straight repost, quote repost as action types in the executor
8. **Fallback logic** — engagement-only mode when curation pipeline produces nothing
