/*

   Derby - Class org.apache.derby.diag.StatementCache

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.diag;

import java.io.InputStream;
import java.io.Reader;
import java.sql.NClob;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.apache.derby.iapi.reference.Limits;
import org.apache.derby.iapi.services.cache.CacheManager;
import org.apache.derby.iapi.sql.ResultColumnDescriptor;
import org.apache.derby.iapi.sql.conn.ConnectionUtil;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.util.StringUtil;
import org.apache.derby.impl.jdbc.EmbedResultSetMetaData;
import org.apache.derby.impl.sql.GenericPreparedStatement;
import org.apache.derby.impl.sql.GenericStatement;
import org.apache.derby.impl.sql.conn.CachedStatement;
import org.apache.derby.vti.VTITemplate;

/**
	StatementCache is a virtual table that shows the contents of the SQL statement cache.
	
	This virtual table can be invoked by calling it directly.
	<PRE> select * from new org.apache.derby.diag.StatementCache() t</PRE>


	<P>The StatementCache virtual table has the following columns:
	<UL>
	<LI> ID CHAR(36) - not nullable.  Internal identifier of the compiled statement.
	<LI> SCHEMANAME VARCHAR(128) - nullable.  Schema the statement was compiled in.
	<LI> SQL_TEXT VARCHAR(32672) - not nullable.  Text of the statement
	<LI> UNICODE BIT/BOOLEAN - not nullable.  True if the statement is compiled as a pure unicode string, false if it handled unicode escapes.
	<LI> VALID BIT/BOOLEAN - not nullable.  True if the statement is currently valid, false otherwise
	<LI> COMPILED_AT TIMESTAMP nullable - time statement was compiled, requires STATISTICS TIMING to be enabled.


	</UL>
	<P>
	The internal identifier of a cached statement matches the toString() method of a PreparedStatement object for a Derby database.

	<P>
	This class also provides a static method to empty the statement cache, StatementCache.emptyCache()

*/
public final class StatementCache extends VTITemplate {

	private int position = -1;
	private Vector data;
	private GenericPreparedStatement currentPs;
	private boolean wasNull;

	public StatementCache() throws SQLException {

		LanguageConnectionContext lcc = ConnectionUtil.getCurrentLCC();
        
        CacheManager statementCache =
            lcc.getLanguageConnectionFactory().getStatementCache();

		if (statementCache != null) {
			final Collection values = statementCache.values();
			data = new Vector(values.size());
			for (Iterator i = values.iterator(); i.hasNext(); ) {
				final CachedStatement cs = (CachedStatement) i.next();
				final GenericPreparedStatement ps =
					(GenericPreparedStatement) cs.getPreparedStatement();
				data.addElement(ps);
			}
		}
	}

	public boolean next() {

		if (data == null)
			return false;

		position++;

		for (; position < data.size(); position++) {
			currentPs = (GenericPreparedStatement) data.elementAt(position);
	
			if (currentPs != null)
				return true;
		}

		data = null;
		return false;
	}

	public void close() {
		data = null;
		currentPs = null;
	}


	public String getString(int colId) {
		wasNull = false;
		switch (colId) {
		case 1:
			return currentPs.getObjectName();
		case 2:
			return ((GenericStatement) currentPs.statement).getCompilationSchema();
		case 3:
			String sql = currentPs.getSource();
			sql = StringUtil.truncate(sql, Limits.DB2_VARCHAR_MAXWIDTH);
			return sql;
		default:
			return null;
		}
	}

	public boolean getBoolean(int colId) {
		wasNull = false;
		switch (colId) {
		case 4:
			// was/is UniCode column, but since Derby 10.0 all
			// statements are compiled and submitted as UniCode.
			return true;
		case 5:
			return currentPs.isValid();
		default:
			return false;
		}
	}

	public Timestamp getTimestamp(int colId) {

		Timestamp ts = currentPs.getEndCompileTimestamp();
		wasNull = (ts == null);
		return ts;
	}

	public boolean wasNull() {
		return wasNull;
	}

	/*
	** Metadata
	*/
	private static final ResultColumnDescriptor[] columnInfo = {

		EmbedResultSetMetaData.getResultColumnDescriptor("ID",		  Types.CHAR, false, 36),
		EmbedResultSetMetaData.getResultColumnDescriptor("SCHEMANAME",    Types.VARCHAR, true, 128),
		EmbedResultSetMetaData.getResultColumnDescriptor("SQL_TEXT",  Types.VARCHAR, false, Limits.DB2_VARCHAR_MAXWIDTH),
		EmbedResultSetMetaData.getResultColumnDescriptor("UNICODE",   Types.BIT, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("VALID",  Types.BIT, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("COMPILED_AT",  Types.TIMESTAMP, true),

	};
	
	private static final ResultSetMetaData metadata = new EmbedResultSetMetaData(columnInfo);

	public ResultSetMetaData getMetaData() {

		return metadata;
	}

	public int getHoldability() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	public Reader getNCharacterStream(int columnIndex) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public Reader getNCharacterStream(String columnLabel) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public NClob getNClob(int columnIndex) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public NClob getNClob(String columnLabel) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getNString(int columnIndex) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getNString(String columnLabel) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}


	public RowId getRowId(int columnIndex) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public RowId getRowId(String columnLabel) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public SQLXML getSQLXML(int columnIndex) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public SQLXML getSQLXML(String columnLabel) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isClosed() throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	public void updateAsciiStream(int columnIndex, InputStream x)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateAsciiStream(String columnLabel, InputStream x)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateAsciiStream(int columnIndex, InputStream x, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateAsciiStream(String columnLabel, InputStream x, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateBinaryStream(int columnIndex, InputStream x)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateBinaryStream(String columnLabel, InputStream x)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateBinaryStream(int columnIndex, InputStream x, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateBinaryStream(String columnLabel, InputStream x,
			long length) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateBlob(int columnIndex, InputStream inputStream)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateBlob(String columnLabel, InputStream inputStream)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateBlob(int columnIndex, InputStream inputStream, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateBlob(String columnLabel, InputStream inputStream,
			long length) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateCharacterStream(int columnIndex, Reader x)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateCharacterStream(String columnLabel, Reader reader)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateCharacterStream(int columnIndex, Reader x, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateCharacterStream(String columnLabel, Reader reader,
			long length) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateClob(int columnIndex, Reader reader) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateClob(String columnLabel, Reader reader)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateClob(int columnIndex, Reader reader, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateClob(String columnLabel, Reader reader, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNCharacterStream(int columnIndex, Reader x)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNCharacterStream(String columnLabel, Reader reader)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNCharacterStream(int columnIndex, Reader x, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNCharacterStream(String columnLabel, Reader reader,
			long length) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNClob(int columnIndex, NClob clob) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNClob(String columnLabel, NClob clob) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNClob(int columnIndex, Reader reader) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNClob(String columnLabel, Reader reader)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNClob(int columnIndex, Reader reader, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNClob(String columnLabel, Reader reader, long length)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNString(int columnIndex, String string)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateNString(String columnLabel, String string)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateRowId(int columnIndex, RowId x) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateRowId(String columnLabel, RowId x) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateSQLXML(int columnIndex, SQLXML xmlObject)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public void updateSQLXML(String columnLabel, SQLXML xmlObject)
			throws SQLException {
		// TODO Auto-generated method stub
		
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}
}
