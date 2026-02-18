package model

import kotlinx.serialization.Serializable
import model.Widgets.autoIncrement
import org.jetbrains.exposed.sql.Table


object Origamis : Table() {
    val id = integer("id").autoIncrement()
    val hash = integer("hash")
    val date = long("date")
    val tags = varchar("tags", 255).default("")
    val likes = integer("likes").default(0)
    val comments = text("comments").default("[]")
    val lastUpdated = long("last_updated").default(System.currentTimeMillis())
    val shares = integer("shares").default(0)
    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class Origami(
    val id: Int,
    val hash: Int,
    val date: Long,
    val tags: String,
    val likes: Int = 0,
    val comments: String = "[]",
    val lastUpdated: Long = 0,
    val shares: Int = 0
)
