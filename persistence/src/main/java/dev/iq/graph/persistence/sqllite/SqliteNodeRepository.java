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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * SQLite implementation of NodeRepository.
 */
public final class SqliteNodeRepository implements VersionedRepository<Node> {

    private final Serde<Map<String, Object>> serde = new PropertiesSerde();
    private final SqliteSession session;

    public SqliteNodeRepository(final SqliteSession session) {
        this.session = session;
    }

    private Connection getConnection() {
        // Use the connection from the associated session
        return session.connection();
    }

    @Override
    public Node save(final Node node) {
        final var sql = """
            INSERT INTO node (id, version_id, created, expired)
            VALUES (?, ?, ?, ?)
            """;

        Io.withVoid(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, node.locator().id().id());
                stmt.setInt(2, node.locator().version());
                stmt.setString(3, node.created().toString());
                stmt.setString(4, node.expired().map(Object::toString).orElse(null));
                stmt.executeUpdate();
            }

            // Save properties in separate table
            saveProperties(node.locator().id(), node.locator().version(), node.data());
        });

        return node;
    }

    @Override
    public Optional<Node> findActive(final NanoId nodeId) {
        final var sql = "SELECT * FROM node WHERE id = ? AND expired IS NULL ORDER BY version_id DESC LIMIT 1";

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, nodeId.id());
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(resultSetToNode(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Node> findAll(final NanoId nodeId) {
        final var sql = "SELECT * FROM node WHERE id = ? ORDER BY version_id";

        return Io.withReturn(() -> {
            final var nodes = new ArrayList<Node>();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, nodeId.id());
                try (final var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        nodes.add(resultSetToNode(rs));
                    }
                    return nodes;
                }
            }
        });
    }

    @Override
    public Optional<Node> find(final Locator locator) {
        final var sql = "SELECT * FROM node WHERE id = ? AND version_id = ?";

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, locator.id().id());
                stmt.setInt(2, locator.version());
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(resultSetToNode(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Node> findAt(final NanoId nodeId, final Instant timestamp) {
        final var sql = """
            SELECT * FROM node
            WHERE id = ? AND created <= ? AND (expired IS NULL OR expired > ?)
            ORDER BY version_id DESC
            LIMIT 1
            """;

        return Io.withReturn(() -> {
            final var timestampStr = timestamp.toString();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, nodeId.id());
                stmt.setString(2, timestampStr);
                stmt.setString(3, timestampStr);
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(resultSetToNode(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public boolean delete(final NanoId nodeId) {
        final var sql = "DELETE FROM node WHERE id = ?";

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, nodeId.id());
                return stmt.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean expire(final NanoId elementId, final Instant expiredAt) {
        final var sql = "UPDATE node SET expired = ? WHERE id = ? AND expired IS NULL";

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, expiredAt.toString());
                stmt.setString(2, elementId.id());
                return stmt.executeUpdate() > 0;
            }
        });
    }


    private Node resultSetToNode(final ResultSet rs) throws SQLException {
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

    private void saveProperties(final NanoId nodeId, final int version, final Data data) {
        final var sql = """
            INSERT INTO node_properties (id, version_id, property_key, property_value)
            VALUES (?, ?, ?, ?)
            """;

        final var properties = serde.serialize(data);
        Io.withVoid(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                for (final var entry : properties.entrySet()) {
                    stmt.setString(1, nodeId.id());
                    stmt.setInt(2, version);
                    stmt.setString(3, entry.getKey());
                    stmt.setString(4, String.valueOf(entry.getValue()));
                    stmt.executeUpdate();
                }
            }
        });
    }

    private Data loadProperties(final NanoId nodeId, final int version) {
        final var sql = """
            SELECT property_key, property_value
            FROM node_properties
            WHERE id = ? AND version_id = ?
            """;

        return Io.withReturn(() -> {
            final var properties = new HashMap<String, Object>();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, nodeId.id());
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