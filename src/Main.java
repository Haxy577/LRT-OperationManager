import java.sql.*;

public class Main {
    public static void main(String[] args) throws SQLException {
        java.sql.Connection c = SqlUtils.getConnection();
        Statement s = c.createStatement();
        SqlUtils.displayResult(s.executeQuery("SELECT taskno, trainno, taskdate, tasktype, hours, revno FROM servicetask"));
    }
}
