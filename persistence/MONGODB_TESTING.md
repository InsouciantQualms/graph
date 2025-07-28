# MongoDB Testing Configuration

## Overview

The MongoDB tests in this module use the de.flapdoodle.embed.mongo library to run an embedded MongoDB instance during tests. This library requires network connectivity to download MongoDB binaries on first use, but caches them for subsequent offline use.

## Current Implementation

- Tests use `MongoTestConfig.startMongoDbOrSkip()` to start embedded MongoDB
- If network connectivity is unavailable and binaries are not cached, tests are skipped using JUnit 5's `Assumptions.assumeFalse()`
- MongoDB binaries are cached in `~/.embedmongo/` after first download
- The tests include:
  - MongoNodeRepositoryTest
  - MongoGraphOperationsTest
  - MongoGraphOperationsIntegrationTest
  - MongoGraphListenerReferentialIntegrityIntegrationTest

## Running Tests Offline

To run MongoDB tests without network connectivity:

1. **First Time Setup** (requires network):
   ```bash
   # Run tests once with network to download and cache MongoDB binaries
   ./gradlew :persistence:test
   ```

2. **Subsequent Runs** (offline):
   - Tests will use cached binaries from `~/.embedmongo/`
   - No network connectivity required

3. **Manual Binary Setup** (alternative):
   - Download MongoDB binaries from https://www.mongodb.com/try/download/community
   - Place them in `~/.embedmongo/` following the expected directory structure
   - Example: `~/.embedmongo/linux/mongodb-linux-x86_64-7.0.x.tgz`

## Troubleshooting

If tests are being skipped:
1. Check if `~/.embedmongo/` directory exists and contains MongoDB binaries
2. Run tests once with network connectivity to cache binaries
3. Check test output for specific error messages

## MongoDB Operations Tested

The MongoDB persistence layer leverages native graph operations including:
- `$graphLookup` for graph traversal
- `$lookup` for edge relationships
- Aggregation pipelines for complex graph queries
- JSON Schema validation for data integrity

All operations are fully tested when embedded MongoDB is available.