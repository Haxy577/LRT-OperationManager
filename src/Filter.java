/**
 * A generic class that serves as a filter to a specific field/column.
 *
 * @author Richmond Salvador
 */
public class Filter {
    private final String fieldName;
    private final String label;
    private final String[] options;
    private String selectedValue;

    /**
     * Creates a filter object with the specified values.
     *
     * @param fieldName the name of the field/column in the database
     * @param label the name to be displayed in the filter menu
     * @param options the options the user can choose from. This can be null which indicates that the
     *                user has to manually input a value
     */
    public Filter(String fieldName, String label, String[] options) {
        this.fieldName = fieldName;
        this.label = label;
        this.options = options;
        this.selectedValue = null;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getLabel() {
        return label;
    }

    public String[] getOptions() {
        return options;
    }

    public String getSelectedValue() {
        return selectedValue;
    }

    public void setSelectedValue(String selectedValue) {
        this.selectedValue = selectedValue;
    }
}
