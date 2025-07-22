/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.serde.PropertiesSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.springframework.stereotype.Repository;

/**
 * SQLite implementation of EdgeRepository.
 */
@Repository("sqliteEdgeRepository")
public final class SqliteEdgeRepository implements ExtendedVersionedRepository<Edge> {

    private static final String EDGE_BASE_QUERY =
            """
            SELECT DISTINCT e.id, e.version_id, e.source_id, e.source_version_id,
                            e.target_id, e.target_version_id, e.created, e.expired
            FROM edge e
            """;

    private final SqliteNodeRepository nodeRepository;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();
    private final SqliteHandleProvider session;

    public SqliteEdgeRepository(final SqliteHandleProvider session, final SqliteNodeRepository nodeRepository) {
        this.session = session;
        this.nodeRepository = nodeRepository;
        // Schema initialization is handled by SqliteConnectionProvider
    }

    private Handle getHandle() {

        return session.handle();
    }

    @Override
    public Edge save(final Edge edge) {
        final var sql =
                """
                INSERT INTO edge (id, version_id, source_id, source_version_id,
                                  target_id, target_version_id, created, expired)
                VALUES (:id, :version_id, :source_id, :source_version_id,
                        :target_id, :target_version_id, :created, :expired)
                """;

        Io.withVoid(() -> {
            getHandle()
                    .createUpdate(sql)
                    .bind("id", edge.locator().id().id())
                    .bind("version_id", edge.locator().version())
                    .bind("source_id", edge.source().locator().id().id())
                    .bind("source_version_id", edge.source().locator().version())
                    .bind("target_id", edge.target().locator().id().id())
                    .bind("target_version_id", edge.target().locator().version())
                    .bind("created", edge.created().toString())
                    .bind("expired", edge.expired().map(Object::toString).orElse(null))
                    .execute();

            // Save properties in separate table
            saveProperties(edge.locator().id(), edge.locator().version(), edge.data());
        });

        return edge;
    }

    @Override
    public Optional<Edge> findActive(final NanoId edgeId) {
        final var sql = EDGE_BASE_QUERY
                + """
                WHERE e.id = :id AND e.expired IS NULL
                ORDER BY e.version_id DESC LIMIT 1
                """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", edgeId.id())
                .map(new EdgeMapper())
                .findOne());
    }

    @Override
    public List<Edge> findAll(final NanoId edgeId) {
        final var sql = EDGE_BASE_QUERY
                + """
                WHERE e.id = :id
                ORDER BY e.version_id
                """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", edgeId.id())
                .map(new EdgeMapper())
                .list());
    }

    @Override
    public Optional<Edge> find(final Locator locator) {
        final var sql = EDGE_BASE_QUERY
                + """
                WHERE e.id = :id AND e.version_id = :version_id
                """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", locator.id().id())
                .bind("version_id", locator.version())
                .map(new EdgeMapper())
                .findOne());
    }

    @Override
    public Optional<Edge> findAt(final NanoId edgeId, final Instant timestamp) {
        final var sql = EDGE_BASE_QUERY
                + """
                WHERE e.id = :id AND e.created <= :timestamp
                      AND (e.expired IS NULL OR e.expired > :timestamp)
                ORDER BY e.version_id DESC
                LIMIT 1
                """;

        return Io.withReturn(() -> {
            final var timestampStr = timestamp.toString();
            return getHandle()
                    .createQuery(sql)
                    .bind("id", edgeId.id())
                    .bind("timestamp", timestampStr)
                    .map(new EdgeMapper())
                    .findOne();
        });
    }

    @Override
    public boolean delete(final NanoId edgeId) {
        final var sql = "DELETE FROM edge WHERE id = :id";

        return Io.withReturn(
                () -> getHandle().createUpdate(sql).bind("id", edgeId.id()).execute() > 0);
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var sql = "UPDATE edge SET expired = :expired WHERE id = :id AND expired IS NULL";

        return Io.withReturn(() -> getHandle()
                        .createUpdate(sql)
                        .bind("expired", expiredAt.toString())
                        .bind("id", elementId.id())
                        .execute()
                > 0);
    }

    @Override
    public List<NanoId> allIds() {
        final var sql = "SELECT DISTINCT id FROM edge";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .map((rs, ctx) -> new NanoId(rs.getString("id")))
                .list());
    }

    @Override
    public List<NanoId> allActiveIds() {
        final var sql = "SELECT DISTINCT id FROM edge WHERE expired IS NULL";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .map((rs, ctx) -> new NanoId(rs.getString("id")))
                .list());
    }

    private class EdgeMapper implements RowMapper<Edge> {

        @Override
        public final Edge map(final ResultSet rs, final StatementContext ctx) throws SQLException {

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
                    .orElseThrow(() -> new RuntimeException("missing source " + sourceId));
            final var targetNode = nodeRepository
                    .find(new Locator(targetId, targetVersionId))
                    .orElseThrow(() -> new RuntimeException("missing target " + targetId));

            final var locator = new Locator(id, versionId);
            return new SimpleEdge(locator, sourceNode, targetNode, data, created, expired);
        }
    }

    private void saveProperties(final NanoId edgeId, final int version, final Data data) {
        final var sql =
                """
                INSERT INTO edge_properties (id, version_id, property_key, property_value)
                VALUES (:id, :version_id, :property_key, :property_value)
                """;

        final var properties = serde.serialize(data);
        Io.withVoid(() -> {
            final var batch = getHandle().prepareBatch(sql);
            for (final var entry : properties.entrySet()) {
                batch.bind("id", edgeId.id())
                        .bind("version_id", version)
                        .bind("property_key", entry.getKey())
                        .bind("property_value", String.valueOf(entry.getValue()))
                        .add();
            }
            batch.execute();
        });
    }

    private Data loadProperties(final NanoId edgeId, final int version) {
        final var sql =
                """
                SELECT property_key, property_value
                FROM edge_properties
                WHERE id = :id AND version_id = :version_id
                """;

        return Io.withReturn(() -> {
            final var properties = new HashMap<String, Object>();
            getHandle()
                    .createQuery(sql)
                    .bind("id", edgeId.id())
                    .bind("version_id", version)
                    .map((rs, ctx) -> {
                        properties.put(rs.getString("property_key"), rs.getString("property_value"));
                        return null;
                    })
                    .list();
            return serde.deserialize(properties);
        });
    }
}
