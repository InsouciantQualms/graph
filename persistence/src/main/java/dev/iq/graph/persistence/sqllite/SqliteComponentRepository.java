/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.simple.SimpleComponent;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.ExtendedVersionedRepository;
import dev.iq.graph.persistence.serde.PropertiesSerde;
import dev.iq.graph.persistence.serde.Serde;
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
 * SQLite implementation of ComponentRepository.
 */
@Repository("sqliteComponentRepository")
public final class SqliteComponentRepository implements ExtendedVersionedRepository<Component> {

    private final Serde<Map<String, Object>> serde = new PropertiesSerde();
    private final SqliteHandleProvider session;

    public SqliteComponentRepository(final SqliteHandleProvider session) {
        this.session = session;
        // Schema initialization is handled by SqliteConnectionProvider
    }

    private Handle getHandle() {

        return session.handle();
    }

    @Override
    public Component save(final Component component) {
        final var sql =
                """
                INSERT INTO component (id, version_id, type, created, expired)
                VALUES (:id, :version_id, :type, :created, :expired)
                """;

        Io.withVoid(() -> {
            getHandle()
                    .createUpdate(sql)
                    .bind("id", component.locator().id().code())
                    .bind("version_id", component.locator().version())
                    .bind("type", component.type().code())
                    .bind("created", component.created().toString())
                    .bind("expired", component.expired().map(Object::toString).orElse(null))
                    .execute();

            // Save properties in separate table
            saveProperties(component.locator().id(), component.locator().version(), component.data());
        });

        return component;
    }

    @Override
    public Optional<Component> findActive(final Uid id) {
        final var sql =
                """
                SELECT id, version_id, type, created, expired
                FROM component
                WHERE id = :id AND expired IS NULL
                ORDER BY version_id DESC
                LIMIT 1
                """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", id.code())
                .map(new ComponentMapper())
                .findOne());
    }

    @Override
    public Optional<Component> findAt(final Uid id, final Instant timestamp) {
        final var sql =
                """
                SELECT id, version_id, type, created, expired
                FROM component
                WHERE id = :id
                  AND created <= :timestamp
                  AND (expired IS NULL OR expired > :timestamp)
                ORDER BY version_id DESC
                LIMIT 1
                """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", id.code())
                .bind("timestamp", timestamp.toString())
                .map(new ComponentMapper())
                .findOne());
    }

    @Override
    public Component find(final Locator locator) {
        final var sql =
                """
                SELECT id, version_id, type, created, expired
                FROM component
                WHERE id = :id AND version_id = :version_id
                """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", locator.id().code())
                .bind("version_id", locator.version())
                .map(new ComponentMapper())
                .findOne()
                .orElseThrow(() -> new IllegalArgumentException("Component not found for locator: " + locator)));
    }

    @Override
    public List<Component> findAll(final Uid id) {
        final var sql =
                """
                SELECT id, version_id, type, created, expired
                FROM component
                WHERE id = :id
                ORDER BY version_id
                """;

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .bind("id", id.code())
                .map(new ComponentMapper())
                .list());
    }

    @Override
    public List<Component> findVersions(final Uid id) {
        return findAll(id);
    }

    @Override
    public boolean expire(final Uid id, final Instant expiredAt) {
        final var sql =
                """
                UPDATE component
                SET expired = :expired
                WHERE id = :id AND expired IS NULL
                """;

        return Io.withReturn(() -> getHandle()
                        .createUpdate(sql)
                        .bind("expired", expiredAt.toString())
                        .bind("id", id.code())
                        .execute()
                > 0);
    }

    @Override
    public boolean delete(final Uid id) {
        final var deleteComponentSql =
                """
                DELETE FROM component
                WHERE id = :id
                """;

        return Io.withReturn(() -> getHandle()
                        .createUpdate(deleteComponentSql)
                        .bind("id", id.code())
                        .execute()
                > 0);
    }

    @Override
    public List<NanoId> allIds() {
        final var sql = "SELECT DISTINCT id FROM component";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .map((rs, ctx) -> NanoId.from(rs.getString("id")))
                .list());
    }

    @Override
    public List<NanoId> allActiveIds() {
        final var sql = "SELECT DISTINCT id FROM component WHERE expired IS NULL";

        return Io.withReturn(() -> getHandle()
                .createQuery(sql)
                .map((rs, ctx) -> NanoId.from(rs.getString("id")))
                .list());
    }

    private class ComponentMapper implements RowMapper<Component> {

        @Override
        public final Component map(final ResultSet rs, final StatementContext ctx) throws SQLException {

            final var id = NanoId.from(rs.getString("id"));
            final var version = rs.getInt("version_id");
            final var locator = new Locator(id, version);
            final var type = new SimpleType(rs.getString("type"));
            final var created = Instant.parse(rs.getString("created"));
            final var expiredStr = rs.getString("expired");
            final var expired =
                    (expiredStr != null) ? Optional.of(Instant.parse(expiredStr)) : Optional.<Instant>empty();
            final var data = loadProperties(id, version);

            return new SimpleComponent(locator, type, data, created, expired);
        }
    }

    private void saveProperties(final Uid componentId, final int version, final Data data) {
        final var sql =
                """
                INSERT INTO component_properties (id, version_id, property_key, property_value)
                VALUES (:id, :version_id, :property_key, :property_value)
                """;

        final var properties = serde.serialize(data);
        Io.withVoid(() -> {
            final var batch = getHandle().prepareBatch(sql);
            for (final var entry : properties.entrySet()) {
                batch.bind("id", componentId.code())
                        .bind("version_id", version)
                        .bind("property_key", entry.getKey())
                        .bind("property_value", String.valueOf(entry.getValue()))
                        .add();
            }
            batch.execute();
        });
    }

    private Data loadProperties(final Uid componentId, final int version) {
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
                    .bind("id", componentId.code())
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
