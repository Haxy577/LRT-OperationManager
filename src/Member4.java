import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Member 4: Station passenger-flow management
 * <p>
 * Member 4 owns count-interval creation, search, retrieval, update, and
 * controlled deletion. Users
 * select an active station and enter a completed quarter-hour start. The
 * application derives and
 * displays the exclusive end 15 minutes later. It accepts nonnegative
 * whole-number entries and
 * exits, a Counter or Manual source, and optional notes; revisions begin at 1.
 * </p>
 * <p>
 * Search supports station, interval-start range, and source. Selecting a result
 * loads both identifier
 * components, saved boundaries, counts, source, notes, and revision. Zero-count
 * intervals remain
 * visible. Multiple intervals are individually selectable; missing intervals
 * are not silently created.
 * </p>
 * <p>
 * Ordinary edits protect the station and start time. An incorrectly identified
 * interval requires an
 * authorized correction rather than silently moving the saved record. Saving
 * coordinates changes
 * for the station, rejects duplicates and overlaps, validates completed
 * boundaries, and rejects stale
 * revisions. Confirmed deletion is limited to an erroneous count record.
 * Legitimate zero records are
 * not deleted simply because they contain no passengers. Member 4 does not
 * assign counts to a
 * platform or infer boarding, alighting, or occupancy.
 * </p>
 *
 * @author
 */
