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
        try (Connection c = Utils.getSqlConnection()){
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
            new Filter("tc.callno", "Call Reference", "VARCHAR(16)", null, "LIKE", false),    
            new Filter("tc.trainno", "Train Reference", "VARCHAR(12)", null, "LIKE", false),
            new Filter("p.stncode", "Station Code", "VARCHAR(8)", null, "LIKE", false),
            new Filter("tc.platno", "Platform Reference", "VARCHAR(12)", null, "LIKE", false),
            new Filter("p.directn", "Direction", "VARCHAR(15)", new String[]{"Northbound", "Southbound"}, "=", false),
            new Filter("tc.status", "State / Status", "VARCHAR(15)", new String[]{"Arrived", "Departed"}, "=", false),
            new Filter("tc.arrat", "Start Arrival Date", "DATE", null, ">=", false),
            new Filter("tc.arrat", "End Arrival Date", "DATE", null, "<=", false)
        );
        try (Connection c = Utils.getSqlConnection()){
            
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


}
