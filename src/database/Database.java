package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Database {
	
	private String db;
	private static String user;
	private static String pass;
	private Connection conn;
	
	private Map<String, Object> bindParams = new HashMap<>();
	private List<Map<String, Object>> resultList = new ArrayList<>();
	
	public static void setMariaUser(String MariaUser) {
		user = MariaUser;
	}
	
	public static void setMariaPassword(String MariaPassword) {
		pass = MariaPassword;
	}
	
	public void setDatabase(String database) {
		this.db = database;
	}
	
	public void connect() {
		
		if(this.db == null) {
			System.err.println("Specify a database first!");
			return;
		}
		
	    String jdbcUrl = "jdbc:mariadb://181.214.99.122:3306/" + db;

	    try {
	        this.conn = DriverManager.getConnection(jdbcUrl, user, pass);
	        System.out.println("Successfully connected to Database: " + db);
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}
	
	public void close() {
	    if (conn != null) {
	        try {
	            conn.close();
	        } catch (SQLException e) {
	            e.printStackTrace();
	        }
	    }
	}
	
	public List<Map<String, Object>> query(String sql) {
	    resultList.clear();

	    if (conn == null) {
	        System.err.println("No DB connection established. => use connect() first!");
	        return resultList;
	    }

	    try (Statement stmt = conn.createStatement();
	         ResultSet rs = stmt.executeQuery(sql)) {

	        ResultSetMetaData meta = rs.getMetaData();
	        int columnCount = meta.getColumnCount();

	        while (rs.next()) {
	            Map<String, Object> row = new HashMap<>();
	            for (int i = 1; i <= columnCount; i++) {
	                row.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
	            }
	            resultList.add(row);
	        }

	    } catch (SQLException e) {
	        e.printStackTrace();
	    }

	    return resultList;
	}
	
	public void query2(String sql) {
		resultList.clear();
		
	    if (conn == null) {
	        System.err.println("No DB connection established. => use connect() first!");
	        return;
	    }

	    List<Object> orderedValues = new ArrayList<>();
	    StringBuffer parsedSql = new StringBuffer();

	    Pattern pattern = Pattern.compile(":(\\w+)");
	    Matcher matcher = pattern.matcher(sql);

	    while (matcher.find()) {
	        String paramName = matcher.group(1);
	        if (!bindParams.containsKey(paramName)) {
	            throw new IllegalArgumentException("Missing bind parameter: " + paramName);
	        }

	        orderedValues.add(bindParams.get(paramName));
	        matcher.appendReplacement(parsedSql, "?");
	    }
	    matcher.appendTail(parsedSql);

	    try (PreparedStatement stmt = conn.prepareStatement(parsedSql.toString())) {
	        for (int i = 0; i < orderedValues.size(); i++) {
	            stmt.setObject(i + 1, orderedValues.get(i));
	        }

	        try (ResultSet rs = stmt.executeQuery()) {
	            ResultSetMetaData meta = rs.getMetaData();
	            int columnCount = meta.getColumnCount();

	            while (rs.next()) {
	                Map<String, Object> row = new HashMap<>();
	                for (int i = 1; i <= columnCount; i++) {
	                	row.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
	                }
	                resultList.add(row);
	            }
	        }

	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
		bindParams.clear();
	}
	
	public void bindValue(String bindname, Object value) {
		this.bindParams.put(bindname, value);
	}
	
	public List<Map<String, Object>> result() {
		return resultList;
	}

}
