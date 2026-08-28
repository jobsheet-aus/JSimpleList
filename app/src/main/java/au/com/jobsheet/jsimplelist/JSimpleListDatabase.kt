package au.com.jobsheet.jsimplelist

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        ListEntity::class,
        ItemEntity::class
    ],
    version = 4
)
abstract class JSimpleListDatabase : RoomDatabase() {
    abstract fun dao(): JSimpleListDao
}
