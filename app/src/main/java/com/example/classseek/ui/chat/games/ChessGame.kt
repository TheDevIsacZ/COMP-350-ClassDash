package com.example.classseek.ui.chat.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.classseek.data.ChatRepository
import com.example.classseek.models.GameState
import kotlinx.coroutines.launch
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.Piece
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.media.AudioManager
import com.example.classseek.ui.chat.ChatUserProfile

/**
 * Full-screen Chess Game Overlay.
 * Manages the real-time game state from Firestore and handles move synchronization.
 */
@Composable
fun ChessGameOverlay(
    chatId: String,
    gameId: String,
    myUid: String,
    userProfiles: Map<String, ChatUserProfile>, // Added to fetch personalized names
    repo: ChatRepository,
    onDismiss: () -> Unit
) {
    // Current state of the game fetched from the 'games' collection
    var gameState by remember { mutableStateOf<GameState?>(null) }
    val scope = rememberCoroutineScope()
    
    // Core chess engine board from kchesslib
    val chessBoard = remember { Board() }
    
    // List of legal moves for the current position, refreshed each turn
    var legalMoves by remember { mutableStateOf<List<Move>>(emptyList()) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }

    // Helper to get personalized name
    fun getDisplayName(uid: String): String {
        val profile = userProfiles[uid]
        return when {
            profile?.displayName?.isNotBlank() == true -> profile.displayName
            profile?.email?.isNotBlank() == true -> profile.email.substringBefore("@")
            else -> "Player"
        }
    }

    // Helper to get possessive name (e.g. Isac's)
    fun getPossessiveName(uid: String): String {
        val name = getDisplayName(uid)
        return if (name.endsWith("s")) "$name'" else "$name's"
    }

    // Listen to real-time updates for this specific game
    DisposableEffect(gameId) {
        val listener = repo.listenToGame(gameId) { updated ->
            gameState = updated
            updated?.let { 
                // Sync the local engine board with the Firestore FEN string
                chessBoard.loadFromFen(it.state)
                
                // Only generate legal moves if it's the current user's turn
                if (it.currentTurn == myUid && it.status == "active") {
                    legalMoves = chessBoard.legalMoves().toList()
                } else {
                    legalMoves = emptyList()
                }
            }
        }
        onDispose {
            listener.remove()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val game = gameState
                if (game == null) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = "Chess",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(16.dp))

                    ChessBoard(
                        board = chessBoard,
                        myUid = myUid,
                        currentTurn = game.currentTurn,
                        playerWhite = game.playerWhite,
                        legalMoves = legalMoves,
                        isCheck = chessBoard.isKingAttacked,
                        onMove = { move ->
                            // Haptic feedback (Tactile "thump" on piece placement)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            
                            // Context-aware sound (Capture clack vs standard Move click)
                            val isCapture = chessBoard.getPiece(move.to) != Piece.NONE
                            val sound = if (isCapture) AudioManager.FX_KEYPRESS_STANDARD else AudioManager.FX_KEY_CLICK
                            audioManager.playSoundEffect(sound)

                            // Get move in Standard Algebraic Notation (e.g., Nf3)
                            val sanMove = chessBoard.boardToSan(move)
                            legalMoves = emptyList() // Prevent multiple move clicks
                            
                            scope.launch {
                                try {
                                    // Togle turn between White and Black players
                                    val nextTurn = if (game.currentTurn == game.playerWhite) game.playerBlack else game.playerWhite
                                    
                                    // Check for game over conditions using kchesslib
                                    val isMated = chessBoard.isMated
                                    val isDraw = chessBoard.isDraw
                                    val status = if (isMated || isDraw) "finished" else "active"
                                    val winnerId = if (isMated) game.currentTurn else null

                                    // Status message displayed in the chat bubble and inbox (Personalized names)
                                    val statusMessage = when {
                                        isMated -> "🏆 ${getDisplayName(winnerId ?: "")} won."
                                        isDraw -> "🤝 Game Over: Draw."
                                        else -> "🎮 ${getPossessiveName(nextTurn)} Turn"
                                    }

                                    // Push all updates to Firestore in a single atomic batch
                                    repo.updateGameState(
                                        chatId = chatId,
                                        gameId = gameId,
                                        newState = chessBoard.fen,
                                        nextTurnUserId = nextTurn,
                                        move = sanMove,
                                        status = status,
                                        winnerId = winnerId,
                                        statusMessage = statusMessage
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("ChessGame", "Failed to update game state", e)
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(24.dp))

                    // Move History Section (Chess.com horizontal style)
                    if (game.moveHistory.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(48.dp)
                                .background(Color(0xFF262421), MaterialTheme.shapes.small)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val pairs = game.moveHistory.chunked(2)
                                items(pairs.size) { index ->
                                    val pair = pairs[index]
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF999491)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        MoveTextWithIcon(
                                            text = pair[0],
                                            isWhite = true
                                        )
                                        if (pair.size > 1) {
                                            Spacer(Modifier.width(8.dp))
                                            MoveTextWithIcon(
                                                text = pair[1],
                                                isWhite = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    val isMyTurn = game.currentTurn == myUid
                    val gameStatus = game.status
                    
                    if (gameStatus == "finished") {
                        val resultText = when {
                            game.winnerId == myUid -> "You Win! 🏆"
                            game.winnerId != null -> "${getDisplayName(game.winnerId)} Wins!"
                            else -> "Draw! 🤝"
                        }
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        val turnName = getDisplayName(game.currentTurn)
                        val turnStatus = if (game.currentTurn == myUid) "Your Turn" else "$turnName's Turn"
                        Text(
                            text = turnStatus,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isMyTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    Button(onClick = onDismiss) {
                        Text("Close Game")
                    }
                }
            }
        }
    }
}

@Composable
fun MoveTextWithIcon(text: String, modifier: Modifier = Modifier, isWhite: Boolean) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pieceChar = text.firstOrNull()
        val pieceSymbol = when (pieceChar) {
            'N' -> if (isWhite) "♘" else "♞"
            'B' -> if (isWhite) "♗" else "♝"
            'R' -> if (isWhite) "♖" else "♜"
            'Q' -> if (isWhite) "♕" else "♛"
            'K' -> if (isWhite) "♔" else "♚"
            else -> null
        }
        
        if (pieceSymbol != null) {
            Text(
                text = pieceSymbol,
                fontSize = 18.sp,
                color = if (isWhite) Color.White else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 2.dp)
            )
            Text(
                text = text.substring(1),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Standard 8x8 Chess Board UI.
 * Handles board coordinate mapping, piece rendering, and move interaction.
 */
@Composable
fun ChessBoard(
    board: Board,
    myUid: String,
    currentTurn: String,
    playerWhite: String,
    legalMoves: List<Move>,
    isCheck: Boolean,
    onMove: (Move) -> Unit
) {
    val isWhite = myUid == playerWhite
    val isMyTurn = currentTurn == myUid
    val sideToMove = board.sideToMove

    var selectedSquare by remember { mutableStateOf<Square?>(null) }
    
    // Calculate highlighted squares based on the currently selected piece and the engine's legal moves
    val highlightedSquares = remember(selectedSquare, legalMoves) {
        if (selectedSquare == null) emptyList()
        else {
            legalMoves.filter { it.from == selectedSquare }
                .map { it.to }
        }
    }

    Column(
        modifier = Modifier
            .border(2.dp, MaterialTheme.colorScheme.outline)
            .padding(4.dp)
    ) {
        // Iterate through rows and columns to draw the grid.
        // Logic handles board flipping (if playing as black, board is inverted).
        for (uiRow in 0 until 8) {
            Row {
                for (uiCol in 0 until 8) {
                    val file = if (isWhite) uiCol else 7 - uiCol
                    val rank = if (isWhite) 7 - uiRow else uiRow
                    
                    val square = Square.values()[rank * 8 + file]
                    val piece = board.getPiece(square)
                    
                    val isLight = (rank + file) % 2 != 0
                    val isSelected = selectedSquare == square
                    val isHighlighted = highlightedSquares.contains(square)
                    
                    // Logic for King in Check red glow - triggers when the engine detects an attack on the active King
                    val isKingInCheck = isCheck && 
                        ((sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE && piece == Piece.WHITE_KING) ||
                         (sideToMove == com.github.bhlangonijr.chesslib.Side.BLACK && piece == Piece.BLACK_KING))

                    val bgColor = when {
                        isKingInCheck -> Color.Red.copy(alpha = 0.7f) // Visual alert for Check
                        isSelected -> Color(0xFFF7F769) // Yellow selection highlight
                        isLight -> Color(0xFFDEB887) // Classic wooden theme (Light)
                        else -> Color(0xFF8B4513)    // Classic wooden theme (Dark)
                    }

                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .background(bgColor)
                            .clickable(enabled = isMyTurn) {
                                if (selectedSquare == null) {
                                    // Select a piece if it's the user's turn and the piece belongs to them
                                    val isMyPiece = (sideToMove == piece.pieceSide)
                                    if (piece != Piece.NONE && isMyPiece) {
                                        selectedSquare = square
                                    }
                                } else {
                                    // Check if the click is on a legal move destination
                                    val move = legalMoves.find { it.from == selectedSquare && it.to == square }
                                    if (move != null) {
                                        // Execute move on the local engine and notify the overlay
                                        board.doMove(move)
                                        onMove(move)
                                        selectedSquare = null
                                    } else {
                                        // Re-select if clicking another of the user's pieces
                                        val isMyPiece = (sideToMove == piece.pieceSide)
                                        if (piece != Piece.NONE && isMyPiece) {
                                            selectedSquare = square
                                        } else {
                                            selectedSquare = null
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isHighlighted) {
                            // Draw a small dot on valid destination squares
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                            )
                        }
                        if (piece != Piece.NONE) {
                            val isWhitePiece = piece.pieceSide == com.github.bhlangonijr.chesslib.Side.WHITE
                            
                            Box(contentAlignment = Alignment.Center) {
                                if (isWhitePiece) {
                                    // Layered rendering for White pieces to provide a dark outline/shadow for high visibility
                                    Text(
                                        text = pieceToUnicode(piece),
                                        fontSize = 32.sp,
                                        color = Color.Black.copy(alpha = 0.5f),
                                        modifier = Modifier.offset(x = 1.dp, y = 1.dp)
                                    )
                                }
                                
                                Text(
                                    text = pieceToUnicode(piece),
                                    fontSize = 32.sp,
                                    color = if (isWhitePiece) Color.White else Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sound constants
private const val SOUND_MOVE = AudioManager.FX_KEY_CLICK
private const val SOUND_CAPTURE = AudioManager.FX_KEYPRESS_STANDARD

// Extension to get SAN move
fun Board.boardToSan(move: Move): String {
    val san = this.backup.last().move.toString()
    return if (this.isMated) "$san#" else if (this.isKingAttacked) "$san+" else san
}

fun pieceToUnicode(piece: Piece): String {
    return when (piece) {
        Piece.WHITE_PAWN -> "♟" // Using filled black character for white piece, colored via Compose
        Piece.WHITE_ROOK -> "♜"
        Piece.WHITE_KNIGHT -> "♞"
        Piece.WHITE_BISHOP -> "♝"
        Piece.WHITE_QUEEN -> "♛"
        Piece.WHITE_KING -> "♚"
        Piece.BLACK_PAWN -> "♟"
        Piece.BLACK_ROOK -> "♜"
        Piece.BLACK_KNIGHT -> "♞"
        Piece.BLACK_BISHOP -> "♝"
        Piece.BLACK_QUEEN -> "♛"
        Piece.BLACK_KING -> "♚"
        else -> ""
    }
}
