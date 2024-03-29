/*
 * Derby - Class org.apache.derby.impl.sql.execute.ScanResultSet
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.derby.impl.sql.execute;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.loader.GeneratedMethod;
import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.ExecutionContext;
import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * Abstract <code>ResultSet</code> class for <code>NoPutResultSet</code>s which
 * contain a scan.
 */
abstract class ScanResultSet extends NoPutResultSetImpl {

    /** If true, the table is marked as table locked in SYS.SYSTABLES. */
    private final boolean tableLocked;
    /** If true, the isolation level is unspecified and must be refreshed on
     * each open. */
    private final boolean unspecifiedIsolationLevel;
    /** The lock mode supplied through the constructor. */
    private final int suppliedLockMode;
    /** Tells whether the isolation level needs to be updated. */
    private boolean isolationLevelNeedsUpdate;

    /** The actual lock mode used. */
    int lockMode;
    /** The scan isolation level. */
    int isolationLevel;

    /** The candidate row. */
    final ExecRow candidate;

    /**
     * Construct a <code>ScanResultSet</code>.
     *
     * @param activation the activation
     * @param resultSetNumber number of the result set (unique within statement)
     * @param resultRowAllocator method which generates rows
     * @param lockMode lock mode (record or table)
     * @param tableLocked true if marked as table locked in SYS.SYSTABLES
     * @param isolationLevel language isolation level for the result set
     * @param optimizerEstimatedRowCount estimated row count
     * @param optimizerEstimatedCost estimated cost
     */
    ScanResultSet(Activation activation, int resultSetNumber,
                  GeneratedMethod resultRowAllocator,
                  int lockMode, boolean tableLocked, int isolationLevel,
                  double optimizerEstimatedRowCount,
                  double optimizerEstimatedCost) throws StandardException {
        super(activation, resultSetNumber,
              optimizerEstimatedRowCount,
              optimizerEstimatedCost);

        this.tableLocked = tableLocked;
        suppliedLockMode = lockMode;

        if (isolationLevel == ExecutionContext.UNSPECIFIED_ISOLATION_LEVEL) {
            unspecifiedIsolationLevel = true;
            isolationLevel = lcc.getCurrentIsolationLevel();
        } else {
            unspecifiedIsolationLevel = false;
        }

        this.lockMode = getLockMode(isolationLevel);
        this.isolationLevel =
            translateLanguageIsolationLevel(isolationLevel);

        /* Only call row allocators once */
        candidate = (ExecRow) resultRowAllocator.invoke(activation);
    }

    /**
     * Initialize the isolation level and the lock mode. If the result set was
     * constructed with an explicit isolation level, or if the isolation level
     * has already been initialized, this is a no-op. All sub-classes should
     * invoke this method from their <code>openCore()</code> methods.
     */
    void initIsolationLevel() {
        if (isolationLevelNeedsUpdate) {
            int languageLevel = lcc.getCurrentIsolationLevel();
            lockMode = getLockMode(languageLevel);
            isolationLevel = translateLanguageIsolationLevel(languageLevel);
            isolationLevelNeedsUpdate = false;
        }
    }

    /**
     * Get the lock mode based on the language isolation level. Always do row
     * locking unless the isolation level is serializable or the table is
     * marked as table locked.
     *
     * @param languageLevel the (language) isolation level
     * @return lock mode
     */
    private int getLockMode(int languageLevel) {
        /* NOTE: always do row locking on READ COMMITTED/UNCOMITTED scans,
         * unless the table is marked as table locked (in sys.systables)
         * This is to improve concurrency.  Also see FromBaseTable's
         * updateTargetLockMode (KEEP THESE TWO PLACES CONSISTENT!
         * bug 4318).
         */
        /* NOTE: always do row locking on READ COMMITTED/UNCOMMITTED
         *       and repeatable read scans unless the table is marked as
         *       table locked (in sys.systables).
         *
         *       We always get instantaneous locks as we will complete
         *       the scan before returning any rows and we will fully
         *       requalify the row if we need to go to the heap on a next().
         */
        if (tableLocked ||
                (languageLevel ==
                     ExecutionContext.SERIALIZABLE_ISOLATION_LEVEL)) {
            return suppliedLockMode;
        } else {
            return TransactionController.MODE_RECORD;
        }
    }

    /**
     * Translate isolation level from language to store.
     *
     * @param languageLevel language isolation level
     * @return store isolation level
     */
    private int translateLanguageIsolationLevel(int languageLevel) {

        switch (languageLevel) {
        case ExecutionContext.READ_UNCOMMITTED_ISOLATION_LEVEL:
            return TransactionController.ISOLATION_READ_UNCOMMITTED;
        case ExecutionContext.READ_COMMITTED_ISOLATION_LEVEL:
            /*
             * Now we see if we can get instantaneous locks
             * if we are getting share locks.
             * (For example, we can get instantaneous locks
             * when doing a bulk fetch.)
             */
            if (!canGetInstantaneousLocks()) {
                return TransactionController.ISOLATION_READ_COMMITTED;
            }
            return TransactionController.ISOLATION_READ_COMMITTED_NOHOLDLOCK;
        case ExecutionContext.REPEATABLE_READ_ISOLATION_LEVEL:
            return TransactionController.ISOLATION_REPEATABLE_READ;
        case ExecutionContext.SERIALIZABLE_ISOLATION_LEVEL:
            return TransactionController.ISOLATION_SERIALIZABLE;
        }

        if (SanityManager.DEBUG) {
            SanityManager.THROWASSERT("Unknown isolation level - " +
                                      languageLevel);
        }

        return 0;
    }

    /**
     * Can we get instantaneous locks when getting share row
     * locks at READ COMMITTED.
     */
    abstract boolean canGetInstantaneousLocks();

    /**
     * Return the isolation level of the scan in the result set.
     */
    public int getScanIsolationLevel() {
        return isolationLevel;
    }

    /**
     * Close the result set.
     *
     * @exception StandardException if an error occurs
     */
    public void close() throws StandardException {
        // need to update isolation level on next open if it was unspecified
        isolationLevelNeedsUpdate = unspecifiedIsolationLevel;
        // Prepare row array for reuse (DERBY-827).
        candidate.resetRowArray();
        super.close();
    }
}
