public class Filter {
    private final String fieldName;
    private final String label;
    private final String[] options;
    private String selectedValue;

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
