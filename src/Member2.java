import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * Member 2: Train management and supporting Service tasks
 * <p>
 * Member 2 owns train creation, search, retrieval, update, and controlled deletion. Compact
 * Service-task CRUD is integrated into the selected train workspace. Train creation requires its
 * operational reference, label, type, and all applicable specific attributes. Activation and revision
 * begin at 1; inapplicable attributes remain blank.
 * </p>
 * <p>
 * Search supports reference, label, type, activation, and principal Service duty, where applicable.
 * Selection loads saved values, visit references, and Service tasks if eligible. Ordinary edits protect
 * the reference. Current specification corrections must not imply that earlier visits carried a
 * calculated passenger load. Type changes are blocked once calls or tasks depend on the train.
 * Confirmed deletion requires no calls or tasks.
 * </p>
 * <p>
 * Task entry offers Service trains only, requires reference, actual date, category, positive duration,
 * and details, and rechecks eligibility on save. Search supports task reference, selected train, task
 * date, and category. Loading precedes correction; ordinary edits protect task identity and train
 * attribution. Confirmed deletion is limited to an erroneous task, not routine removal of completed
 * operational history. Member 2 does not create calls merely because a task was performed.
 * </p>
 *
 * @author Richmond Salvador
 */
public class Member2 implements Application {

    @Override
    public void menu() {
        String[] options = {
                "Manage Trains",
                "Manage Support Tasks",
                "Exit"
        };
        String prompt = "Choose which category you want to manage:";
        switch (Utils.displayMenu(prompt, options)) {
            case 0:
                trainMenu();
                break;
            case 1:
                taskMenu();
                break;
        }
    }

    private void trainMenu() {
        while (true) {
            String prompt = "Which operation do you wish to do:";
            String[] options = {
                    "Create new train record",
                    "Search the train database",
                    "Edit an existing train record",
                    "Delete an existing train record",
                    "Go back"
            };
            switch (Utils.displayMenu(prompt, options)) {
                case 0 -> createTrain();
                case 1 -> {
                    String sql = "SELECT t.trainno, t.label, t.traintyp, p.cars, p.paxcap, s.dutytype, s.loadton, t.active " +
                                 "FROM train t LEFT JOIN passengertrain p ON t.trainno = p.trainno " +
                                 "LEFT JOIN servicetrain s ON t.trainno = s.trainno";
                    List<Filter> filters = List.of(
                            new Filter("t.trainno", "Train Reference", null),
                            new Filter("t.label", "Train Label", null),
                            new Filter("t.traintyp", "Train Type", new String[]{"Passenger", "Service"}),
                            new Filter("t.active", "Train Activation Status", new String[]{"1", "0"}),
                            new Filter("s.dutytype", "Train Duty Type", new String[]{"Inspection", "Maintenance", "Recovery"})
                    );
                    Utils.displaySqlResult(Utils.sqlSearch("Trains", sql, filters));
                }
                case 2 -> updateTrain();
                case 3 -> deleteTrain();
                default -> {
                    return;
                }
            }
        }
    }

    private void taskMenu() {
        while (true) {
            String prompt = "Which operation do you wish to do:";
            String[] options = {
                    "Create new service task record",
                    "Search the service task database",
                    "Edit an existing service task record",
                    "Delete an existing service task record",
                    "Go back"
            };
            switch (Utils.displayMenu(prompt, options)) {
                case 0 -> createTask();
                // Search
                case 1 -> {
                    String sql = "SELECT s.taskno, s.taskdate, s.tasktype, s.hours, t.trainno, t.dutytype, t.loadton, s.details " +
                            "FROM servicetask s JOIN servicetrain t ON s.trainno = t.trainno ";
                    List<Filter> filters = List.of(
                            new Filter("s.taskno", "Task Reference", null),
                            new Filter("t.trainno", "Train Reference", null),
                            new Filter("s.taskdate", "Task Date", null),
                            new Filter("s.tasktype", "Task Type", new String[]{"Inspection", "Maintenance", "Recovery"})
                    );
                    Utils.displaySqlResult(Utils.sqlSearch("Trains", sql, filters));
                }
                case 2 -> updateTask();
                case 3 -> deleteTask();
                default -> {
                    return;
                }
            }
        }
    }