/*public class Member4 implements Application {

    private static Scanner sc = new Scanner(System.in);
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // DateTime format

    private static final int MAX_COUNT = 1000000;

    @Override
    public void menu() {
        Connection conn = Utils.getSqlConnection();
        if (conn == null) {
            System.out.println("Could not connect to the database.");
            return;
        }
        String[] options = {
                "Create Interval",
                "Search Interval",
                "Edit Interval",
                "Delete Interval",
                "Report 4",
                "Exit"
        };

        String prompt = "Which action would you like to take";
        Boolean isRunning = true;

        while (isRunning) {
            int choice = Utils.getIntChoice(prompt, options);
            switch (choice) {
                case 0:
                    try {
                        createMenu(conn);
                    } catch (SQLException e) {
                        System.out.println("Database Error: " + e.getMessage());
                    }
                    break;
                case 1:
                    try {
                        searchMenu(conn);
                    } catch (SQLException e) {
                        System.out.println("Database Error: " + e.getMessage());
                    }
                    break;
                case 2:
                    editMenu();
                    break;
                case 3:
                    deleteMenu();
                    break;
                case 4:
                    report4Menu();
                    break;
                case 5:
                    isRunning = false;
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        System.out.println("Database Error.");
                    }
                    System.out.println("Thank you and have a great day!");
                    break;
                default:
                    System.out.println("Invalid choice, please try again!");
                    break;
            }
        }
    }

    private void createMenu(Connection conn) throws SQLException {
        // SQL query to insert the new record into the database
        boolean isValid;
        String query3 = """
                INSERT INTO gateflow(stncode, startat, endat, entries, exits, source, notes, revno)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;
        try (PreparedStatement statement3 = conn.prepareStatement(query3)) {

            String stncode = getStationCode(conn, true); // Gets the user's choice for active stations
            if (stncode == null) { // No Active stations available
                System.out.println("No Active Stations available.");
                return;
            }
            statement3.setString(1, stncode); // Setting the station code to a valid active station

            // SQL query for getting the number of records at a specific starting date and
            // time
            String query2 = """
                    SELECT      COUNT(*) AS count
                    FROM        gateflow
                    WHERE       stncode = ?
                    AND         startat = ?;
                    """;

            // Gets a unique time interval that the user wishes to add to the database
            isValid = false;
            LocalDateTime startTime = null;
            LocalDateTime endTime = null;
            while (!isValid) {
                startTime = getValidDateTime("Enter Date and Time (yyyy-MM-dd HH:mm:ss): ");
                endTime = startTime.plusMinutes(15);
                try (PreparedStatement statement2 = conn.prepareStatement(query2)) {
                    statement2.setString(1, stncode);
                    statement2.setObject(2, startTime);
                    try (ResultSet result2 = statement2.executeQuery()) {
                        if (result2.next()) {
                            if (result2.getInt("count") > 0) {
                                System.out.println("Date and time already existing");
                            } else {
                                statement3.setObject(2, startTime); // Setting starting datetime to a unique
                                                                    // starting time
                                statement3.setObject(3, endTime); // Setting ending datetime to derived datetime (15
                                                                  // min interval)
                                isValid = true;
                            }
                        }
                    }
                }
            }
            System.out.println("Creating Record for [" + stncode + "].");
            System.out.println("-----------------------");
            System.out.println("Starting at  : " + startTime.format(formatter));
            System.out.println("Ending at    : " + endTime.format(formatter));
            System.out.println("-----------------------");

            // Gets the entry and exit counts
            int entryCount = getIntInRange("Enter Entry Count: ", "Invalid entry count, please try again.", 0,
                    MAX_COUNT);
            int exitCount = getIntInRange("Enter Exit Count: ", "Invalid exit count, please try again.", 0,
                    MAX_COUNT);

            statement3.setInt(4, entryCount); // Setting the entry count
            statement3.setInt(5, exitCount); // Setting the exit count

            // Gets the source for the interval
            String source = getSource(conn);

            statement3.setString(6, source); // Setting source to either manual or counter

            // Gets the additional notes that the user wants to input
            isValid = false;
            while (!isValid) {
                System.out.print("Enter notes: ");
                String notes = sc.nextLine();
                String trimmed = notes.trim();
                if (trimmed.length() > 255) {
                    System.out.println("Notes cannot exceed length 255");
                } else {
                    isValid = true;
                    statement3.setString(7, trimmed.isEmpty() ? null : trimmed); // Setting notes column (null if empty,
                                                                                 // else sets to user notes)
                }
            }
            statement3.setInt(8, 1); // Setting revision number to 1 (by default)

            int rows = statement3.executeUpdate();
            if (rows > 0) {
                System.out.println("Successfully added interval into the database.");
            }
        }
    }

    private String getSource(Connection conn) throws SQLException {
        ArrayList<String> sources = new ArrayList<>();
        String query4 = "SELECT source FROM refsource;";
        try (PreparedStatement statement4 = conn.prepareStatement(query4)) {
            try (ResultSet result4 = statement4.executeQuery()) {
                while (result4.next()) {
                    sources.add(result4.getString("source"));
                }
            }
        }
        String[] sourceOptions = sources.toArray(new String[0]);
        return Utils.displayMenu("Source Options: ", sourceOptions);
    }

    private LocalDateTime getValidDateTime(String prompt) {
        boolean isValid = false;
        LocalDateTime userDateTime = null;
        while (!isValid) {
            System.out.print(prompt);
            String dateTime = sc.nextLine();
            try {
                userDateTime = LocalDateTime.parse(dateTime, formatter);
                if (userDateTime.getMinute() % 15 != 0 || userDateTime.getSecond() != 0) { // Checks for quarter time
                    System.out.println(Utils.RED + "Please enter a completed quarter-hour time" + Utils.RESET);
                } else if (userDateTime.plusMinutes(15).isAfter(LocalDateTime.now())) { // Checks if the time exceeds
                                                                                        // the
                                                                                        // current time
                    System.out.println(Utils.RED + "Selected time interval has not yet finished." + Utils.RESET);
                } else {
                    isValid = true;
                }
            } catch (java.time.format.DateTimeParseException e) {
                System.out.println(Utils.RED + "Invalid time format, please follow the assigned format." + Utils.RESET);
            }
        }
        return userDateTime;

    }

    private String getStationCode(Connection conn, Boolean activeFilter) throws SQLException {
        ArrayList<String> codes = new ArrayList<>();
        // SQL query for getting all the active stations
        String query = " SELECT stncode, stnname, active FROM station " +
                (activeFilter == null ? "" : "WHERE        active = ? ") +
                "ORDER BY stncode;";
        // Retrieves all the active stations with their code and names
        try (PreparedStatement statement = conn.prepareStatement(query)) {
            if (activeFilter != null) {
                statement.setInt(1, activeFilter ? 1 : 0);
            }
            try (ResultSet result = statement.executeQuery()) {
                int counter = 0;
                System.out.println("\nList of Stations: ");
                System.out.println("-----------------------");
                while (result.next()) {
                    codes.add(result.getString("stncode"));
                    System.out.println(String.format("[%d] %-5s - %-13s - %s", ++counter, codes.get(counter - 1),
                            result.getString("stnname"), (result.getInt("active") == 1 ? "Active" : "Not Active")));
                }
                System.out.println("-----------------------");
            }
        }

        if (codes.isEmpty()) { // Checks if there are available stations
            return null;
        }
        // Gets the user's chosen station
        int index = getIntInRange("Enter Choice: ", Utils.RED + "Please choose a valid station." + Utils.RESET, 1,
                codes.size());

        return codes.get(index - 1);
    }

    private int getIntInRange(String prompt, String invalidPrompt, int lowerBound, int upperBound) {
        Boolean isValid = false;
        int choice = -1;
        while (!isValid) {
            try {
                System.out.print(prompt);
                choice = Integer.parseInt(sc.nextLine());
                if (choice < lowerBound || choice > upperBound) {
                    System.out.println(invalidPrompt);
                } else {
                    isValid = true;
                }
            } catch (java.lang.NumberFormatException e) {
                System.out.println(invalidPrompt);
            }
        }
        return choice;
    }

    private void searchMenu(Connection conn) throws SQLException {
        // System.out.println("This is the search menu!");
        Map<String, Object> filters = new LinkedHashMap<>();

        filters.put("Station Code", null);
        filters.put("Starting Time Interval", null);
        filters.put("Ending Time Interval", null);
        filters.put("Source", null);

        LocalDateTime start = null;
        boolean isRunning = true;
        while (isRunning) {
            int counter = 1;
            System.out.println("Filter Options: ");
            System.out.println("---------------------------------------------------");
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String filterName = entry.getKey();
                String filterValue = entry.getValue() == null ? "[NONE]" : entry.getValue().toString();

                System.out.println(String.format(" [%d] %-25s - %20s", counter++, filterName, filterValue.toString()));
            }
            System.out.println(" [5] Run Search");
            System.out.println(" [6] Back to Main Menu");
            System.out.println("---------------------------------------------------");
            int choice = getIntInRange("Choice: ",
                    Utils.RED + "Invalid input, please select from the list above." + Utils.RESET, 1, 6);
            switch (choice) {
                case 1: // Gets the station code
                    filters.put("Station Code", getStationCode(conn, null));

                    break;
                case 2:
                    start = getValidDateTime("Enter starting interval (yyyy-MM-dd HH:mm:ss) : ");
                    filters.put("Starting Time Interval", start);

                    break;
                case 3:
                    if (start == null) {
                        System.out.println(Utils.RED + "Please select a starting interval first" + Utils.RESET);
                        continue;
                    }
                    boolean isValid = false;
                    while (!isValid) {
                        LocalDateTime end = getValidDateTime("Enter ending interval (yyyy-MM-dd HH:mm:ss)   : ");
                        if (end.isBefore(start)) {
                            System.out.println(
                                    Utils.RED + "Please enter a date time after the starting interval." + Utils.RESET);
                        } else {
                            filters.put("Ending Time Interval", end);
                            isValid = true;
                        }
                    }
                    break;
                case 4:
                    filters.put("Source", getSource(conn));
                    break;
                case 5:
                    
                    break;
                case 6:
                    isRunning = false;
                    break;
            }

        }
    }

    private void editMenu() {
        System.out.println("This is the edit menu!");
    }

    private void deleteMenu() {
        System.out.println("This is the delete menu!");
    }

    private void report4Menu() {
        System.out.println("This is the report menu!");
    }

}*/
