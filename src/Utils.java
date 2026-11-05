import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * A collection of static helper methods
 *
 * @author Richmond Salvador
 */
public class Utils {
    private static final String URL = "jdbc:mysql://localhost:3306/G09_lrtops";
    private static final String USER = "appuser";
    private static final String PASSWORD = "password";
    private static final Scanner SCANNER = new Scanner(System.in);

    private static final String RESET = "\u001b[0m";
    private static final String BOLD = "\u001b[1m";
    private static final String RED = "\u001b[31m";

    /**
     * Creates and returns a connection to the database.
     *
     * @return a connection object that is successfully connected to the database. It returns null if the connection failed.
     */
    public static Connection getSqlConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Displays a menu display with the prompt and the choices the user can pick. Would also handle obtaining a valid input
     * from the user. It would return the corresponding value of the chosen option.
     *
     * @param prompt an instruction telling the user what to do or what the menu is about
     * @param options array of strings representing the choices to be displayed
     * @return the chosen option
     */
    public static String displayMenu(String prompt, String[] options) {
        if (options == null || options.length < 1) {
            System.err.println("The provided options cannot be null nor empty");
            return "";
        }

        System.out.println(prompt);
        int len = options.length;
        for (int i = 0; i < len; i++) System.out.printf("  [%d] %s\n", i + 1, options[i]);

        while (true) {
            System.out.print("Please enter your choice: ");
            String input = SCANNER.nextLine();
            if (input.matches("^[0-9]+$")) {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= len) {
                    return options[choice - 1];
                }
            }
            // if input is invalid then print error message
            System.out.println(RED + "Please enter an integer between 1 to " + len + "!" + RESET);
        }
    }

    /**
     * Displays the results of an SQL statement in the terminal in a formatted manner.
     * It would first display the labels in the result, then every row in the result.
     * It would print nothing if the specified result is null.
     *
     * @param result the result of the executed SQL statement
     */
    public static void displaySqlResult(ResultSet result) {
        if (result == null) return;
        try {
            ResultSetMetaData data = result.getMetaData();
            final int fields = data.getColumnCount();
            final int padding = 2;
            int[] sizes = new int[fields];

            // display result field labels
            StringBuffer divider = new StringBuffer("+");
            StringBuffer labels = new StringBuffer("|");
            for (int i = 1; i <= fields; i++) {
                String name = data.getColumnName(i);
                int size = Math.max(data.getColumnDisplaySize(i), name.length());
                sizes[i - 1] = size;
                labels.append(BOLD + name + RESET + " ".repeat(size - name.length() + padding) + "|");
                divider.append("-".repeat(size + padding) + "+");
            }
            System.out.println(divider + "\n" + labels + "\n" + divider);

            // display resulting rows
            while(result.next()) {
                StringBuffer row = new StringBuffer("|");
                for (int i = 1; i <= fields; i++) {
                    String res = result.getString(i);
                    if (res == null) res = "";

                    int width = sizes[i - 1];
                    if (res.length() > width) res = res.substring(0, width - 3) + "..."; // if longer than the width, truncate the string

                    row.append(res + " ".repeat(width - res.length() + padding) + "|");
                }
                System.out.println(row);
            }
            System.out.println(divider);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a list of options containing the provided labels together with its value, a reset option,
     * a confirm option, and a return option
     *
     * @param labels the labels for each option
     * @param values the value for each label
     * @return a list of options
     */
    public static List<String> createModifiableMenu(String[] labels, Object[] values) {
        if (labels == null || values == null || labels.length > values.length) return new ArrayList<>();
        List<String> menu = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            menu.add(String.format("%-25s -> %s", labels[i], values[i] == null ? "None" : values[i]));
        }
        menu.add("Reset");
        menu.add("Confirm");
        menu.add("Return");
        return menu;
    }

    /**
     * Searches the table's database for records that match the user's wanted filters
     *
     * @param tableName the name of the table to be queried
     * @param baseSql a select SQL statement containing the wanted fields
     * @param filters the conditions the user can impose on the query
     * @return the record(s) that match the user's query
     */
    public static ResultSet sqlSearch(String tableName, String baseSql, List<Filter> filters) {
        while (true) {
            // Create the menu
            int size = filters.size();
            String[] menu = new String[size];
            for (int i = 0; i < size; i++) menu[i] = filters.get(i).getLabel();

            String prompt = "Configure " + tableName + " search filters, then select 'Execute search':";
            String choice = displayMenu(prompt, menu);

            if ("Return".equals(choice)) return null;
            else if ("Reset".equals(choice)) for (Filter f : filters) f.setValue(null);
            else if ("Confirm".equals(choice)) return executeSqlSearch(baseSql, filters);
            else {
                Filter sel = null;
                for (int i = 0; i < size && sel == null; i++) {
                    Filter f = filters.get(i);
                    if (f.getLabel().equalsIgnoreCase(choice)) sel = f;
                }
                if (sel == null) continue;
                if (sel.getOptions() == null) {
                    prompt = "Enter " + sel.getLabel() + " filter value (Leave blank to reset): ";
                    sel.setValue(getInput(prompt, sel.getType()));
                } else {
                    List<String> options = new ArrayList<>(List.of(sel.getOptions()));
                    options.add("Reset");
                    prompt = "Enter " + sel.getLabel() + " filter value:";
                    String res = displayMenu(prompt, options.toArray(new String[0]));
                    sel.setValue(("Reset".equalsIgnoreCase(res)) ? null : res);
                }
            }
        }
    }

    /**
     * Adds the user modified filters to the base query then execute that query
     *
     * @param baseSql a select SQL statement containing the wanted fields
     * @param filters the conditions the user can impose on the query
     * @return the record(s) that match the user's query
     */
    public static ResultSet executeSqlSearch(String baseSql, List<Filter> filters) {
        try {
            Connection c = getSqlConnection();

            // Make sure there is a space between the baseSql to separate the conditions to be added
            StringBuilder query = new StringBuilder(baseSql);
            query.append(" ");

            // if there is no where clause
            if (!"WHERE".contains(baseSql.toUpperCase())) query.append("WHERE 1=1 ");

            // Create condition blocks
            List<Object> values = new ArrayList<>();
            List<JDBCType> types = new ArrayList<>();
            for (Filter f : filters) {
                if (f.getValue() == null) continue;
                if ("LIKE".equalsIgnoreCase(f.getOperator())) {
                    query.append(String.format("AND %s LIKE ? ", f.getName()));
                    values.add("%" + f.getValue() + "%");
                }
                else {
                    query.append(String.format("AND %s %s ? ", f.getName(), f.getOperator()));
                    values.add(f.getValue());
                }
                types.add(f.getSqlType());
            }

            ResultSet res = prepareStatement(c, query.toString(), values.toArray(new Object[0]), types.toArray(new JDBCType[0])).executeQuery();
            return res;
        } catch (SQLException e) {
            displayDatabaseError(null, e);
            return null;
        }
    }

    /**
     * Gets the string input of a user, validates the input, then return its corresponding object value depending on the type.
     * If the input were to be blank, then it is assumed that the wanted input would be null.
     *
     * @param prompt instruction to user
     * @param type SQL type
     * @return the converted string input
     */
    public static Object getInput(String prompt, String type) {
        Object val = null;
        while (val == null) {
            System.out.print(prompt);
            String in = SCANNER.nextLine().trim();
            if (in.isEmpty()) return null;
            val = parseInput(in, type);
        } return val;
    }

    /**
     * Gets the user input, validates it, then updates the value of the field based on the input of the user.
     * If the input is blank then it would set its value to null.
     *
     * @param field the field to be updated/modified
     */
    public static void updateValue(Field field) {
        while (true) {
            System.out.printf("Enter %s: ", field.getLabel());
            String in = SCANNER.next().trim();
            if (in.isEmpty()) {
                field.setValue(null);
                break;
            }
            field.setValue(parseInput(in, field.getType()));
        }
    }

    /**
     * Validates the passed input and converts the input to its type if it were to be valid.
     * If the input is invalid then it would display an error message describing why its invalid.
     *
     * @param input the input of the user
     * @param type SQL type
     * @return the converted string input
     */
    public static Object parseInput(String input, String type) {
        if (input == null || type == null) return null;
        type = type.trim().toUpperCase();
        String error = "";

        try {
            if (type.contains("UNSIGNED") && input.contains("-")) { // Check if the number has to be unsigned
                displayInputError("SIGNED");
                return null;
            }
            if (type.contains("INT")) {
                error = "INT";
                return Integer.parseInt(input);
            }
            else if (type.contains("DATETIME")) {
                error = "DATETIME";
                return Timestamp.valueOf(input);
            }
            else if (type.contains("DATE")) {
                error = "DATE";
                return Date.valueOf(input);
            }
            else if (type.contains("VARCHAR")) { // if its a varchar with a specified size
                error = "VARCHAR";
                int len = Integer.parseInt(type.substring(type.indexOf('(') + 1, type.indexOf(')')));
                if (input.length() > len) {
                    displayInputError("LENGTH");
                    return null;
                }
                return input;
            }
            else if (type.contains("DECIMAL")) {
                error = "DECIMAL";
                int deci = Integer.parseInt(type.substring(type.indexOf(',') + 1, type.indexOf(')')));
                int num = Integer.parseInt(type.substring(type.indexOf('(') + 1, type.indexOf(','))) - deci;
                String pattern = String.format("^-?(\\d{1,%d}(\\.\\d{1,%d})?|\\.\\d{1,%d})$", num, deci, deci);
                if (!input.matches(pattern)) {
                    displayInputError("PRECISION");
                    return null;
                }
                return Double.parseDouble(input);
            }
            else return null;
        } catch (Exception e) {
            displayInputError(error);
            return null;
        }
    }

    /**
     * Displays an input error message based on which type of input is expected.
     *
     * @param type SQL type
     */
    public static void displayInputError(String type) {
        String msg = switch (type) {
            case "INT", "INT UNSIGNED", "TINYINT", "SMALLINT", "INTEGER" -> "Please input an number/integer";
            case "DECIMAL" -> "Please input a decimal number";
            case "DATE" -> "Please input a date in this format (YYYY-MM-DD)";
            case "DATETIME" -> "Please input a date and time in this format (YYYY-MM-DD HH:MM:SS)";
            case "IDENTIFIER" -> "Please input a different value, this already exists in the database";
            case "SIGNED" -> "Please input a positive number";
            case "NO_RECORD" -> "Please input an identifier for an existing record in the database";
            case "LENGTH" -> "Please input a shorter text";
            case "DELETE" -> "Cannot delete the record due to it being referenced in other table(s)";
            case "REFERENCE" -> "Cannot modify this field, this value is being referenced in other table(s)";
            case "PRECISION" -> "The decimal is to large/precise. Please enter a smaller/less precise number";
            default -> "Error";
        };
        System.out.println(RED + "Invalid input: " + msg + RESET);
    }

    /**
     * Display error messages regarding database errors.
     *
     * @param type type of error
     * @param e the SQL exception
     */
    public static void displayDatabaseError(String type, Exception e) {
        if (e != null) {
            System.out.println(RED + "Database Error: " + e.toString() + RESET);
            return;
        }
        String msg = switch (type) {
            case "REVISION" -> "Please try again, the record you are trying to update is outdated";
            default -> "Error";
        };
        System.out.println(RED + "Database Error: " + msg + RESET);
    }

    /**
     * Checks if the specified field and value combination exists within the specified table
     *
     * @param table the name of the table in the database
     * @param field the name of the field in the database
     * @param value the value to look for
     * @return true if the record exists, false otherwise
     */
    public static boolean doesRecordExist(String table, String field, String value) {
        Connection c = getSqlConnection();
        String sql = "SELECT 1 FROM " + table + " WHERE " + field + " = ?;";
        try {
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, value);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Deletes a record in a table with the specified field and value combinations. Could also check if other tables depend on this record,
     * if it does then it does not delete the record.
     *
     * @param table the table of the record to be deleted
     * @param relatedTables checks these tables first if the value exists there. Can be null indicating to not check any tables
     * @param field the name of the field in the table
     * @param value the value of the record to be deleted
     * @return true if the record was deleted, false otherwise
     */
    public static boolean safeDeleteRecord(String table, String[] relatedTables, String field, String value) {
        Connection c = getSqlConnection();
        if (c == null) return false;

        try {
            // get revision number
            String sql = String.format("SELECT revno FROM %s WHERE %s = ?;", table, field);
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, value);
            ResultSet res = ps.executeQuery();

            // if there was no match
            if (!res.next()) {
                displayInputError("NO_RECORD");
                return false;
            }
            int revno = res.getInt("revno");

            // check if value exists in other tables
            if (relatedTables != null) {
                for (String t : relatedTables) {
                    if (doesRecordExist(t, field, value)) {
                        displayInputError("DELETE");
                        return false;
                    }
                }
            }

            // delete the record
            sql = String.format("DELETE FROM %s WHERE %s = ? AND revno = ?;", table, field);
            ps = c.prepareStatement(sql);
            ps.setString(1, value);
            ps.setInt(2, revno);
            ps.executeUpdate();
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    /**
     * Creates a prepared statement where it would automatically insert the specified values to the provided sql statement.
     *
     * @param c connection to the database
     * @param sql the sql statement with placeholders for every value
     * @param values the values to be inserted to each placeholder
     * @param types the sql type of each value
     * @return the prepared statement with a sql statement ready to be run
     */
    public static PreparedStatement prepareStatement(Connection c, String sql, Object[] values, JDBCType[] types) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        if (values == null || types == null || values.length != types.length) return ps;
        for (int i = 1; i <= values.length; i++) {
            ps.setObject(i, values[i - 1], types[i - 1]);
        }
        return ps;
    }
}
