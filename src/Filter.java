/**
 * A generic class that serves as a filter to a specific field/column.
 *
 * @author Richmond Salvador
 */
public class Filter extends Field {
    private final String[] options;
    private final String operator;
    private final boolean isHaving;

    /**
     * Creates a filter object with the specified values.
     *
     * @param fieldName the name of the field/column in the database
     * @param label the name to be displayed in the filter menu
     * @param type the data type of the field in the database
     * @param options the options the user can choose from. This can be null which indicates that the
     *                user has to manually input a value
     * @param operator the logical operator to be used (e.g. LIKE, IS, =, !=, <, <=, >=, >=)
     * @param isHaving true if the filter is imposed on the HAVING clause, false otherwise if it is imposed in the WHERE clause
     */
    public Filter(String fieldName, String label, String type, String[] options, String operator, boolean isHaving) {
        super(fieldName, label, type);
        this.options = options;
        this.operator = operator;
        this.isHaving = isHaving;
    }

    public String[] getOptions() {
        return options;
    }

    public String getOperator() {
        return operator;
    }

    public boolean isHaving() {
        return isHaving;
    }
}
