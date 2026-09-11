package com.example.cursorquitterweb.musicmv.repository;

import static org.junit.jupiter.api.Assertions.*;
import com.example.cursorquitterweb.musicmv.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MusicMvLatestPublicationTest {
    @Test void newerCommitCannotBeOverwrittenByLateOlderRequest() throws Exception {
        try (Connection connection = database()) {
            MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(new SqlD1(connection));
            assertTrue(repository.publish("t", "v3"));
            assertFalse(repository.publish("t", "v2"));
            assertEquals("v3", scalar(connection, "SELECT current_version_id FROM templates"));
            assertEquals("draft", scalar(connection, "SELECT status FROM template_versions WHERE version_id='v2'"));
            assertTrue(repository.publish("t", "v3"));
            connection.createStatement().execute("UPDATE template_versions SET validation_status='blocked' WHERE version_id='v3'");
            assertFalse(repository.publish("t", "v3"));
        }
        try (Connection connection = database()) {
            MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(new SqlD1(connection));
            assertTrue(repository.publish("t", "v2"));
            assertTrue(repository.publish("t", "v3"));
            assertFalse(repository.publish("t", "v2"));
            assertEquals("v3", scalar(connection, "SELECT current_version_id FROM templates"));
        }
    }

    @Test void missingUnreadyForeignOrDeletedTargetCannotChangeCurrent() throws Exception {
        try (Connection connection = database()) {
            MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(new SqlD1(connection));
            assertFalse(repository.publish("t", "missing"));
            connection.createStatement().execute("UPDATE template_versions SET validation_status='blocked' WHERE version_id='v3'");
            assertFalse(repository.publish("t", "v3"));
            connection.createStatement().execute("UPDATE template_versions SET template_id='other' WHERE version_id='v2'");
            assertFalse(repository.publish("t", "v2"));
            assertNull(scalar(connection, "SELECT current_version_id FROM templates"));
            connection.createStatement().execute("UPDATE templates SET deleted_at=CURRENT_TIMESTAMP");
            assertFalse(repository.publish("t", "v1"));
        }
    }

    private Connection database() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE");
        c.createStatement().execute("CREATE TABLE templates(template_id VARCHAR PRIMARY KEY,status VARCHAR,current_version_id VARCHAR,revision INT DEFAULT 0,published_at TIMESTAMP,updated_at TIMESTAMP,deleted_at TIMESTAMP)");
        c.createStatement().execute("CREATE TABLE template_versions(version_id VARCHAR PRIMARY KEY,template_id VARCHAR,version_number INT,status VARCHAR,validation_status VARCHAR,published_at TIMESTAMP)");
        c.createStatement().execute("INSERT INTO templates(template_id,status) VALUES('t','draft')");
        for (int n=1;n<=3;n++) c.createStatement().execute("INSERT INTO template_versions VALUES('v"+n+"','t',"+n+",'draft','browser_ready',NULL)");
        return c;
    }

    private String scalar(Connection c, String sql) throws Exception {
        try (ResultSet rows = c.createStatement().executeQuery(sql)) { rows.next();return rows.getString(1); }
    }

    private static class SqlD1 extends D1DatabaseClient {
        private final Connection connection;
        SqlD1(Connection connection) { super(new ObjectMapper());this.connection=connection; }
        @Override public List<D1QueryResult> batch(List<D1Statement> statements) {
            try {
                connection.setAutoCommit(false);
                List<D1QueryResult> results = new ArrayList<>();
                for (D1Statement statement : statements) {
                    try (PreparedStatement prepared = connection.prepareStatement(statement.getSql())) {
                        for (int i=0;i<statement.getParams().size();i++) prepared.setObject(i+1,statement.getParams().get(i));
                        List<Map<String,Object>> rows = new ArrayList<>();
                        if (prepared.execute()) try (ResultSet rs = prepared.getResultSet()) {
                            while(rs.next()) {Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i));rows.add(row);}
                        }
                        results.add(new D1QueryResult(rows,null));
                    }
                }
                connection.commit();return results;
            } catch (SQLException error) {try{connection.rollback();}catch(SQLException ignored){}throw new IllegalStateException(error);}
        }
    }
}
