package ar.edu.is2.scouting.domain.player;

import ar.edu.is2.scouting.domain.user.UserAccountService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvImportService {
    private final JdbcTemplate jdbc;
    private final UserAccountService users;

    public CsvImportService(JdbcTemplate jdbc, UserAccountService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public record ImportError(int row, String message) {}

    @Transactional
    public void record(String fileName, int rowCount, int imported, int rejected,
            List<ImportError> errors, String email) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into csv_import(file_name, imported_by, row_count, imported_count, rejected_count, status)
                    values (?,?,?,?,?,'CONFIRMED')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, fileName == null || fileName.isBlank() ? "import.csv" : fileName);
            statement.setLong(2, users.idByEmail(email));
            statement.setInt(3, rowCount);
            statement.setInt(4, imported);
            statement.setInt(5, rejected);
            return statement;
        }, key);
        long importId = ((Number) key.getKeys().get("id")).longValue();
        for (ImportError error : errors) {
            jdbc.update("insert into import_error(import_id,row_number,message) values (?,?,?)",
                    importId, error.row(), error.message());
        }
    }
}
