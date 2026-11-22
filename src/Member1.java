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
                default -> {return;}
            }
        }
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
            case "stnname", "address", "active" -> Utils.updateValue(field);
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
        String sql = "SELECT s.stncode, s.stnname, s.brgycode, b.brgyname, s.address, s.active, " +
                            "p.platno, p.platname, p.directn, p.capacity, p.active, " +
                            "(SELECT COUNT(*) " +
                            "FROM traincall t " +
                            "JOIN platform p ON t.platno = p.platno " +
                            "WHERE p.stncode = s.stncode) AS TotalTrainCalls, " +

                            "(SELECT COUNT(*) " +
                            "FROM GATEFLOW g " +
                            "WHERE g.stncode = s.stncode) AS RecordedIntervals " +
                      "FROM station s JOIN refbarangay b ON s.brgycode = b.brgycode " +
                      "LEFT JOIN platform p ON s.stncode = p.stncode";
        String tail = "ORDER BY s.stncode ASC;";
        List<Filter> filters = List.of(
                new Filter("s.stncode", "Station Code", "VARCHAR(8)", "LIKE", false),
                new Filter("s.stnname", "Station Name", "VARCHAR(100)", "LIKE", false),
                new Filter("s.brgycode", "Barangay Code", "VARCHAR(8)", "LIKE", false),
                new Filter("s.address", "Address", "VARCHAR(180)", "LIKE", false)
        );
        try (Connection c = Utils.getSqlConnection()){
            ResultSet res = Utils.sqlSearch(c, sql, null, tail, null, filters);
            Field identifier = new Field("s.stncode", "Station Code", "VARCHAR(8)");
            Utils.retrieveRecordMenu(res, identifier);
            selected = identifier.getValue() == null ? null : identifier;
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void updateTrain() {
        Map<String, Field> fields = new LinkedHashMap<>();
        // Add modifiable fields
        fields.put("Station Name", new Field("stnname", "Station Name", "VARCHAR(100)"));
        fields.put("Barangay Code", new Field("brgycode", "Barangay Code", "VARCHAR(8)"));
        fields.put("Address", new Field("address", "Address", "VARCHAR(180)"));
        fields.put("Active", new Field("active", "Address", "TINYINT"));

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
                        saveStationUpdate(c, fields, hasTypeChange);
                        return;
                    }
                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        handleStationInput(c, fields.get(key));
                    }
                }
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
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
                    deleteTrain();
                    return;
                }

                // show the record to be deleted
                String sql = "SELECT t.trainno, t.label, t.traintyp, p.cars, p.paxcap, s.dutytype, s.loadton, if(t.active = 1, 'Active', 'Inactive') AS active " +
                        "FROM train t LEFT JOIN passengertrain p ON t.trainno = p.trainno " +
                        "LEFT JOIN servicetrain s ON t.trainno = s.trainno " +
                        "WHERE t.trainno = ?;";

                try (PreparedStatement ps = Utils.prepareStatement(c, sql, new Field[]{ref})) {
                    Utils.displaySqlResult(ps.executeQuery());
                }

                // confirm if the user wants to delete the record
                String[] choices = {"Yes", "No"};
                String confirm = Utils.displayMenu("Are you sure you want to delete this record?", choices);
                if (confirm.equalsIgnoreCase("No")) return;

                boolean sub = false;

                if (Utils.doesRecordExist(c, "passengertrain", ref)) {
                    sub = Utils.safeDeleteRecord(c, "passengertrain", null, ref);
                } else if (Utils.doesRecordExist(c, "servicetrain", ref)) {
                    sub = Utils.safeDeleteRecord(c, "servicetrain", new String[] {"servicetask"}, ref);
                } else sub = true;

                boolean main = false;
                if (sub) main = Utils.safeDeleteRecord(c, "train", new String[] {"traincall"}, ref); // only try to delete once child records are deleted

                if (main && sub) {
                    c.commit();
                    selected = null;
                    Utils.displaySuccess(String.format("%s has been successfully deleted!", ref.getValue()));
                    return;
                } else c.rollback();
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }
}
