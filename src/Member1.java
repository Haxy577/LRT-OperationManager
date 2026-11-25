import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Member 1: Station management and directional platform registration
 * <p>
 * Member 1 owns station creation, search, retrieval, update, and controlled deletion. The same
 * station workspace includes compact platform registration and current-capacity maintenance.
 * Creating a station requires its unique code, name, barangay code with matching name, and
 * location. A new station starts inactive with revision 1 until both platforms have been registered.
 * The platform workflow records a unique reference, station, label, Northbound or Southbound
 * direction, positive registered capacity, activation, and revision 1. The interface offers only the
 * missing direction when completing a pair and rechecks uniqueness at save time. Activating a
 * station verifies that exactly one record exists for each direction. It does not require both to be
 * currently available when one platform is legitimately suspended.
 * </p>
 * <p>
 * Station search supports code, name, barangay, location, and activation. Selection loads saved
 * station values, both directional platform records and current capacities, and relevant
 * movement/count references read-only. Platform search within the station supports reference,
 * label, direction, and activation. No matches and multiple matches remain explicit and individually
 * selectable.
 * </p>
 * <p>
 * Ordinary edits protect the station's identity and preserve consistency in shared barangay names.
 * Platform station/direction attribution is protected once referenced. Inactivation preserves earlier
 * activity. Confirmed station deletion is limited to an erroneous record without platforms or counts;
 * platforms must be handled explicitly rather than cascade-deleted. Confirmed platform deletion
 * requires no calls and must not break an active station's required pair. Member 1 does not own
 * Station-call or gate-flow CRUD.
 * </p>
 *
 * @author John Paul Ong-Hay
 */
public class Member1 implements Application {

    private Field selected;

    public Member1() {selected = null;}

    @Override
    public void menu() {
        while (true) {
            String[] options = {"Manage Stations", "Manage Platforms", "Generate Report", "Exit"};
            String prompt = "Choose which category you want to manage:";
            switch (Utils.displayMenu(prompt, options)) {
                case "Manage Stations" -> stationMenu();
                case "Manage Platforms" -> platformMenu();
                default -> {return;}
            }
        }
    }

