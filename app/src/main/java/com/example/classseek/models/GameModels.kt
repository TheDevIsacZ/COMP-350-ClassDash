package com.example.classseek.models

import com.google.firebase.Timestamp

data class GameState(
    val id: String = "",
    val type: String = "", // e.g., "chess"
    val playerWhite: String = "", // userId
    val playerBlack: String = "", // userId
    val currentTurn: String = "", // userId of whose turn it is
    val state: String = "", // Game-specific state (e.g., FEN for chess)
    val status: String = "active", // "active", "finished"
    val winnerId: String? = null,
    val lastMoveAt: Timestamp? = null,
    val moveHistory: List<String> = emptyList(),
    val messageId: String = "" // Added to track which chat bubble to update
)
