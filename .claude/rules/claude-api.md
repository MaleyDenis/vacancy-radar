---
paths:
  - "src/**/service/**"
---

# Claude API Rules

- Model: `claude-haiku-4-5-20251001`
- Input: plain text description (~1500 tokens) + UserProfile
- Output: strictly typed JSON (structured output via Anthropic Java SDK)
- NEVER send raw HTML to Claude — plain text only
- Do NOT switch to Sonnet/Opus without approval — cost scales with offer volume
