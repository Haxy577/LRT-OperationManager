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
     * @param values the value associated with each option. Can be null, would use the options as the values instead
     * @return the corresponding value of the chosen option
     */
    public static String displayMenu(String prompt, String[] options, String[] values) {
        if (options == null || options.length < 1) {
            System.err.println("The provided options cannot be null nor empty");
            return null;
        }
        if (values != null && options.length != values.length) {
            System.err.println("The options and values must match each other");
            return null;
        }
        String[] res = values == null ? options : values;

        System.out.println(prompt);
        int len = options.length;
        for (int i = 0; i < len; i++) System.out.printf("  [%d] %s\n", i + 1, options[i]);

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("Please enter your choice: ");
            String input = sc.nextLine();
            if (input.matches("^[0-9]+$")) {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= len) {
                    return res[choice - 1];
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

    public static List<String> createModifiableMenu(String[] labels, String[] values) {
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

    public static ResultSet sqlSearch(String tableName, String baseSql, List<Filter> filters) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            // Create the menu
            List<String> menu = new ArrayList<>();
            List<String> ref = new ArrayList<>();
            for (Filter f : filters) {
                String valDisplay = (f.getSelectedValue() != null) ? f.getSelectedValue() : "None";
                menu.add(String.format("%-25s -> %s", f.getLabel(), valDisplay));
                ref.add(f.getFieldName());
            }
            menu.add("Reset all filters");
            ref.add("Reset");
            menu.add("Execute search");
            ref.add("Execute");
            menu.add("Return");
            ref.add("Return");

            int len = menu.size();
            String prompt = "Configure " + tableName + " search filters, then select 'Execute search':";
            int choice = displayMenu(prompt, menu.toArray(new String[0]));

            // Return to previous menu
            if (choice == len - 1) {
                return null;
            }
            // Execute search
            else if (choice == len - 2) {
                return executeSqlSearch(baseSql, filters);
            }
            // Reset all filters
            else if (choice == len - 3) {
                for (Filter f : filters) f.setSelectedValue(null);
            }
            else {
                Filter selected = filters.get(choice);
                if (selected.getOptions() == null) {
                    System.out.print("Enter " + selected.getLabel() + " filter value (Leave blank to reset): ");
                    String input = null;
                    while (input == null) input = sc.nextLine();
                    input = input.trim();
                    selected.setSelectedValue((input.isEmpty()) ? null : input);
                } else {
                    List<String> options = new ArrayList<>(List.of(selected.getOptions()));
                    options.add("Reset");
                    int val = displayMenu("Enter " + selected.getLabel() + " filter value:", options.toArray(new String[0]));
                    selected.setSelectedValue((val == options.size() - 1) ? null : options.get(val));
                }
            }
        }
    }

    public static ResultSet executeSqlSearch(String baseSql, List<Filter> filters) {
        // Make sure there is a space between the baseSql to separate the conditions to be added
        StringBuffer query = new StringBuffer(baseSql);
        query.append(" ");

        // Check if the original sql already contains a WHERE statement
        boolean hasWhere = baseSql.toUpperCase().contains("WHERE");

        // Add the filter conditions
        for (Filter f : filters) {
            String val = f.getSelectedValue();
            if (val == null) continue;
            if (!hasWhere) {
                query.append(String.format("WHERE %s = '%s' ", f.getFieldName(), f.getSelectedValue()));
                hasWhere = true;
            } else {
                query.append(String.format("AND %s = '%s' ", f.getFieldName(), f.getSelectedValue()));
            }
        }

        // terminate the query and return its result
        query.append(";");
        try {
            return getSqlConnection().createStatement().executeQuery(query.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isValidInput(String input, String type) {
        if (input == null || type == null) return false;
        type = type.trim().toUpperCase();
        String error = "";

        try {
            if (type.contains("INT")) {
                error = "INT";
                Integer.parseInt(input);
            }
            else if (type.contains("DATETIME")) {
                error = "DATETIME";
                Timestamp.valueOf(input);
            }
            else if (type.contains("DATE")) {
                error = "DATE";
                Date.valueOf(input);
            }
            else if (type.contains("VARCHAR")) { // if its a varchar with a specified size
                error = "VARCHAR";
                int len = Integer.parseInt(type.substring(type.indexOf('(') + 1, type.indexOf(')')));
                if (input.length() > len) {
                    displayInputError("LENGTH");
                    return false;
                }
            }
            else if (type.contains("DECIMAL")) {
                error = "DECIMAL";
                Double.parseDouble(input);

                int deci = Integer.parseInt(type.substring(type.indexOf(',') + 1, type.indexOf(')')));
                int num = Integer.parseInt(type.substring(type.indexOf('(') + 1, type.indexOf(','))) - deci;
                String pattern = String.format("^(\\d{1,%d})?(\\.\\d{1,%d})?$", num, deci);
                if (!input.matches(pattern)) {
                    displayInputError("PRECISION");
                    return false;
                }
            }
            else if (type.contains("UNSIGNED") && input.contains("-")) { // Check if the number has to be unsigned
                displayInputError("SIGNED");
                return false;
            }
            else {
                return false;
            }
            return true;
        } catch (Exception e) {
            displayInputError(error);
            return false;
        }
    }

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

    public static PreparedStatement prepareStatement(Connection c, String sql, Object[] values, SQLType[] types) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        if (values == null || types == null || values.length != types.length) return ps;
        for (int i = 1; i <= values.length; i++) {
            ps.setObject(i, values[i - 1], types[i - 1]);
        }
        return ps;
    }
}
