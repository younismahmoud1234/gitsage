# Configuration Reference

GitSage is configured via `application.yml` with environment variable overrides.

## Required Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `GITHUB_TOKEN` | GitHub personal access token (read-only scope) | `ghp_xxxxxxxxxxxx` |
| `GITHUB_ORG` | GitHub organisation to index | `my-org` |
| `OPENAI_API_KEY` | OpenAI API key for embeddings and chat | `sk-xxxxxxxxxxxx` |

## Optional Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `POSTGRES_HOST` | PostgreSQL hostname | `localhost` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_DB` | Database name | `gitsage` |
| `POSTGRES_USER` | Database user | `gitsage` |
| `POSTGRES_PASSWORD` | Database password | `gitsage` |

## Full Configuration

```yaml
gitsage:
  github:
    token: ${GITHUB_TOKEN}          # Required: GitHub PAT
    org: ${GITHUB_ORG}              # Required: Org to index
    index-readmes: true             # Index README files
    index-source-code: true         # Index source code files
    index-issues: true              # Index open issues
    index-pull-requests: false      # Index pull requests

  llm:
    provider: openai                # LLM provider (openai)
    api-key: ${OPENAI_API_KEY}     # Required: API key
    model: gpt-4o                   # Chat model
    embedding-model: text-embedding-3-small  # Embedding model
    embedding-dimensions: 1536      # Vector dimensions

  indexing:
    cron: "0 0 */6 * * ?"          # Re-index every 6 hours
    batch-size: 50                  # Files per batch
    incremental-only: true          # Only re-index changed files

  rag:
    max-results: 10                 # Max chunks to retrieve
    similarity-threshold: 0.7       # Minimum similarity score
    chunk-size: 1000               # Characters per chunk
    chunk-overlap: 200             # Overlap between chunks
    system-prompt: >               # Custom system prompt
      You are GitSage...

  copilot:
    webhook-secret: ${COPILOT_WEBHOOK_SECRET:}  # For production
```

## GitHub Token Scopes

GitSage requires a GitHub Personal Access Token with **read-only** access:

| Scope | Purpose |
|-------|---------|
| `repo` (read) | Access private repositories |
| `read:org` | List organisation repositories |

For public-only organisations, a classic token with `public_repo` scope is sufficient.

## Tuning Tips

### Chunk Size
- **Smaller chunks** (500-800): More precise retrieval, more API calls
- **Larger chunks** (1000-2000): More context per chunk, may include noise
- **Recommended**: Start with 1000, adjust based on answer quality

### Similarity Threshold
- **Lower** (0.5-0.6): More results, may include less relevant content
- **Higher** (0.8-0.9): Fewer but more relevant results
- **Recommended**: 0.7 is a good balance

### Max Results
- More results = more context for the LLM = better answers but higher token cost
- **Recommended**: 5-10 for most use cases
