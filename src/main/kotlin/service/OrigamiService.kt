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

    suspend fun getAll(limit: Int = 100, offset: Long = 0, tag: String? = null): List<Origami> = dbQuery {
        val query = if (tag != null && tag.isNotEmpty()) {
            Origamis.select { Origamis.tags like "%$tag%" }
        } else {
            Origamis.selectAll()
        }
        
        query.orderBy(Origamis.id to SortOrder.DESC)
            .limit(limit, offset)
            .map { toOrigami(it) }
    }

    suspend fun getAllTags(): List<String> = dbQuery {
        Origamis.slice(Origamis.tags)
            .selectAll()
            .flatMap { it[Origamis.tags].split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    suspend fun updateTags(id: Int, tags: String) = dbQuery {
        Origamis.update({ Origamis.id eq id }) {
            it[Origamis.tags] = tags
        }
    }

}
