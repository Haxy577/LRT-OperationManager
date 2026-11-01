import java.util.List;

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
                case 0 -> {

                }
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

}
