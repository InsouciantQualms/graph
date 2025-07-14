package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.serde.PropertiesSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleComponent;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * SQLite implementation of ComponentRepository.
 */
public final class SqliteComponentRepository implements VersionedRepository<Component> {

    private final SqliteNodeRepository nodeRepository;
    private final SqliteEdgeRepository edgeRepository;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();
    private final SqliteSession session;

    public SqliteComponentRepository(final SqliteSession session,
        final SqliteNodeRepository nodeRepository,
        final SqliteEdgeRepository edgeRepository
    ) {
        this.session = session;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        // Schema initialization is handled by SqliteConnectionProvider
    }

    private Connection getConnection() {
        // Use the connection from the associated session
        return session.connection();
    }

    @Override
    public Component save(final Component component) {
        final var sql = """
            INSERT INTO component (id, version_id, created, expired)
            VALUES (?, ?, ?, ?)
            """;

        Io.withVoid(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, component.locator().id().id());
                stmt.setInt(2, component.locator().version());
                stmt.setString(3, component.created().toString());
                stmt.setString(4, component.expired().map(Object::toString).orElse(null));
                stmt.executeUpdate();
            }

            // Save properties in separate table
            saveProperties(component.locator().id(), component.locator().version(), component.data());
        });

        saveComponentElements(component);
        return component;
    }

    @Override
    public Optional<Component> findActive(final NanoId id) {
        final var sql = """
            SELECT id, version_id, created, expired, data
            FROM component
            WHERE id = ? AND expired IS NULL
            ORDER BY version_id DESC
            LIMIT 1
            """;

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, id.id());
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapComponent(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<Component> findAt(final NanoId id, final Instant timestamp) {
        final var sql = """
            SELECT id, version_id, created, expired, data
            FROM component
            WHERE id = ?
              AND created <= ?
              AND (expired IS NULL OR expired > ?)
            ORDER BY version_id DESC
            LIMIT 1
            """;

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, id.id());
                stmt.setString(2, timestamp.toString());
                stmt.setString(3, timestamp.toString());
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapComponent(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<Component> find(final Locator locator) {
        final var sql = """
            SELECT id, version_id, created, expired, data
            FROM component
            WHERE id = ? AND version_id = ?
            """;

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, locator.id().id());
                stmt.setInt(2, locator.version());
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapComponent(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<Component> findAll(final NanoId id) {
        final var sql = """
            SELECT id, version_id, created, expired, data
            FROM component
            WHERE id = ?
            ORDER BY version_id
            """;

        return Io.withReturn(() -> {
            final var components = new ArrayList<Component>();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, id.id());
                try (final var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        components.add(mapComponent(rs));
                    }
                    return components;
                }
            }
        });
    }


    @Override
    public boolean expire(final NanoId id, final Instant expiredAt) {
        final var sql = """
            UPDATE component
            SET expired = ?
            WHERE id = ? AND expired IS NULL
            """;

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, expiredAt.toString());
                stmt.setString(2, id.id());
                return stmt.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean delete(final NanoId id) {
        final var deleteElementsSql = """
            DELETE FROM component_element
            WHERE component_id = ?
            """;

        final var deleteComponentSql = """
            DELETE FROM component
            WHERE id = ?
            """;

        return Io.withReturn(() -> {
            try (final var stmt = getConnection().prepareStatement(deleteElementsSql)) {
                stmt.setString(1, id.id());
                stmt.executeUpdate();
            }

            try (final var stmt = getConnection().prepareStatement(deleteComponentSql)) {
                stmt.setString(1, id.id());
                return stmt.executeUpdate() > 0;
            }
        });
    }


    private Component mapComponent(final ResultSet rs) throws SQLException {
        final var id = new NanoId(rs.getString("id"));
        final var version = rs.getInt("version_id");
        final var locator = new Locator(id, version);
        final var created = Instant.parse(rs.getString("created"));
        final var expiredStr = rs.getString("expired");
        final var expired = (expiredStr != null) ? Optional.of(Instant.parse(expiredStr)) : Optional.<Instant>empty();
        final var data = loadProperties(id, version);

        final var elements = loadComponentElements(id, version);

        return new SimpleComponent(locator, elements, data, created, expired);
    }

    private void saveProperties(final NanoId componentId, final int version, final Data data) {
        final var sql = """
            INSERT INTO component_properties (id, version_id, property_key, property_value)
            VALUES (?, ?, ?, ?)
            """;

        final var properties = serde.serialize(data);
        Io.withVoid(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                for (final var entry : properties.entrySet()) {
                    stmt.setString(1, componentId.id());
                    stmt.setInt(2, version);
                    stmt.setString(3, entry.getKey());
                    stmt.setString(4, String.valueOf(entry.getValue()));
                    stmt.executeUpdate();
                }
            }
        });
    }

    private Data loadProperties(final NanoId componentId, final int version) {
        final var sql = """
            SELECT property_key, property_value
            FROM component_properties
            WHERE id = ? AND version_id = ?
            """;

        return Io.withReturn(() -> {
            final var properties = new HashMap<String, Object>();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, componentId.id());
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

    private void saveComponentElements(final Component component) {
        final var sql = """
            INSERT INTO component_element (component_id, component_version, element_id, element_version, element_type)
            VALUES (?, ?, ?, ?, ?)
            """;

        Io.withVoid(() -> {
            try (final var stmt = getConnection().prepareStatement(sql)) {
                for (final var element : component.elements()) {
                    stmt.setString(1, component.locator().id().id());
                    stmt.setInt(2, component.locator().version());
                    stmt.setString(3, element.locator().id().id());
                    stmt.setInt(4, element.locator().version());
                    stmt.setString(5, element.getClass().getSimpleName());
                    stmt.executeUpdate();
                }
            }
        });
    }

    private List<Element> loadComponentElements(final NanoId componentId, final int componentVersion) {
        final var sql = """
            SELECT element_id, element_version, element_type
            FROM component_element
            WHERE component_id = ? AND component_version = ?
            """;

        return Io.withReturn(() -> {
            final var elements = new ArrayList<Element>();
            try (final var stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, componentId.id());
                stmt.setInt(2, componentVersion);
                try (final var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        final var elementId = new NanoId(rs.getString("element_id"));
                        final var elementVersion = rs.getInt("element_version");
                        final var elementType = rs.getString("element_type");

                        if ("SimpleNode".equals(elementType)) {
                            nodeRepository.find(new Locator(elementId, elementVersion))
                                .ifPresent(elements::add);
                        } else if ("SimpleEdge".equals(elementType)) {
                            edgeRepository.find(new Locator(elementId, elementVersion))
                                .ifPresent(elements::add);
                        }
                    }
                    return elements;
                }
            }
        });
    }
}