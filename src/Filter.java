/**
 * A generic class that serves as a filter to a specific field/column.
 *
 * @author Richmond Salvador
 */
public class Filter extends Field {
    private final String[] options;

    /**
     * Creates a filter object with the specified values.
     *
     * @param fieldName the name of the field/column in the database
     * @param label the name to be displayed in the filter menu
     * @param type the data type of the field in the database
     * @param options the options the user can choose from. This can be null which indicates that the
     *                user has to manually input a value
     */
    public Filter(String fieldName, String label, String type, String[] options) {
        super(fieldName, label, type);
        this.options = options;
    }

    public String[] getOptions() {
        return options;
    }
}
