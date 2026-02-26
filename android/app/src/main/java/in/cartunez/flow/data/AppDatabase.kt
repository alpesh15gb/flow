package `in`.cartunez.flow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Transaction::class, Party::class, Slip::class, SlipCollection::class, Category::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun partyDao(): PartyDao
    abstract fun slipDao(): SlipDao
    abstract fun slipCollectionDao(): SlipCollectionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS parties (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS slips (
                        id TEXT PRIMARY KEY NOT NULL,
                        partyId TEXT NOT NULL,
                        amount REAL NOT NULL,
                        amountPaid REAL NOT NULL DEFAULT 0,
                        date TEXT NOT NULL,
                        imageUri TEXT,
                        status TEXT NOT NULL,
                        linkedTxId TEXT,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(partyId) REFERENCES parties(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_slips_partyId ON slips(partyId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS slip_collections (
                        id TEXT PRIMARY KEY NOT NULL,
                        partyId TEXT NOT NULL,
                        amountPaid REAL NOT NULL,
                        date TEXT NOT NULL,
                        note TEXT,
                        FOREIGN KEY(partyId) REFERENCES parties(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_slip_collections_partyId ON slip_collections(partyId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parties ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE slips ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE slip_collections ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE slips ADD COLUMN dueDate TEXT")
                db.execSQL("ALTER TABLE slips ADD COLUMN isPayable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_slips_dueDate ON slips(dueDate)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flow.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
