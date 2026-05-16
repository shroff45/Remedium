package com.remedium.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "remedium.db"
        const val DB_VERSION = 12
        private const val TAG = "RemediumDB"
    }

    private val dbPath: String = context.getDatabasePath(DB_NAME).path

    fun ensureDatabase() {
        val dbFile = File(dbPath)

        if (!dbFile.exists()) {
            Log.i(TAG, "Database not found on device. Copying from assets.")
            dbFile.parentFile?.mkdirs()
            copyDatabaseFromAssets()
            stampVersion()
            return
        }

        val storedVersion = readStoredVersion()
        if (storedVersion != DB_VERSION) {
            Log.i(TAG, "DB version mismatch (stored=$storedVersion, expected=$DB_VERSION). Recopying.")
            dbFile.delete()
            copyDatabaseFromAssets()
            stampVersion()
        } else {
            Log.i(TAG, "Database version OK (v$storedVersion).")
        }
    }

    private fun readStoredVersion(): Int {
        return try {
            val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            val v = db.version
            db.close()
            v
        } catch (e: Exception) {
            Log.w(TAG, "Could not read DB version: ${e.message}. Treating as stale.")
            -1
        }
    }

    private fun stampVersion() {
        try {
            val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
            db.version = DB_VERSION
            db.close()
        } catch (e: Exception) {
            Log.e(TAG, "Could not stamp DB version: ${e.message}")
        }
    }

    private fun copyDatabaseFromAssets() {
        context.assets.open(DB_NAME).use { input ->
            FileOutputStream(dbPath).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Returns a read-only DB connection with foreign keys enforced.
     * Foreign keys must be enabled per-connection in SQLite — the pragma
     * in the schema file alone does not persist. Every call to openDb()
     * must explicitly enable it for consistency with the build-time policy.
     */
    fun openDb(): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(
            dbPath, null, SQLiteDatabase.OPEN_READONLY
        )
        try {
            db.rawQuery("PRAGMA foreign_keys = ON", null).use { it.moveToFirst() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable foreign_keys pragma: ${e.message}")
        }
        return db
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
}