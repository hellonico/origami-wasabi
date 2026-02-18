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
            (Origamis.id eq id)
        }
    }

    suspend fun addOrigami(origami: Origami): Origami {
        var key = 0
        dbQuery {
            key = (Origamis.insert {
                it[hash] = origami.hash
                it[date] = System.currentTimeMillis()
                it[tags] = origami.tags
                it[likes] = 0
                it[shares] = 0
                it[comments] = "[]"
                it[lastUpdated] = System.currentTimeMillis()
                it[workspaceId] = origami.workspaceId
            } get Origamis.id)
        }
        return getOrigami(key)!!
    }

    private fun toOrigami(row: ResultRow): Origami =
        Origami(
            id = row[Origamis.id],
            hash = row[Origamis.hash],
            date = row[Origamis.date],
            tags = row[Origamis.tags],
            likes = row[Origamis.likes],
            comments = row[Origamis.comments],
            lastUpdated = row[Origamis.lastUpdated],
            shares = row[Origamis.shares],
            workspaceId = row[Origamis.workspaceId]
        )

    suspend fun getAll(limit: Int = 100, offset: Long = 0, tag: String? = null, sortBy: String = "id", workspaceId: String = "default"): List<Origami> = dbQuery {
        val query = Origamis.select { Origamis.workspaceId eq workspaceId }
        
        if (tag != null && tag.isNotEmpty()) {
            query.andWhere { Origamis.tags like "%$tag%" }
        }
        
        val sortOrder = if(sortBy == "recent") {
            Origamis.lastUpdated to SortOrder.DESC
        } else {
            Origamis.id to SortOrder.DESC
        }
        
        query.orderBy(sortOrder)
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
            it[lastUpdated] = System.currentTimeMillis()
        }
    }

    suspend fun updateLikes(id: Int, count: Int) = dbQuery {
        Origamis.update({ Origamis.id eq id }) {
            it[likes] = count
            it[lastUpdated] = System.currentTimeMillis()
        }
    }

    suspend fun updateShares(id: Int, count: Int) = dbQuery {
        Origamis.update({ Origamis.id eq id }) {
            it[shares] = count
            it[lastUpdated] = System.currentTimeMillis()
        }
    }

    suspend fun updateComments(id: Int, json: String) = dbQuery {
        Origamis.update({ Origamis.id eq id }) {
            it[Origamis.comments] = json
            it[lastUpdated] = System.currentTimeMillis()
        }
    }

}
