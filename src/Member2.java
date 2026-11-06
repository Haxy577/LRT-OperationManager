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
        while (true) {
            String[] options = {
                    "Manage Trains",
                    "Manage Support Tasks",
                    "Exit"
            };
            String prompt = "Choose which category you want to manage:";
            switch (Utils.displayMenu(prompt, options)) {
                case "Manage Trains" -> trainMenu();
                case "Manage Support Tasks" -> taskMenu();
                default -> {return;}
            }
        }
    }

    private void trainMenu() {
        while (true) {
            String prompt = "Which operation do you wish to do:";
            String[] options = {"Create Train", "Search Train", "Edit Train", "Delete Train", "Return"};
            switch (Utils.displayMenu(prompt, options)) {
                case "Create Train" -> createTrain();
                case "Search Train" -> searchTrain();
//                case "Edit Train" -> updateTrain();
                case "Delete Train" -> deleteTrain();
                default -> {return;}
            }
        }
    }

    private void taskMenu() {
        while (true) {
            String prompt = "Which operation do you wish to do:";
            String[] options = {"Create Task", "Search Task", "Edit Task", "Delete Task", "Return"};
            switch (Utils.displayMenu(prompt, options)) {
                case "Create Task" -> createTask();
                case "Search Task" -> searchTask();
                case "Edit Task" -> updateTask();
                case "Delete Task" -> deleteTask();
                default -> {return;}
            }
        }
    }

    private void searchTrain() {
        String sql = "SELECT t.trainno, t.label, t.traintyp, p.cars, p.paxcap, s.dutytype, s.loadton, t.active " +
                "FROM train t LEFT JOIN passengertrain p ON t.trainno = p.trainno " +
                "LEFT JOIN servicetrain s ON t.trainno = s.trainno WHERE 1=1 ";
        List<Filter> filters = List.of(
                new Filter("t.trainno", "Train Reference", "VARCHAR(12)", null, "LIKE"),
                new Filter("t.label", "Train Label", "VARCHAR(80)", null, "LIKE"),
                new Filter("t.traintyp", "Train Type", "VARCHAR(12)", new String[]{"Passenger", "Service"}, "="),
                new Filter("t.active", "Train Activation Status", "TINYINT", new String[]{"1", "0"}, "="),
                new Filter("s.dutytype", "Train Duty Type", "VARCHAR(15)", new String[]{"Inspection", "Maintenance", "Recovery"}, "=")
        );
        try (Connection c = Utils.getSqlConnection()){
            Utils.displaySqlResult(Utils.sqlSearch(c,"Trains", sql, filters));
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void searchTask() {
        String sql = "SELECT s.taskno, s.taskdate, s.tasktype, s.hours, t.trainno, t.dutytype, t.loadton, s.details " +
                "FROM servicetask s JOIN servicetrain t ON s.trainno = t.trainno WHERE 1=1 ";
        List<Filter> filters = List.of(
                new Filter("s.taskno", "Task Reference", "VARCHAR(16)", null, "LIKE"),
                new Filter("t.trainno", "Train Reference", "VARCHAR(12)", null, "LIKE"),
                new Filter("s.taskdate", "Task Date", "DATE", null, "="),
                new Filter("s.tasktype", "Task Type", "VARCHAR(15)", new String[]{"Inspection", "Maintenance", "Recovery"}, "=")
        );
        try (Connection c = Utils.getSqlConnection()){
            Utils.displaySqlResult(Utils.sqlSearch(c,"Service Task", sql, filters));
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    /*private void updateTrain() {
        String prompt = "Input the Train Reference of the record to be edited (blank to return): ";
        String rec;

        // get the record to be updated
        do {
            Object in = Utils.getInput(prompt, "VARCHAR(12)");
            if (in == null) return;
            rec = in.toString();
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

                String choice = Utils.displayMenu("Modify fields to be updated, then choose 'Confirm' afterwards:", menu.toArray(new String[0]));
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
    }*/

    private void updateTask() {
        Map<String, Field> fields = new LinkedHashMap<>();
        // Add modifiable fields
        fields.put("Task Date", new Field("taskdate", "Task Date", "DATE"));
        fields.put("Task Type", new Field("tasktype", "Task Type", "VARCHAR(15)"));
        fields.put("Duration in Hours", new Field("hours", "Duration in Hours", "DECIMAL(8,2) UNSIGNED"));
        fields.put("Details", new Field("details", "Details", "VARCHAR(255)"));

        int fieldNum = fields.size();

        // add fields for search/conditions
        fields.put("Revision", new Field("revno", "Revision", "INT UNSIGNED"));
        fields.put("Task Reference", new Field("taskno", "Task Reference", "VARCHAR(16)"));

        try (Connection c = Utils.getSqlConnection()) {
            // get the record to be updated
            Field ref = fields.get("Task Reference");
            while (true) {
                Utils.updateValue(ref);
                if (ref.getValue() == null) return;
                if (Utils.doesRecordExist(c, "servicetask", ref)) break;
                Utils.displayInputError("NO_RECORD");
            }

            // load values
            final String selectSql = "SELECT taskdate, tasktype, hours, details, revno, taskno FROM servicetask WHERE taskno = ?;";
            try (PreparedStatement ps = c.prepareStatement(selectSql)){
                ps.setObject(1, ref.getValue(), ref.getSqlType());
                ResultSet res = ps.executeQuery();
                if (!res.next()) {
                    Utils.displayInputError("NO_RECORD");
                    return;
                }
                for (String k : fields.keySet()) {
                    Field f = fields.get(k);
                    f.setValue(res.getObject(f.getName()));
                }
            }

            // Store the original values
            Map<String, Object> originalValues = new LinkedHashMap<>(fields);
            for (String k : fields.keySet()) originalValues.put(k, fields.get(k).getValue());

            while (true) {
                List<String> menu = Utils.createModifiableMenu(new ArrayList<>(fields.values()).subList(0, fieldNum));
                String prompt = "Modify every fields, then choose 'Confirm' afterwards:";
                String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));

                switch (choice) {
                    case "Return" -> {return;}
                    case "Reset" -> {for (String k : fields.keySet()) fields.get(k).setValue(originalValues.get(k));}
                    case "Confirm" -> {
                        String update = "UPDATE servicetask SET taskdate = ?, tasktype = ?, hours = ?, details = ?, revno = revno + 1 WHERE revno = ? AND taskno = ?;";
                        try {
                            c.setAutoCommit(false);

                            boolean res = Utils.modifyTable(c, update, new ArrayList<>(fields.values()));
                            if (!res) c.rollback();
                            else {
                                c.commit();
                                return;
                            }
                        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
                        return;
                    }
                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        handleTaskInput(c, fields.get(key));
                    }
                }
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void deleteTrain() {
        String prompt = "Input the Train Reference of the record you wish to delete (blank to return): ";
        Field ref = new Field("trainno", "Train Reference", "VARCHAR(12)");

        try (Connection c = Utils.getSqlConnection()) {
            c.setAutoCommit(false);
            while (true) {
                Object choice = Utils.getInput(prompt, ref.getType());
                if (choice == null) return;
                ref.setValue(choice);

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
                    return;
                } else c.rollback();
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void deleteTask() {
        String prompt = "Input the Task Reference of the record you wish to delete (blank to return): ";
        Field field = new Field("taskno", "Task Reference", "VARCHAR(16)");

        try (Connection c = Utils.getSqlConnection()) {
            c.setAutoCommit(false);
            while (true) {
                Object choice = Utils.getInput(prompt, field.getType());
                if (choice == null) return;
                field.setValue(choice);
                if (Utils.safeDeleteRecord(c, "servicetask", null, field)) {
                    c.commit();
                    return;
                } else c.rollback();
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void createTrain() {
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("Train Reference", new Field("trainno", "Train Reference", "VARCHAR(12)"));
        fields.put("Train Label", new Field("label", "Train Label", "VARCHAR(80)"));
        fields.put("Train Type", new Field("traintyp", "Train Type", "VARCHAR(12)"));
        fields.put("Number of Cars", new Field("cars", "Number of Cars", "SMALLINT UNSIGNED"));
        fields.put("Passenger Capacity", new Field("paxcap", "Passenger Capacity", "INT UNSIGNED"));
        fields.put("Duty Type", new Field("dutytype", "Duty Type", "VARCHAR(15)"));
        fields.put("Load Capacity", new Field("loadton", "Load Capacity", "DECIMAL(8,2) UNSIGNED"));

        try (Connection c = Utils.getSqlConnection()) {
            while (true) {
                List<Field> active = new ArrayList<>();
                active.add(fields.get("Train Reference"));
                active.add(fields.get("Train Label"));
                active.add(fields.get("Train Type"));
                Field typ = fields.get("Train Type");
                String type = typ == null || typ.getValue() == null ? null : typ.getValue().toString();

                if ("Passenger".equalsIgnoreCase(type)) {
                    active.add(fields.get("Number of Cars"));
                    active.add(fields.get("Passenger Capacity"));
                    fields.get("Duty Type").setValue(null);
                    fields.get("Load Capacity").setValue(null);
                } else if ("Service".equalsIgnoreCase(type)) {
                    active.add(fields.get("Duty Type"));
                    active.add(fields.get("Load Capacity"));
                    fields.get("Number of Cars").setValue(null);
                    fields.get("Passenger Capacity").setValue(null);
                }

                List<String> menu = Utils.createModifiableMenu(active);
                String prompt = "Modify every field, then choose 'Confirm' afterwards:";
                String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));

                switch (choice) {
                    case "Return" -> {return;}
                    case "Reset" -> {for (Field f : fields.values()) f.setValue(null);}
                    case "Confirm" -> {
                        saveTrainRecord(c, new ArrayList<>(fields.values()));
                        return;
                    }
                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        handleTrainInput(c, fields.get(key));
                    }
                }
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void handleTrainInput(Connection c, Field field) {
        switch (field.getName()) {
            case "trainno" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (!Utils.doesRecordExist(c, "train", field)) break;
                    Utils.displayInputError("IDENTIFIER");
                }
            }
            case "traintyp" -> {
                String[] types = {"Passenger", "Service", "Reset"};
                String sel = Utils.displayMenu("Choose the type of this train: ", types);
                if (sel == null || "Reset".equalsIgnoreCase(sel)) field.setValue(null);
                else field.setValue(Utils.parseInput(sel, field.getType()));
            }
            case "dutytype" -> {
                String[] duties = {"Inspection", "Maintenance", "Recovery", "Reset"};
                String sel = Utils.displayMenu("Choose the duty of this train: ", duties);
                if (sel == null || "Reset".equalsIgnoreCase(sel)) field.setValue(null);
                else field.setValue(Utils.parseInput(sel, field.getType()));
            }
            case "label", "cars", "paxcap", "loadton" -> Utils.updateValue(field);
        }
    }

    private void saveTrainRecord (Connection c, List<Field> fields) {
        final String sqlTrain = "INSERT INTO train (trainno, label, traintyp, active, revno) VALUES (?, ?, ?, 1, 1);";
        final String sqlPassenger = "INSERT INTO passengertrain (trainno, cars, paxcap, revno) VALUES (?, ?, ?, 1);";
        final String sqlService = "INSERT INTO servicetrain (trainno, dutytype, loadton, revno) VALUES (?, ?, ?, 1);";

        try {
            c.setAutoCommit(false);

            List<Field> inserts = List.of(
                    Field.searchByName("trainno", fields),
                    Field.searchByName("label", fields),
                    Field.searchByName("traintyp", fields)
            );

            String typ = Field.searchByName("traintyp", fields) == null ? null : Field.searchByName("traintyp", fields).getValue().toString();
            boolean main = Utils.modifyTable(c, sqlTrain, inserts);
            boolean sub = false;

            if ("Passenger".equalsIgnoreCase(typ)) {
                inserts = List.of(
                        Field.searchByName("trainno", fields),
                        Field.searchByName("cars", fields),
                        Field.searchByName("paxcap", fields)
                );
                sub = Utils.modifyTable(c, sqlPassenger, inserts);
            } else if ("Service".equalsIgnoreCase(typ)) {
                inserts = List.of(
                        Field.searchByName("trainno", fields),
                        Field.searchByName("dutytype", fields),
                        Field.searchByName("loadton", fields)
                );
                sub = Utils.modifyTable(c, sqlService, inserts);
            }

            if (main && sub) c.commit();
            else c.rollback();
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void createTask() {
        final String sql = "INSERT INTO servicetask (taskno, trainno, taskdate, tasktype, hours, details, revno) VALUES (?, ?, ?, ?, ?, ?, 1);";
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("Task Reference", new Field("taskno", "Task Reference", "VARCHAR(16)"));
        fields.put("Train Reference", new Field("trainno", "Train Reference", "VARCHAR(12)"));
        fields.put("Task Date", new Field("taskdate", "Task Date", "DATE"));
        fields.put("Task Type", new Field("tasktype", "Task Type", "VARCHAR(15)"));
        fields.put("Duration in Hours", new Field("hours", "Duration in Hours", "DECIMAL(8,2) UNSIGNED"));
        fields.put("Details", new Field("details", "Details", "VARCHAR(255)"));

        while (true) {
            List<String> menu = Utils.createModifiableMenu(new ArrayList<>(fields.values()));
            String prompt = "Modify every field, then choose 'Confirm' afterwards:";
            String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));

            try (Connection c = Utils.getSqlConnection()) {
                switch (choice) {
                    case "Return" -> {return;}
                    case "Reset" -> {for (Field f : fields.values()) f.setValue(null);}
                    case "Confirm" -> {
                        c.setAutoCommit(false);

                        boolean res = Utils.modifyTable(c, sql, new ArrayList<>(fields.values()));
                        if (res) {
                            c.commit();
                            return;
                        } else c.rollback();
                    }
                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        handleTaskInput(c, fields.get(key));
                    }
                }
            } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
        }
    }

    private void handleTaskInput(Connection c, Field field) {
        switch (field.getName()) {
            case "taskno" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (!Utils.doesRecordExist(c, "servicetask", field)) break;
                    Utils.displayInputError("IDENTIFIER");
                }
            }
            case "trainno" -> {
                while (true) {
                    Utils.updateValue(field);
                    if (field.getValue() == null) return;
                    if (Utils.doesRecordExist(c, "servicetrain", field)) break;
                    Utils.displayInputError("NO_RECORD");
                }
            }
            case "tasktype" -> {
                String[] duties = {"Inspection", "Maintenance", "Recovery", "Reset"};
                String sel = Utils.displayMenu("Choose the type of this task: ", duties);
                if (sel == null || "Reset".equalsIgnoreCase(sel)) field.setValue(null);
                else field.setValue(Utils.parseInput(sel, field.getType()));
            }
            case "taskdate", "hours", "details" -> Utils.updateValue(field);
        }
    }
}
