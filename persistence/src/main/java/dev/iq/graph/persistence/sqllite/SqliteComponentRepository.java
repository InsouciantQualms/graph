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
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.serde.PropertiesSerde;
import dev.iq.graph.model.serde.Serde;
import dev.iq.graph.model.simple.SimpleComponent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * SQLite implementation of ComponentRepository.
 */
public final class SqliteComponentRepository implements VersionedRepository<Component> {

    private final SqliteNodeRepository nodeRepository;
    private final SqliteEdgeRepository edgeRepository;
    private final Serde<Map<String, Object>> serde = new PropertiesSerde();
    private final SqliteSession session;

    public SqliteComponentRepository(
            final SqliteSession session,
            final SqliteNodeRepository nodeRepository,
            final SqliteEdgeRepository edgeRepository) {
        this.session = session;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        // Schema initialization is handled by SqliteConnectionProvider
    }

    private Handle getHandle() {

        return session.handle();
    }

    @Override
    public Component save(final Component component) {
        final var sql =
                """
            INSERT INTO component (id, version_id, created, expired)
            VALUES (:id, :version_id, :created, :expired)
            """;

        Io.withVoid(() -> {
            getHandle()
                    .createUpdate(sql)
                    .bind("id", component.locator().id().id())
                    .bind("version_id", component.locator().version())
                    .bind("created", component.created().toString())
                    .bind("expired", component.expired().map(Object::toString).orElse(null))
                    .execute();

            // Save properties in separate table
            saveProperties(component.locator().id(), component.locator().version(), component.data());
        });

        saveComponentElements(component);
        return component;
    }

    @Override
    public Optional<Component> findActive(final NanoId id) {
        final var sql =
                """
            SELECT id, version_id, created, expired
            FROM component
            WHERE id = :id AND expired IS NULL
            ORDER BY version_id DESC
            LIMIT 1
            """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", id.id())
                .map(new ComponentMapper())
                .findOne());
    }

    @Override
    public Optional<Component> findAt(final NanoId id, final Instant timestamp) {
        final var sql =
                """
            SELECT id, version_id, created, expired
            FROM component
            WHERE id = :id
              AND created <= :timestamp
              AND (expired IS NULL OR expired > :timestamp)
            ORDER BY version_id DESC
            LIMIT 1
            """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", id.id())
                .bind("timestamp", timestamp.toString())
                .map(new ComponentMapper())
                .findOne());
    }

    @Override
    public Optional<Component> find(final Locator locator) {
        final var sql =
                """
            SELECT id, version_id, created, expired
            FROM component
            WHERE id = :id AND version_id = :version_id
            """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", locator.id().id())
                .bind("version_id", locator.version())
                .map(new ComponentMapper())
                .findOne());
    }

    @Override
    public List<Component> findAll(final NanoId id) {
        final var sql =
                """
            SELECT id, version_id, created, expired
            FROM component
            WHERE id = :id
            ORDER BY version_id
            """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", id.id())
                .map(new ComponentMapper())
                .list());
    }

    @Override
    public boolean expire(final NanoId id, final Instant expiredAt) {
        final var sql =
                """
            UPDATE component
            SET expired = :expired
            WHERE id = :id AND expired IS NULL
            """;

        return Io.withReturn(() -> getHandle()
                        .createUpdate(sql)
                        .bind("expired", expiredAt.toString())
                        .bind("id", id.id())
                        .execute()
                > 0);
    }

    @Override
    public boolean delete(final NanoId id) {
        final var deleteElementsSql =
                """
            DELETE FROM component_element
            WHERE component_id = :id
            """;

        final var deleteComponentSql = """
            DELETE FROM component
            WHERE id = :id
            """;

        return Io.withReturn(() -> {
            getHandle().createUpdate(deleteElementsSql).bind("id", id.id()).execute();

            return getHandle()
                            .createUpdate(deleteComponentSql)
                            .bind("id", id.id())
                            .execute()
                    > 0;
        });
    }

    private class ComponentMapper implements RowMapper<Component> {

        @Override
        public final Component map(final ResultSet rs, final StatementContext ctx) throws SQLException {

            final var id = new NanoId(rs.getString("id"));
            final var version = rs.getInt("version_id");
            final var locator = new Locator(id, version);
            final var created = Instant.parse(rs.getString("created"));
            final var expiredStr = rs.getString("expired");
            final var expired =
                    (expiredStr != null) ? Optional.of(Instant.parse(expiredStr)) : Optional.<Instant>empty();
            final var data = loadProperties(id, version);

            final var elements = loadComponentElements(id, version);

            return new SimpleComponent(locator, elements, data, created, expired);
        }
    }

    private void saveProperties(final NanoId componentId, final int version, final Data data) {
        final var sql =
                """
            INSERT INTO component_properties (id, version_id, property_key, property_value)
            VALUES (:id, :version_id, :property_key, :property_value)
            """;

        final var properties = serde.serialize(data);
        Io.withVoid(() -> {
            final var batch = getHandle().prepareBatch(sql);
            for (final var entry : properties.entrySet()) {
                batch.bind("id", componentId.id())
                        .bind("version_id", version)
                        .bind("property_key", entry.getKey())
                        .bind("property_value", String.valueOf(entry.getValue()))
                        .add();
            }
            batch.execute();
        });
    }

    private Data loadProperties(final NanoId componentId, final int version) {
        final var sql =
                """
            SELECT property_key, property_value
            FROM component_properties
            WHERE id = :id AND version_id = :version_id
            """;

        return Io.withReturn(() -> {
            final var properties = new HashMap<String, Object>();
            getHandle()
                    .createQuery(sql)
                    .bind("id", componentId.id())
                    .bind("version_id", version)
                    .map((rs, ctx) -> {
                        properties.put(rs.getString("property_key"), rs.getString("property_value"));
                        return null;
                    })
                    .list();
            return serde.deserialize(properties);
        });
    }

    private void saveComponentElements(final Component component) {
        final var sql =
                """
            INSERT INTO component_element (component_id, component_version, element_id, element_version, element_type)
            VALUES (:component_id, :component_version, :element_id, :element_version, :element_type)
            """;

        Io.withVoid(() -> {
            final var batch = getHandle().prepareBatch(sql);
            for (final var element : component.elements()) {
                batch.bind("component_id", component.locator().id().id())
                        .bind("component_version", component.locator().version())
                        .bind("element_id", element.locator().id().id())
                        .bind("element_version", element.locator().version())
                        .bind("element_type", element.getClass().getSimpleName())
                        .add();
            }
            batch.execute();
        });
    }

    private List<Element> loadComponentElements(final NanoId componentId, final int componentVersion) {
        final var sql =
                """
            SELECT element_id, element_version, element_type
            FROM component_element
            WHERE component_id = :component_id AND component_version = :component_version
            """;

        return Io.withReturn(() -> {
            final var elements = new ArrayList<Element>();
            getHandle()
                    .createQuery(sql)
                    .bind("component_id", componentId.id())
                    .bind("component_version", componentVersion)
                    .map((rs, ctx) -> {
                        final var elementId = new NanoId(rs.getString("element_id"));
                        final var elementVersion = rs.getInt("element_version");
                        final var elementType = rs.getString("element_type");

                        if ("SimpleNode".equals(elementType)) {
                            nodeRepository
                                    .find(new Locator(elementId, elementVersion))
                                    .ifPresent(elements::add);
                        } else if ("SimpleEdge".equals(elementType)) {
                            edgeRepository
                                    .find(new Locator(elementId, elementVersion))
                                    .ifPresent(elements::add);
                        }
                        return null;
                    })
                    .list();
            return elements;
        });
    }
}
