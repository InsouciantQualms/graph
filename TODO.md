# Persistence

* Persistence routines need to expire rather than remove
* Persistence routines need to serialize data as JSON

# Testing

* Create integration tests that validate referential integrity as new node versions are added
* Create integration tests that validate referential integrity as new edge versions are added (say, if the edge data changes)
* Create integration tests that validate expired nodes have all expired edges connecting to or from it
* Integration tests should be suffixed with "IntegrationTest"
* Integration tests need to be created both for SqlLite (dev.iq.graph.persistence.sqllite) and Tinkerpop (
  dev.iq.graph.persistence.tinkerpop)
* All unit and integration tests need to pass
* Run the Gradle build to validate changes

# Possible Bugs

* ComponentOperations#update(), the 'existingComponents' is never used. Is this a bug or something missing?  (line 48)
* NodeOperations#expire(), the 'edgesToExpire' is populated/updated but never queried. Is this a bug or something missing? (line 145)