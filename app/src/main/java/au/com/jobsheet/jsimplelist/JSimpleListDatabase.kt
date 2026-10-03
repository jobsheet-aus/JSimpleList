package au.com.jobsheet.jsimplelist

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        ListEntity::class,
        ItemEntity::class,
        ListAccountEntity::class,
        PendingReorderEntity::class
    ],
    version = 8
)
abstract class JSimpleListDatabase : RoomDatabase() {
    abstract fun dao(): JSimpleListDao
}
