import java.sql.*;
import java.util.*;

/**
 * Member 3: Train-call management
 * <p>
 * Member 3 owns arrival creation, search, retrieval, departure recording, update, and controlled
 * deletion. The operator selects an active train and station, and the corresponding eligible
 * Northbound or Southbound platform, using meaningful references and labels. The interface
 * displays current platform capacity as register information, never as a measured headcount or
 * available-space calculation.
 * </p>
 * <p>
 * Creating a call requires the issued reference and actual arrival time. State begins Arrived,
 * departure is blank, and revision begins at 1. Search supports call reference, train, station,
 * platform, direction, state, and arrival date range. Selection loads the saved values and register
 * context; repeated visits remain distinct.
 * </p>
 * <p>
 * Departure loads the existing call and records the actual timestamp and Departed state. The
 * program rechecks revision and surrounding train/platform visits under coordinated saving.
 * Ordinary edits protect call identity and train/platform attribution. Timestamp corrections cannot
 * create incompatible visit intervals. Deletion requires confirmation and is limited to an erroneous,
 * unfinished call that did not represent a valid arrival; real departed visits are retained. A false
 * record requiring exceptional correction is not removed through an unrestricted delete option.
 * </p>
 *
 * @author Patrick Ang Ngo Ching
 */
public class Member3 implements Application{

    @Override
    public void menu() {
        Boolean isRunning = true;
        String[] options = {
                "Create Train Call",
                "Search Train Call",
                "Record Departure",
                "Edit Train Call",
                "Delete Train Call",
                "Generate Report 3",
                "Exit"
        };
        while (isRunning) {
            switch (Utils.displayMenu("Select Option: ", options)) {
                case "Create Train Call" -> createTrainCall();
                case "Search Train Call" -> searchTrainCall();
                case "Record Departure" -> recordDeparture();
                case "Edit Train Call" -> editTrainCall();
                case "Delete Train Call" -> deleteTrainCall();
                case "Generate Report 3" -> generateReport3();
                default -> isRunning = false;
            }
        }
    }

    private void createTrainCall() {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("callno", "Call Reference", "VARCHAR(16)"));
        fields.add(new Field("trainno", "Train Reference", "VARCHAR(12)"));
        fields.add(new Field("platno", "Platform Reference", "VARCHAR(12)"));
        fields.add(new Field("arrat", "Arrival Time", "DATETIME"));
        fields.add(new Field("remarks", "Remarks", "VARCHAR(255)"));
        try (Connection c = Utils.getSqlConnection()) {
            // String sql = "insert into traincall(callno, trainno, platno, arrat, depat, status, remarks, revno) values ('C123', )";
            Boolean isRunning = true;
            while (isRunning) {
                List<String> options = Utils.createModifiableMenu(fields);
                int choice = Utils.getIntChoice("Modify Train Call details, then choose 'Confirm':", options.toArray(new String[0]));
                int totalFields = fields.size();

                if (choice == totalFields) {
                    for (Field f : fields) f.setValue(null);
                } else if (choice == totalFields + 1) {
                    List<Field> requiredFields = new ArrayList<>();
                    for (Field f : fields) {
                        if (!"remarks".equals(f.getName())) {
                            requiredFields.add(f);
                        }
                    }
                    if (!Utils.checkAllFields(requiredFields)) 
                        continue;

                    Field callno = fields.get(0);  // callno
                    Field trainno = fields.get(1); // trainno
                    Field platno = fields.get(2);  // platno
                    Field arrat = fields.get(3);   // arrat
                    Field remarks = fields.get(4); // remarks

                    Field depatField = new Field("depat", "Departure Time", "DATETIME");
                    depatField.setValue(null);

                    Field statusField = new Field("status", "Status", "VARCHAR(15)");
                    statusField.setValue("Arrived");

                    Field revnoField = new Field("revno", "Revision", "INT");
                    revnoField.setValue(1);

                    List<Field> insertValues = List.of(callno, trainno, platno, arrat, depatField, statusField, remarks, revnoField);

                    String sql = "INSERT INTO traincall (callno, trainno, platno, arrat, depat, status, remarks, revno) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
                    
                    if (Utils.modifyTable(c, sql, insertValues)) {
                        System.out.println("Train Call successfully created!");
                        return;
                    }
                } else if (choice == totalFields + 2) {
                    return;
                } else {
                    Field selectedField = fields.get(choice);
                    handleTrainCallInput(c, selectedField);
                }
            }
        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
    }

