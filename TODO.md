
- dev.iq.graph.model.simply types should delegate equals() and hashCode() to Versions
- dev.iq.graph.model.lazy types should delegate equals() and hashCode() to Versions
- Really only need to store a Locator for nodes and edges in the graph
- Want a union type 

# Current Instructions

I want to rework how the api classes interact with the model.  These changes involve:

- Several operations load all possible IDs into memory and then filter.  This is *very* inefficient.
- API operations should call a respository to fetch the actual NanoID or whatever is needed directly.
- If an API operation is findActive(NanoID), then it should use a similar method on the persistence class.
- These persistence operations will, in effect, return their own instance of a JGraphT graph
- API classes need to supply an instance of JGraphT graph to each Operations instance in the jgrapht package
- ComponentOperations therefore should not maintain its own in-memory set of Map instances
- Rather, ComponentOperations should use the Graph passed in to infer/compute the component
- API operations like update() should first find(), then mutate the graph in memory and finally persist

# Answers to your clarifying questions (prefixed by A:)

1. JGraphT Graph Generation: When you say "persistence operations will return their own
   instance of a JGraphT graph", do you mean:
    - Each repository method (like findActive) should return a small graph containing just the
      requested elements?
    - Or should there be a new method that loads a subset of the graph based on criteria?

2. ComponentOperations Graph Usage: Currently ComponentOperations maintains Maps because the
   immutable records prevent modifying the components field in nodes/edges. How should we handle
   component membership tracking if we remove these Maps? Should we:
    - Query the persistence layer each time we need component information?
    - Pass a pre-loaded graph that already contains all component relationships?
      A:  Query the persistence layer each time we need component information
3. Update Pattern: For the update pattern (find -> mutate -> persist), since JGraphT graphs
   contain immutable records, we can't actually mutate them in place. Should the pattern be:
    - Find from persistence -> Create new immutable objects -> Add to graph -> Persist the new
      objects?
      A:  Create new copies of the immutable objects and persist those
4. Scope of Changes: Should I modify the @Stable interfaces (GraphRepository,
   ExtendedVersionedRepository) or work around them by adding new methods/interfaces?
   A:  ExtendedVersionRepository is loading all IDs, so that whole interface should disappear
   A:  I do not think you need additional methos for @Stable interfaces, as these are immutable (see above)
5. Graph Lifecycle: When API operations receive a JGraphT graph instance, who is responsible
   for keeping it synchronized with the persistence layer? Should each operation:
    - Get a fresh graph from persistence?
    - Work with a potentially stale graph passed in?
      A:  Operations (in the model) keeps everything in sync on a high level
      A:  Operations will take immutable types in dev.iq.graph.model.simple and ensure jGraphT reflects the same structure
      A:  Persistence operations should return instances of dev.iq.graph.model.simple
      A:  Service (api) methods will delegaet to Operations to build out a JGraphT graph as needed
      A:  Most persistence operations (aside) from Components do not return whole subgraphs anyway
      A:  Persistence routines should include "just enough" information to build a dev.iq.graph.model.simple
      A:  Do not load the entire graph recursively in a persistence operation