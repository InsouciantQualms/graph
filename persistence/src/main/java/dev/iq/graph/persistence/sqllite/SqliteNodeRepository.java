/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.serde.PropertiesSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleNode;
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

/**
 * SQLite implementation of NodeRepository.
 */
public final class SqliteNodeRepository implements VersionedRepository<Node> {

    private final Serde<Map<String, Object>> serde = new PropertiesSerde();
    private final SqliteSession session;

    public SqliteNodeRepository(final SqliteSession session) {
        this.session = session;
    }

    private Handle getHandle() {

        return session.handle();
    }

    @Override
    public Node save(final Node node) {
        final var sql =
                """
            INSERT INTO node (id, version_id, created, expired)
            VALUES (:id, :version_id, :created, :expired)
            """;

        Io.withVoid(() -> {
            getHandle()
                    .createUpdate(sql)
                    .bind("id", node.locator().id().id())
                    .bind("version_id", node.locator().version())
                    .bind("created", node.created().toString())
                    .bind("expired", node.expired().map(Object::toString).orElse(null))
                    .execute();

            // Save properties in separate table
            saveProperties(node.locator().id(), node.locator().version(), node.data());
        });

        return node;
    }

    @Override
    public Optional<Node> findActive(final NanoId nodeId) {
        final var sql = "SELECT * FROM node WHERE id = :id AND expired IS NULL ORDER BY version_id DESC LIMIT 1";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", nodeId.id())
                .map(new NodeMapper())
                .findOne());
    }

    @Override
    public List<Node> findAll(final NanoId nodeId) {
        final var sql = "SELECT * FROM node WHERE id = :id ORDER BY version_id";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", nodeId.id())
                .map(new NodeMapper())
                .list());
    }

    @Override
    public Optional<Node> find(final Locator locator) {
        final var sql = "SELECT * FROM node WHERE id = :id AND version_id = :version_id";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", locator.id().id())
                .bind("version_id", locator.version())
                .map(new NodeMapper())
                .findOne());
    }

    @Override
    public Optional<Node> findAt(final NanoId nodeId, final Instant timestamp) {
        final var sql =
                """
            SELECT * FROM node
            WHERE id = :id AND created <= :timestamp AND (expired IS NULL OR expired > :timestamp)
            ORDER BY version_id DESC
            LIMIT 1
            """;

        return Io.withReturn(() -> {
            final var timestampStr = timestamp.toString();
            return getHandle()
                    .createQuery(sql)
                    .bind("id", nodeId.id())
                    .bind("timestamp", timestampStr)
                    .map(new NodeMapper())
                    .findOne();
        });
    }

    @Override
    public boolean delete(final NanoId nodeId) {
        final var sql = "DELETE FROM node WHERE id = :id";

        return Io.withReturn(
                () -> getHandle().createUpdate(sql).bind("id", nodeId.id()).execute() > 0);
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var sql = "UPDATE node SET expired = :expired WHERE id = :id AND expired IS NULL";

        return Io.withReturn(() -> getHandle()
                        .createUpdate(sql)
                        .bind("expired", expiredAt.toString())
                        .bind("id", elementId.id())
                        .execute()
                > 0);
    }

    private class NodeMapper implements RowMapper<Node> {

        @Override
        public final Node map(final ResultSet rs, final StatementContext ctx) throws SQLException {

            final var id = new NanoId(rs.getString("id"));
            final var versionId = rs.getInt("version_id");
            final var created = Instant.parse(rs.getString("created"));

            Optional<Instant> expired = Optional.empty();
            final var expiredStr = rs.getString("expired");
            if (expiredStr != null) {
                expired = Optional.of(Instant.parse(expiredStr));
            }

            final var data = loadProperties(id, versionId);
            final var locator = new Locator(id, versionId);

            return new SimpleNode(locator, List.of(), data, created, expired);
        }
    }

    private void saveProperties(final NanoId nodeId, final int version, final Data data) {
        final var sql =
                """
            INSERT INTO node_properties (id, version_id, property_key, property_value)
            VALUES (:id, :version_id, :property_key, :property_value)
            """;

        final var properties = serde.serialize(data);
        Io.withVoid(() -> {
            final var batch = getHandle().prepareBatch(sql);
            for (final var entry : properties.entrySet()) {
                batch.bind("id", nodeId.id())
                        .bind("version_id", version)
                        .bind("property_key", entry.getKey())
                        .bind("property_value", String.valueOf(entry.getValue()))
                        .add();
            }
            batch.execute();
        });
    }

    private Data loadProperties(final NanoId nodeId, final int version) {
        final var sql =
                """
            SELECT property_key, property_value
            FROM node_properties
            WHERE id = :id AND version_id = :version_id
            """;

        return Io.withReturn(() -> {
            final var properties = new HashMap<String, Object>();
            getHandle()
                    .createQuery(sql)
                    .bind("id", nodeId.id())
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
