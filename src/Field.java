import java.util.*;
import java.sql.JDBCType;

public class Field {
    private final String name;
    private final String label;
    private final String type;
    private final JDBCType sqlType;
    private Object value;

    public Field(String name, String label, String type) {
        this.name = name.trim();
        this.label = label.trim();
        this.type = type.trim().toUpperCase();
        this.sqlType = getJdbcType(type);
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

    public JDBCType getSqlType() {
        return sqlType;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    private JDBCType getJdbcType(String type) {
        if (type.contains("TINYINT")) return JDBCType.TINYINT;
        else if (type.contains("SMALLINT")) return JDBCType.SMALLINT;
        else if (type.contains("INT")) return JDBCType.INTEGER;
        else if (type.contains("DECIMAL")) return JDBCType.DECIMAL;
        else if (type.contains("VARCHAR")) return JDBCType.VARCHAR;
        else if (type.contains("DATETIME")) return JDBCType.TIMESTAMP;
        else if (type.contains("DATE")) return JDBCType.DATE;
        else return null;
    }

    public static Field searchByName(String target, List<Field> fields) {
        if (target == null || fields == null) return null;
        for (Field f : fields) if (target.equalsIgnoreCase(f.getName())) return f;
        return null;
    }
}
