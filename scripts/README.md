# Changelog Automation Scripts

This directory contains scripts for automated changelog generation.

## Files

- `generate-changelog-entry.kts` - Main Kotlin script that generates changelog entries
- `example-changelog-entry.json` - Example output format

## Quick Start

### Local Testing

```bash
# Test with a sample commit
kotlinc -script scripts/generate-changelog-entry.kts -- \
  --commit-hash "abc1234" \
  --commit-message "[TKT-123] Fix notification crash on Android 14" \
  --openai-key "sk-your-key-here" \
  --kb-url "https://httpbin.org/post"
```

### Required Dependencies

The script automatically downloads:
- `okhttp3` (v4.12.0) - HTTP client
- `gson` (v2.10.1) - JSON parsing

These are resolved automatically when running with `kotlinc -script`.

## See Also

- `CHANGELOG_AUTOMATION.md` - Full documentation
- `.github/workflows/update-changelog.yml` - GitHub Actions workflow
