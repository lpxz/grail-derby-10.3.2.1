/*

   Derby - Class org.apache.derby.catalog.GetProcedureColumns

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

package org.apache.derby.catalog;

import java.sql.Types;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.*;
import java.sql.NClob;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.sql.SQLXML;
import java.util.Map;

import org.apache.derby.catalog.TypeDescriptor;

import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.DataTypeUtilities;
import org.apache.derby.iapi.sql.ResultColumnDescriptor;
import org.apache.derby.impl.jdbc.EmbedResultSetMetaData;
import org.apache.derby.catalog.types.RoutineAliasInfo;

import org.apache.derby.shared.common.reference.JDBC40Translation;
/**
    <P>Use of VirtualTableInterface to provide support for
    DatabaseMetaData.getProcedureColumns().
	

    <P>This class is called from a Query constructed in 
    java/org.apache.derby.impl.jdbc/metadata.properties:
<PRE>


    <P>The VTI will return columns 3-14, an extra column to the specification
    METHOD_ID is returned to distinguish between overloaded methods.

  <OL>
        <LI><B>PROCEDURE_CAT</B> String => procedure catalog (may be null)
        <LI><B>PROCEDURE_SCHEM</B> String => procedure schema (may be null)
        <LI><B>PROCEDURE_NAME</B> String => procedure name
        <LI><B>COLUMN_NAME</B> String => column/parameter name 
        <LI><B>COLUMN_TYPE</B> Short => kind of column/parameter:
      <UL>
      <LI> procedureColumnUnknown - nobody knows
      <LI> procedureColumnIn - IN parameter
      <LI> procedureColumnInOut - INOUT parameter
      <LI> procedureColumnOut - OUT parameter
      <LI> procedureColumnReturn - procedure return value
      <LI> procedureColumnResult - result column in ResultSet
      </UL>
  <LI><B>DATA_TYPE</B> int => SQL type from java.sql.Types
        <LI><B>TYPE_NAME</B> String => SQL type name, for a UDT type the
  type name is fully qualified
        <LI><B>PRECISION</B> int => precision
        <LI><B>LENGTH</B> int => length in bytes of data
        <LI><B>SCALE</B> short => scale
        <LI><B>RADIX</B> short => radix
        <LI><B>NULLABLE</B> short => can it contain NULL?
      <UL>
      <LI> procedureNoNulls - does not allow NULL values
      <LI> procedureNullable - allows NULL values
      <LI> procedureNullableUnknown - nullability unknown
      </UL>
        <LI><B>REMARKS</B> String => comment describing parameter/column
        <LI><B>METHOD_ID</B> Short => Derby extra column (overloading)
        <LI><B>PARAMETER_ID</B> Short => Derby extra column (output order)
  </OL>

*/

public class GetProcedureColumns extends org.apache.derby.vti.VTITemplate 
{
	private boolean isFunction;
	private int translate(int val) {
		if (!isFunction) { return val; }
		switch (val) {
		case DatabaseMetaData.procedureColumnUnknown:
			return JDBC40Translation.FUNCTION_PARAMETER_UNKNOWN;	
		case DatabaseMetaData.procedureColumnIn:
			return JDBC40Translation.FUNCTION_PARAMETER_IN;
		case DatabaseMetaData.procedureColumnInOut:
			return JDBC40Translation.FUNCTION_PARAMETER_INOUT;	
		case DatabaseMetaData.procedureColumnOut:
			return JDBC40Translation.FUNCTION_PARAMETER_OUT;
		case DatabaseMetaData.procedureColumnReturn:
			return JDBC40Translation.FUNCTION_RETURN;
		default:
			return JDBC40Translation.FUNCTION_PARAMETER_UNKNOWN;	
		}
    }

	private boolean isProcedure;
	// state for procedures.
	private RoutineAliasInfo procedure;
	private int paramCursor;
    private short method_count;
    private short param_number;

    private TypeDescriptor sqlType;
    private String columnName;
    private short columnType;
    private final short nullable;

    public ResultSetMetaData getMetaData()
    {        
        return metadata;
    }

    //
    // Instantiates the vti given a class name and methodname.
    // 
    // @exception SQLException  Thrown if there is a SQL error.
    //
    //
    public GetProcedureColumns(AliasInfo aliasInfo, String aliasType) throws SQLException
    {
		// compile time aliasInfo will be null.
		if (aliasInfo != null) {
			isProcedure = aliasType.equals("P");
			isFunction = aliasType.equals("F");
			procedure = (RoutineAliasInfo) aliasInfo;
			method_count = (short) procedure.getParameterCount();
		}
		if (aliasType == null) { 
			nullable = 0;
			return;
		}

		if (isFunction) {
			nullable = (short) JDBC40Translation.FUNCTION_NULLABLE;
			sqlType = procedure.getReturnType();
			columnName = "";  // COLUMN_NAME is VARCHAR NOT NULL
			columnType = (short) JDBC40Translation.FUNCTION_RETURN;
			paramCursor = -2;
			return;
		}
		nullable = (short) DatabaseMetaData.procedureNullable;

		paramCursor = -1;
    }

