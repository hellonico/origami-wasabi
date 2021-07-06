package db.migration

import model.Widgets
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class V2__add_widgets: BaseJavaMigration() {
    override fun migrate(context: Context?) {
        transaction {

            Widgets.insert {
                it[name] = "nico widget"
                it[quantity] = 25
                it[dateUpdated] = System.currentTimeMillis()
            }

        }
    }
}