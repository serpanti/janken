import sqlite3InitModule from './sqlite/index.mjs';

const databases = new Map();
const statements = new Map();
let nextDatabaseId = 0;
let nextStatementId = 0;

const sqliteReady = (async () => {
  try {
    const sqlite = await sqlite3InitModule();
    const pool = await sqlite.installOpfsSAHPoolVfs({
      directory: '.janken-history',
      preserveOnInitFailure: true,
    });
    return { sqlite, pool };
  } catch (error) {
    return { error };
  }
})();

// Commands and response fields follow AndroidX WebWorkerSQLiteDriver.
self.onmessage = async ({ data: { id, data: command } }) => {
  try {
    const { sqlite, pool, error } = await sqliteReady;
    if (error) throw error;
    const data = execute(command, sqlite, pool);
    if (data !== undefined) self.postMessage({ id, data });
  } catch (error) {
    self.postMessage({ id, error: String(error.message || error) });
  }
};

function execute(command, sqlite, pool) {
  switch (command.cmd) {
    case 'open': {
      const db = new pool.OpfsSAHPoolDb(command.fileName);
      const databaseId = nextDatabaseId++;
      databases.set(databaseId, db);
      return { databaseId };
    }
    case 'prepare': {
      const statement = databases.get(command.databaseId).prepare(command.sql);
      const statementId = nextStatementId++;
      const columnNames = Array.from({ length: statement.columnCount }, (_, index) =>
        sqlite.capi.sqlite3_column_name(statement, index));
      statements.set(statementId, statement);
      return {
        statementId,
        parameterCount: sqlite.capi.sqlite3_bind_parameter_count(statement),
        columnNames,
      };
    }
    case 'step': {
      const statement = statements.get(command.statementId);
      const rows = [];
      const columnTypes = [];
      statement.reset();
      statement.clearBindings();
      for (const [index, value] of command.bindings.entries()) {
        statement.bind(index + 1, value ?? null);
      }
      while (statement.step()) {
        if (rows.length === 0) {
          for (let index = 0; index < statement.columnCount; index++) {
            columnTypes.push(sqlite.capi.sqlite3_column_type(statement, index));
          }
        }
        rows.push(statement.get([]));
      }
      return { rows, columnTypes };
    }
    case 'close': {
      if (command.statementId != null) {
        statements.get(command.statementId).finalize();
        statements.delete(command.statementId);
      }
      if (command.databaseId != null) {
        databases.get(command.databaseId).close();
        databases.delete(command.databaseId);
      }
      return;
    }
    default:
      throw new Error('Unknown SQLite worker command: ' + command.cmd);
  }
}
