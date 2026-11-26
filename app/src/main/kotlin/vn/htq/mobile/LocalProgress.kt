package vn.htq.mobile

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import vn.htq.core.*

class LocalProgress(private val context: Context, catalog: Catalog): SQLiteOpenHelper(context, "htq_progress.db", null, 1), ProgressStore {
    private val sessionPolicy = SessionPolicy(catalog)
    private val rewardPolicy = RewardPolicy(catalog)
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        val schema = context.resources.openRawResource(R.raw.storage_schema).bufferedReader().use { it.readText() }
        schema.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("No destructive migration allowed") }
    override fun saveSession(snapshot: Obj) {
        val safe = requireNotNull(sessionPolicy.sanitize(snapshot)) { "Invalid session" }
        writableDatabase.insertWithOnConflict("session_state", null, ContentValues().apply { put("slot", 1); put("payload", Json.encode(safe)) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    override fun loadSession(): Obj? = readableDatabase.rawQuery("SELECT payload FROM session_state WHERE slot=1", null).use { c ->
        if (!c.moveToFirst()) null else runCatching { sessionPolicy.sanitize(Json.parse(c.getString(0)).obj()) }.getOrNull()
    }
    private fun <T> transaction(fn: (SQLiteDatabase) -> T): T {
        val db = writableDatabase; db.beginTransaction(); try { val result = fn(db); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    override fun commit(ticket: CompletionTicket): Boolean {
        val reward = rewardPolicy.entitlement(ticket)
        return transaction { db ->
            val inserted = db.insertWithOnConflict("completions", null, ContentValues().apply { put("task_id", reward.taskId); put("kind", reward.completionKind) }, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted == -1L) false else {
                db.insertOrThrow("reward_ledger", null, ContentValues().apply { put("task_id", reward.taskId); put("item_id", reward.itemId); put("quantity", reward.quantity); put("claimed", 0) }); true
            }
        }
    }
    override fun claim(taskId: String): Boolean = transaction { db ->
        val entry = db.rawQuery("SELECT item_id, quantity FROM reward_ledger WHERE task_id=? AND claimed=0", arrayOf(taskId)).use { c -> if (c.moveToFirst()) c.getString(0) to c.getInt(1) else null }
        if (entry == null) false else {
            val changed = db.update("reward_ledger", ContentValues().apply { put("claimed", 1) }, "task_id=? AND claimed=0", arrayOf(taskId))
            check(changed == 1)
            db.execSQL("INSERT OR IGNORE INTO garden_inventory(item_id,quantity) VALUES(?,0)", arrayOf(entry.first))
            db.execSQL("UPDATE garden_inventory SET quantity=quantity+? WHERE item_id=?", arrayOf(entry.second, entry.first)); true
        }
    }
    override fun inventory(): Map<String, Int> = readableDatabase.rawQuery("SELECT item_id,quantity FROM garden_inventory", null).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) } }
    override fun completedCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM completions", null).use { c -> c.moveToFirst(); c.getInt(0) }
    fun place(taskId: String, x: Double, y: Double): Boolean = transaction { db ->
        require(x in 0.0..1.0 && y in 0.0..1.0)
        val owned = db.rawQuery("SELECT claimed FROM reward_ledger WHERE task_id=?", arrayOf(taskId)).use { c -> c.moveToFirst() && c.getInt(0) == 1 }
        if (!owned) false else { db.insertWithOnConflict("garden_placements", null, ContentValues().apply { put("task_id", taskId); put("x", x); put("y", y) }, SQLiteDatabase.CONFLICT_REPLACE); true }
    }
    fun putAway(taskId: String) { writableDatabase.delete("garden_placements", "task_id=?", arrayOf(taskId)) }
}
