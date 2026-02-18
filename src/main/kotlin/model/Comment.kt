package model

import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val text: String,
    val date: Long,
    val author: String = "User"
)