    private void updateTrain() {
        Scanner sc = new Scanner(System.in);
        String rec;

        // get the record to be updated
        do {
            System.out.print("Input the Train Reference of the record to be edited (blank to return): ");
            rec = sc.nextLine().trim();
            if (rec.isEmpty()) return;
            if (!Utils.isValidInput(rec, "VARCHAR(12)")) continue;
            if (Utils.doesRecordExist("train", "trainno", rec)) break;
            Utils.displayInputError("NO_RECORD");
        } while (true);

        try {
            // load values
            ResultSet res = (retrieveTrainRecord(rec));
            ResultSetMetaData data = res.getMetaData();
            int n = data.getColumnCount();
            String[] values = new String[n];
            for (int i = 1; i <= n; i++) values[i - 1] = res.getString(i);
            String[] orig = Arrays.copyOf(values, n);

            String[] labels = {
                    "Train Label", "Train Type",
                    "Number of Cars", "Passenger Capacity",
                    "Principal Duty", "Payload Capacity",
                    "Train Activation"
            };

            while (true) {
                // create menu
                List<String> menu = Utils.createModifiableMenu(labels, Arrays.copyOfRange(values, 1, n));
                int index = 4;
                if (!"Passenger".equalsIgnoreCase(values[2])) {
                    menu.remove(2);
                    menu.remove(2);
                    index -= 2;
                }
                if (!"Service".equalsIgnoreCase(values[2])) {
                    menu.remove(index);
                    menu.remove(index);
                }

                int choice = Utils.displayMenu("Modify fields to be updated, then choose 'Confirm' afterwards:", menu.toArray(new String[0]));
                int num = menu.size() - 1;

                if (choice == num) return; // return
                else if (choice == num - 1) { // confirm
                    saveTrainUpdate(values, !orig[2].equalsIgnoreCase(values[2]));
                    return;
                }
                else if (choice == num - 2) values = Arrays.copyOf(orig, n); // reset
                else { // modify
                    choice += 1;
                    if (num == 5 && choice == 3) handleTrainInput(5, values);
                    else handleTrainInput(choice, values);
                }
            }
        } catch (SQLException _) {}
    }

