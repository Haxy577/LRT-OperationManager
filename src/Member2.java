import java.sql.*;
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

    private Field selected;

    public Member2() {selected = null;}

    @Override
    public void menu() {
        while (true) {
            String[] options = {"Manage Trains", "Manage Support Tasks", "Generate Report", "Exit"};
            String prompt = "Choose which category you want to manage:";
            switch (Utils.displayMenu(prompt, options)) {
                case "Manage Trains" -> trainMenu();
                case "Manage Support Tasks" -> taskMenu();
                case "Generate Report" -> generateReport();
                default -> {return;}
            }
        }
    }

    private void trainMenu() {
        while (true) {
            String prompt = "Which operation do you wish to do:";
            List<String> menu = new ArrayList<>(List.of(
                    "Create Train", "Search Train",
                    String.format("Edit %s", selected != null ? selected.getValue() : "Train"),
                    String.format("Delete %s", selected != null ? selected.getValue() : "Train")
            ));
            if (selected != null) menu.add("Reset Selection");
            menu.add("Return");
            String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));
            if (choice.contains("Create")) createTrain();
            else if (choice.contains("Search")) searchTrain();
            else if (choice.contains("Edit")) updateTrain();
            else if (choice.contains("Delete")) deleteTrain();
            else if (choice.contains("Reset Selection")) selected = null;
            else return;
        }
    }

    private void taskMenu() {
        while (true) {
            String prompt = "Which operation do you wish to do:";
            List<String> menu = new ArrayList<>(List.of(
                    "Create Task", "Search Task",
                    String.format("Edit %s", selected != null ? selected.getValue() : "Task"),
                    String.format("Delete %s", selected != null ? selected.getValue() : "Task")
            ));
            if (selected != null) menu.add("Reset Selection");
            menu.add("Return");
            String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));
            if (choice.contains("Create")) createTask();
            else if (choice.contains("Search")) searchTask();
            else if (choice.contains("Edit")) updateTask();
            else if (choice.contains("Delete")) deleteTask();
            else if (choice.contains("Reset Selection")) selected = null;
            else return;
        }
    }

    private void generateReport() {
        String sql = "SELECT t.trainno, t.label, t.traintyp, " +
                "COUNT(c.callno) AS callcount, COUNT(DISTINCT p.stncode) AS visitedstation, MIN(c.arrat) AS earliest, MAX(c.arrat) AS latest " +
                "FROM train t LEFT JOIN traincall c ON t.trainno = c.trainno AND c.arrat >= ? AND c.arrat <= ? LEFT JOIN platform p ON c.platno = p.platno ";
        String groupBy = "GROUP BY t.trainno";
        String tail = "ORDER BY callcount DESC, t.trainno ASC;";
        List<Filter> mandatory = List.of(
                new Filter("c.arrat", "Start Date", "DATETIME", ">=", false),
                new Filter("c.arrat", "End Date", "DATETIME", "<=", false)
        );
        List<Filter> optional = List.of(
                new Filter("t.trainno", "Train Reference", "VARCHAR(12)", "LIKE", false),
                new Filter("t.traintyp", "Train Type", "VARCHAR(12)", new String[]{"Passenger", "Service"}, "=", false),
                new Filter("callcount", "Minimum Call Count", "INT UNSIGNED", ">=", true)
        );
        try (Connection c = Utils.getSqlConnection()){
            Utils.displaySqlResult(Utils.sqlSearch(c, sql, groupBy, tail, mandatory, optional));
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void searchTrain() {
        String sql = "SELECT ROW_NUMBER() OVER (ORDER BY t.trainno ASC) row_num, " +
                "t.trainno, t.label, t.traintyp, p.cars, p.paxcap, s.dutytype, s.loadton, if(t.active = 1, 'Active', 'Inactive') AS active " +
                "FROM train t LEFT JOIN passengertrain p ON t.trainno = p.trainno " +
                "LEFT JOIN servicetrain s ON t.trainno = s.trainno ";
        String tail = "ORDER BY t.trainno ASC;";
        List<Filter> filters = List.of(
                new Filter("t.trainno", "Train Reference", "VARCHAR(12)", "LIKE", false),
                new Filter("t.label", "Train Label", "VARCHAR(80)", "LIKE", false),
                new Filter("t.traintyp", "Train Type", "VARCHAR(12)", new String[]{"Passenger", "Service"}, "=", false),
                new Filter("t.active", "Train Activation Status", "TINYINT", new String[]{"1", "0"}, "=", false),
                new Filter("s.dutytype", "Train Duty Type", "VARCHAR(15)", "SELECT ROW_NUMBER() OVER (ORDER BY tasktype ASC) AS row_num, tasktype AS dutytype, taskdes FROM reftasktype", "=", false)
        );
        try (Connection c = Utils.getSqlConnection()){
            ResultSet res = Utils.sqlSearch(c, sql, null, tail, null, filters);
            Field identifier = new Field("t.trainno", "Train Reference", "VARCHAR(12)");
            Utils.retrieveRecordMenu(res, identifier);
            selected = identifier.getValue() == null ? null : identifier;
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void searchTask() {
        String sql = "SELECT ROW_NUMBER() OVER (ORDER BY s.taskno ASC) row_num, " +
                "s.taskno, s.taskdate, s.tasktype, s.hours, t.trainno, t.dutytype, t.loadton, s.details " +
                "FROM servicetask s JOIN servicetrain t ON s.trainno = t.trainno WHERE 1=1 ";
        String tail = "ORDER BY s.taskno ASC;";
        List<Filter> filters = List.of(
                new Filter("s.taskno", "Task Reference", "VARCHAR(16)", "LIKE", false),
                new Filter("t.trainno", "Train Reference", "VARCHAR(12)", "LIKE", false),
                new Filter("s.taskdate", "Task Date", "DATE", "=", false),
                new Filter("s.tasktype", "Task Type", "VARCHAR(15)", "SELECT ROW_NUMBER() OVER (ORDER BY tasktype ASC) AS row_num, tasktype, taskdes FROM reftasktype", "=", false)
        );
        try (Connection c = Utils.getSqlConnection()){
            ResultSet res = Utils.sqlSearch(c, sql, null, tail, null, filters);
            Field identifier = new Field("taskno", "Task Reference", "VARCHAR(16)");
            Utils.retrieveRecordMenu(res, identifier);
            selected = identifier.getValue() == null ? null : identifier;
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void updateTrain() {
        Map<String, Field> fields = new LinkedHashMap<>();
        // Add modifiable fields
        fields.put("Train Label", new Field("label", "Train Label", "VARCHAR(80)"));
        fields.put("Train Type", new Field("traintyp", "Train Type", "VARCHAR(12)"));
        fields.put("Train Activation", new Field("active", "Train Activation", "TINYINT"));
        fields.put("Number of Cars", new Field("cars", "Number of Cars", "SMALLINT UNSIGNED"));
        fields.put("Passenger Capacity", new Field("paxcap", "Passenger Capacity", "INT UNSIGNED"));
        fields.put("Duty Type", new Field("dutytype", "Duty Type", "VARCHAR(15)"));
        fields.put("Load Capacity", new Field("loadton", "Load Capacity", "DECIMAL(8,2) UNSIGNED"));

        // add fields for search/conditions
        fields.put("Revision", new Field("revno", "Revision", "INT UNSIGNED"));
        fields.put("Train Reference", new Field("trainno", "Train Reference", "VARCHAR(12)"));

        try (Connection c = Utils.getSqlConnection()) {
            // get the record to be updated
            Field ref = fields.get("Train Reference");
            if (selected == null) {
                while (true) {
                    Utils.updateValue(ref);
                    if (ref.getValue() == null) return;
                    if (Utils.doesRecordExist(c, "train", ref)) break;
                    Utils.displayInputError("NO_RECORD");
                }
            } else ref.setValue(selected.getValue());

            // Retrieve and store the original values
            final String selectSql = "SELECT t.label, t.traintyp, p.cars, p.paxcap, s.dutytype, s.loadton, t.active, t.revno, t.trainno " +
                    "FROM train t LEFT JOIN passengertrain p ON t.trainno = p.trainno LEFT JOIN servicetrain s ON t.trainno = s.trainno " +
                    "WHERE t.trainno = ? ";
            Map<String, Object> originalValues = Utils.loadValues(c, selectSql, ref, fields);

            while (true) {
                List<Field> active = new ArrayList<>();
                active.add(fields.get("Train Label"));
                active.add(fields.get("Train Type"));
                active.add(fields.get("Train Activation"));
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
                    case "Reset" -> {for (String k : fields.keySet()) fields.get(k).setValue(originalValues.get(k));}
                    case "Confirm" -> {
                        if (!Utils.checkAllFields(active)) continue;
                        boolean hasTypeChange = !originalValues.get("Train Type").toString().equalsIgnoreCase(type);
                        saveTrainUpdate(c, fields, hasTypeChange);
                        return;
                    }
                    default -> {
                        String key = choice.substring(0, choice.indexOf("->")).trim();
                        // can only change type if there is no record depending on train
                        if ("Train Type".equals(key)) {
                            if (Utils.doesRecordExist(c, "servicetask", ref) || Utils.doesRecordExist(c, "traincall", ref)) {
                                Utils.displayInputError("REFERENCE");
                                continue;
                            }
                        }
                        handleTrainInput(c, fields.get(key));
                    }
                }
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private boolean saveTrainUpdate(Connection c, Map<String, Field> fields, boolean hasTypeChange) {
        String mainUpdate = "UPDATE train SET label = ?, traintyp = ?, active = ?, revno = revno + 1 WHERE revno = ? AND trainno = ?;";
        Field ref = fields.get("Train Reference");
        String type = fields.get("Train Type").getValue().toString();
        try {
            c.setAutoCommit(false);
            List<Field> mainValues = List.of(fields.get("Train Label"), fields.get("Train Type"), fields.get("Train Activation"), fields.get("Revision"), ref);
            boolean main = Utils.modifyTable(c, mainUpdate, mainValues);
            boolean sub = true, del = true;

            if (main && "Passenger".equalsIgnoreCase(type)) {
                List<Field> subValues = List.of(fields.get("Number of Cars"), fields.get("Passenger Capacity"), fields.get("Revision"), ref);
                if (hasTypeChange) {
                    del = Utils.safeDeleteRecord(c, "servicetrain", new String[]{"traincall", "servicetask"}, ref);
                    String insert = "INSERT INTO passengertrain(cars, paxcap, revno, trainno) VALUES (?, ?, ?, ?);";
                    Utils.modifyTable(c, insert, subValues);
                }
                String subUpdate = "UPDATE passengertrain SET cars = ?, paxcap = ?, revno = revno + 1 WHERE revno = ? AND trainno = ?;";
                sub = Utils.modifyTable(c, subUpdate, subValues);
            } else if (main && "Service".equalsIgnoreCase(type)) {
                List<Field> subValues = List.of(fields.get("Duty Type"), fields.get("Load Capacity"), fields.get("Revision"), fields.get("Train Reference"));
                if (hasTypeChange) {
                    del = Utils.safeDeleteRecord(c, "passengertrain", new String[]{"traincall"}, ref);
                    String insert = "INSERT INTO servicetrain(dutytype, loadton, revno, trainno) VALUES (?, ?, ?, ?);";
                    Utils.modifyTable(c, insert, subValues);
                }
                String subUpdate = "UPDATE servicetrain SET dutytype = ?, loadton = ?, revno = revno + 1 WHERE revno = ? AND trainno = ?;";
                sub = Utils.modifyTable(c, subUpdate, subValues);
            }

            if (main && sub && del) {
                c.commit();
                return true;
            }
            else c.rollback();
            return false;
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
        return false;
    }

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
            if (selected == null) {
                while (true) {
                    Utils.updateValue(ref);
                    if (ref.getValue() == null) return;
                    if (Utils.doesRecordExist(c, "servicetask", ref)) break;
                    Utils.displayInputError("NO_RECORD");
                }
            } else ref.setValue(selected.getValue());

            // Retrieve and store the original values
            final String selectSql = "SELECT taskdate, tasktype, hours, details, revno, taskno FROM servicetask WHERE taskno = ?;";
            Map<String, Object> originalValues = Utils.loadValues(c, selectSql, ref, fields);

            while (true) {
                List<String> menu = Utils.createModifiableMenu(new ArrayList<>(fields.values()).subList(0, fieldNum));
                String prompt = "Modify every fields, then choose 'Confirm' afterwards:";
                String choice = Utils.displayMenu(prompt, menu.toArray(new String[0]));

                switch (choice) {
                    case "Return" -> {return;}
                    case "Reset" -> {for (String k : fields.keySet()) fields.get(k).setValue(originalValues.get(k));}
                    case "Confirm" -> {
                        if (!Utils.checkAllFields(new ArrayList<>(fields.values()))) continue;
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
                if (selected == null) {
                    Object choice = Utils.getInput(prompt, ref.getType());
                    if (choice == null) return;
                    ref.setValue(choice);
                } else ref.setValue(selected.getValue());

                if (!Utils.doesRecordExist(c, "train", ref)) {
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
                    return;
                } else c.rollback();
            }
        } catch (SQLException e) {Utils.displayDatabaseError(null, e);}
    }

    private void deleteTask() {
        String prompt = "Input the Task Reference of the record you wish to delete (blank to return): ";
        Field field = new Field("taskno", "Task Reference", "VARCHAR(16)");
        String sql = "SELECT s.taskno, s.taskdate, s.tasktype, s.hours, t.trainno, t.dutytype, t.loadton, s.details " +
                "FROM servicetask s JOIN servicetrain t ON s.trainno = t.trainno WHERE s.taskno = ?;";

        try (Connection c = Utils.getSqlConnection()) {
            c.setAutoCommit(false);
            while (true) {
                if (selected == null) {
                    Object choice = Utils.getInput(prompt, field.getType());
                    if (choice == null) return;
                    field.setValue(choice);
                } else field.setValue(selected.getValue());

                if (!Utils.doesRecordExist(c, "servicetask", field)) {
                    Utils.displayInputError("NO_RECORD");
                    deleteTrain();
                    return;
                }

                // show the task to be deleted
                try (PreparedStatement ps = Utils.prepareStatement(c, sql, new Field[]{field})) {
                    Utils.displaySqlResult(ps.executeQuery());
                }

                // confirm if the user wants to delete the record
                String[] choices = {"Yes", "No"};
                String confirm = Utils.displayMenu("Are you sure you want to delete this record?", choices);
                if (confirm.equalsIgnoreCase("No")) return;

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
                        if (!Utils.checkAllFields(active)) continue;
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
                String sql = "SELECT ROW_NUMBER() OVER (ORDER BY tasktype ASC) AS row_num, tasktype AS dutytype, taskdes FROM reftasktype";
                try (ResultSet res = c.prepareStatement(sql).executeQuery()) {
                    Utils.retrieveRecordMenu(res, field);
                } catch (Exception e) {Utils.displayDatabaseError(null, e);}
            }
            case "active" -> {
                String[] status = {"Active", "Inactive", "Reset"};
                String sel = Utils.displayMenu("Choose the activation status of this train: ", status);
                if (sel == null || "Reset".equalsIgnoreCase(sel)) field.setValue(null);
                else {
                    String val = "Active".equals(sel) ? "1" : "0";
                    field.setValue(Utils.parseInput(val, field.getType()));
                }
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
                        if (!Utils.checkAllFields(new ArrayList<>(fields.values()))) continue;
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
                String sql = "SELECT ROW_NUMBER() OVER (ORDER BY s.trainno ASC) AS row_num, s.trainno, s.dutytype, s.loadton FROM servicetrain s JOIN train t ON s.trainno = t.trainno WHERE t.active = 1;";
                try (ResultSet res = c.prepareStatement(sql).executeQuery()) {
                    Utils.retrieveRecordMenu(res, field);
                } catch (Exception e) {Utils.displayDatabaseError(null, e);}
            }
            case "tasktype" -> {
                String sql = "SELECT ROW_NUMBER() OVER (ORDER BY tasktype ASC) AS row_num, tasktype, taskdes FROM reftasktype";
                try (ResultSet res = c.prepareStatement(sql).executeQuery()) {
                    Utils.retrieveRecordMenu(res, field);
                } catch (Exception e) {Utils.displayDatabaseError(null, e);}
            }
            case "taskdate", "hours", "details" -> Utils.updateValue(field);
        }
    }
}
