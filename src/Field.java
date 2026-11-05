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
        return switch (type.toUpperCase().trim()) {
            case "INT" -> JDBCType.INTEGER;
            case "SMALLINT" -> JDBCType.SMALLINT;
            case "TINYINT" -> JDBCType.TINYINT;
            case "DECIMAL" -> JDBCType.DECIMAL;
            case "VARCHAR" -> JDBCType.VARCHAR;
            case "DATETIME", "TIMESTAMP" -> JDBCType.TIMESTAMP;
            case "DATE" -> JDBCType.DATE;
            default -> null;
        };
    }
}
