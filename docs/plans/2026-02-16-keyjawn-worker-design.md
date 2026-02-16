# KeyJawn worker design

An autonomous marketing agent that runs on officejawn during evenings, monitoring developer communities for relevant conversations and taking 1-3 daily actions to grow KeyJawn's profile, user count, and sales.

## System architecture

### Two operating modes

**1. Monitor loop** (9am-9pm ET, weekdays)
- Lightweight scan every 30-60 minutes: Twitter search API, Bluesky search, HN/Reddit keyword watches
- Looks for high-value conversations: people asking about mobile SSH, CLI keyboards, AI agent workflows from phones
- Queues findings in SQLite with relevance scoring
- Time-sensitive opportunities escalate to Telegram immediately with action buttons

**2. Action window** (6-9pm ET, weekdays)
- Picks 1-3 actions from the queue + weekly content calendar
- Executes auto-approved actions (original posts, likes, reposts, follows)
- Sends Telegram escalation for anything needing review
- Logs all actions and outcomes

### Data flow

```
Monitor loop -> findings queue (SQLite)
                    |
Content calendar -> Action picker (6-9pm) -> Auto-execute OR Telegram escalation
                                                              |
                                              Joe taps button -> Execute / Skip / Backlog / Rethink
```

### Infrastructure

- **Runs on:** officejawn (compute brain)
- **Talks to:** houseofjawn Telegram bot (notifications), keyjawn-store API (metrics)
- **Storage:** SQLite for queue, action log, content calendar, and performance metrics
- **Content generation:** Gemini CLI (free tier, 1500 req/day) for drafting posts
- **Platform APIs:** Twitter/X API, Bluesky AT Protocol, Reddit API (read-only), HN Algolia API

## Identity and disclosure

