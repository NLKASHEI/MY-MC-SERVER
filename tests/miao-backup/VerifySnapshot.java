import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;

/** Opens restored copies only; never connects to the server's live databases. */
public class VerifySnapshot {
    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[1]); Files.createDirectories(folder);
        Path restored;
        try (ZipFile zip = new ZipFile(args[0])) {
            var entries = zip.stream().filter(e -> e.getName().endsWith(".mv.db")).toList();
            if (entries.size() != 1) throw new IllegalStateException("Expected one H2 database");
            String name = entries.getFirst().getName();
            if (!Path.of(name).getFileName().toString().equals(name)) throw new IllegalStateException("Unexpected ZIP path");
            restored = folder.resolve(name);
            try (var in = zip.getInputStream(entries.getFirst())) { Files.copy(in, restored, StandardCopyOption.REPLACE_EXISTING); }
        }
        String base = restored.toAbsolutePath().toString().replaceFirst("\\.mv\\.db$", "");
        Connection opened = null;
        for (String user : List.of("", "sa", "root")) {
            try { opened = DriverManager.getConnection("jdbc:h2:" + base + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r", user, ""); break; }
            catch (SQLException e) { if (!"28000".equals(e.getSQLState())) throw e; }
        }
        if (opened == null) throw new IllegalStateException("Cannot open restored database");
        int tables = 0; long rows = 0;
        try (Connection connection = opened; ResultSet rs = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[]{"BASE TABLE", "TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                try (Statement s = connection.createStatement(); ResultSet count = s.executeQuery("SELECT COUNT(*) FROM \"PUBLIC\".\"" + table.replace("\"", "\"\"") + "\"")) {
                    count.next(); rows += count.getLong(1); tables++;
                }
            }
        }
        if (tables == 0) throw new IllegalStateException("Restored database has no plugin tables");
        System.out.println("RESTORE_PASS " + Path.of(args[0]).getFileName() + " tables=" + tables + " rows=" + rows);
    }
}
