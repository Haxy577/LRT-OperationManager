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
                    if (res == null) res = "NULL";

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
                    sc.close();
                    return choice - 1;
                }
            }

            // if input is invalid then print error message
            System.out.println(RED + "Please enter an integer between 1 to " + len + "!" + RESET);
        }
    }

    public static void sqlSearch(String tableName, String baseSql, List<Filter> filters) {
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
            int choice = displayMenu("Configure " + tableName + "search filters, then select 'Execute search':", menu.toArray(new String[0]));
        }

        sc.close();
    }
}
