/*

   Derby - Class org.apache.derby.iapi.sql.execute.ExecutionContext

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.iapi.sql.execute;

import org.apache.derby.iapi.services.context.Context;

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.sql.ResultSet;

/**
 * ExecutionContext stores the factories that are to be used by
 * the current connection. It also provides execution services
 * for statement atomicity.
 *
 */
public interface ExecutionContext extends Context {

	/**
	 * this is the ID we expect execution contexts
	 * to be stored into a context manager under.
	 */
	String CONTEXT_ID = "ExecutionContext";
	
	
	/* Constants for scan isolation levels. */
	public static final int UNSPECIFIED_ISOLATION_LEVEL = 0;
	public static final int READ_UNCOMMITTED_ISOLATION_LEVEL = 1;
	public static final int READ_COMMITTED_ISOLATION_LEVEL = 2;
	public static final int REPEATABLE_READ_ISOLATION_LEVEL = 3;
	public static final int SERIALIZABLE_ISOLATION_LEVEL = 4;

	public static final int[] CS_TO_JDBC_ISOLATION_LEVEL_MAP = {
		java.sql.Connection.TRANSACTION_NONE,				// UNSPECIFIED_ISOLATION_LEVEL
		java.sql.Connection.TRANSACTION_READ_UNCOMMITTED,	// READ_UNCOMMITTED_ISOLATION_LEVEL
		java.sql.Connection.TRANSACTION_READ_COMMITTED,		// READ_COMMITTED_ISOLATION_LEVEL
		java.sql.Connection.TRANSACTION_REPEATABLE_READ,	// REPEATABLE_READ_ISOLATION_LEVEL		
		java.sql.Connection.TRANSACTION_SERIALIZABLE		// SERIALIZABLE_ISOLATION_LEVEL
	};

	public static final String[][] CS_TO_SQL_ISOLATION_MAP = {
		{ "  "},					// UNSPECIFIED_ISOLATION_LEVEL
		{ "UR", "DIRTY READ", "READ UNCOMMITTED"},
		{ "CS", "CURSOR STABILITY", "READ COMMITTED"},
		{ "RS"},		// read stability	
		{ "RR", "REPEATABLE READ", "SERIALIZABLE"}
	};

	/**
	 * Get the ResultSetStatisticsFactory from this ExecutionContext.
	 *
	 * @return	The result set statistics factory associated with this
	 *		ExecutionContext
	 *
	 * @exception StandardException		Thrown on error
	 */
	ResultSetStatisticsFactory getResultSetStatisticsFactory()
								throws StandardException;

	/**
	 * Get the ExecutionFactory from this ExecutionContext.
	 *
	 * @return	The Execution factory associated with this
	 *		ExecutionContext
	 */
	ExecutionFactory getExecutionFactory();
}
