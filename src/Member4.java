import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDate;

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

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // DateTime format

    private static final int MAX_COUNT = 1000000;

    private record PrimaryKey(String stncode, LocalDateTime startat) {
    }

    private record Entry(String stncode, LocalDateTime startat, LocalDateTime endat, int entries, int exits,
            String source, String notes, int revno) {

    }

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
                        PrimaryKey entry = searchMenu(conn);
                        if (entry != null) {
                            showEntry(conn, entry);
                        }
                    } catch (SQLException e) {
                        System.out.println("Database Error: " + e.getMessage());
                    }
                    break;
                case 2:
                    try {
                        editMenu(conn);
                    } catch (SQLException e) {
                        System.out.println("Database Error: " + e.getMessage());
                    }
                    break;
                case 3:
                    try {
                        deleteMenu(conn);
                    } catch (SQLException e) {
                        System.out.println("Database Error: " + e.getMessage());
                    }
                    break;
                case 4:
                    try {
                        report4Menu(conn);
                    } catch (SQLException e) {
                        System.out.println("Database Error: " + e.getMessage());
                    }
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
            String source = getSource(conn, false);

            statement3.setString(6, source); // Setting source to either manual or counter

            // Gets the additional notes that the user wants to input
            isValid = false;
            while (!isValid) {
                System.out.print("Enter notes: ");
                String notes = Utils.SCANNER.nextLine();
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

    private String getSource(Connection conn, boolean allowReset) throws SQLException {
        ArrayList<String> sources = new ArrayList<>();
        String query4 = "SELECT source FROM refsource;";
        try (PreparedStatement statement4 = conn.prepareStatement(query4)) {
            try (ResultSet result4 = statement4.executeQuery()) {
                while (result4.next()) {
                    sources.add(result4.getString("source"));
                }
            }
        }
        if (allowReset)
            sources.add("Reset");
        String[] sourceOptions = sources.toArray(new String[0]);
        return Utils.displayMenu("Source Options: ", sourceOptions);
    }

    private LocalDateTime getValidDateTime(String prompt) {
        boolean isValid = false;
        LocalDateTime userDateTime = null;
        while (!isValid) {
            System.out.print(prompt);
            String dateTime = Utils.SCANNER.nextLine();
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
                choice = Integer.parseInt(Utils.SCANNER.nextLine());
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

    private PrimaryKey searchMenu(Connection conn) throws SQLException {
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
            System.out.println("--------------------------------");
            System.out.println(" [5] Run Search");
            System.out.println(" [6] Reset Filters");
            System.out.println(" [7] Back to Main Menu");
            System.out.println("---------------------------------------------------");
            int choice = getIntInRange("Choice: ",
                    Utils.RED + "Invalid input, please select from the list above." + Utils.RESET, 1, 7);
            switch (choice) {
                case 1: // Gets the station code
                    filters.put("Station Code", getStationCode(conn, null));

                    break;
                case 2:
                    start = getValidDateTime("Enter starting interval (yyyy-MM-dd HH:mm:ss) : ");
                    filters.put("Starting Time Interval", start);
                    filters.put("Ending Time Interval", null);

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
                    String src = getSource(conn, true);
                    filters.put("Source", "Reset".equals(src) ? null : src);
                    break;
                case 5:
                    StringBuilder statement = new StringBuilder("""
                                    SELECT      stncode, startat, endat, entries, exits, source, notes, revno
                                    FROM        gateflow
                                    WHERE       1 = 1
                            """);
                    List<Object> parameters = new ArrayList<>();

                    if (filters.get("Station Code") != null) {
                        statement.append(" AND stncode = ?");
                        parameters.add(filters.get("Station Code"));
                    }
                    if (filters.get("Starting Time Interval") != null) {
                        statement.append(" AND startat >= ?");
                        parameters.add(filters.get("Starting Time Interval"));
                    }
                    if (filters.get("Ending Time Interval") != null) {
                        statement.append(" AND startat <= ?");
                        parameters.add(filters.get("Ending Time Interval"));
                    }
                    if (filters.get("Source") != null) {
                        statement.append(" AND source = ?");
                        parameters.add(filters.get("Source"));
                    }
                    statement.append(" ORDER BY stncode, startat;");

                    try (PreparedStatement stmt = conn.prepareStatement(statement.toString())) {
                        for (int i = 0; i < parameters.size(); i++) {
                            stmt.setObject(i + 1, parameters.get(i));
                        }
                        try (ResultSet result = stmt.executeQuery()) {
                            List<PrimaryKey> entries = new ArrayList<>();
                            System.out.println("------------------------------------------------");
                            while (result.next()) {
                                LocalDateTime time = result.getTimestamp("startat").toLocalDateTime();
                                entries.add(new PrimaryKey(result.getString("stncode"), time));
                                System.out.println(String.format("[%d] %-5s | %s | Entries : %3d | Exits: %3d | %s",
                                        entries.size(), result.getString("stncode"), time.format(formatter),
                                        result.getInt("entries"), result.getInt("exits"), result.getString("source")));
                            }
                            System.out.println("------------------------------------------------");
                            if (entries.isEmpty()) {
                                System.out.println("No intervals found!");
                                break;
                            }

                            System.out.print("Enter row number (leave blank to return): ");
                            String input = Utils.SCANNER.nextLine().trim();
                            if (input.isEmpty())
                                break;

                            try {
                                int pick = Integer.parseInt(input);
                                if (pick >= 1 && pick <= entries.size())
                                    return entries.get(pick - 1);
                            } catch (NumberFormatException e) {
                            }
                            System.out.println(Utils.RED + "Invalid selection." + Utils.RESET);
                        }
                    }
                    break;
                case 6:
                    filters.put("Station Code", null);
                    filters.put("Starting Time Interval", null);
                    start = null;
                    filters.put("Ending Time Interval", null);
                    filters.put("Source", null);
                    break;
                case 7:
                    isRunning = false;
                    break;
            }

        }
        return null;
    }

    private Entry showEntry(Connection conn, PrimaryKey entry) throws SQLException {
        String query = """
                            SELECT      stncode, startat, endat, entries, exits, source, notes, revno
                            FROM        gateflow
                            WHERE       stncode = ?
                            AND         startat = ?;
                """;

        try (PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, entry.stncode());
            statement.setObject(2, entry.startat());

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    System.out.println(Utils.RED + "Record no longer exists" + Utils.RESET);
                    return null;
                }
                String stncode = result.getString("stncode");
                LocalDateTime startat = result.getTimestamp("startat").toLocalDateTime();
                LocalDateTime endat = result.getTimestamp("endat").toLocalDateTime();
                int entries = result.getInt("entries");
                int exits = result.getInt("exits");
                String source = result.getString("source");
                String notes = result.getString("notes");
                int revno = result.getInt("revno");

                System.out.println("-----------------------");
                System.out.println("Station  : " + stncode);
                System.out.println("Start    : " + startat.format(formatter));
                System.out.println("End      : " + endat.format(formatter));
                System.out.println("Entries  : " + entries);
                System.out.println("Exits    : " + exits);
                System.out.println("Source   : " + source);
                System.out.println("Notes    : " + (notes == null ? "-" : notes));
                System.out.println("Revision : " + revno);
                System.out.println("-----------------------");
                return new Entry(stncode, startat, endat, entries, exits, source, notes, revno);
            }

        }
    }

    private void editMenu(Connection conn) throws SQLException {
        PrimaryKey entry = searchMenu(conn);
        if (entry == null) {
            return;
        }

        Entry entryToEdit = showEntry(conn, entry);
        if (entryToEdit == null) {
            return;
        }
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("Entries", entryToEdit.entries());
        labels.put("Exits", entryToEdit.exits());
        labels.put("Source", entryToEdit.source());
        labels.put("Notes", entryToEdit.notes());

        int newEntries = -1;
        int newExits = -1;
        String newSource = null;
        String newNotes = null;

        boolean isRunning = true;
        while (isRunning) {
            System.out.println("Select label to change: ");
            System.out.println("-----------------------------");
            int counter = 1;
            for (Map.Entry<String, Object> ent : labels.entrySet()) {
                String labelName = ent.getKey();
                String labelVal = ent.getValue() == null ? "[NONE]" : ent.getValue().toString();

                System.out.println(String.format(" [%d] %-10s - %s", counter++, labelName, labelVal));
            }
            System.out.println("---------------");
            System.out.println(" [5] Save");
            System.out.println(" [6] Cancel");
            System.out.println("-----------------------------");

            int choice = getIntInRange("Enter Choice: ", Utils.RED + "Invalid selection." + Utils.RESET, 1, 6);
            switch (choice) {
                case 1:
                    newEntries = getIntInRange(
                            "Old Entries [" + Utils.BOLD + entryToEdit.entries() + Utils.RESET + "] -> ",
                            Utils.RED + "Invalid entry count, please try again." + Utils.RESET, 0, MAX_COUNT);
                    labels.put("Entries", newEntries);
                    break;
                case 2:
                    newExits = getIntInRange("Old Exits [" + Utils.BOLD + entryToEdit.exits() + Utils.RESET + "] -> ",
                            Utils.RED + "Invalid exit count, please try again." + Utils.RESET, 0, MAX_COUNT);
                    labels.put("Exits", newExits);
                    break;
                case 3:
                    System.out.println("Old Source [" + Utils.BOLD + entryToEdit.source() + Utils.RESET + "]");
                    newSource = getSource(conn, false);
                    labels.put("Source", newSource);
                    break;
                case 4:
                    boolean isValid = false;
                    while (!isValid) {
                        System.out.print("Old Notes (leave blank for null/no notes) [" + Utils.BOLD
                                + (entryToEdit.notes() == null ? "NONE" : entryToEdit.notes()) + Utils.RESET + "] -> ");
                        newNotes = Utils.SCANNER.nextLine().trim();
                        if (newNotes.length() > 255) {
                            System.out.println("Note cannot exceed length of 255.");
                        } else {
                            if (newNotes.equals("")) {
                                newNotes = null;
                            }
                            isValid = true;
                        }
                    }
                    labels.put("Notes", newNotes);
                    break;
                case 5:
                    isRunning = false;
                    break;
                case 6:
                    System.out.println("Edit Cancelled! Returning Now ...");
                    return;
            }
        }

        boolean recordChanged = !Objects.equals(labels.get("Entries"), entryToEdit.entries())
                || !Objects.equals(labels.get("Exits"), entryToEdit.exits())
                || !Objects.equals(labels.get("Source"), entryToEdit.source())
                || !Objects.equals(labels.get("Notes"), entryToEdit.notes());

        if (!recordChanged) {
            System.out.println("No changes has been made. Returning Now ...");
            return;
        }

        String query = """
                                UPDATE      gateflow
                                SET         entries = ?, exits = ?, source = ?, notes = ?, revno = revno + 1
                                WHERE       stncode = ?
                                AND         startat = ?
                                AND         revno = ?
                """;

        try (PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setObject(1, labels.get("Entries"));
            statement.setObject(2, labels.get("Exits"));
            statement.setObject(3, labels.get("Source"));
            statement.setObject(4, labels.get("Notes"));
            statement.setObject(5, entryToEdit.stncode());
            statement.setObject(6, entryToEdit.startat());
            statement.setObject(7, entryToEdit.revno());

            int rows = statement.executeUpdate();
            if (rows == 0) {
                System.out.println(Utils.RED
                        + "Edit failed: the record was changed or deleted since you loaded it. Please search again."
                        + Utils.RESET);
            } else {
                System.out.println("Record edited successfully.");
            }
        }

    }

    private void deleteMenu(Connection conn) throws SQLException {
        PrimaryKey entry = searchMenu(conn);
        if (entry == null)
            return;

        Entry entryToDelete = showEntry(conn, entry);
        if (entryToDelete == null)
            return;

        System.out.println(Utils.RED + "You are about to delete this Interval" + Utils.RESET);
        System.out.print("Type [Confirm] to confirm the deletion: ");
        String confirmText = Utils.SCANNER.nextLine();
        if (confirmText.trim().equalsIgnoreCase("Confirm")) {
            String query = """
                    DELETE FROM         gateflow
                    WHERE               stncode = ?
                    AND                 startat = ?
                    AND                 revno = ?
                    """;
            try (PreparedStatement statement = conn.prepareStatement(query)) {
                statement.setString(1, entryToDelete.stncode());
                statement.setObject(2, entryToDelete.startat());
                statement.setInt(3, entryToDelete.revno());

                int rows = statement.executeUpdate();
                if (rows == 0) {
                    System.out.println(Utils.RED
                            + "Delete failed: the record was changed or removed since you loaded it. Please search again."
                            + Utils.RESET);
                } else {
                    System.out.println("Record deleted successfully!");
                }
            }
        } else {
            System.out.println("Deletion cancelled. Returning Now ...");
        }
    }

    private LocalDate getDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return LocalDate.parse(Utils.SCANNER.nextLine().trim());
            } catch (java.time.format.DateTimeParseException e) {
                System.out.println(Utils.RED + "Invalid date format, please use yyyy-MM-dd." + Utils.RESET);
            }
        }
    }

    private void report4Menu(Connection conn) throws SQLException {
        LocalDate from = getDate("Enter start date (yyyy-MM-dd): ");
        LocalDate to = null;

        boolean isValid = false;
        while (!isValid) {
            to = getDate("Enter end date (yyyy-MM-dd): ");
            if (to.isBefore(from)) {
                System.out.println(Utils.RED + "Start date cannot be after the end date" + Utils.RESET);
            } else {
                isValid = true;
            }
        }
        String stncode = null;
        String stationChoice = Utils.displayMenu("Station Filter", new String[] { "All Stations", "Choose a Station" });
        if (stationChoice.equals("Choose a Station")) {
            stncode = getStationCode(conn, null);
            if (stncode == null) {
                System.out.println("There are no stations to choose from. Returning Now ...");
                return;
            }
        }
        System.out.println("\nSelect " + Utils.BOLD + "[3] Reset" + Utils.RESET + " to get both sources");
        String source = getSource(conn, true);
        if ("Reset".equals(source))
            source = null;

        int minEntries = getIntInRange("Minimum total entries (Enter 0 for none): ",
                Utils.RED + "Invalid Selection. Please try again." + Utils.RESET, 0, Integer.MAX_VALUE);

        StringBuilder query = new StringBuilder("""
                SELECT   g.stncode, s.stnname, COUNT(*) AS intervals,
                         SUM(g.entries) AS total_entries, SUM(g.exits) AS total_exits,
                         AVG(g.entries) AS avg_entries,   AVG(g.exits) AS avg_exits
                FROM     gateflow g
                JOIN     station s ON s.stncode = g.stncode
                WHERE    g.startat >= ? AND g.startat < ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(from.atStartOfDay());
        parameters.add(to.plusDays(1).atStartOfDay());

        if (stncode != null) {
            query.append(" AND g.stncode = ?");
            parameters.add(stncode);
        }

        if (source != null) {
            query.append(" AND g.source = ?");
            parameters.add(source);
        }

        query.append(" GROUP BY g.stncode, s.stnname");
        query.append(" HAVING SUM(g.entries) >= ?");
        parameters.add(minEntries);
        query.append(" ORDER BY total_entries DESC, g.stncode");

        System.out.println("\nREPORT 4: Station Entry and Exit Volumes by Member 4 - Sean Chan");
        System.out.println("Dates (inclusive): " + from + " to " + to + " | Date basis: interval start");
        System.out.println("Station: " + (stncode == null ? "ALL" : stncode));
        System.out.println("Source: " + (source == null ? "ALL" : source));
        System.out.println("Minimum total Entries: " + minEntries);

        try (PreparedStatement statement = conn.prepareStatement(query.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet result = statement.executeQuery()) {
                boolean entryFound = false;

                String header = String.format("| %-7s | %-20s | %9s | %13s | %12s | %14s | %13s |",
                        "stncode", "station name", "intervals", "total_entries", "total_exits", "avg_entries",
                        "avg_exits");
                String line = "-".repeat(header.length());

                System.out.println(line);
                System.out.println(header);
                System.out.println(line);

                while (result.next()) {
                    entryFound = true;
                    System.out.println(String.format("| %-7s | %-20s | %9d | %13d | %12d | %14.2f | %13.2f |",
                            result.getString("stncode"), result.getString("stnname"), result.getInt("intervals"),
                            result.getLong("total_entries"), result.getLong("total_exits"),
                            result.getDouble("avg_entries"), result.getDouble("avg_exits")));
                }
                System.out.println(line);

                if (!entryFound) {
                    System.out.println("No recorded intervals found!");
                } else {
                    System.out.println("Averages are based on 15 minute intervals. Missing intervals are not counted.");
                }
            }
        }

    }

}
