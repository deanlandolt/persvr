package org.persvr.store;

import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.sql.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.commons.dbcp.BasicDataSource;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.UniqueTag;


public class SQLStore {
	String[] getColumnLabels(ResultSet rs) throws SQLException{
		ResultSetMetaData metaData = rs.getMetaData();
		String[] columns = new String[metaData.getColumnCount()];
		for(int i = 0; i < columns.length; i++){
			columns[i] = metaData.getColumnLabel(i+1);
		}
		return columns;
	}
	class ReturnValue{
		boolean returnValue;
	}
	public Scriptable executeSql(final String sql, final Object parameters) throws Exception {
		final boolean isSelect = sql.toUpperCase().startsWith("SELECT");
		final Scriptable results = ScriptRuntime.newObject(org.mozilla.javascript.Context.enter(), global, "Object", new Object[]{});
		if(!isSelect){
			Connection conn = getTransactionConnection();
			boolean insert = sql.toUpperCase().startsWith("INSERT");
			PreparedStatement statement = insert && !sqlite ? 
					conn.prepareStatement(sql) : conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS); 
			if(parameters instanceof NativeArray){
				int length = ((Number) ScriptableObject.getProperty((Scriptable) parameters, "length")).intValue();
				for(int i = 0; i < length; i++){
					statement.setObject(i+1,convertToSQLValue(ScriptableObject.getProperty((Scriptable) parameters, i)));
				}
			}
			
			if(insert){
				int tableNameIndex = sql.toUpperCase().indexOf("INTO ") + 5;
				String tableName = sql.substring(tableNameIndex, sql.indexOf(" ", tableNameIndex));
				Object idColumn = parameters instanceof Scriptable ? ScriptableObject.getProperty((Scriptable) parameters, "idColumn") : null;
				results.put("insertId",results, executeAndGetGeneratedKey(statement, tableName, idColumn instanceof String ? (String) idColumn : null));
			}
			else{
				statement.execute();
			}
			return results;
		}
		else{
			final ReturnValue returnValue = new ReturnValue();
			ScriptableObject some = new BaseFunction(){
				public Object call(final org.mozilla.javascript.Context cx, final Scriptable scope, Scriptable thisObj, final Object[] args){
					executeQuery(sql, parameters, new ResultSetHandler() {
						
						public void handleResultSet(ResultSet rs) throws SQLException {
							String[] columns = getColumnLabels(rs);
							Function callback = (Function) args[0];
							while(rs.next()){
								if(ScriptRuntime.toBoolean(callback.call(cx, scope, results, new Object[]{mapResult(rs, columns)}))){
									returnValue.returnValue = true;
									return;
								}
							}
						}
					});
	
					return returnValue.returnValue;
				}
			};
			results.put("some",results, some);
			ScriptRuntime.setObjectProtoAndParent(some, global);
		}
		return results;
	}
	interface ResultSetHandler{
		void handleResultSet(ResultSet rs) throws SQLException;
	}
	void executeQuery(String sql, Object parameters, ResultSetHandler handler){
		boolean mustCloseConnection = false;
		Connection conn = null;
		try {
			conn = transactionConnections.get(connectionKey).get();
			if(conn == null){
				conn = createConnection();
				mustCloseConnection = true;
			}
			ResultSet rs;
			if(parameters instanceof NativeArray){
				PreparedStatement statement = conn.prepareStatement(sql);
				int length = ((Number) ScriptableObject.getProperty((Scriptable) parameters, "length")).intValue();
				for(int i = 0; i < length; i++){
					statement.setObject(i+1,convertToSQLValue(ScriptableObject.getProperty((Scriptable) parameters, i)));
				}
				rs = statement.executeQuery();
			}
			else{
				rs = executeQuery(conn, sql);
			}
			handler.handleResultSet(rs);
			rs.close();
		} catch (SQLException e) {
			throw ScriptRuntime.constructError("Error", e.getMessage());
		}
		finally{
			if(mustCloseConnection){
				try {
					conn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}


	int columnCount = 0;
    Scriptable global;
    public static Object getParameter(Scriptable parameters, String key){
    	Object value = parameters.get(key, parameters);
    	if (value == UniqueTag.NOT_FOUND){
    		return null;
    	}
    	return value;
    }
	public Function initParameters = new BaseFunction(){
		@Override
		public Object call(org.mozilla.javascript.Context cx, Scriptable scope, Scriptable thisObj, Object[] args){
			if(global == null){
				global = scope;
				objectToSource = (Function) cx.evaluateString(global, "({}).toSource", "toSource", 1, null);
				arrayToSource = (Function) cx.evaluateString(global, "([]).toSource", "toSource", 1, null);
			}
			

			Scriptable parameters = (Scriptable) args[0]; 

	    	SQLStore.this.jndiName = (String) getParameter(parameters,"datasourceRef");
	    	SQLStore.this.initialContextClass = (String) getParameter(parameters,"initialContext");
	    	if(jndiName != null){
	    		try {
					pooledDataSource = lookupDatasource();
				} catch (NamingException e) {
					throw ScriptRuntime.constructError("Error", e.getMessage());
				}
	    	}
	    	else{
		    	if (parameters.has("starterStatements", parameters)) {
		    		starterStatements = new ArrayList();
		    		List<String> array = asList(getParameter(parameters,"starterStatements"));
		    		for (int i = 0; i < array.size(); i++)
		    			starterStatements.add(array.get(i));
		    	}
		    	if (parameters.has("connection", parameters))
		    		SQLStore.this.connectionString = (String) getParameter(parameters,"connection");
		    	if (parameters.has("username", parameters))
		    		SQLStore.this.username = (String) getParameter(parameters,"username");
		    	connectionKey = connectionString + '-' + username;
		    	if(!transactionConnections.containsKey(connectionKey)){
		    		transactionConnections.put(connectionKey, new ThreadLocal<Connection>());
		    	}
	    		if(pooledDataSources.containsKey(connectionKey)){
	    			pooledDataSource = pooledDataSources.get(connectionKey);
	    		}
	    		else {
			    	if (parameters.has("password", parameters))
			    		SQLStore.this.password = (String) getParameter(parameters,"password");
			
			    	pooledDataSource = new BasicDataSource();
			    	pooledDataSources.put(connectionKey, pooledDataSource);
			    	((BasicDataSource)pooledDataSource).setUsername(username);
			    	((BasicDataSource)pooledDataSource).setPassword(password);
			    	((BasicDataSource)pooledDataSource).setUrl(connectionString);
			    	((BasicDataSource)pooledDataSource).setMaxWait(1000);
			    	((BasicDataSource)pooledDataSource).setTestWhileIdle(true);
	    		}
		    	if (parameters.has("driver", parameters)) {
		    		if("org.sqlite.JDBC".equals(getParameter(parameters,"driver"))) {
		    			sqlite = true;
		    		}
		    		((BasicDataSource)pooledDataSource).setDriverClassName(getParameter(parameters,"driver").toString());
		    	}

	    	}
	    	
	    	if (parameters.has("characterSet", parameters)) {
	    		SQLStore.this.needConversion = true;
	    		SQLStore.this.characterSet = (String) getParameter(parameters,"characterSet");
	    	}

			
	    	
	    	try {
	    		Connection conn = pooledDataSource.getConnection();
	    		conn.close();
/*	    		try{
	    			conn.
					conn.createStatement().execute("SELECT COUNT(*) FROM " + table);
					conn.close();
				} catch (SQLException e) {
		    		try{
						conn.createStatement().execute("SELECT COUNT(*) FROM " + table);
						conn.close();
					} catch (SQLException e1) {
						try {
							conn.close();
							runStarterStatements();
						} catch (SQLException e2) {
							throw new RuntimeException(e.getMessage() + " so tried to create table " + table + " which failed for " + e1.getMessage());
						}
					}
				}*/
	    	}catch (SQLException e) {
	    		throw new RuntimeException(e);
	    	}
	    	// May want to now run the starter statements if the table does not yet exist?
	    	return null;
		}
	};
	public boolean sqlite;
	
    protected Scriptable mapResult(ResultSet rs, String[] columns) throws SQLException {
    	Scriptable object = ScriptRuntime.newObject(org.mozilla.javascript.Context.enter(), global, "Object", ScriptRuntime.emptyArgs);
		for (int i = 0; i < columns.length; i++) {// load all the columns as properties
			String column= columns[i];
			object.put(column, object, convertFromSQLValue(rs.getObject(i+1)));
		}
		return object;
    }

	Function objectToSource;
	Function arrayToSource;
	Object convertToSQLValue(Object value){
		if (value instanceof Scriptable){
			if("Date".equals(((Scriptable)value).getClassName())) {
				// it is a date
				double time = (Double) ((Function) ScriptableObject.getProperty((Scriptable)value,"getTime")).call(org.mozilla.javascript.Context.enter(), global, (Scriptable)value, new Object[]{});
				value = new Date((long) time);
			}
			else if (value instanceof NativeArray){
				value = "__js:" + arrayToSource.call(org.mozilla.javascript.Context.enter(), global, (Scriptable) value, new Object[]{});
			}
			else{
				value = "__js:" + objectToSource.call(org.mozilla.javascript.Context.enter(), global, (Scriptable) value, new Object[]{}); 
			}
		}
		else if(value instanceof String && ((String)value).startsWith("__")){
			return "__string:" + value;
		}
		if(sqlite){
			if(value instanceof Boolean){
				return "__" + value + "__";
			}
			if(value instanceof java.util.Date){
				return "__date:" + ((Date)value).getTime();
			}
		}
		return value;
	}
	Object convertFromSQLValue(Object value){
		if(value instanceof String && (((String)value).startsWith("__"))){
			String valueString = (String)value;
			if(sqlite){ 
				if("__true__".equals(value)){
					return true;
				}
				else if ("__false__".equals(value)){
					return false;
				}
				if(valueString.startsWith("__date:")){
					value = new java.util.Date(Long.parseLong(valueString.substring(7)));
				}
			}
			if(valueString.startsWith("__js:")){
				return org.mozilla.javascript.Context.enter().evaluateString(global, valueString.substring(5) , "dbsource", 1, null);
			}
			if(valueString.startsWith("__string:")){
				return valueString.substring(9);
			}
		}
		if (value instanceof java.util.Date) {
			// it is a date
			value = ScriptRuntime.newObject(org.mozilla.javascript.Context.enter(), global, "Date", new Object[]{
				((java.util.Date)value).getTime()
			});
		}
		return value;
	}
	

	javax.sql.DataSource pooledDataSource;
	static Map<String, javax.sql.DataSource> pooledDataSources = new HashMap<String, javax.sql.DataSource>(); 
	static protected Map<String, ThreadLocal<Connection>> transactionConnections = new HashMap<String, ThreadLocal<Connection>>();
	Connection getTransactionConnection(){
		Connection conn = transactionConnections.get(connectionKey).get();
		if(conn == null){
			throw ScriptRuntime.constructError("Error", "A transaction has not been started for this action");
		}
		return conn;
		
	}
	void setTransactionConnection(Connection conn) throws SQLException {
		transactionConnections.get(connectionKey).set(conn);
	}
	String connectionKey;
	String username;
	String password;
	List<String> starterStatements;
	protected void runStarterStatements() throws SQLException {
		Connection conn = createConnection();
		Statement statement = conn.createStatement();
		for (String starterStatement : starterStatements){
			statement.execute(starterStatement);
		}
		statement.close();
		conn.close();
	}
	List asList(Object list){
		List realList;
    	if(list instanceof Scriptable){
    		realList = new ArrayList();
    		int i = 0;
    		do{
    			Object item = ((Scriptable)list).get(i++, ((Scriptable)list));
    			if(item == Scriptable.NOT_FOUND)
    				break;
    			realList.add(item);
    		}while(true);
    	}else{
    		realList = (List) list;
    	}
    	return realList;
	}
	
    
    protected String connectionString;
    protected String jndiName;
    protected String initialContextClass;
    
	
    
    protected javax.sql.DataSource lookupDatasource() throws NamingException {
		Hashtable params = null;
		if (this.initialContextClass != null) {
			params = new Hashtable();
			params.put(Context.INITIAL_CONTEXT_FACTORY, this.initialContextClass);
		}
		InitialContext ctx = new InitialContext(params);
		return (javax.sql.DataSource) ctx.lookup(jndiName);
    }
    
    protected Connection createConnection() throws SQLException {
    	return pooledDataSource.getConnection();
    }
    
	Object executeAndGenerateLock = new Object();
	public Object executeAndGetGeneratedKey(PreparedStatement statement, String table, String idColumn) throws SQLException {
		ResultSet rs;
		synchronized(executeAndGenerateLock) {
			Connection conn = getTransactionConnection();
			if (conn.getClass().getName().equals("org.hsqldb.jdbc.jdbcConnection")) {
				statement.execute();
		    	statement = conn.prepareStatement("CALL IdENTITY()");
		    	// derby: IDENTITY_VAL_LOCAL
		        rs = statement.executeQuery();
			}
			else if (conn.getClass().getName().equals("org.apache.derby.impl.jdbc.EmbedConnection40")) {
				statement.execute();
		    	statement = conn.prepareStatement("select IDENTITY_VAL_LOCAL() from " + table);
		        rs = statement.executeQuery();
			}
			else {
				statement.execute();
				rs = statement.getGeneratedKeys();
			}
	        long key;
	        if (rs.next()) {
	        	if(idColumn == null){
	        		key = rs.getLong(1);
	        	}else{
	        		key = rs.getLong(idColumn);
	        	}	            
	            rs.close();
	        }
	        else {
	        	rs.close();
	        	return null;
	        }
	        return key;
		}
	}
    
    String characterSet = "latin1";
	boolean needConversion = false;
    String decodeString(String value) {
    	if (needConversion)
			try {
				value = new String(value.getBytes(characterSet),"UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		return value;
    }
    
    String encodeString(String value) {
    	if (needConversion)
			try {
				value = new String(value.getBytes("UTF-8"),characterSet);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		return value;
    }
	ResultSet executeQuery(final String sql) throws SQLException {
		Connection conn = createConnection();
		try{
			return executeQuery(conn, sql);
		}
		finally{
			conn.close();
		}
	}    
	boolean execute(final String sql) throws SQLException {
		Connection conn = createConnection();
		try{
			return execute(conn, sql);
		}
		finally{
			conn.close();
		}
	}
	ResultSet executeQuery(Connection conn, final String sql) throws SQLException {
		Statement statement = conn.createStatement();
		return statement.executeQuery(sql);
	}

	boolean execute(Connection conn, final String sql) throws SQLException {
		Statement statement = conn.createStatement();
		return statement.execute(sql);
	}
	
	public void commitTransaction() throws SQLException {
		if (transactionConnections.get(connectionKey).get() != null) {
			Connection conn = getTransactionConnection();
			try {
				conn.commit();
			}
			finally {
				conn.setAutoCommit(true);
				try {
					conn.close();
				}
				finally {
					transactionConnections.get(connectionKey).remove();
				}
			}
		}
	}

	public void startTransaction() throws SQLException {
		if (transactionConnections.get(connectionKey).get() == null) {
			Connection conn = createConnection();
			try {
			conn.setAutoCommit(false);
			} catch (SQLException e) {
				conn = createConnection();
				conn.setAutoCommit(false);
			}
			setTransactionConnection(conn);
		}
	}

	public void abortTransaction() throws SQLException {
		if (transactionConnections.get(connectionKey).get() != null) {
			Connection conn = getTransactionConnection();
			if (conn == null) {
				throw new SQLException("transactionConnection is not valid in call to abortTransaction!");
			}
			try{
				conn.rollback();
			} finally {
				conn.setAutoCommit(true);
				try {
					conn.close();
				}
				finally {
					transactionConnections.get(connectionKey).remove();
				}
			}
		}
	}
	

}
