# Release notes style guide

Format for GitHub release notes on the keyjawn repository.

## Template

```markdown
![KeyJawn branding graphic](https://i.imgur.com/c6z2Gl0.jpeg)

[![Build](https://github.com/jamditis/keyjawn/actions/workflows/build.yml/badge.svg)](https://github.com/jamditis/keyjawn/actions/workflows/build.yml)

## What's new / What's fixed

### Feature or fix name
Description paragraph. What it does, why it matters. Keep it concrete.

### Second feature or fix name
Same format.

## Downloads

| File | Description |
|------|-------------|
| `app-lite-release.apk` | Free version (GitHub) |
| Full version | Available via [keyjawn.amditis.tech](https://keyjawn.amditis.tech/pricing) ($4, includes all features) |

**Full changelog**: https://github.com/jamditis/keyjawn/compare/vPREV...vCURR
```

## Rules

- Start with branding image and CI badge
- Use `## What's new` for feature releases, `## What's fixed` for bug fixes, both if mixed
- Each feature/fix gets an `### H3` with a short name
- Description is 1-2 paragraphs, plain language, no marketing speak
- End with downloads table and full changelog link
- No emojis
- Release title format: `vX.Y.Z - Short description in sentence case`
