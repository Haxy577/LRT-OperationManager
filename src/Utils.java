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

    public static final String RESET = "\u001b[0m";
    public static final String BOLD = "\u001b[1m";
    public static final String RED = "\u001b[31m";

    /**
     * Creates and returns a connection to the database.
     *
     * @return a connection object that is successfully connected to the database.
     *         It returns null if the connection failed.
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
     * Displays a menu display with the prompt and the choices the user can pick.
     * Would also handle obtaining a valid input
     * from the user. It would return the corresponding value of the chosen option.
     *
     * @param prompt  an instruction telling the user what to do or what the menu is
     *                about
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
        for (int i = 0; i < len; i++)
            System.out.printf("  [%d] %s\n", i + 1, options[i]);

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
     * Displays a menu display with the prompt and the choices the user can pick.
     * Would also handle obtaining a valid input
     * from the user. It would return the corresponding index of the chosen option.
     *
     * @param prompt  a instruction telling the user what to do or what is the menu
     *                about
     * @param options an array of strings representing every choice the user can
     *                pick from
     * @return the corresponding index in the choices array representing the choice
     *         that the user made
     */
    public static int getIntChoice(String prompt, String[] options) {
        if (options == null || options.length < 1)
            System.err.println("The provided options cannot be null nor empty");

        System.out.println(prompt);
        int len = options.length;
        for (int i = 0; i < len; i++)
            System.out.printf("  [%d] %s\n", i + 1, options[i]);

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

    /**
     * Displays the results of an SQL statement in the terminal in a formatted
     * manner.
     * It would first display the labels in the result, then every row in the
     * result.
     * It would print nothing if the specified result is null.
     *
     * @param result the result of the executed SQL statement
     */
    public static void displaySqlResult(ResultSet result) {
        if (result == null)
            return;
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
            while (result.next()) {
                StringBuffer row = new StringBuffer("|");
                for (int i = 1; i <= fields; i++) {
                    String res = result.getString(i);
                    if (res == null)
                        res = "";

                    int width = sizes[i - 1];
                    if (res.length() > width)
                        res = res.substring(0, width - 3) + "..."; // if longer than the width, truncate the string

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
     * Returns a list of options containing the labels and value of each field, a reset option,
     * a confirm option, and a return option
     *
     * @param fields the list of fields to be displayed
     * @return a list of options
     */
    public static List<String> createModifiableMenu(List<Field> fields) {
        if (fields == null) return new ArrayList<>();
        List<String> menu = new ArrayList<>();
        for (Field f : fields) {
            String label = f.getLabel();
            Object val = f.getValue();
            menu.add(String.format("%-25s -> %s", label, val == null ? "None" : val.toString()));
        }
        menu.add("Reset");
        menu.add("Confirm");
        menu.add("Return");
        return menu;
    }

    public static List<Filter> configureFilters(List<Filter> mandatory, List<Filter> optional) {
        List<Filter> filters = new ArrayList<>();
        if (mandatory != null) filters.addAll(mandatory);
        if (optional != null) filters.addAll(optional);
        if (filters.isEmpty()) return null;

        while (true) {
            List<String> menu = createModifiableMenu(new ArrayList<>(filters));
            String prompt = "Modify all desired fields to search for, choose 'Confirm' after: ";
            String choice = displayMenu(prompt, menu.toArray(new String[0]));

            switch (choice) {
                case "Return" -> {return null;}
                case "Reset" -> {
                    if (mandatory != null) for (Filter f : mandatory) f.setValue(null);
                    if (optional != null) for (Filter f : optional) f.setValue(null);
                }
                case "Confirm" -> {
                    List<Filter> configured = new ArrayList<>();

                    // add filters before the having clause
                    for (Filter f : filters) if (f.getValue() != null && !f.isHaving()) configured.add(f);

                    // add filters on the having clause
                    for (Filter f : filters) if (f.getValue() != null && f.isHaving()) configured.add(f);
                    return configured;
                }
                default -> {
                    choice = choice.substring(0, choice.indexOf("->")).trim();
                    Filter filter = null;
                    for (Filter f : filters) if (f.getLabel().equalsIgnoreCase(choice)) filter = f;

                    if (filter.getOptions() == null) {
                        prompt = "Enter " + filter.getLabel() + " filter value (Leave blank to reset): ";
                        filter.setValue(getInput(prompt, filter.getType()));
                    } else {
                        List<String> options = new ArrayList<>(List.of(filter.getOptions()));
                        options.add("Reset");
                        prompt = "Enter " + filter.getLabel() + " filter value: ";
                        String sel = displayMenu(prompt, options.toArray(new String[0]));
                        if ("Reset".equalsIgnoreCase(sel)) filter.setValue(null);
                        else filter.setValue(sel);
                    }
                }
            }
        }
    }

    public static ResultSet sqlSearch(Connection c, String headSql, String groupBy, String tailSql, List<Filter> mandatory, List<Filter> optional) {
        List<Filter> filters = configureFilters(mandatory, optional);
        if (filters == null) return null;
        StringBuilder query = new StringBuilder(headSql.trim());

        // add condition blocks for optional where clause filters
        if (optional != null) for (Filter f : optional) if (!f.isHaving()) appendClause(query, "WHERE", f);

        // append the groupBy clause if there is
        if (groupBy != null) {
            query.append(" ").append(groupBy.trim());
            // add condition blocks for optional having clause filters
            if (optional != null) for (Filter f : optional) if (f.isHaving()) appendClause(query, "HAVING", f);
        }

        // append the tailing sql statement if there is
        if (tailSql != null) query.append(" ").append(tailSql.trim());

        // execute the search
        try {
            PreparedStatement ps = prepareStatement(c, query.toString(), filters.toArray(new Field[0]));
            if (ps == null) return null;
            return ps.executeQuery();
        } catch (SQLException e) {
            displayDatabaseError(null, e);
            return null;
        }
    }

    public static void appendClause(StringBuilder query, String keyword, Filter filter) {
        if (filter == null || filter.getValue() == null) return;
        keyword = keyword.toUpperCase().trim();
        boolean hasKeyword = query.toString().contains(keyword);
        query.append(hasKeyword ? " AND " : String.format(" %s ", keyword));

        if ("LIKE".equalsIgnoreCase(filter.getOperator())) {
            query.append(String.format("%s LIKE ?", filter.getName()));
            String val = filter.getValue().toString();
            if (!val.startsWith("%") || !val.endsWith("%"))
                filter.setValue("%".concat(val).concat("%"));
        } else {
            query.append(String.format("%s %s ?", filter.getName(), filter.getOperator()));
        }
    }

    /**
     * Gets the string input of a user, validates the input, then return its
     * corresponding object value depending on the type.
     * If the input were to be blank, then it is assumed that the wanted input would
     * be null.
     *
     * @param prompt instruction to user
     * @param type   SQL type
     * @return the converted string input
     */
    public static Object getInput(String prompt, String type) {
        Object val = null;
        while (val == null) {
            System.out.print(prompt);
            String in = SCANNER.nextLine().trim();
            if (in.isEmpty())
                return null;
            val = parseInput(in, type);
        }
        return val;
    }

    /**
     * Gets the user input, validates it, then updates the value of the field based on the input of the user.
     * If the input is blank then it would set its value to null.
     *
     * @param field the field to be updated/modified
     */
    public static void updateValue(Field field) {
        String prompt = String.format("Enter %s: ", field.getLabel());
        field.setValue(getInput(prompt, field.getType()));
    }

    /**
     * Validates the passed input and converts the input to its type if it were to be valid.
     * If the input is invalid then it would display an error message describing why its invalid.
     *
     * @param input the input of the user
     * @param type  SQL type
     * @return the converted string input
     */
    public static Object parseInput(String input, String type) {
        if (input == null || type == null)
            return null;
        type = type.trim().toUpperCase();
        boolean isUnsigned = type.contains("UNSIGNED");
        String error = "";

        try {
            if (isUnsigned && input.contains("-")) { // Check if the number has to be unsigned
                displayInputError("SIGNED");
                return null;
            }
            if (type.contains("INT")) {
                error = "INT";
                long val = Long.parseLong(input);

                if (type.contains("TINYINT")) {
                    if ((isUnsigned && val <= 255) || (!isUnsigned && val >= -128 && val <= 127)) return (int) val;
                }
                else if (type.contains("SMALLINT")) {
                    if ((isUnsigned && val <= 65535) || (!isUnsigned && val >= -32768 && val <= 32767)) return (int) val;
                }
                else if ((isUnsigned && val <= 4294967295L) || (!isUnsigned && val >= -2147483648 && val <= 2147483647)) return Integer.parseInt(input);
                displayInputError("OVERFLOW");
                return null;
            }
            else if (type.contains("DATETIME")) {
                error = "DATETIME";
                return Timestamp.valueOf(input);
            } else if (type.contains("DATE")) {
                error = "DATE";
                return Date.valueOf(input);
            } else if (type.contains("VARCHAR")) { // if its a varchar with a specified size
                error = "VARCHAR";
                int len = Integer.parseInt(type.substring(type.indexOf('(') + 1, type.indexOf(')')));
                if (input.length() > len) {
                    displayInputError("LENGTH");
                    return null;
                }
                return input;
            } else if (type.contains("DECIMAL")) {
                error = "DECIMAL";
                int deci = Integer.parseInt(type.substring(type.indexOf(',') + 1, type.indexOf(')')));
                int num = Integer.parseInt(type.substring(type.indexOf('(') + 1, type.indexOf(','))) - deci;
                String pattern = String.format("^-?(\\d{1,%d}(\\.\\d{1,%d})?|\\.\\d{1,%d})$", num, deci, deci);
                if (!input.matches(pattern)) {
                    displayInputError("PRECISION");
                    return null;
                }
                return Double.parseDouble(input);
            } else
                return null;
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
            case "OVERFLOW" -> "Please input a smaller number";
            default -> "Error";
        };
        System.out.println(RED + "Invalid input: " + msg + RESET);
    }

    /**
     * Display error messages regarding database errors.
     *
     * @param type type of error
     * @param e    the SQL exception
     */
    public static void displayDatabaseError(String type, Exception e) {
        if (e != null) {
            System.out.println(RED + "Database Error: " + e.toString() + RESET);
            return;
        }
        String msg = switch (type) {
            case "REVISION" -> "Please try again, the record/table you are trying to update is outdated";
            default -> "Error";
        };
        System.out.println(RED + "Database Error: " + msg + RESET);
    }

    /**
     * Checks if the specified field and value combination exists within the
     * specified table
     *
     * @param c connection to database
     * @param table the name of the table in the database
     * @param field the field in the database and the value to look for inside it
     * @return true if the record exists, false otherwise
     */
    public static boolean doesRecordExist(Connection c, String table, Field field) {
        if (c == null || table == null || field == null) return false;
        String sql = String.format("SELECT 1 FROM %s WHERE %s = ?;", table, field.getName());
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, field.getValue(), field.getSqlType());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            displayDatabaseError(null, e);
            return false;
        }
    }

    /**
     * Deletes a record in a table with the specified field and value combinations.
     * Could also check if other tables depend on this record,
     * if it does then it does not delete the record.
     *
     * @param table the table of the record to be deleted
     * @param relatedTables checks these tables first if the value exists there. Can be null indicating to not check any tables
     * @param field the name, type, and value of the record to be deleted
     * @return true if the record was deleted, false otherwise
     */
    public static boolean safeDeleteRecord(Connection c, String table, String[] relatedTables, Field field) {
        if (c == null || table == null || field == null) return false;
        String sql = String.format("SELECT revno FROM %s WHERE %s = ?;", table, field.getName());

        try (PreparedStatement ps = prepareStatement(c, sql, new Field[]{field})) {
            // get revision number
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
                    if (doesRecordExist(c, t, field)) {
                        displayInputError("DELETE");
                        return false;
                    }
                }
            }

            // delete the record
            sql = String.format("DELETE FROM %s WHERE %s = ? AND revno = ?;", table, field.getName());

            try (PreparedStatement del = c.prepareStatement(sql)) {
                del.setObject(1, field.getValue(), field.getSqlType());
                del.setInt(2, revno);

                if (del.executeUpdate() == 0) {
                    displayDatabaseError("REVISION", null);
                    return false;
                }
                return true;
            } catch (SQLException e) {
                displayDatabaseError(null, e);
                return false;
            }
        } catch (SQLException e) {
            displayDatabaseError(null, e);
            return false;
        }
    }

    /**
     * Creates a prepared statement where it would automatically insert the
     * specified values to the provided sql statement.
     *
     * @param c connection to the database
     * @param sql the SQL statement with placeholders for every value
     * @param fields the fields to be inserted per placeholder
     * @return the prepared statement with a SQL statement ready to be run
     */
    public static PreparedStatement prepareStatement(Connection c, String sql, Field[] fields) {
        if (c == null || sql == null || fields == null) return null;
        try {
            PreparedStatement ps = c.prepareStatement(sql);
            for (int i = 1; i <= fields.length; i++) {
                Field f = fields[i - 1];
                Object val = f.getValue();
                SQLType typ = f.getSqlType();
                if (typ == null) ps.setObject(i, val);
                else ps.setObject(i, val, typ);
            }
            return ps;
        } catch (SQLException e) {displayDatabaseError(null, e);}
        return null;
    }

    /**
     * Useful to INSERT or UPDATE records.
     * Modifies the table stated on the SQL statement. The provided SQL statements must have the same amount of
     * placeholders as there are fields. The SQL statement should also be designed wherein it would follow the order
     * defined in the field list. If not then it could lead to unintended behavior.
     *
     * @param c connection to the database
     * @param sql the SQL statement with placeholders to run
     * @param fields the field values to be added per placeholder
     * @return true if the table was updated, false otherwise
     */
    public static boolean modifyTable(Connection c, String sql, List<Field> fields) {
        if (c == null || sql == null || fields == null) return false;
        try (PreparedStatement ps = prepareStatement(c, sql, fields.toArray(new Field[0]))) {
            if (ps == null) return false;
            if (ps.executeUpdate() == 0) {
                displayDatabaseError("REVISION", null);
                return false;
            }
            return true;
        } catch (Exception e) {
            displayDatabaseError(null, e);
            return false;
        }
    }

    /**
     * Queries the SQL and updates the specified fields stated in the SQL statement.
     *
     * @param c connection to the database
     * @param sql the SQL select query statement to obtain the values for each desired field
     * @param identifier the field to identify the record to be retrieved
     * @param fields the fields that would have their values updated based on the values of the current obtained record
     * @return a copy of the field values obtained
     */
    public static Map<String, Object> loadValues(Connection c, String sql, Field identifier, Map<String, Field> fields) {
        try (PreparedStatement ps = c.prepareStatement(sql)){
            ps.setObject(1, identifier.getValue(), identifier.getSqlType());
            Map<String, Object> values = new LinkedHashMap<>();

            try (ResultSet res = ps.executeQuery()) {
                if (!res.next()) {
                    Utils.displayInputError("NO_RECORD");
                    return new LinkedHashMap<>();
                }
                for (String k : fields.keySet()) {
                    Field f = fields.get(k);
                    f.setValue(res.getObject(f.getName()));
                    values.put(k, f.getValue());
                }
            }
            return values;
        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
            return new LinkedHashMap<>();
        }
    }
}
