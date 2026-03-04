# Contributing to GitSage

First off, thanks for taking the time to contribute! 🎉

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check [existing issues](https://github.com/rameshreddy-adutla/gitsage/issues) to avoid duplicates.

When creating a bug report, include:
- **Clear title** describing the issue
- **Steps to reproduce** the behaviour
- **Expected vs actual behaviour**
- **Environment details** (OS, Java version, Docker version)
- **Logs** if applicable (sanitise any tokens/keys)

### Suggesting Features

Feature requests are welcome! Open an issue with the `enhancement` label and describe:
- The problem you're trying to solve
- Your proposed solution
- Any alternatives you've considered

### Pull Requests

1. **Fork** the repository
2. **Create a branch** from `main`: `git checkout -b feature/your-feature`
3. **Make your changes** — follow the coding standards below
4. **Write tests** — all new code must have tests
5. **Run the test suite**: `./gradlew test`
6. **Commit** with a clear message: `feat: add Ollama embedding support`
7. **Push** and open a PR against `main`

## Coding Standards

### Java
- **Java 21** — use modern features (records, sealed classes, pattern matching)
- **Micronaut conventions** — constructor injection, `@Singleton` scope by default
- Follow SOLID principles
- Use records for immutable data transfer objects
- Explicit error handling — no silent failures

### Tests
- Follow AAA pattern (Arrange, Act, Assert)
- Descriptive test names: `should_returnRelevantChunks_when_queryMatchesContent`
- Unit tests with Mockito, integration tests with TestContainers
- Aim for meaningful coverage, not 100% line coverage

### Commits
We follow [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` — new feature
- `fix:` — bug fix
- `docs:` — documentation
- `test:` — tests
- `refactor:` — code refactoring
- `chore:` — maintenance

### Code Style
- 4 spaces for indentation
- No wildcard imports
- Use `var` for local variables when the type is obvious
- Constants in `UPPER_SNAKE_CASE`

## Development Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/gitsage.git
cd gitsage

# Start dependencies
docker compose -f docker/docker-compose.yml up -d postgres

# Set environment variables
export GITHUB_TOKEN=ghp_your_token
export GITHUB_ORG=your-org
export OPENAI_API_KEY=sk-your-key

# Run tests
./gradlew test

# Run the app
./gradlew run
```

## Questions?

Open a [Discussion](https://github.com/rameshreddy-adutla/gitsage/discussions) — we're happy to help!