    private void platformMenu() {
        while (true) {
            String prompt = "Which operation do you wish to do:";
            List<String> menu = new ArrayList<>(List.of(
                    "Create Platform", "Search Platform",
                    String.format("Edit %s", selected != null ? selected.getValue() : "Platform"),
                    String.format("Delete %s", selected != null ? selected.getValue() : "Platform")
            ));
            if (selected != null) menu.add("Reset Selection");
            menu.add("Return");
            String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));
            if (choice.contains("Create")) createPlatform();
            else if (choice.contains("Search")) searchPlatform();
            else if (choice.contains("Edit")) updatePlatform();
            else if (choice.contains("Delete")) deletePlatform();
            else if (choice.contains("Reset Selection")) selected = null;
            else return;
        }
    }

    private void deletePlatform() {
        String prompt = "Input the Platform Reference of the record you wish to delete (blank to return): ";
        Field ref = new Field("platno", "Platform Reference", "VARCHAR(12)");

        try (Connection c = Utils.getSqlConnection()) {
            c.setAutoCommit(false);

            while (true) {
                if (selected == null) {
                    Object choice = Utils.getInput(prompt, ref.getType());
                    if (choice == null) return;
                    ref.setValue(choice);
                } else {
                    ref.setValue(selected.getValue());
                }

                if (!Utils.doesRecordExist(c, "platform", ref)) {
                    Utils.displayInputError("NO_RECORD");
                    continue;
                }
                String sql =
                        "SELECT p.platno, p.stncode, p.platname, p.directn, " +
                        "p.capacity, p.active, s.active AS stationactive " +
                        "FROM `platform` p " +
                        "JOIN station s ON p.stncode = s.stncode " +
                        "WHERE p.platno = ?";

                int stationActive = 0;

                try (PreparedStatement ps = Utils.prepareStatement(c, sql, new Field[]{ref})) {
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        stationActive = rs.getInt("stationactive");
                        Utils.displaySqlResult(rs);
                    }
                }

                // Confirm if the user wants to delete the record
                String[] choices = {"Yes", "No"};
                String confirm = Utils.displayMenu(
                        "Are you sure you want to delete this record?",
                        choices
                );

                if (confirm.equalsIgnoreCase("No")) return;

                // Check whether the platform has existing train calls
                if (Utils.doesRecordExist(c, "traincall", ref)) {
                    Utils.displayError( "Failed to delete record because it has existing train calls!");
                    return;
                }

                // An active station must retain both directional platforms
                if (stationActive == 1) {
                    Utils.displayError("Failed to delete record because its station is still active!");
                    return;
                }

                boolean main = Utils.safeDeleteRecord(c, "platform", null, ref);

                if (main) {
                    c.commit();
                    selected = null;

                    Utils.displaySuccess(
                            String.format("%s has been successfully deleted!", ref.getValue())
                    );

                    return;
                } else {
                    c.rollback();
                }
            }

        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
    }

    private void updatePlatform() {
        Map<String, Field> fields = new LinkedHashMap<>();

        // Add modifiable fields
        fields.put("Platform Name", new Field("platname", "Platform Name", "VARCHAR(50)"));
        fields.put("Capacity", new Field("capacity", "Capacity", "INT UNSIGNED"));
        fields.put("Active", new Field("active", "Active", "TINYINT"));

        // Add fields for search/conditions
        fields.put("Revision", new Field("revno", "Revision", "INT UNSIGNED"));
        fields.put("Platform Reference", new Field("platno", "Platform Reference", "VARCHAR(12)"));
        fields.put("Station Code", new Field("stncode", "Station Code", "VARCHAR(8)"));
        fields.put("Direction", new Field("directn", "Direction", "VARCHAR(10)"));

        try (Connection c = Utils.getSqlConnection()) {

            // Get the platform record to be updated
            Field ref = fields.get("Platform Reference");

            if (selected == null) {
                while (true) {
                    Utils.updateValue(ref);

                    if (ref.getValue() == null) return;

                    if (Utils.doesRecordExist(c, "platform", ref)) break;

                    Utils.displayInputError("NO_RECORD");
                }
            } else {
                ref.setValue(selected.getValue());
            }

            // Retrieve and store the original values
            final String selectSql =
                    "SELECT p.platno, p.stncode, p.platname, p.directn, " +
                    "p.capacity, p.active, p.revno " +
                    "FROM `platform` p " +
                    "WHERE p.platno = ?";

            Map<String, Object> originalValues =
                    Utils.loadValues(c, selectSql, ref, fields);

            while (true) {
                List<Field> active = new ArrayList<>();
                active.add(fields.get("Platform Name"));
                active.add(fields.get("Capacity"));
                active.add(fields.get("Active"));

                List<String> menu = Utils.createModifiableMenu(active);
                String prompt = "Modify every field, then choose 'Confirm' afterwards:";
                String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));

                switch (choice) {
                    case "Return" -> {
                        return;
                    }

                    case "Reset" -> {
                        for (String k : fields.keySet()) {
                            fields.get(k).setValue(originalValues.get(k));
                        }
                    }

                    case "Confirm" -> {
                        if (!Utils.checkAllFields(active)) continue;

                        savePlatformUpdate(c, fields);
                        return;
                    }

                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        handlePlatformInput(c, fields.get(key));
                    }
                }
            }

        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
    }

    private boolean savePlatformUpdate(Connection c, Map<String, Field> fields) {
        String updateSql = "UPDATE `platform` SET platname = ?, capacity = ?, active = ?, " +
                           "revno = revno + 1 WHERE revno = ? AND platno = ?";

        Field ref = fields.get("Platform Reference");

        try {
            c.setAutoCommit(false);

            List<Field> values = List.of(
                    fields.get("Platform Name"),
                    fields.get("Capacity"),
                    fields.get("Active"),
                    fields.get("Revision"),
                    ref
            );

            boolean main = Utils.modifyTable(c, updateSql, values);

            if (main) {
                c.commit();

                Utils.displaySuccess(
                        String.format("%s has been successfully updated!", ref.getValue())
                );

                return true;
            } else {
                c.rollback();
                return false;
            }

        } catch (SQLException e) {
            try {
                c.rollback();
            } catch (SQLException rollbackError) {
                rollbackError.printStackTrace();
            }

            Utils.displayDatabaseError(null, e);
            return false;
        }
    }

    private void searchPlatform() {
    String sql = "SELECT ROW_NUMBER() OVER (ORDER BY p.platno ASC) AS row_num, " +
            "p.platno, p.stncode, s.stnname, p.platname, p.directn, " +
            "p.capacity, IF(p.active = 1, 'Active', 'Inactive') AS active " +
            "FROM `platform` p " +
            "JOIN station s ON p.stncode = s.stncode ";

    String tail = "ORDER BY p.platno ASC;";

    List<Filter> filters = List.of(
            new Filter("p.platno", "Platform Reference", "VARCHAR(12)", "LIKE", false),
            new Filter("p.stncode", "Station Code", "VARCHAR(8)", "=", false),
            new Filter("s.stnname", "Station Name", "VARCHAR(100)", "LIKE", false),
            new Filter("p.platname", "Platform Name", "VARCHAR(50)", "LIKE", false),
            new Filter("p.directn", "Direction", "VARCHAR(10)", new String[]{"Northbound", "Southbound"}, "=", false),
            new Filter("p.active", "Platform Activation Status", "TINYINT", new String[]{"1", "0"}, "=", false)
    );

    try (Connection c = Utils.getSqlConnection()) {
        ResultSet res = Utils.sqlSearch(c, sql, null, tail, null, filters);

        Field identifier = new Field("p.platno", "Platform Reference", "VARCHAR(12)");
        Utils.retrieveRecordMenu(res, identifier);

        selected = identifier.getValue() == null ? null : identifier;

    } catch (SQLException e) {
        Utils.displayDatabaseError(null, e);
    }
}

    private void createPlatform() {
        ArrayList<Field> fields = new ArrayList<>();
        fields.add(new Field("platno", "Platform Code", "VARCHAR(12)"));
        fields.add(new Field("stncode", "Station Code", "VARCHAR(8)"));
        fields.add(new Field("platname", "Platform Name", "VARCHAR(80)"));
        fields.add(new Field("capacity", "Capacity", "UNSIGNED INT"));
        fields.add(new Field("active", "Active", "TINYINT"));

        try (Connection c = Utils.getSqlConnection()) {
            while (true) {
                List<String> menu = Utils.createModifiableMenu(fields);
                String prompt = "Modify every field, then choose 'Confirm' afterwards:";
                String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));

                switch (choice) {
                    case "Return" -> {return;}
                    case "Reset" -> {for (Field f : fields) f.setValue(null);}
                    case "Confirm" -> {
                        if (!Utils.checkAllFields(fields)) continue;
                        getNorthOrSouth(c, fields, fields.get(1));
                        return;
                    }
                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        handlePlatformInput(c, Field.searchByLabel(key, fields));
                    }
                }
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void handlePlatformInput(Connection c, Field field) {
        switch (field.getName()) {
            case "platno" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (!Utils.doesRecordExist(c, "platform", field)) break;
                    Utils.displayInputError("IDENTIFIER");
                }
            }
            case "stncode" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (!Utils.doesRecordExist(c, "station", field)) {
                        Utils.displayInputError("NO_RECORD");
                        continue;
                    }
                    if (Utils.doesSouthAndNorthExist(c, field) != 1) break;
                    else Utils.displayError("Both Southbound and Northbound platform already exists for this station"); 
                }
            }
            case "platname" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (!Utils.doesRecordExist(c, "platform", field)) break;
                    Utils.displayInputError("IDENTIFIER");
                }
            }
            case "active" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue().equals(1) || field.getValue().equals(0)) break;
                    Utils.displayInputError("1_0_ONLY");
                }
            }
            case "capacity" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (!field.getValue().equals(0)) break;
                    Utils.displayInputError("Postive numbers only");
                }
            }
        }    
    }

    public void getNorthOrSouth(Connection c, ArrayList<Field> fields, Field ref) {
        fields.add(3, new Field("directn", "Direction", "VARCHAR(10)"));
        System.out.println("Choose a platform to create for this station:");

        int det = Utils.doesSouthAndNorthExist(c, ref);

        if (det == -1) {
            System.out.println("  [1] Northbound");
            System.out.println("  [2] Southbound");
            
            int choice = Utils.getIntInput(2);
            
            switch (choice) {
                case 1 -> {
                    fields.get(3).setValue("Northbound");
                    savePlatformRecord(c, fields);
                }
                case 2 -> {
                    fields.get(3).setValue("Southbound");
                    savePlatformRecord(c, fields);
                }
            }
        } else if (det == 3) {
            System.out.println("  [1] Southbound");
            int choice = Utils.getIntInput(1);
            fields.get(3).setValue("Southbound");
            savePlatformRecord(c, fields);
        } else if (det == 2) {
            System.out.println("  [1] Northbound");
            int choice = Utils.getIntInput(1);
            fields.get(3).setValue("Northbound");
            savePlatformRecord(c, fields);
        }
    }

    private void savePlatformRecord (Connection c, List<Field> fields) {
        final String sqlStation = "INSERT INTO platform (platno, stncode, platname, directn, capacity, active, revno) VALUES (?, ?, ?, ?, ?, ?, 1);";

        try {
            c.setAutoCommit(false);

            Object stncode = Field.searchByName("stncode", fields).getValue();
            Object directn = Field.searchByName("directn", fields).getValue();

            String sqlCheck =
                "SELECT COUNT(*) FROM `platform` WHERE stncode = ? AND directn = ?";

            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setObject(1, stncode);
                ps.setObject(2, directn);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        c.rollback();
                        System.out.println("A platform with this direction already exists for this station.");
                        return;
                    }
                }
            }

            boolean main = Utils.modifyTable(c, sqlStation, fields);

            if (main) {
                c.commit();
                Utils.displaySuccess(String.format("%s has been successfully created!", Field.searchByName("stncode", fields).getValue()));
            }
            else c.rollback();
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void stationMenu() {
        while (true) {
            String prompt = "Which operation do you wish to do:";
            List<String> menu = new ArrayList<>(List.of(
                    "Create Station", "Search Station",
                    String.format("Edit %s", selected != null ? selected.getValue() : "Station"),
                    String.format("Delete %s", selected != null ? selected.getValue() : "Station")
            ));
            if (selected != null) menu.add("Reset Selection");
            menu.add("Return");
            String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));
            if (choice.contains("Create")) createStation();
            else if (choice.contains("Search")) searchStation();
            else if (choice.contains("Edit")) updateStation();
            else if (choice.contains("Delete")) deleteStation();
            else if (choice.contains("Reset Selection")) selected = null;
            else return;
        }
    }

    private void createStation() {
        ArrayList<Field> fields = new ArrayList<>();
        fields.add(new Field("stncode", "Station Code", "VARCHAR(8)"));
        fields.add(new Field("stnname", "Station Name", "VARCHAR(100)"));
        fields.add(new Field("brgycode", "Barangay Code", "VARCHAR(8)"));
        fields.add(new Field("address", "Address", "VARCHAR(180)"));

        try (Connection c = Utils.getSqlConnection()) {
            while (true) {
                List<String> menu = Utils.createModifiableMenu(fields);
                String prompt = "Modify every field, then choose 'Confirm' afterwards:";
                String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));

                switch (choice) {
                    case "Return" -> {return;}
                    case "Reset" -> {for (Field f : fields) f.setValue(null);}
                    case "Confirm" -> {
                        if (!Utils.checkAllFields(fields)) continue;
                        saveStationRecord(c, fields);
                        return;
                    }
                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        handleStationInput(c, Field.searchByLabel(key, fields));
                    }
                }
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void handleStationInput(Connection c, Field field) {
        switch (field.getName()) {
            case "stncode" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (!Utils.doesRecordExist(c, "station", field)) break;
                    Utils.displayInputError("IDENTIFIER");
                }
            }
            case "brgycode" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (Utils.doesRecordExist(c, "refbarangay", field)) break;
                    Utils.displayInputError("NO_RECORD");
                }
            }
            case "active" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue().equals(1) || field.getValue().equals(0)) break;
                    Utils.displayInputError("1_0_ONLY");
                }
            }
            case "stnname", "address" -> Utils.updateValue(field);
        }
    }

    private void saveStationRecord (Connection c, List<Field> fields) {
        final String sqlStation = "INSERT INTO station (stncode, stnname, brgycode, address, active, revno) VALUES (?, ?, ?, ?, 0, 1);";

        try {
            c.setAutoCommit(false);

            boolean main = Utils.modifyTable(c, sqlStation, fields);

            if (main) {
                c.commit();
                Utils.displaySuccess(String.format("%s has been successfully created!", Field.searchByName("stncode", fields).getValue()));
            }
            else c.rollback();
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    
    private void searchStation() {
        String sql = "SELECT ROW_NUMBER() OVER (ORDER BY s.stncode ASC) AS row_num, " +
                    "s.stncode, s.stnname, s.brgycode, b.brgyname, " +
                    "s.address, " +
                    "IF(s.active = 1, 'Active', 'Inactive') AS active, " +

                    "(SELECT COUNT(*) " +
                    "FROM traincall t " +
                    "JOIN `platform` p ON t.platno = p.platno " +
                    "WHERE p.stncode = s.stncode) AS TotalTrainCalls, " +

                    "(SELECT COUNT(*) " +
                    "FROM gateflow g " +
                    "WHERE g.stncode = s.stncode) AS RecordedIntervals " +

                    "FROM station s " +
                    "JOIN refbarangay b ON s.brgycode = b.brgycode ";

        String tail = "ORDER BY s.stncode ASC;";

        List<Filter> filters = List.of(
            new Filter("s.stncode", "Station Code", "VARCHAR(8)", "=", false),
            new Filter("s.stnname", "Station Name", "VARCHAR(100)", "LIKE", false),
            new Filter("s.brgycode", "Barangay Code", "VARCHAR(8)", "=", false),
            new Filter("s.address", "Address", "VARCHAR(180)", "LIKE", false),
            new Filter("s.active", "Active", "TINYINT", new String[]{"1", "0"}, "=", false)
        );

        try (Connection c = Utils.getSqlConnection()) {
            ResultSet res = Utils.sqlSearch(c, sql, null, tail, null, filters);

            Field identifier = new Field("s.stncode", "Station Code", "VARCHAR(8)");
            Utils.retrieveRecordMenu(res, identifier);

            selected = identifier.getValue() == null ? null : identifier;

        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
    }


    private void updateStation() {
        Map<String, Field> fields = new LinkedHashMap<>();
        // Add modifiable fields
        fields.put("Station Name", new Field("stnname", "Station Name", "VARCHAR(100)"));
        fields.put("Barangay Code", new Field("brgycode", "Barangay Code", "VARCHAR(8)"));
        fields.put("Address", new Field("address", "Address", "VARCHAR(180)"));
        fields.put("Active", new Field("active", "Active", "TINYINT"));

        // add fields for search/conditions
        fields.put("Revision", new Field("revno", "Revision", "INT UNSIGNED"));
        fields.put("Station Code", new Field("stncode", "Station Code", "VARCHAR(8)"));

        try (Connection c = Utils.getSqlConnection()) {
            // get the record to be updated
            Field ref = fields.get("Station Code");
            if (selected == null) {
                while (true) {
                    Utils.updateValue(ref);
                    if (ref.getValue() == null) return;
                    if (Utils.doesRecordExist(c, "station", ref)) break;
                    Utils.displayInputError("NO_RECORD");
                }
            } else ref.setValue(selected.getValue());

            // Retrieve and store the original values
            final String selectSql = "SELECT s.stncode, s.stnname, s.brgycode, s.address, s.active, s.revno " +
                    "FROM station s " +
                    "WHERE s.stncode = ? ";
            Map<String, Object> originalValues = Utils.loadValues(c, selectSql, ref, fields);

            while (true) {
                List<Field> active = new ArrayList<>();
                active.add(fields.get("Station Name"));
                active.add(fields.get("Barangay Code"));
                active.add(fields.get("Address"));
                active.add(fields.get("Active"));

                List<String> menu = Utils.createModifiableMenu(active);
                String prompt = "Modify every field, then choose 'Confirm' afterwards:";
                String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));


                switch (choice) {
                    case "Return" -> {return;}
                    case "Reset" -> {for (String k : fields.keySet()) fields.get(k).setValue(originalValues.get(k));}
                    case "Confirm" -> {
                        if (!Utils.checkAllFields(active)) continue;
                        saveStationUpdate(c, fields);
                        return;
                    }
                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        if ("Active".equalsIgnoreCase(key)) {
                            int det = Utils.doesSouthAndNorthExist(c, ref);
                            if (det != 1) {
                                Utils.displayInputError("S/N_PLATFORM");
                                continue;
                            }
                            
                        }
                        handleStationInput(c, fields.get(key));
                    }
                }
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private boolean saveStationUpdate(Connection c, Map<String, Field> fields) {
        String mainUpdate = "UPDATE station SET stnname = ?, brgycode = ?, address = ?, active = ?, revno = revno + 1 WHERE revno = ? AND stncode = ?";
        Field ref = fields.get("Station Code");
        try {
            c.setAutoCommit(false);
            List<Field> mainValues = List.of(fields.get("Station Name"), fields.get("Barangay Code"), fields.get("Address"), fields.get("Active"), fields.get("Revision"), ref);
            boolean main = Utils.modifyTable(c, mainUpdate, mainValues);

            if (main) {
                c.commit();
                Utils.displaySuccess(String.format("%s has been successfully updated!", ref.getValue()));
                return true;
            }
            else c.rollback();
            return false;
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
        return false;
    }

    private void deleteStation() {
        String prompt = "Input the Station Code of the record you wish to delete (blank to return): ";
        Field ref = new Field("stncode", "Station Code", "VARCHAR(8)");

        try (Connection c = Utils.getSqlConnection()) {
            c.setAutoCommit(false);
            while (true) {
                if (selected == null) {
                    Object choice = Utils.getInput(prompt, ref.getType());
                    if (choice == null) return;
                    ref.setValue(choice);
                } else ref.setValue(selected.getValue());

                if (!Utils.doesRecordExist(c, "station", ref)) {
                    Utils.displayInputError("NO_RECORD");
                    deleteStation();
                    return;
                }

                // show the record to be deleted
                String sql = "SELECT * FROM station WHERE stncode = ?;";

                try (PreparedStatement ps = Utils.prepareStatement(c, sql, new Field[]{ref})) {
                    Utils.displaySqlResult(ps.executeQuery());
                }

                // confirm if the user wants to delete the record
                String[] choices = {"Yes", "No"};
                String confirm = Utils.displayMenu("Are you sure you want to delete this record?", choices);
                if (confirm.equalsIgnoreCase("No")) return;

                boolean main = false;

                if (!Utils.doesRecordExist(c, "gateflow", ref) && !Utils.doesRecordExist(c, "platform", ref)) {
                    main = Utils.safeDeleteRecord(c, "station", null, ref);
                } else {
                    Utils.displayError("Failed to delete record because it has already has counts or platforms or both!");
                }

                if (main) {
                    c.commit();
                    selected = null;
                    Utils.displaySuccess(String.format("%s has been successfully deleted!", ref.getValue()));
                    return;
                } else c.rollback();
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }
}
