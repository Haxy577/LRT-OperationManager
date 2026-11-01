/**
 * A contract that would allow for the integration of all menus of different member menu under a single central menu
 *
 * @author Richmond Salvador
 */
public interface Application {
    /**
     * All member classes shall implement this method. This should contain the main menu of the application assigned
     * to the member. It SHOULD also have an option to terminate the loop and return to the main menu. This basically
     * serves as the main method for each member.
     */
    public void menu();
}
