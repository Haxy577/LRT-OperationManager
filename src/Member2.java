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
                // Search
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
                case 0 -> {

                }
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
                default -> {
                    return;
                }
            }
        }
    }

    private void createTrain() {
        // Create arrays to store values of trains
        String[] train = new String[3];
        String[] passenger = new String[2];
        String[] service = new String[2];

        while (true) {
            // create menu
            List<String> menu = new ArrayList<>();
            menu.add(String.format("%-25s -> %s", "Train Reference", train[0] == null ? "None" : train[0]));
            menu.add(String.format("%-25s -> %s", "Train Label", train[1] == null ? "None" : train[1]));
            menu.add(String.format("%-25s -> %s", "Train Type", train[2] == null ? "None" : train[2]));
            if (train[2] != null) {
                if (train[2].equals("Passenger")) {
                    menu.add(String.format("%-25s -> %s", "Number of Cars", passenger[0] == null ? "None" : passenger[0]));
                    menu.add(String.format("%-25s -> %s", "Passenger Capacity", passenger[1] == null ? "None" : passenger[1]));
                } else if (train[2].equals("Service")) {
                    menu.add(String.format("%-25s -> %s", "Principal Duty", service[0] == null ? "None" : service[0]));
                    menu.add(String.format("%-25s -> %s", "Payload Capacity", service[1] == null ? "None" : service[1]));
                }
            }

            int num = menu.size();
            menu.add("Reset");
            menu.add("Create Record");
            menu.add("Return");

            int choice = Utils.displayMenu("Modify every field, then choose 'Create Record' afterwards:", menu.toArray(new String[0]));

            // reset
            if (choice == num) {
                Arrays.fill(train, null);
                Arrays.fill(passenger, null);
                Arrays.fill(service, null);
            }
            // insert record
            else if (choice == num + 1) {
                saveTrainRecord(train, passenger, service);
                return;
            }
            // return
            else if (choice == num + 2) {
                return;
            }
            // modify field values
            else {
                handleCreateTrainInput(choice, train, passenger, service);
            }
        }
    }

    private void handleCreateTrainInput(int choice, String[] train, String[] passenger, String[] service) {
        Scanner sc = new Scanner(System.in);

        switch(choice) {
            case 0 -> { // Train Reference
                System.out.print("Enter unique Train Reference: ");
                String in = sc.nextLine().trim();
                if (in == null || in.isEmpty()) train[0] = null;
                else train[0] = in;
            }
            case 1 -> { // Train Label
                System.out.print("Enter Train Label: ");
                String in = sc.nextLine();
                train[1] = in.isEmpty() ? null : in;
            }
            case 2 -> { // Train Type
                String[] choices = {"Passenger", "Service", "Reset"};
                int in = Utils.displayMenu("Choose which type of train it would be:", choices);
                switch (in) {
                    case 0 -> { // passenger train
                        train[2] = choices[0];
                        Arrays.fill(service, null);
                    }
                    case 1 -> { // service train
                        train[2] = choices[1];
                        Arrays.fill(passenger, null);
                    }
                    default -> train[2] = null;
                }
                train[2] = in == choices.length - 1 ? null : choices[in];
            }
            case 3 -> { // cars or duty
                if ("Passenger".equals(train[2])) {
                    String in = "";
                    do {
                        System.out.print("Enter the amount of passenger cars: ");
                        in = sc.nextLine().trim();
                        if (in.isEmpty()) break;
                    } while(!Utils.isValidInput(in, "INT UNSIGNED"));
                    passenger[0] = in.isEmpty() ? null : in;
                } else if ("Service".equals(train[2])) {
                    String[] duties = {"Inspection", "Maintenance", "Recovery", "Reset"};
                    int in = Utils.displayMenu("Choose the duty of this train: ", duties);
                    service[0] = in == duties.length - 1 ? null : duties[in];
                }
            }
            case 4 -> { // passenger or service capacity
                if ("Passenger".equals(train[2])) {
                    String in = "";
                    do {
                        System.out.print("Enter the maximum passenger capacity: ");
                        in = sc.nextLine().trim();
                        if (in.isEmpty()) break;
                    } while(!Utils.isValidInput(in, "INT UNSIGNED"));
                    passenger[1] = in.isEmpty() ? null : in;
                } else if ("Service".equals(train[2])) {
                    String in = "";
                    do {
                        System.out.print("Enter the maximum load capacity: ");
                        in = sc.nextLine().trim();
                        if (in.isEmpty()) break;
                    } while(!Utils.isValidInput(in, "DECIMAL UNSIGNED"));
                    service[1] = in.isEmpty() ? null : in;
                }
            }
        }
    }

    private void saveTrainRecord (String[] trainVal, String[] passengerVal, String[] serviceVal) {
        try {
            Connection c = Utils.getSqlConnection();

            final String insertTrain = "INSERT INTO train (trainno, label, traintyp, active, revno) VALUES (?, ?, ?, 1, 1);";
            PreparedStatement ps = c.prepareStatement(insertTrain);
            for (int i = 0; i < trainVal.length; i++) ps.setString(i + 1, trainVal[i]);
            ps.execute();

            if ("Passenger".equalsIgnoreCase(trainVal[2])) {
                final String insertPassenger = "INSERT INTO passengertrain (trainno, cars, paxcap, revno) VALUES (?, ?, ?, 1);";
                ps = c.prepareStatement(insertPassenger);
                ps.setString(1, trainVal[0]);
                ps.setInt(2, Integer.parseInt(passengerVal[0]));
                ps.setInt(3, Integer.parseInt(passengerVal[1]));
                ps.execute();
            }
            else if ("Service".equalsIgnoreCase(trainVal[2])) {
                final String insertService = "INSERT INTO servicetrain (trainno, dutytype, loadton, revno) VALUES (?, ?, ?, 1);";
                ps = c.prepareStatement(insertService);
                ps.setString(1, trainVal[0]);
                ps.setString(2, serviceVal[0]);
                ps.setDouble(3, Double.parseDouble(serviceVal[1]));
                ps.execute();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private ResultSet retrieveTrainRecord(String trainRef) {
        Connection c = Utils.getSqlConnection();
        try {
            String sql = "SELECT * FROM train WHERE trainno = " + trainRef;
            PreparedStatement ps = c.prepareStatement(sql);
            ResultSet res = ps.executeQuery();

            // if there is no match just return
            if (!res.next()) return res;

            String type = res.getString("traintyp");
            if ("Passenger".equalsIgnoreCase(type))
                sql = "SELECT * FROM train t JOIN passengertrain p ON t.trainno = p.trainno WHERE t.trainno = " + trainRef;
            else if ("Service".equalsIgnoreCase(type))
                sql = "SELECT * FROM train t JOIN servicetrain s ON t.trainno = s.trainno WHERE t.trainno = " + trainRef;

            ps = c.prepareStatement(sql);
            return ps.executeQuery();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
