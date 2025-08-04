#!/bin/bash

# Create test output directory
mkdir -p model/build/classes/java/test

# Compile the three referential integrity tests
echo "Compiling referential integrity tests..."

javac -cp "model/build/classes/java/main:common/core/build/classes/java/main:model/build/libs/*:common/core/build/libs/*:/home/node/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-api/5.11.4/1f90fb5f2b1bb86b97b5e211d94ad3a962b7efab/junit-jupiter-api-5.11.4.jar:/home/node/.gradle/caches/modules-2/files-2.1/org.jgrapht/jgrapht-core/1.5.2/c0c231de7e2b97c36fe57e7a9b8f4cc1f6e8d056/jgrapht-core-1.5.2.jar" \
  -d model/build/classes/java/test \
  model/src/test/java/dev/iq/graph/model/operations/NodeOperationsReferentialIntegrityTest.java \
  model/src/test/java/dev/iq/graph/model/operations/EdgeOperationsReferentialIntegrityTest.java \
  model/src/test/java/dev/iq/graph/model/operations/ComponentReferentialIntegrityIntegrationTest.java \
  model/src/test/java/dev/iq/graph/model/simple/SimpleComponentTest.java

echo "Compilation status: $?"