- Dedicated **@KeyJawn** brand accounts on each platform (not Joe's personal accounts)
- Account bios disclose AI operation (e.g., "AI-assisted account for KeyJawn keyboard. Built by @jamditis")
- Individual posts do not repeat the disclosure
- If anyone asks "are you a bot?" — answer honestly, every time, no exceptions

## Telegram approval flow

### Message format

```
[KeyJawn Worker] Action ready

Type: Reply to conversation
Platform: Twitter/X
Context: @devuser123 asked "anyone know a good keyboard
for using Claude Code on my phone?"

Draft:
"KeyJawn was built for exactly this -- terminal key row
(Esc/Tab/Ctrl/arrows) always visible, plus voice input
for prompts. Free lite version on GitHub, $4 full version.
keyjawn.amditis.tech"

[Approve] [Deny] [Backlog] [Rethink]
```

### Button behaviors

| Button | What happens |
|--------|-------------|
| **Approve** | Worker posts immediately, logs result, confirms with a quiet reply edit |
| **Deny** | Action discarded, logged as denied. Worker avoids similar patterns |
| **Backlog** | Action returns to queue with lower priority, tagged for next session |
| **Rethink** | Worker generates a new draft or alternative action, sends new message with buttons |

### Escalation tiers

**Auto-approved** (no buttons, just logged):
- Original tweets/Bluesky posts from the content calendar
- Likes, reposts, follows of relevant accounts

**Buttons required:**
- Replies to specific people
- Reddit drafts (always — Joe posts these manually)
- Outreach DMs to influencers/bloggers
- Anything mentioning a competitor
- Content that deviates from the weekly calendar

**Immediate escalation** (outside 6-9pm window):
- Time-sensitive opportunities where someone is actively asking for what KeyJawn does

### Timeouts and limits

- No button tap within 2 hours: auto-backlogged (never auto-approved on timeout)
- Rethink can repeat up to 3 times per opportunity, then auto-backlogs
- Max 3 posts per platform per day
- Max 5 Telegram escalations per evening (prevent notification fatigue)

## Content strategy

### Weekly content calendar

Generated every Monday, stored in SQLite. Maps themes to days based on rotating pillars.

### Content pillars

1. **Problem awareness** — Screenshots/demos of standard keyboards failing at CLI tasks. "Did you know Gboard autocorrects your shell commands?"
2. **Demo/showcase** — KeyJawn in action: voice input composing prompts, SCP upload, terminal row usage. Visual proof over words.
3. **Community engagement** — Respond to conversations about mobile dev, SSH from phones, AI CLI tools. Genuinely helpful, not salesy.
4. **Social proof** — User feedback, download milestones, GitHub stars, support tickets with good outcomes. Small wins count at this stage.
5. **Behind the scenes** — Dev updates, what's next, the open-source angle, the "$4 and no tricks" philosophy.

### Platform mapping

| Action | Twitter | Bluesky | Reddit | HN/dev.to |
|--------|---------|---------|--------|-----------|
| Original post | Auto | Auto | Draft only | Draft only |
| Reply to conversation | Buttons | Buttons | Draft only | Draft only |
| Like/repost | Auto | Auto | N/A | N/A |
| Follow relevant accounts | Auto | Auto | N/A | N/A |
| Outreach DM | Buttons | Buttons | N/A | N/A |
| Product Hunt/newsletter | Buttons | N/A | N/A | Buttons |
| Blog post | N/A | N/A | N/A | Buttons |

### Measurement

- Track impressions, clicks to keyjawn.amditis.tech (via UTM params per platform)
- Correlate with store metrics (signups, purchases, downloads)
- Weekly summary every Monday before generating the new calendar

## Writing rules

### Voice

Write like a developer talking to another developer. Short sentences. Contractions. Lowercase unless starting a sentence. No marketing voice.

### Banned patterns

| Pattern | Instead |
|---------|---------|
| "Excited to announce..." | Just say what happened |
| "Game-changer" / "revolutionary" | Describe the specific thing it does |
| "We're thrilled..." | Skip it |
| "Check out..." / "Don't miss..." | Share the thing, let people decide |
| "Thread (1/n)" | If it needs a thread, it's probably a blog post |
| Hashtag spam | Zero or one hashtag, max |
| "If you're like me..." | State the problem directly |
| "Imagine a world where..." | Describe the real world |
| "It just works" | Show it working |

### Global banned words

No "comprehensive", "robust", "seamless", "leveraging", "innovative", "cutting-edge", "holistic", "synergy", "ecosystem", "paradigm", "empower", "transformative", "sophisticated". Full list in global CLAUDE.md.

### Structural rules

- Max 2 sentences before getting to the point
- No rhetorical questions as openers
- No emoji as punctuation or emphasis. One per post max, only if it adds meaning
- No exclamation marks except in genuinely surprising contexts
- Links at the end, not mid-sentence
- Screenshots and short video clips over words — visual proof preferred

### Good examples

- "KeyJawn has a permanent Esc/Tab/Ctrl row. No long-pressing, no hunting through symbol layers."
- "Voice input -> streaming transcription -> straight into your Claude Code prompt. Faster than typing shell commands on glass."
- "Someone asked how I SSH from my phone without losing my mind. This is how." [screenshot]

### Bad examples

- "We're excited to share KeyJawn, an innovative mobile keyboard that seamlessly integrates with your CLI workflow!"
- "Are you tired of fumbling with your phone keyboard while coding? KeyJawn is the game-changer you've been waiting for! #coding #dev #AI"
- "Don't miss out on this incredible tool that will fundamentally transform your mobile development experience!"

### Reddit-specific (for drafts)

- Write like a regular user sharing something they use, not a brand
- Lead with the problem and the story, not the product
- "I built this" or "my friend built this" framing where honest
- Expect and welcome criticism — never get defensive

## Legal and ethical boundaries

### Hard rules

- **No fake accounts or sockpuppets.** One brand account per platform, clearly identified.
- **No fake reviews, testimonials, or social proof.** All claims must be real and verifiable.
- **No purchased followers, likes, or engagement.** Growth must be organic.
- **No scraping or storing personal data** from platform users beyond public profile info needed for replies.
- **No spam.** If the same person has been replied to once, don't reply again unless they engage back.
- **No astroturfing.** Don't pretend to be multiple people or create fake grassroots buzz.
- **No competitor disparagement.** Compare features honestly, never trash other products.
- **Honest when asked.** If anyone asks whether the account is automated or AI-operated, answer truthfully immediately.
- **Comply with each platform's ToS and bot/automation policies.** If a platform bans automated posting, don't post there automatically.
- **FTC compliance:** Disclose material connections. The bio disclosure plus honest-when-asked policy covers this.

### Things the worker must never do

- Send unsolicited DMs to people who haven't expressed interest in the problem space
- Post in communities where self-promotion is against the rules (unless the post is genuinely helpful and the product mention is incidental)
- Create urgency or scarcity that doesn't exist ("limited time!", "only X left!")
- Make claims about the product that aren't true
- Engage in political, controversial, or off-topic conversations
- Respond to negative feedback with anything other than genuine helpfulness or graceful silence
- Spend money on ads, promotions, or boosted posts without explicit approval

## Success metrics

### Short-term (first month)

- Brand accounts created and established on Twitter, Bluesky
- 1-3 actions per weekday executed consistently
- Approval flow working smoothly (Joe isn't overwhelmed)
- At least a few genuine conversations started

### Medium-term (months 2-3)

- Measurable traffic increase to keyjawn.amditis.tech from social (via UTM)
- Growth in followers/engagement on brand accounts
- Increase in store signups and downloads
- Reddit drafts leading to successful posts by Joe

### Long-term targets

- Sustainable growth in paying customers
- Community recognition of KeyJawn as the go-to mobile CLI keyboard
- Organic mentions and recommendations from users (not prompted by the worker)
- The worker becomes less necessary as organic word-of-mouth takes over
