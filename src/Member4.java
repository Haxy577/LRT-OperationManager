import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
public class Member4 implements Application {

    private static Scanner sc = new Scanner(System.in);

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
                    searchMenu();
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
        String query3 = """
                INSERT INTO gateflow(stncode, startat, endat, entries, exits, source, notes, revno)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;
        try (PreparedStatement statement3 = conn.prepareStatement(query3)) {

            ArrayList<String> codes = new ArrayList<>();
            // SQL query for getting all the active stations
            String query1 = """
                    SELECT stncode, stnname
                    FROM station
                    WHERE active = 1;
                    """;
            // Retrieves all the active stations with their code and names
            try (PreparedStatement statement1 = conn.prepareStatement(query1)) {
                try (ResultSet result1 = statement1.executeQuery()) {
                    int counter = 0;
                    System.out.println("\nList of Active Stations: ");
                    System.out.println("-----------------------");
                    while (result1.next()) {
                        codes.add(result1.getString("stncode"));
                        System.out.println(String.format("[%d] %s - %s", ++counter, codes.get(counter - 1),
                                result1.getString("stnname")));
                    }
                    System.out.println("-----------------------");
                }
            }

            if (codes.isEmpty()) { // Checks if there are active stations, it returns early when there are no
                                   // active stations
                System.out.println("No active stations available.");
                return;
            }
            // Gets the user's chosen station
            int index = 0;
            boolean isValid = false;
            while (index < 1 || index > codes.size()) {
                index = getNonNegativeInt("Enter Choice: ", "Please choose a valid station.");
                if (index < 1 || index > codes.size()) {
                    System.out.println("Please choose a valid station.");
                }
            }
            String stncode = codes.get(index - 1);
            statement3.setString(1, stncode); // Setting the station code to a valid active station

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // DateTime format
            // SQL query for getting the number of records at a specific starting date and
            // time
            String query2 = """
                    SELECT      COUNT(*) AS count
                    FROM        gateflow
                    WHERE       stncode = ?
                    AND         startat = ?;
                    """;

            isValid = false;
            LocalDateTime startTime = null;
            LocalDateTime endTime = null;
            // Gets the time interval that the user wishes to add to the database
            while (!isValid) {
                System.out.print("Enter Date and Time (yyyy-MM-dd HH:mm:ss): ");
                String dateTime = sc.nextLine();

                try {
                    startTime = LocalDateTime.parse(dateTime, formatter);
                    endTime = startTime.plusMinutes(15);
                    if (startTime.getMinute() % 15 != 0 || startTime.getSecond() != 0) {
                        System.out.println("Please enter a completed quarter-hour time");
                        continue;
                    }

                    if (endTime.isAfter(LocalDateTime.now())) {
                        System.out.println("Selected time interval has not yet finished.");
                        continue;
                    }

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

                } catch (java.time.format.DateTimeParseException e) {
                    System.out.println("Invalid time format, please follow the assigned format.");
                }
            }
            System.out.println("Creating Record for [" + stncode + "].");
            System.out.println("-----------------------");
            System.out.println("Starting at  : " + startTime.format(formatter));
            System.out.println("Ending at    : " + endTime.format(formatter));
            System.out.println("-----------------------");

            // Gets the entry and exit counts
            int entryCount = getNonNegativeInt("Enter Entry Count: ", "Invalid entry count, please try again.");
            int exitCount = getNonNegativeInt("Enter Exit Count: ", "Invalid exit count, please try again.");

            statement3.setInt(4, entryCount); // Setting the entry count
            statement3.setInt(5, exitCount); // Setting the exit count

            // Gets the source for the interval
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
            int sourceChoice = Utils.getIntChoice("Source Options: ", sourceOptions);
            String source = sourceOptions[sourceChoice];

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

    private int getNonNegativeInt(String prompt, String invalidPrompt) {
        Boolean isValid = false;
        int choice = -1;
        while (!isValid) {
            try {
                System.out.print(prompt);
                choice = Integer.parseInt(sc.nextLine());
                if (choice < 0) {
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

    private void searchMenu() {
        System.out.println("This is the search menu!");
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

}
