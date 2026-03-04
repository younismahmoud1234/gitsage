# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 0.x.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in GitSage, please report it responsibly.

**DO NOT** open a public issue for security vulnerabilities.

Instead, please email **rameshreddy.adutla@gmail.com** with:
- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

You will receive an acknowledgement within 48 hours, and a detailed response
within 5 business days indicating next steps.

## Security Considerations

GitSage handles sensitive data including:
- **GitHub tokens** — used to access org repositories
- **OpenAI API keys** — used for embeddings and LLM calls
- **Repository content** — indexed and stored in the vector database

### Best Practices

1. **Never commit secrets** — use environment variables or `.env` files (gitignored)
2. **Use read-only GitHub tokens** — GitSage only needs read access to repos
3. **Network isolation** — run PostgreSQL in a private network, not exposed publicly
4. **Copilot Extension signatures** — always verify GitHub webhook signatures in production
5. **Regular token rotation** — rotate API keys periodically
