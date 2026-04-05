---
description: Work on the content for a single explore page screen (header, description, tech stack, under the hood)
---

# Screen Content Workflow

Follow these steps EXACTLY for every screen. Do NOT skip any step.

## Step 1: Read the content strategy
// turbo
Read the file `showcase/public/TEXT.md` to understand the 4 content zones and their rules.

## Step 2: Identify the target screen
Ask the user which screen ID they want to work on (e.g., `first-screen`, `vol-updates`, `ttw-scheduling`). If they already specified it, proceed.

## Step 3: Read the current screen data
// turbo
Read the screen's entry in `showcase/app/explore/screenData.ts`. Search for the screen ID to find its exact content block.

## Step 4: Read the actual Android source code
Find and read the relevant Android source files for this screen. Use grep to search in the NSS-App source code:
- Search for the screen's Activity/Fragment/Composable class name
- Read the actual implementation to discover real features, patterns, and hidden UX details
- Look for things like: Excel upload, smart search, caching, error handling, edge cases, algorithms

## Step 5: Propose new content and implement
Based on Steps 1-4, propose AND implement the updated content for all 4 zones in `showcase/app/explore/screenData.ts`:
1. **Header** (pageName) — Is the current one good or needs changing?
2. **Description** (pageDescription) — The 1-2 sentence description with highlight_text spans.
3. **Tech Stack** (techTags) — The specific technologies.
4. **Under the Hood** (features) — Exactly 5 cards, mix of Flavor A (Hidden Gem) and Flavor B (Engineering Flex).

After implementing, tell the user:
> **Check on localhost:3006/explore →** navigate to [screen name]. Verify:
> - Left panel: header text, description text, tech tags
> - Right panel: all 5 feature cards visible and scrollable
> - No text overflow or layout breaks
>
> Send me a screenshot or feedback if anything needs changing.

## STRICT: Do NOT use the browser subagent. Do NOT take screenshots. The user will verify visually.
