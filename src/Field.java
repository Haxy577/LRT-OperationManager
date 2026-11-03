import java.sql.SQLType;

public class Field {
    private final String name;
    private final String label;
    private final String type;
    private Object value;

    public Field(String name, String label, String type) {
        this.name = name.trim();
        this.label = label.trim();
        this.type = type.trim().toUpperCase();
        this.value = null;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
