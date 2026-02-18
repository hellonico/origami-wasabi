package service

import model.*
import org.jetbrains.exposed.sql.*
import service.DatabaseFactory.dbQuery

class OrigamiService {


    suspend fun getOrigami(id: Int): Origami? = dbQuery {
        Origamis.select {
            (Origamis.id eq id)
        }.mapNotNull { toOrigami(it) }
            .singleOrNull()
    }

    suspend fun deleteOrigami(id: Int) = dbQuery {
        Origamis.deleteWhere {
            (Origamis.hash eq id)
        }
    }

    suspend fun addOrigami(origami: Origami): Origami {
        var key = 0
        dbQuery {
            key = (Origamis.insert {
                it[hash] = origami.hash
                it[date] = System.currentTimeMillis()
                it[tags] = origami.tags
            } get Origamis.id)
        }
        return getOrigami(key)!!
    }

    private fun toOrigami(row: ResultRow): Origami =
        Origami(
            id = row[Origamis.id],
            hash = row[Origamis.hash],
            date = row[Origamis.date],
            tags = row[Origamis.tags]
        )

    suspend fun getAll(): List<Origami> = dbQuery {
        Origamis.selectAll().map { toOrigami(it) }
    }

    suspend fun updateTags(id: Int, tags: String) = dbQuery {
        Origamis.update({ Origamis.id eq id }) {
            it[Origamis.tags] = tags
        }
    }

}
