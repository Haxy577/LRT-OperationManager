import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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
     * Displays the results of an SQL statement in the terminal in a formatted manner.
     * It would first display the labels in the result, then every row in the result.
     * It would print nothing if the specified result is null.
     *
     * @param result the result of the executed SQL statement
     */
    public static void displaySqlResult(ResultSet result) {
        if (result == null) {
            System.err.println("Invalid argument, the passed result cannot be null nor empty");
            return;
        }
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
     * Displays a menu display with the prompt and the choices the user can pick. Would also handle obtaining a valid input
     * from the user. It would return the corresponding index of the chosen option.
     *
     * @param prompt a instruction telling the user what to do or what is the menu about
     * @param options an array of strings representing every choice the user can pick from
     * @return the corresponding index in the choices array representing the choice that the user made
     */
    public static int displayMenu(String prompt, String[] options) {
        if (options == null || options.length < 1) System.err.println("The provided options cannot be null nor empty");

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
                    return choice - 1;
                }
            }

            // if input is invalid then print error message
            System.out.println(RED + "Please enter an integer between 1 to " + len + "!" + RESET);
        }
    }

    public static ResultSet sqlSearch(String tableName, String baseSql, List<Filter> filters) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            // Create the menu
            List<String> menu = new ArrayList<>();
            for (Filter f : filters) {
                String valDisplay = (f.getSelectedValue() != null) ? f.getSelectedValue() : "None";
                menu.add(String.format("%-25s -> %s", f.getLabel(), valDisplay));
            }
            menu.add("Reset all filters");
            menu.add("Execute search");
            menu.add("Return");

            int len = menu.size();
            int choice = displayMenu("Configure " + tableName + " search filters, then select 'Execute search':", menu.toArray(new String[0]));

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

        try {
            if (type.contains("INT")) Integer.parseInt(input);
            else if (type.contains("DATETIME")) Timestamp.valueOf(input);
            else if (type.contains("DATE")) Date.valueOf(input);
            else if (type.contains("VARCHAR")) { // if its a varchar with a specified size
                int len = Integer.parseInt(type.substring(type.indexOf('(') + 1));
                if (input.length() > len) {
                    System.out.println(displayInputError("LENGTH"));
                    return false;
                }
            }
            else if (type.contains("DECIMAL")) {
                Double.parseDouble(input);

                int real = Integer.parseInt(type.substring(type.indexOf('(' + 1), type.indexOf(',')));
                int deci = Integer.parseInt(type.substring(type.indexOf(',' + 1), type.indexOf(')')));
                String pattern = String.format("^(\\d{1,%d})?(\\.\\d{1,%d})?$", real, deci);
                if (!input.matches(pattern)) {
                    System.out.println(displayInputError("PRECISION"));
                }
            }
            else if (type.contains("UNSIGNED") && input.contains("-")) { // Check if the number has to be unsigned
                System.out.println(displayInputError("SIGNED"));
                return false;
            }
            else {
                return false;
            }
            return true;
        } catch (Exception e) {
            System.out.println(displayInputError(type));
            return false;
        }
    }

    public static String displayInputError(String type) {
        if (type == null) return null;
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
        return RED + "Invalid input: " + msg + RESET;
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
                System.out.println(displayInputError("NO_RECORD"));
                return false;
            }
            int revno = res.getInt("revno");

            // check if value exists in other tables
            if (relatedTables != null) {
                for (String t : relatedTables) {
                    if (doesRecordExist(t, field, value)) {
                        System.out.println(displayInputError("DELETE"));
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
}