    private void searchTrainCall() {
        String sql = "SELECT tc.callno, tc.trainno, p.stncode, tc.platno, p.directn, tc.arrat, tc.depat, tc.status, LEFT(tc.remarks, 20) AS remarks, tc.revno " +
                "FROM traincall tc JOIN platform p ON tc.platno = p.platno WHERE 1=1 ";
        List<Filter> filters = List.of(
            new Filter("tc.callno", "Call Reference", "VARCHAR(16)", "LIKE", false),
            new Filter("tc.trainno", "Train Reference", "VARCHAR(12)", "LIKE", false),
            new Filter("p.stncode", "Station Code", "VARCHAR(8)", "LIKE", false),
            new Filter("tc.platno", "Platform Reference", "VARCHAR(12)", "LIKE", false),
            new Filter("p.directn", "Direction", "VARCHAR(15)", new String[]{"Northbound", "Southbound"}, "=", false),
            new Filter("tc.status", "State / Status", "VARCHAR(15)", new String[]{"Arrived", "Departed"}, "=", false),
            new Filter("tc.arrat", "Start Arrival Date", "DATE", ">=", false),
            new Filter("tc.arrat", "End Arrival Date", "DATE", "<=", false)
        );
        try (Connection c = Utils.getSqlConnection()) {
            // Scanner scanner = new Scanner(System.in);
            // System.out.print("Enter Call Reference: ");
            // String filter = scanner.nextLine();

            // String sql = "SELECT * FROM traincall WHERE callno = ?";

            // try (PreparedStatement ps = c.prepareStatement(sql)) {
            //     ps.setString(1, filter);
                
            //     try (ResultSet result = ps.executeQuery()) {
            //         while (result.next()) {
            //             String callno = result.getString("callno");
            //             System.out.println(callno);
            //         }
            //     }
            // }
            Utils.displaySqlResult(Utils.sqlSearch(c, sql, null, null, null, filters));
        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
    }

    private void recordDeparture() {
        // Scanner scanner = new Scanner(System.in);
        // // System.out.print("Enter Call Reference: ");
        // // String lol = scanner.nextLine();
        // String lol = "CL250000001";
        // String sql = "SELECT * FROM traincall WHERE callno = ?";
        Map<String, Field> fields = createCallFields();

        try (Connection c = Utils.getSqlConnection()) {
            String sqlQuery1 = "SELECT * FROM traincall where callno = ?";
            Field identifierField = fields.get("Call Reference");

            while (true) {
                Utils.updateValue(identifierField);
                if (identifierField.getValue() == null) {
                    return;
                } else if (Utils.doesRecordExist(c, "traincall", identifierField)) {
                    break;
                }
                Utils.displayInputError("NO_RECORD");
            }

            Map<String, Object> origAttributes = Utils.loadValues(c, sqlQuery1, identifierField, fields);
            if (origAttributes.isEmpty()) { 
                return;
            } else if (!"Arrived".equals(fields.get("Status").getValue())) {
                Utils.displayError("This train call has already departed.");
                return;
            }

            normalizeTimestamps(fields, origAttributes);

            System.out.println("Loaded Call -> Train: " + fields.get("Train Reference").getValue()
                    + " | Platform: " + fields.get("Platform Reference").getValue()
                    + " | Arrived: " + fields.get("Arrival Time").getValue());

            Field depatField = fields.get("Departure Time");
            List<Field> editableFields = List.of(depatField);
            
            while (true) {
                List<String> options = Utils.createModifiableMenu(editableFields);
                int choice = Utils.getIntChoice("Enter Departure Time, then choose 'Confirm':", options.toArray(new String[0]));

                if (choice == 0) { // departure Time
                    Utils.updateValue(depatField);
                } else if (choice == 1) { // reset
                    depatField.setValue(null);
                } else if (choice == 2) { // confirm
                    if (!Utils.checkAllFields(editableFields)) 
                        continue;

                    // validate depat is after arrat
                    if (!isValidTimeRange(fields.get("Arrival Time"), depatField)) {
                        continue;
                    }

                    // set status to Departed
                    Field statusField = fields.get("Status");
                    statusField.setValue("Departed");
                    Field revnoField = fields.get("Revision");

                    String updateSql = "UPDATE traincall SET depat = ?, status = ?, revno = revno + 1 WHERE callno = ? AND revno = ?";
                    if (Utils.modifyTable(c, updateSql, List.of(depatField, statusField, identifierField, revnoField))) {
                        System.out.println("Departure successfully recorded!");
                        return;
                    }
                } else if (choice == 3) { // Return
                    return;
                }
            }
            
            // PreparedStatement ps = c.prepareStatement(sql);
            // ps.setString(1, lol);

            // try (ResultSet result = ps.executeQuery()) {
            //     if (result.next()) {
            //         String callno = result.getString("callno");
            //         String trainno = result.getString("trainno");
            //         String platno = result.getString("platno");
            //         String status = result.getString("status");

            //         System.out.println("Call Reference: " + callno);
            //         System.out.println("Train Reference: " + trainno);
            //         System.out.println("Platform Reference: " + platno);
            //         System.out.println("Status: " + status);

            //         System.out.println("wat u wanna update?");
            //         System.out.println("1. Status");
            //         System.out.println("2. Departed At");
                    
            //         int choice = scanner.nextInt();

            //         String sqlQuery = "UPDATE traincall SET ";
            //         if (choice == 1) {
            //             sqlQuery = sqlQuery + "status = 'Departed' WHERE callno = " + lol;
            //             PreparedStatement ps2 = c.prepareStatement(sqlQuery);
            //             int rows = ps2.executeUpdate();
            //             if (rows > 0) {
            //                 System.out.println("Status updated successfully.");
            //             }
            //         } else if (choice == 2) {
            //             System.out.println("Enter Date");
            //             String newDate = scanner.nextLine();
            //             sqlQuery = sqlQuery + "depat = ? WHERE callno = " + lol;
            //             PreparedStatement ps2 = c.prepareStatement(sqlQuery);
            //             ps2.setString(1, newDate);

            //             int rows = ps2.executeUpdate();
            //             if (rows > 0) {
            //                 System.out.println("DepAt updated successfully.");
            //             }
            //         }
            //     } else {
            //         System.out.println("Record not found.");
            //     }
            // }
        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
        // scanner.close();
    }

    private void editTrainCall() {
        Map<String, Field> fields = createCallFields();

        try (Connection c = Utils.getSqlConnection()) {
            String sqlQuery1 = "SELECT * FROM traincall where callno = ?";
            Field identifierField = fields.get("Call Reference");

            while (true) {
                Utils.updateValue(identifierField);
                if (identifierField.getValue() == null) {
                    return;
                } if (Utils.doesRecordExist(c, "traincall", identifierField)) {
                    break;
                }
                Utils.displayInputError("NO_RECORD");
            }

            Map<String, Object> origAttributes = Utils.loadValues(c, sqlQuery1, identifierField, fields);
            if (origAttributes.isEmpty())
                return;

            normalizeTimestamps(fields, origAttributes);

            String currentStatus = (String) fields.get("Status").getValue();
            System.out.println("Editing Call -> " + identifierField.getValue()
                    + " | Train: " + fields.get("Train Reference").getValue()
                    + " | Platform: " + fields.get("Platform Reference").getValue()
                    + " | Status: " + currentStatus);
            
            Field arratField = fields.get("Arrival Time");
            Field depatField = fields.get("Departure Time");
            Field remarksField = fields.get("Remarks");
            Field revnoField = fields.get("Revision");
            List<Field> editableFields = new ArrayList<>();
            editableFields.add(arratField);
            if ("Departed".equals(currentStatus)) {
                editableFields.add(depatField);
            }
            editableFields.add(remarksField);
            
            while (true) {
                List<String> options = Utils.createModifiableMenu(editableFields);
                int choice = Utils.getIntChoice("Modify Train Call details, then choose 'Confirm':", options.toArray(new String[0]));
                int totalFields = editableFields.size();

                if (choice == totalFields) { // reset
                    for (Field f : editableFields) {
                        f.setValue(origAttributes.get(f.getLabel()));
                    }
                } else if (choice == totalFields + 1) { // cibfurn
                    // arrival time is always required
                    // departure time is required only if Departed
                    List<Field> requiredFields = new ArrayList<>();
                    requiredFields.add(arratField);
                    if ("Departed".equals(currentStatus)) {
                        requiredFields.add(depatField);
                    }
                    if (!Utils.checkAllFields(requiredFields)) 
                        continue;

                    // validate arrat and depat inputs if Departed
                    if (!isValidTimeRange(arratField, depatField)) {
                        continue;
                    }

                    String sqlQuery2 = "UPDATE traincall SET arrat = ?, depat = ?, remarks = ?, revno = revno + 1 WHERE callno = ? AND revno = ?";
                    List<Field> updateValues = List.of(arratField, depatField, remarksField, identifierField, revnoField);

                    if (Utils.modifyTable(c, sqlQuery2, updateValues)) {
                        System.out.println("Train Call successfully updated!");
                        return;
                    }
                } else if (choice == totalFields + 2) { // return
                    return;
                } else {
                    Field selectedField = editableFields.get(choice);
                    handleTrainCallInput(c, selectedField);
                }
            }
        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
    }

    private void deleteTrainCall() {
        Map<String, Field> fields = createCallFields();
        
        try (Connection c = Utils.getSqlConnection()) {
            String sqlQuery1 = "SELECT * FROM traincall where callno = ?";
            Field identifierField = fields.get("Call Reference");

            while (true) {
                Utils.updateValue(identifierField);
                if (identifierField.getValue() == null) {
                    return;
                } else if (Utils.doesRecordExist(c, "traincall", identifierField)) {
                    break;
                }
                Utils.displayInputError("NO_RECORD");
            }

            Map<String, Object> origAttributes = Utils.loadValues(c, sqlQuery1, identifierField, fields);
            if (origAttributes.isEmpty()) { 
                return;
            } else if (!"Arrived".equals(fields.get("Status").getValue())) {
                Utils.displayError("This train call has already departed.");
                return;
            }

            normalizeTimestamps(fields, origAttributes);

            System.out.println("Loaded Call -> Train: " + fields.get("Train Reference").getValue()
                    + " | Platform: " + fields.get("Platform Reference").getValue()
                    + " | Arrived: " + fields.get("Arrival Time").getValue());

            String[] choices = {"Yes", "No"};
            String confirm = Utils.displayMenu("Are you sure you want to delete this record?", choices);
            if (confirm.equalsIgnoreCase("No")) {
                return;
            }

            if (Utils.safeDeleteRecord(c, "traincall", null, identifierField)) {
                System.out.println("Train Call successfully deleted!");
            }
        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
        
        // try (Connection c = Utils.getSqlConnection()) {
        //     Scanner scanner = new Scanner(System.in);
        //     System.out.print("Enter Call Reference: ");
        //     String filter = scanner.nextLine();

        //     String sql = "DELETE FROM traincall where callno = ?";

        //     try (PreparedStatement ps = c.prepareStatement(sql)) {
        //         ps.setString(1, filter);
        //         int rows = ps.executeUpdate();

        //         if (rows > 0) {
        //             System.out.println("Traincall has been deleted!");
        //         } else {
        //             System.out.println("No record found.");
        //         }
        //     }
        //     scanner.close();
        // } catch (SQLException e) {
        //     Utils.displayDatabaseError(null, e);
        // }
    }

    private void generateReport3() {
        String sql = "SELECT s.stncode, s.stnname, p.platno, p.platname, p.directn, p.capacity, tc.status, " +
                     "COUNT(tc.callno) AS callcount, COUNT(DISTINCT tc.trainno) AS traincount, " +
                     "MIN(tc.arrat) AS earliestarrival, MAX(tc.depat) AS latestdeparture " +
                     "FROM traincall tc " +
                     "JOIN platform p ON tc.platno = p.platno " +
                     "JOIN station s ON p.stncode = s.stncode " +
                     "WHERE DATE(tc.arrat) >= ? AND DATE(tc.arrat) <= ? ";
        String groupBy = "GROUP BY s.stncode, s.stnname, p.platno, p.platname, p.directn, p.capacity, tc.status ";
        String tail = "ORDER BY callcount DESC, s.stncode, p.platno, tc.status;";
        List<Filter> mandatory = List.of(
            new Filter("tc.arrat", "Start Arrival Date", "DATE", ">=", false),
            new Filter("tc.arrat", "End Arrival Date", "DATE", "<=", false)
        );
        List<Filter> optional = List.of(
            new Filter("s.stncode", "Station Code", "VARCHAR(8)", "LIKE", false),
            new Filter("p.platno", "Platform Reference", "VARCHAR(12)", "LIKE", false),
            new Filter("p.directn", "Direction", "VARCHAR(15)", new String[]{"Northbound", "Southbound"}, "=", false),
            new Filter("tc.status", "State / Status", "VARCHAR(15)", new String[]{"Arrived", "Departed"}, "=", false),
            new Filter("callcount", "Minimum Call Count", "INT UNSIGNED", ">=", true)
        );
        try (Connection c = Utils.getSqlConnection()){
            Utils.displaySqlResult(Utils.sqlSearch(c, sql, groupBy, tail, mandatory, optional));
        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
    }


    // ------------------------------------------------------------


    // helper functions
    private void handleTrainCallInput(Connection c, Field field) {
        switch (field.getName()) {
            case "callno" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (!Utils.doesRecordExist(c, "traincall", field)) break;
                    Utils.displayInputError("IDENTIFIER");
                }
            }
            case "trainno" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (Utils.doesRecordExist(c, "train", field)) break;
                    Utils.displayInputError("NO_RECORD");
                }
            }
            case "platno" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (Utils.doesRecordExist(c, "platform", field)) break;
                    Utils.displayInputError("NO_RECORD");
                }
            }
            default -> Utils.updateValue(field);
        }
    }

    private Map<String, Field> createCallFields() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("Call Reference", new Field("callno", "Call Reference", "VARCHAR(16)"));
        fields.put("Train Reference", new Field("trainno", "Train Reference", "VARCHAR(12)"));
        fields.put("Platform Reference", new Field("platno", "Platform Reference", "VARCHAR(12)"));
        fields.put("Arrival Time", new Field("arrat", "Arrival Time", "DATETIME"));
        fields.put("Departure Time", new Field("depat", "Departure Time", "DATETIME"));
        fields.put("Status", new Field("status", "Status", "VARCHAR(15)"));
        fields.put("Remarks", new Field("remarks", "Remarks", "VARCHAR(255)"));
        fields.put("Revision", new Field("revno", "Revision", "INT"));
        return fields;
    }

    private Timestamp toTimestamp(Object val) {
        if (val instanceof Timestamp ts) 
            return ts;
        if (val instanceof java.time.LocalDateTime ldt) 
            return Timestamp.valueOf(ldt);
        
        return null;
    }

    private void normalizeTimestamps(Map<String, Field> fields, Map<String, Object> origAttributes) {
        String[] timeKeys = {"Arrival Time", "Departure Time"};
        for (String key : timeKeys) {
            Field f = fields.get(key);
            if (f != null && f.getValue() != null) {
                Timestamp ts = toTimestamp(f.getValue());
                f.setValue(ts);
                if (origAttributes != null) {
                    origAttributes.put(key, ts);
                }
            }
        }
    }

    private boolean isValidTimeRange(Field arratField, Field depatField) {
        Timestamp arrat = toTimestamp(arratField.getValue());
        Timestamp depat = toTimestamp(depatField.getValue());
        if (arrat != null && depat != null && !depat.after(arrat)) {
            Utils.displayError("Departure time must be after arrival time (" + arrat + ").");
            return false;
        }
        return true;
    }
}