    private void saveTrainUpdate(String[] values, boolean hasChangedType) {
        try {
            Connection c = Utils.getSqlConnection();
            String sql = "UPDATE train SET label  = ?, traintyp = ?, active = ?, revno = revno + 1 WHERE trainno = ? AND revno = ?;";
            Object[] insert = {values[1], values[2], Integer.parseInt(values[7]), values[0], Integer.parseInt(values[8])};
            SQLType[] types = {JDBCType.VARCHAR, JDBCType.VARCHAR, JDBCType.TINYINT, JDBCType.VARCHAR, JDBCType.INTEGER};
            PreparedStatement ps = Utils.prepareStatement(c, sql, insert, types);

            // update
            int rows = ps.executeUpdate();
            if (rows == 0) {
                Utils.displayDatabaseError("REVISION", null);
                return;
            }

            if ("Passenger".equalsIgnoreCase(values[2])) {
                sql = "UPDATE passengertrain SET cars = ?, paxcap = ?, revno = revno + 1 WHERE trainno = ? AND revno = ?;";
                insert = new Object[]{Integer.parseInt(values[3]), Integer.parseInt(values[4]), values[0], Integer.parseInt(values[9])};
                types = new SQLType[]{JDBCType.INTEGER, JDBCType.INTEGER, JDBCType.VARCHAR, JDBCType.INTEGER};
                ps = Utils.prepareStatement(c, sql, insert, types);

                // update passenger db
                rows = ps.executeUpdate();
                if (rows == 0) Utils.displayDatabaseError("REVISION", null);

                // delete other record if the type changed
                if (hasChangedType)
                    Utils.safeDeleteRecord("servicetrain", new String[] {"traincall", "servicetask"}, "trainno", values[0]);
            } else if ("Service".equalsIgnoreCase(values[2])) {
                sql = "UPDATE servicetrain SET dutytype = ?, loadton = ?, revno = revno + 1 WHERE trainno = ? AND revno = ?;";
                insert = new Object[]{values[5], Double.parseDouble(values[6]), values[0], Integer.parseInt(values[9])};
                types = new SQLType[]{JDBCType.VARCHAR, JDBCType.DECIMAL, JDBCType.VARCHAR, JDBCType.INTEGER};
                ps = Utils.prepareStatement(c, sql, insert, types);

                // update service db
                rows = ps.executeUpdate();
                if (rows == 0) Utils.displayDatabaseError("REVISION", null);

                // delete other record if the type changed
                if (hasChangedType)
                    Utils.safeDeleteRecord("passengertrain", new String[] {"traincall"}, "trainno", values[0]);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private ResultSet retrieveTrainRecord(String trainRef) {
        Connection c = Utils.getSqlConnection();
        if (c == null) return null;
        try {
            String sql = "SELECT t.trainno, t.label, t.traintyp, p.cars, p.paxcap, s.dutytype, s.loadton, t.active, t.revno, p.revno, s.revno " +
                         "FROM train t LEFT JOIN passengertrain p ON t.trainno = p.trainno LEFT JOIN servicetrain s ON t.trainno = s.trainno " +
                         "WHERE t.trainno = ?;";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, trainRef);
            ResultSet res = ps.executeQuery();
            res.next();
            return res;
        } catch (SQLException e) {
            Utils.displayInputError("NO_RECORD");
            return null;
        }
    }

    private void updateTask() {
        Scanner sc = new Scanner(System.in);
        String rec;

        // get the record to be updated
        do {
            System.out.print("Input the Task Reference of the record to be edited (blank to return): ");
            rec = sc.nextLine().trim();
            if (rec.isEmpty()) return;
            if (!Utils.isValidInput(rec, "VARCHAR(16)")) continue;
            if (Utils.doesRecordExist("servicetask", "taskno", rec)) break;
            Utils.displayInputError("NO_RECORD");
        } while (true);

        try {
            // load values
            ResultSet res = retrieveTask(rec);
            int n = res.getMetaData().getColumnCount();
            String[] values = new String[n];
            String[] labels = {"Task Date", "Task Type", "Duration in Hours", "Details"};
            for (int i = 0; i < n; i++) values[i] = res.getString(i + 1);
            String[] orig = Arrays.copyOf(values, n);

            while (true) {
                // Create menu
                List<String> menu = Utils.createModifiableMenu(labels, Arrays.copyOfRange(values, 2, n));
                int choice = Utils.displayMenu("Modify fields to be updated, then choose 'Confirm' afterwards:", menu.toArray(new String[0]));
                int num = menu.size() - 1;

                if (choice == num) return; // return
                else if (choice == num - 1) {
                    saveTaskUpdate(values); // confirm
                    return;
                }
                else if (choice == num - 2) values = Arrays.copyOf(orig, n); // reset
                else handleTaskInput(choice + 2, values); // modify
            }
        } catch (SQLException _) {}
    }

    private void saveTaskUpdate(String[] values) {
        try {
            Connection c = Utils.getSqlConnection();
            String sql = "UPDATE servicetask SET taskdate = ?, tasktype = ?, hours = ?, details = ?, revno = revno + 1 WHERE taskno = ? AND revno = ?;";
            Object[] insert = {Date.valueOf(values[2]), values[3], Double.parseDouble(values[4]), values[5], values[0], Integer.parseInt(values[6])};
            JDBCType[] types = {JDBCType.DATE, JDBCType.VARCHAR, JDBCType.DECIMAL, JDBCType.VARCHAR, JDBCType.VARCHAR, JDBCType.INTEGER};
            PreparedStatement ps = Utils.prepareStatement(c, sql, insert, types);

            int rows = ps.executeUpdate();
            if (rows == 0) Utils.displayDatabaseError("REVISION", null);

            // close resources
            c.close();
            ps.close();
        } catch (SQLException e) {
            Utils.displayDatabaseError(null, e);
        }
    }

    private ResultSet retrieveTask(String val) {
        Connection c = Utils.getSqlConnection();
        if (c == null) return null;
        try {
            String sql = "SELECT * FROM servicetask WHERE taskno = ?;";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, val);
            ResultSet res = ps.executeQuery();
            res.next();
            return res;
        } catch (SQLException e) {
            Utils.displayInputError("NO_RECORD");
            return null;
        }
    }

    private void deleteTrain() {
        Scanner sc = new Scanner(System.in);
        String type;

        do {
            System.out.print("Input the Train Reference of the record you wish to delete (blank to return): ");
            String in = sc.nextLine().trim();
            if (in.isEmpty()) return;
            if (!Utils.isValidInput(in, "VARCHAR(16)")) continue;

            // get the type of the train
            if (Utils.doesRecordExist("passengertrain", "trainno", in)) {
                if (Utils.safeDeleteRecord("passengertrain", new String[] {"traincall"}, "trainno", in))
                    if (Utils.safeDeleteRecord("train", null, "trainno", in)) break;
            }
            else if (Utils.doesRecordExist("servicetrain", "trainno", in)) {
                if (Utils.safeDeleteRecord("servicetrain", new String[] {"servicetask"}, "trainno", in))
                    if (Utils.safeDeleteRecord("train", null, "trainno", in)) break;
            }
            else {
                if (Utils.safeDeleteRecord("train", new String[] {"traincall", "servicetask"}, "trainno", in)) break;
            }
        } while (true);
    }

    private void deleteTask() {
        Scanner sc = new Scanner(System.in);
        do {
            System.out.print("Input the Task Reference of the record you wish to delete (blank to return): ");
            String in = sc.nextLine().trim();
            if (in.isEmpty()) return;
            if (Utils.isValidInput(in, "VARCHAR(16)"))
                if (Utils.safeDeleteRecord("servicetask", null, "taskno", in))
                    break;
        } while (true);
    }

    private void createTrain() {
        String[] values = new String[7];
        String[] labels = {"Train Reference", "Train Label", "Train Type", "Number of Cars", "Passenger Capacity", "Principal Duty", "Payload Capacity"};

        while (true) {
            // create menu
            List<String> menu = Utils.createModifiableMenu(labels, values);
            int index = 5;
            if (!"Passenger".equalsIgnoreCase(values[2])) {
                menu.remove(3);
                menu.remove(3);
                index -= 2;
            }
            if (!"Service".equalsIgnoreCase(values[2])) {
                menu.remove(index);
                menu.remove(index);
            }

            int choice = Utils.displayMenu("Modify every field, then choose 'Confirm' afterwards:", menu.toArray(new String[0]));
            int num = menu.size() - 1;

            if (choice == num) return; // return
            else if (choice == num - 1) { // confirm
                saveTrainRecord(values);
                return;
            }
            else if (choice == num - 2) Arrays.fill(values, null); // reset
            else handleTrainInput(choice, values); // modify
        }
    }

    private void handleTrainInput(int choice, String[] values) {
        Scanner sc = new Scanner(System.in);
        String in;

        switch(choice) {
            case 0 -> { // Train Reference
                do {
                    System.out.print("Enter unique Train Reference: ");
                    in = sc.nextLine().trim();
                    if (Utils.doesRecordExist("train", "trainno", in))
                        Utils.displayInputError("IDENTIFIER");
                    else if (Utils.isValidInput(in, "VARCHAR(12)")) break;
                } while (true);
                values[0] = in.isEmpty() ? null : in;
            }
            case 1 -> { // Train Label
                do {
                    System.out.print("Enter Train Label: ");
                    in = sc.nextLine().trim();
                    if (in.isEmpty()) break;
                } while (!Utils.isValidInput(in, "VARCHAR(80)"));
                values[1] = in.isEmpty() ? null : in;
            }
            case 2 -> { // Train Type
                // only able to change if identifier has no reference with other tables
                if (values[0] != null) {
                    if (Utils.doesRecordExist("traincall", "trainno", values[0]) || Utils.doesRecordExist("servicetask", "trainno", values[0])) {
                        Utils.displayInputError("REFERENCE");
                        return;
                    }
                }

                String[] choices = {"Passenger", "Service", "Reset"};
                int sel = Utils.displayMenu("Choose which type of train it would be:", choices);
                switch (sel) {
                    case 0 -> { // passenger train
                        values[2] = choices[0];
                        values[5] = null;
                        values[6] = null;
                    }
                    case 1 -> { // service train
                        values[2] = choices[1];
                        values[3] = null;
                        values[4] = null;
                    }
                    default -> values[2] = null;
                }
                values[2] = sel == choices.length - 1 ? null : choices[sel];
            }
            case 3 -> { // cars or duty
                if ("Passenger".equals(values[2])) {
                    do {
                        System.out.print("Enter the amount of passenger cars: ");
                        in = sc.nextLine().trim();
                        if (in.isEmpty()) break;
                    } while(!Utils.isValidInput(in, "INT UNSIGNED"));
                    values[3] = in.isEmpty() ? null : in;
                } else if ("Service".equals(values[2])) {
                    String[] duties = {"Inspection", "Maintenance", "Recovery", "Reset"};
                    int sel = Utils.displayMenu("Choose the duty of this train: ", duties);
                    values[5] = sel == duties.length - 1 ? null : duties[sel];
                }
            }
            case 4 -> { // passenger or service capacity
                if ("Passenger".equals(values[2])) {
                    do {
                        System.out.print("Enter the maximum passenger capacity: ");
                        in = sc.nextLine().trim();
                        if (in.isEmpty()) break;
                    } while(!Utils.isValidInput(in, "INT UNSIGNED"));
                    values[4] = in.isEmpty() ? null : in;
                } else if ("Service".equals(values[2])) {
                    do {
                        System.out.print("Enter the maximum load capacity: ");
                        in = sc.nextLine().trim();
                        if (in.isEmpty()) break;
                    } while(!Utils.isValidInput(in, "DECIMAL(8,2) UNSIGNED"));
                    values[6] = in.isEmpty() ? null : in;
                }
            }
            case 5 -> { // Train activation
                String[] status = {"1", "0", "Reset"};
                int sel = Utils.displayMenu("Choose the Activation Status of this Train: ", status);
                values[7] = sel == status.length - 1 ? null : status[sel];
            }
        }
    }

    private void saveTrainRecord (String[] values) {
        try {
            Connection c = Utils.getSqlConnection();
            if (c == null) return;

            final String insertTrain = "INSERT INTO train (trainno, label, traintyp, active, revno) VALUES (?, ?, ?, 1, 1);";
            PreparedStatement ps = c.prepareStatement(insertTrain);
            for (int i = 0; i < 3; i++) ps.setString(i + 1, values[i]);
            ps.execute();

            if ("Passenger".equalsIgnoreCase(values[2])) {
                final String insertPassenger = "INSERT INTO passengertrain (trainno, cars, paxcap, revno) VALUES (?, ?, ?, 1);";
                ps = c.prepareStatement(insertPassenger);
                ps.setString(1, values[0]);
                ps.setInt(2, Integer.parseInt(values[3]));
                ps.setInt(3, Integer.parseInt(values[4]));
                ps.execute();
            }
            else if ("Service".equalsIgnoreCase(values[2])) {
                final String insertService = "INSERT INTO servicetrain (trainno, dutytype, loadton, revno) VALUES (?, ?, ?, 1);";
                ps = c.prepareStatement(insertService);
                ps.setString(1, values[0]);
                ps.setString(2, values[5]);
                ps.setDouble(3, Double.parseDouble(values[6]));
                ps.execute();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createTask() {
        String[] labels = {"Task Reference", "Train Reference", "Task Date", "Task Type", "Duration in Hours", "Details"};
        String[] values = new String[6];

        while (true) {
            // Create menu
            List<String> menu = Utils.createModifiableMenu(labels, values);
            int choice = Utils.displayMenu("Modify every field, then choose 'Confirm' afterwards:", menu.toArray(new String[0]));
            int num = menu.size() - 1;

            if (choice == num) return; // return
            else if (choice == num - 1) { // confirm
                saveTaskRecord(values);
                return;
            }
            else if (choice == num - 2) Arrays.fill(values, null); // reset
            else handleTaskInput(choice, values); // modify
        }
    }

    private void handleTaskInput(int choice, String[] values) {
        Scanner sc = new Scanner(System.in);
        String in;
        switch (choice) {
            case 0 -> { // task reference
                do {
                    System.out.print("Enter unique Task Reference: ");
                    in = sc.nextLine().trim();
                    if (Utils.doesRecordExist("servicetask", "taskno", in))
                        Utils.displayInputError("IDENTIFIER");
                    else if (Utils.isValidInput(in, "VARCHAR(16)")) break;
                } while (true);
                values[0] = in.isEmpty() ? null : in;
            }
            case 1 -> { // train reference
                do {
                    System.out.print("Enter Service Train Reference: ");
                    in = sc.nextLine().trim();
                    if (in.isEmpty()) break;
                    else if (!Utils.doesRecordExist("servicetrain", "trainno", in))
                        Utils.displayInputError("NO_RECORD");
                    else if (Utils.isValidInput(in, "VARCHAR(12)")) break;
                } while (true);
                values[1] = in.isEmpty() ? null : in;
            }
            case 2 -> { // task date
                do {
                    System.out.print("Enter the date of this task (YYYY-MM-DD): ");
                    in = sc.nextLine();
                    if (in.isEmpty()) break;
                } while (!Utils.isValidInput(in, "DATE"));
                values[2] = in.isEmpty() ? null : in;
            }
            case 3 -> { // task type
                String[] duties = {"Inspection", "Maintenance", "Recovery", "Reset"};
                int sel = Utils.displayMenu("Choose the type of this task: ", duties);
                values[3] = sel == duties.length - 1 ? null : duties[sel];
            }
            case 4 -> { // duration
                do {
                    System.out.print("Enter the duration of this task in hours: ");
                    in = sc.nextLine();
                    if (in.isEmpty()) break;
                } while (!Utils.isValidInput(in, "DECIMAL(8,2) UNSIGNED"));
                values[4] = in.isEmpty() ? null : in;
            }
            case 5 -> { // details
                do {
                    System.out.print("Enter details for task: ");
                    in = sc.nextLine();
                    if (in.isEmpty()) break;
                } while (!Utils.isValidInput(in, "VARCHAR(255)"));
                values[5] = in.isEmpty() ? null : in;
            }
        }
    }

    private void saveTaskRecord(String[] values) {
        try {
            Connection c = Utils.getSqlConnection();
            if (c == null) return;

            String sql = "INSERT INTO servicetask (taskno, trainno, taskdate, tasktype, hours, details, revno) VALUES (?, ?, ?, ?, ?, ?, 1);";
            PreparedStatement ps = c.prepareStatement(sql);

            ps.setString(1, values[0]);
            ps.setString(2, values[1]);
            ps.setDate(3, Date.valueOf(values[2]));
            ps.setString(4, values[3]);
            ps.setDouble(5, Double.parseDouble(values[4]));
            ps.setString(6, values[5]);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
