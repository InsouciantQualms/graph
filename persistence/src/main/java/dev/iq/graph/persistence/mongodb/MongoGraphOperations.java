/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.GraphLookupOptions;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;

/**
 * MongoDB-specific graph operations using native aggregation pipeline features.
 */
public final class MongoGraphOperations {

    private final MongoDatabase database;
    private final MongoNodeRepository nodeRepository;
    private final MongoEdgeRepository edgeRepository;

    public MongoGraphOperations(
            final MongoDatabase database,
            final MongoNodeRepository nodeRepository,
            final MongoEdgeRepository edgeRepository) {
        this.database = database;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    /**
     * Finds all nodes reachable from a starting node using $graphLookup.
     * This traverses outgoing edges to find connected nodes.
     */
    public List<Node> findReachableNodes(final NanoId startNodeId, final int maxDepth) {
        final var pipeline = Arrays.asList(
                // Start with the specific node
                Aggregates.match(
                        Filters.and(Filters.eq("id", startNodeId.id()), Filters.not(Filters.exists("expired")))),
                // Use $graphLookup to traverse the graph
                Aggregates.graphLookup(
                        "edges",
                        "$id",
                        "targetId",
                        "sourceId",
                        "reachableEdges",
                        new GraphLookupOptions()
                                .maxDepth(maxDepth - 1)
                                .restrictSearchWithMatch(Filters.not(Filters.exists("expired")))),
                // Extract unique target node IDs from the edges
                Aggregates.project(new Document()
                        .append(
                                "nodeIds",
                                new Document(
                                        "$setUnion",
                                        Arrays.asList(
                                                Arrays.asList("$id"),
                                                new Document(
                                                        "$map",
                                                        new Document()
                                                                .append("input", "$reachableEdges")
                                                                .append("as", "edge")
                                                                .append("in", "$$edge.targetId")))))));

        final var result = database.getCollection("nodes").aggregate(pipeline).first();
        if (result == null) {
            return List.of();
        }

        final var nodeIds = result.getList("nodeIds", String.class);
        final var nodes = new ArrayList<Node>();
        for (final var nodeId : nodeIds) {
            nodeRepository.findActive(new NanoId(nodeId)).ifPresent(nodes::add);
        }
        return nodes;
    }

    /**
     * Finds all incoming edges to a node using $lookup.
     */
    public List<Edge> findIncomingEdges(final NanoId nodeId) {
        final var pipeline = Arrays.asList(
                Aggregates.match(Filters.and(Filters.eq("id", nodeId.id()), Filters.not(Filters.exists("expired")))),
                Aggregates.lookup("edges", "id", "targetId", "incomingEdges"),
                Aggregates.unwind("$incomingEdges"),
                Aggregates.match(Filters.not(Filters.exists("incomingEdges.expired"))),
                Aggregates.replaceRoot("$incomingEdges"));

        final var edges = new ArrayList<Edge>();
        final var results = database.getCollection("nodes").aggregate(pipeline);
        for (final var doc : results) {
            final var edgeId = new NanoId(doc.getString("id"));
            edgeRepository.findActive(edgeId).ifPresent(edges::add);
        }
        return edges;
    }

    /**
     * Finds all outgoing edges from a node using $lookup.
     */
    public List<Edge> findOutgoingEdges(final NanoId nodeId) {
        final var pipeline = Arrays.asList(
                Aggregates.match(Filters.and(Filters.eq("id", nodeId.id()), Filters.not(Filters.exists("expired")))),
                Aggregates.lookup("edges", "id", "sourceId", "outgoingEdges"),
                Aggregates.unwind("$outgoingEdges"),
                Aggregates.match(Filters.not(Filters.exists("outgoingEdges.expired"))),
                Aggregates.replaceRoot("$outgoingEdges"));

        final var edges = new ArrayList<Edge>();
        final var results = database.getCollection("nodes").aggregate(pipeline);
        for (final var doc : results) {
            final var edgeId = new NanoId(doc.getString("id"));
            edgeRepository.findActive(edgeId).ifPresent(edges::add);
        }
        return edges;
    }

    /**
     * Finds neighbors (directly connected nodes) using $lookup.
     */
    public List<Node> findNeighbors(final NanoId nodeId) {
        final var pipeline = Arrays.asList(
                Aggregates.match(Filters.and(Filters.eq("id", nodeId.id()), Filters.not(Filters.exists("expired")))),
                // Find outgoing edges
                Aggregates.lookup("edges", "id", "sourceId", "outgoingEdges"),
                // Find incoming edges
                Aggregates.lookup("edges", "id", "targetId", "incomingEdges"),
                // Project neighbor IDs
                Aggregates.project(createNeighborProjection()));

        final var result = database.getCollection("nodes").aggregate(pipeline).first();
        if (result == null) {
            return List.of();
        }

        final var neighborIds = result.getList("neighborIds", String.class);
        final var neighbors = new ArrayList<Node>();
        for (final var neighborId : neighborIds) {
            nodeRepository.findActive(new NanoId(neighborId)).ifPresent(neighbors::add);
        }
        return neighbors;
    }

    private Document createNeighborProjection() {
        return new Document()
                .append(
                        "neighborIds",
                        new Document(
                                "$setUnion",
                                Arrays.asList(
                                        createEdgeMapping("$outgoingEdges", "$$edge.targetId"),
                                        createEdgeMapping("$incomingEdges", "$$edge.sourceId"))));
    }

    private Document createEdgeMapping(final String edgesField, final String idField) {
        return new Document(
                "$map",
                new Document()
                        .append("input", createEdgeFilter(edgesField))
                        .append("as", "edge")
                        .append("in", idField));
    }

    private Document createEdgeFilter(final String edgesField) {
        return new Document(
                "$filter",
                new Document()
                        .append("input", edgesField)
                        .append("as", "edge")
                        .append("cond", createNotExpiredCondition()));
    }

    private Document createNotExpiredCondition() {
        return new Document("$not", new Document("$ifNull", Arrays.asList("$$edge.expired", false)));
    }

    /**
     * Finds all nodes within a certain number of hops using $graphLookup.
     */
    public Set<NanoId> findNodesWithinDistance(final NanoId startNodeId, final int maxDistance) {
        if (maxDistance == 0) {
            // Only return the start node
            final var result = new HashSet<NanoId>();
            if (nodeRepository.findActive(startNodeId).isPresent()) {
                result.add(startNodeId);
            }
            return result;
        }

        final var pipeline = Arrays.asList(
                // Start with the specific node
                Aggregates.match(
                        Filters.and(Filters.eq("id", startNodeId.id()), Filters.not(Filters.exists("expired")))),
                // Use $graphLookup to traverse the graph via edges
                Aggregates.graphLookup(
                        "edges",
                        "$id",
                        "targetId",
                        "sourceId",
                        "reachableEdges",
                        new GraphLookupOptions()
                                .maxDepth(maxDistance - 1)
                                .restrictSearchWithMatch(Filters.not(Filters.exists("expired")))),
                // Extract unique node IDs
                Aggregates.project(new Document()
                        .append("startNode", "$id")
                        .append(
                                "connectedNodes",
                                new Document(
                                        "$map",
                                        new Document()
                                                .append("input", "$reachableEdges")
                                                .append("as", "edge")
                                                .append("in", "$$edge.targetId")))));

        final var nodeIds = new HashSet<NanoId>();
        final var results = database.getCollection("nodes").aggregate(pipeline);

        for (final var result : results) {
            // Add the start node
            nodeIds.add(new NanoId(result.getString("startNode")));

            // Add all connected nodes
            final var connectedIds = result.getList("connectedNodes", String.class);
            if (connectedIds != null) {
                for (final var id : connectedIds) {
                    nodeIds.add(new NanoId(id));
                }
            }
        }

        // Filter out expired nodes
        final var activeNodeIds = new HashSet<NanoId>();
        for (final var nodeId : nodeIds) {
            if (nodeRepository.findActive(nodeId).isPresent()) {
                activeNodeIds.add(nodeId);
            }
        }

        return activeNodeIds;
    }

    /**
     * Checks if a path exists between two nodes using $graphLookup.
     */
    public boolean pathExists(final NanoId sourceId, final NanoId targetId) {
        final var pipeline = Arrays.asList(
                Aggregates.match(Filters.eq("sourceId", sourceId.id())),
                Aggregates.graphLookup(
                        "edges",
                        "$targetId",
                        "targetId",
                        "sourceId",
                        "path",
                        new GraphLookupOptions()
                                .restrictSearchWithMatch(Filters.and(
                                        Filters.not(Filters.exists("expired")),
                                        Filters.ne("targetId", sourceId.id())))),
                Aggregates.match(
                        Filters.or(Filters.eq("targetId", targetId.id()), Filters.in("path.targetId", targetId.id()))),
                Aggregates.limit(1));

        return database.getCollection("edges").aggregate(pipeline).first() != null;
    }
}
