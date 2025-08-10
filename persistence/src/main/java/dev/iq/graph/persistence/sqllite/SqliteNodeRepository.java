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
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import dev.iq.graph.persistence.serde.PropertiesSerde;
import dev.iq.graph.persistence.serde.Serde;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.springframework.stereotype.Repository;

/**
 * SQLite implementation of NodeRepository.
 */
@Repository("sqliteNodeRepository")
public final class SqliteNodeRepository implements ExtendedVersionedRepository<Node> {

    private final Serde<Map<String, Object>> serde = new PropertiesSerde();
    private final SqliteHandleProvider session;

    public SqliteNodeRepository(final SqliteHandleProvider session) {
        this.session = session;
    }

    private Handle getHandle() {

        return session.handle();
    }

    @Override
    public Node save(final Node node) {
        final var sql =
                """
                INSERT INTO node (id, version_id, type, created, expired)
                VALUES (:id, :version_id, :type, :created, :expired)
                """;

        Io.withVoid(() -> {
            getHandle()
                    .createUpdate(sql)
                    .bind("id", node.locator().id().id())
                    .bind("version_id", node.locator().version())
                    .bind("type", node.type().code())
                    .bind("created", node.created().toString())
                    .bind("expired", node.expired().map(Object::toString).orElse(null))
                    .execute();

            // Save properties in separate table
            saveProperties(node.locator().id(), node.locator().version(), node.data());

            // Save components in junction table
            saveComponents(node.locator().id(), node.locator().version(), node.components());
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
    public List<Node> findVersions(final NanoId nodeId) {
        return findAll(nodeId);
    }

    @Override
    public Node find(final Locator locator) {
        final var sql = "SELECT * FROM node WHERE id = :id AND version_id = :version_id";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", locator.id().id())
                .bind("version_id", locator.version())
                .map(new NodeMapper())
                .findOne()
                .orElseThrow(() -> new IllegalArgumentException("Node not found for locator: " + locator)));
    }

    @Override
    public Optional<Node> findAt(final NanoId nodeId, final Instant timestamp) {
        final var sql =
                """
                SELECT * FROM node
                WHERE id = :id AND created <= :timestamp
                      AND (expired IS NULL OR expired > :timestamp)
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

    @Override
    public List<NanoId> allIds() {
        final var sql = "SELECT DISTINCT id FROM node";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .map((rs, ctx) -> new NanoId(rs.getString("id")))
                .list());
    }

    @Override
    public List<NanoId> allActiveIds() {
        final var sql = "SELECT DISTINCT id FROM node WHERE expired IS NULL";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .map((rs, ctx) -> new NanoId(rs.getString("id")))
                .list());
    }

    private class NodeMapper implements RowMapper<Node> {

        @Override
        public final Node map(final ResultSet rs, final StatementContext ctx) throws SQLException {

            final var id = new NanoId(rs.getString("id"));
            final var versionId = rs.getInt("version_id");
            final var type = new SimpleType(rs.getString("type"));
            final var created = Instant.parse(rs.getString("created"));

            Optional<Instant> expired = Optional.empty();
            final var expiredStr = rs.getString("expired");
            if (expiredStr != null) {
                expired = Optional.of(Instant.parse(expiredStr));
            }

            final var data = loadProperties(id, versionId);
            final var locator = new Locator(id, versionId);

            final var components = loadComponents(id, versionId);

            return new SimpleNode(locator, type, data, components, created, expired);
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

    private void saveComponents(final NanoId nodeId, final int version, final Set<Locator> components) {
        final var sql =
                """
                INSERT INTO node_components (node_id, node_version, component_id, component_version)
                VALUES (:node_id, :node_version, :component_id, :component_version)
                """;

        if (components.isEmpty()) {
            return;
        }

        Io.withVoid(() -> {
            final var batch = getHandle().prepareBatch(sql);
            for (final var component : components) {
                batch.bind("node_id", nodeId.id())
                        .bind("node_version", version)
                        .bind("component_id", component.id().id())
                        .bind("component_version", component.version())
                        .add();
            }
            batch.execute();
        });
    }

    private Set<Locator> loadComponents(final NanoId nodeId, final int version) {
        final var sql =
                """
                SELECT component_id, component_version
                FROM node_components
                WHERE node_id = :node_id AND node_version = :node_version
                """;

        return Io.withReturn(() -> {
            final var components = new HashSet<Locator>();
            getHandle()
                    .createQuery(sql)
                    .bind("node_id", nodeId.id())
                    .bind("node_version", version)
                    .map((rs, ctx) -> {
                        final var componentId = new NanoId(rs.getString("component_id"));
                        final var componentVersion = rs.getInt("component_version");
                        components.add(new Locator(componentId, componentVersion));
                        return null;
                    })
                    .list();
            return components;
        });
    }
}
