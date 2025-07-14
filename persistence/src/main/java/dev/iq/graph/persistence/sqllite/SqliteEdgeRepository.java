package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.serde.PropertiesSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleEdge;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * SQLite implementation of EdgeRepository.
 */
public final class SqliteEdgeRepository implements VersionedRepository<Edge> {

    private final SqliteNodeRepository nodeRepository;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();
    private final SqliteSession session;

    public SqliteEdgeRepository(final SqliteSession session, final SqliteNodeRepository nodeRepository) {
        this.session = session;
        this.nodeRepository = nodeRepository;
        // Schema initialization is handled by SqliteConnectionProvider
    }

    private Connection getConnection() {
        // Use the connection from the associated session
        return session.connection();
    }


    @Override
    public Edge save(final Edge edge) {
        final var sql = """
            INSERT INTO edge (id, version_id, source_id, source_version_id, target_id, target_version_id, created, expired)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        Io.withVoid(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, edge.locator().id().id());
                stmt.setInt(2, edge.locator().version());
                stmt.setString(3, edge.source().locator().id().id());
                stmt.setInt(4, edge.source().locator().version());
                stmt.setString(5, edge.target().locator().id().id());
                stmt.setInt(6, edge.target().locator().version());
                stmt.setString(7, edge.created().toString());
                stmt.setString(8, edge.expired().map(Object::toString).orElse(null));
                stmt.executeUpdate();
            }

            // Save properties in separate table
            saveProperties(edge.locator().id(), edge.locator().version(), edge.data());
        });

        return edge;
    }

    @Override
    public Optional<Edge> findActive(final NanoId edgeId) {
        final var sql = """
            SELECT DISTINCT e.id, e.version_id, e.source_id, e.source_version_id, e.target_id, e.target_version_id, e.created, e.expired
            FROM edge e
            WHERE e.id = ? AND e.expired IS NULL
            ORDER BY e.version_id DESC LIMIT 1
            """;

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, edgeId.id());
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(createEdgeFromResultSet(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Edge> findAll(final NanoId edgeId) {
        final var sql = """
            SELECT DISTINCT e.id, e.version_id, e.source_id, e.source_version_id, e.target_id, e.target_version_id, e.created, e.expired
            FROM edge e
            WHERE e.id = ?
            ORDER BY e.version_id
            """;

        return Io.withReturn(() -> {
            final var edges = new ArrayList<Edge>();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, edgeId.id());
                try (final var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        final var edge = createEdgeFromResultSet(rs);
                        if (edge != null) {
                            edges.add(edge);
                        }
                    }
                    return edges;
                }
            }
        });
    }

    @Override
    public Optional<Edge> find(final Locator locator) {
        final var sql = """
            SELECT DISTINCT e.id, e.version_id, e.source_id, e.source_version_id, e.target_id, e.target_version_id, e.created, e.expired
            FROM edge e
            WHERE e.id = ? AND e.version_id = ?
            """;

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, locator.id().id());
                stmt.setInt(2, locator.version());
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(createEdgeFromResultSet(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Edge> findAt(final NanoId edgeId, final Instant timestamp) {
        final var sql = """
            SELECT DISTINCT e.id, e.version_id, e.source_id, e.source_version_id, e.target_id, e.target_version_id, e.created, e.expired
            FROM edge e
            WHERE e.id = ? AND e.created <= ? AND (e.expired IS NULL OR e.expired > ?)
            ORDER BY e.version_id DESC
            LIMIT 1
            """;

        return Io.withReturn(() -> {
            final var timestampStr = timestamp.toString();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, edgeId.id());
                stmt.setString(2, timestampStr);
                stmt.setString(3, timestampStr);
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(createEdgeFromResultSet(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public boolean delete(final NanoId edgeId) {
        final var sql = "DELETE FROM edge WHERE id = ?";

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, edgeId.id());
                return stmt.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var sql = "UPDATE edge SET expired = ? WHERE id = ? AND expired IS NULL";

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, expiredAt.toString());
                stmt.setString(2, elementId.id());
                return stmt.executeUpdate() > 0;
            }
        });
    }


    private Edge createEdgeFromResultSet(final ResultSet rs) throws SQLException {
        final var id = new NanoId(rs.getString("id"));
        final var versionId = rs.getInt("version_id");
        final var created = Instant.parse(rs.getString("created"));

        Optional<Instant> expired = Optional.empty();
        final var expiredStr = rs.getString("expired");
        if (expiredStr != null) {
            expired = Optional.of(Instant.parse(expiredStr));
        }

        final var data = loadProperties(id, versionId);

        // Get source and target nodes with specific versions
        final var sourceId = new NanoId(rs.getString("source_id"));
        final var sourceVersionId = rs.getInt("source_version_id");
        final var targetId = new NanoId(rs.getString("target_id"));
        final var targetVersionId = rs.getInt("target_version_id");

        final var sourceNode = nodeRepository
            .find(new Locator(sourceId, sourceVersionId))
            .orElseThrow(() -> new RuntimeException("missing source $sourceId"));
        final var targetNode = nodeRepository
            .find(new Locator(targetId, targetVersionId))
            .orElseThrow(() -> new RuntimeException("missing target $targetId"));

        final var locator = new Locator(id, versionId);
        return new SimpleEdge(locator, sourceNode, targetNode, data, created, expired);
    }

    private void saveProperties(final NanoId edgeId, final int version, final Data data) {
        final var sql = """
            INSERT INTO edge_properties (id, version_id, property_key, property_value)
            VALUES (?, ?, ?, ?)
            """;

        final var properties = serde.serialize(data);
        Io.withVoid(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                for (final var entry : properties.entrySet()) {
                    stmt.setString(1, edgeId.id());
                    stmt.setInt(2, version);
                    stmt.setString(3, entry.getKey());
                    stmt.setString(4, String.valueOf(entry.getValue()));
                    stmt.executeUpdate();
                }
            }
        });
    }

    private Data loadProperties(final NanoId edgeId, final int version) {
        final var sql = """
            SELECT property_key, property_value
            FROM edge_properties
            WHERE id = ? AND version_id = ?
            """;

        return Io.withReturn(() -> {
            final var properties = new HashMap<String, Object>();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, edgeId.id());
                stmt.setInt(2, version);
                try (final var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        properties.put(rs.getString("property_key"), rs.getString("property_value"));
                    }
                }
            }
            return serde.deserialize(properties);
        });
    }
}