    public boolean next() throws SQLException {
		if (++paramCursor >= procedure.getParameterCount())
			return false;

		if (paramCursor > -1) {
			sqlType      = procedure.getParameterTypes()[paramCursor];
			columnName   = procedure.getParameterNames()[paramCursor];
			columnType   = 
				(short)translate(procedure.getParameterModes()[paramCursor]);
		}
		param_number = (short) paramCursor;
		return true;
	}   

    //
    // Get the value of the specified data type from a column.
    // 
    // @exception SQLException  Thrown if there is a SQL error.
    //
    //
    public String getString(int column) throws SQLException 
    {
        switch (column) 
        {
		case 1: // COLUMN_NAME:
			return columnName;

		case 4: //_TYPE_NAME: 
               return sqlType.getTypeName();
               
		case 10: // REMARKS:
                return null;

            default: 
                return super.getString(column);  // throw exception
        }
    }

    //
    // Get the value of the specified data type from a column.
    // 
    // @exception SQLException  Thrown if there is a SQL error.
    //
    //
    public int getInt(int column) throws SQLException 
    {
        switch (column) 
        {
        case 3: // DATA_TYPE:
            if (sqlType != null) {
                return sqlType.getJDBCTypeId();
            }
            return java.sql.Types.JAVA_OBJECT;

		case 5: // PRECISION:
                if (sqlType != null)
                {
                    int type = sqlType.getJDBCTypeId();
                    if (DataTypeDescriptor.isNumericType(type))
                        return sqlType.getPrecision();
                    else if (type == Types.DATE || type == Types.TIME
                             || type == Types.TIMESTAMP)
                        return DataTypeUtilities.getColumnDisplaySize(type, -1);
                    else
                        return sqlType.getMaximumWidth();
                }

                // No corresponding SQL type
                return 0;

		case 6: // LENGTH (in bytes):
                if (sqlType != null)
                    return sqlType.getMaximumWidthInBytes();

                // No corresponding SQL type
                return 0;
          
            default:
                return super.getInt(column);  // throw exception
        }
    }

    //
    // Get the value of the specified data type from a column.
    // 
    // @exception SQLException  Thrown if there is a SQL error.
    //
    //
    public short getShort(int column) throws SQLException 
    {
        switch (column) 
        {
		case 2: // COLUMN_TYPE:
			return columnType;

		case 7: // SCALE:
                if (sqlType != null)
                    return (short)sqlType.getScale();

                // No corresponding SQL type
                return 0;

		case 8: // RADIX:
                if (sqlType != null)
                {
                    int sqlTypeID = sqlType.getJDBCTypeId();
                    if (sqlTypeID == java.sql.Types.REAL ||
                        sqlTypeID == java.sql.Types.FLOAT ||
                        sqlTypeID == java.sql.Types.DOUBLE)
                    {
                        return 2;
                    }
                    return 10;
                }

                // No corresponding SQL type
                return 0;

		//FIXME
		case 9: // NULLABLE:
			return nullable;

		case 11: // METHOD_ID: 
                return method_count;

		case 12: // PARAMETER_ID: 
                return param_number;

            default:
                return super.getShort(column);  // throw exception
        }
    }

    public void close()
    {
    }

	/*
	** Metadata
	*/
	private static final ResultColumnDescriptor[] columnInfo = {

		EmbedResultSetMetaData.getResultColumnDescriptor("COLUMN_NAME",				 Types.VARCHAR, false, 128),
		EmbedResultSetMetaData.getResultColumnDescriptor("COLUMN_TYPE",				 Types.SMALLINT, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("DATA_TYPE",				 Types.INTEGER, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("TYPE_NAME",				 Types.VARCHAR, false, 22),
		EmbedResultSetMetaData.getResultColumnDescriptor("PRECISION",				 Types.INTEGER, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("LENGTH",					 Types.INTEGER, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("SCALE",					 Types.SMALLINT, false),

		EmbedResultSetMetaData.getResultColumnDescriptor("RADIX",					 Types.SMALLINT, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("NULLABLE",				 Types.SMALLINT, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("REMARKS",					 Types.VARCHAR, true, 22),
		EmbedResultSetMetaData.getResultColumnDescriptor("METHOD_ID",				 Types.SMALLINT, false),
		EmbedResultSetMetaData.getResultColumnDescriptor("PARAMETER_ID",			 Types.SMALLINT, false),
	};
	private static final ResultSetMetaData metadata = new EmbedResultSetMetaData(columnInfo);
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
