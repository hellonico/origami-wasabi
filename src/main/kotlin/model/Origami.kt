package model

import kotlinx.serialization.Serializable
import model.Widgets.autoIncrement
import org.jetbrains.exposed.sql.Table


object Origamis : Table() {
    val id = integer("id").autoIncrement()
    val hash = integer("hash")
    val date = long("date")
    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class Origami(
    val id: Int,
    val hash: Int,
    val date: Long
)
