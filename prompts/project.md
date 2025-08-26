
The following describes the problem domain and structure of the project as a whole as well as common definitions.

# Goals

Primary:  Model directed graphs that keep history of components and can query components by version
Secondary:  Abstract away JGraphT, SqlLite and Tinkerpop using types in dev.iq.graph.model
Constraint:  In memory graph implementation operations should levereage JGraphT
Constraint:  Data access objects should leverage Apache Tinkerpop for persistence
Constraint:  Data access objects should also have an implementation for SqlLite for persistence
Requirement: For now, do not create unit or integration tests in equivalent package in src/test/java (will do this later)
Requirement:  Gradle is used for builds, there is no Maven in this project

# Definitions

Identifiable:  Represents a versioned item that can be located by a unique NanoId
Identifiable:  Each version is an int that starts at 1 and increments as history is added
Identifiable:  Locator represents a unique instance of a versioned item (NanoId + version)
Identifiable:  Active versions have no expire date
Identifiable:  Only one version can be active at a given time for a given NanoId
Identifiable:  Version history is a given NanoId and its available versions (i.e., one NanoId to many Locator)

Component:  A component is a versioned, maximally connected subgraph identified by a unique componentID
Component:  Current version should have a null expired timestamp (see contract of Identifiable)
Component:  Any node can belong to 0 or more components
Component:  Any edge can belong to 0 or more components
Component:  All elements in a component should be versionable (versionID) and instances of Element
Component:  Components do not nest or contain other components but different Component instances can have common elements
Component:  The leaf elements of a Component must be a Node (see contract of Path)
Component:  All elements in a Component must be connected to one another (no dangling edges or nodes)
Component:  Elements in a Component cannot form a cycle

# Packages

API classes reside in dev.iq.graph.api
Access classes reside in dev.iq.graph.access
Model classes reside in dev.iq.graph.model
Persistence classes reside in dev.iq.graph.persistence
Implementations should reside in their own package when multiple implementations exist (e.g., sqllite and tinkerpop)

# Referential Integrity Constraints

The overall graph and any component sub-graphs must maintain referential integrity in response to update() and expire()
operations.  In particular, please ensure that:

- Each udpate() expires the current version and then adds an incremented version
- When a node is expired (directly or via an update), its incoming and outgoing edges are also expired
- When a component is updated (its Data or Type), that the subgraph remains valid both before and after the update
- Components should be recreatable as of an Instant (timestamp) to accurately recreate the subgraph
- When a component's edges or nodes are modified, the component again must be recreatable as of an Instant
- The above constraints comprise portions of referential integrity that must be maintained
