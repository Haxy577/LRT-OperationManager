import java.sql.*;

public class SqlUtils {
    private static final String URL = "jdbc:mysql://localhost:3306/G09_lrtops";
    private static final String USER = "appuser";
    private static final String PASSWORD = "password";

    private static final String RESET = "\u001b[0m";
    private static final String BOLD = "\u001b[1m";

    public static Connection getConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void displayResult(ResultSet result) {
        if (result == null) {
            System.err.println("Invalid argument, the passed result cannot be null nor empty");
            return;
        }
        try {
            ResultSetMetaData data = result.getMetaData();
            final int fields = data.getColumnCount();

            // display result field labels
            StringBuffer divider = new StringBuffer("+");
            StringBuffer labels = new StringBuffer("|");
            for (int i = 1; i <= fields; i++) {
                String name = data.getColumnName(i);
                int size = Math.max(data.getColumnDisplaySize(i), name.length());
                labels.append(BOLD + name + RESET + " ".repeat(size - name.length()) + "|");
                divider.append("-".repeat(size) + "+");
            }
            System.out.println(divider + "\n" + labels + "\n" + divider);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
