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
                "Exit"
        };
        while (isRunning) {
            switch (Utils.displayMenu("Select Option: ", options)) {
                case "Create Train Call" -> createTrainCall();
                case "Search Train Call" -> searchTrainCall();
                case "Record Departure" -> recordDeparture();
                case "Edit Train Call" -> editTrainCall();
                case "Delete Train Call" -> deleteTrainCall();
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
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("Call Reference", new Field("callno", "Call Reference", "VARCHAR(16)"));
        fields.put("Train Reference", new Field("trainno", "Train Reference", "VARCHAR(12)"));
        fields.put("Platform Reference", new Field("platno", "Platform Reference", "VARCHAR(12)"));
        fields.put("Arrival Time", new Field("arrat", "Arrival Time", "DATETIME"));
        fields.put("Departure Time", new Field("depat", "Departure Time", "DATETIME"));
        fields.put("Status", new Field("status", "Status", "VARCHAR(15)"));
        fields.put("Revision", new Field("revno", "Revision", "INT"));

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
                    Timestamp arrat = toTimestamp(fields.get("Arrival Time").getValue());
                    Timestamp depat = toTimestamp(depatField.getValue());
                    if (!depat.after(arrat)) {
                        Utils.displayError("Departure time must be after arrival time (" + arrat + ").");
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

    }

    private void deleteTrainCall() {

    }

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

    private Timestamp toTimestamp(Object val) {
        if (val instanceof Timestamp ts) 
            return ts;
        if (val instanceof java.time.LocalDateTime ldt) 
            return Timestamp.valueOf(ldt);
        
        return null;
    }
}
