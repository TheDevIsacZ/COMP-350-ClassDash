package com.example.classseek.ui.chat.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import android.view.SoundEffectConstants

@Composable
fun ChessGameOverlay(
    chatId: String,
    gameId: String,
    myUid: String,
    repo: ChatRepository,
    onDismiss: () -> Unit
) {
    var gameState by remember { mutableStateOf<GameState?>(null) }
    val scope = rememberCoroutineScope()
    
    val chessBoard = remember { Board() }
    var legalMoves by remember { mutableStateOf<List<Move>>(emptyList()) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }

    DisposableEffect(gameId) {
        val listener = repo.listenToGame(gameId) { updated ->
            gameState = updated
            updated?.let { 
                chessBoard.loadFromFen(it.state)
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
                            // Haptic feedback (Thump)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            
                            // Context-aware sound (Capture vs Move)
                            val isCapture = chessBoard.getPiece(move.to) != Piece.NONE
                            val sound = if (isCapture) AudioManager.FX_KEYPRESS_STANDARD else AudioManager.FX_KEY_CLICK
                            audioManager.playSoundEffect(sound)

                            val sanMove = chessBoard.boardToSan(move)
                            legalMoves = emptyList()
                            scope.launch {
                                try {
                                    val nextTurn = if (game.currentTurn == game.playerWhite) game.playerBlack else game.playerWhite
                                    
                                    // Check for game over conditions
                                    val isMated = chessBoard.isMated
                                    val isDraw = chessBoard.isDraw
                                    val status = if (isMated || isDraw) "finished" else "active"
                                    val winnerId = if (isMated) game.currentTurn else null

                                    val statusMessage = when {
                                        isMated -> "🏆 Checkmate! ${if (winnerId == myUid) "You" else "Opponent"} won."
                                        isDraw -> "🤝 Game Over: Draw."
                                        else -> if (nextTurn == myUid) "🎮 Your Turn" else "🎮 Opponent's Turn"
                                    }

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
                            androidx.compose.foundation.lazy.LazyRow(
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
                            game.winnerId != null -> "Opponent Wins!"
                            else -> "Draw! 🤝"
                        }
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = if (isMyTurn) "Your Turn" else "Opponent's Turn",
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
                    
                    val isKingInCheck = isCheck && 
                        ((sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE && piece == Piece.WHITE_KING) ||
                         (sideToMove == com.github.bhlangonijr.chesslib.Side.BLACK && piece == Piece.BLACK_KING))

                    val bgColor = when {
                        isKingInCheck -> Color.Red.copy(alpha = 0.7f)
                        isSelected -> Color(0xFFF7F769)
                        isLight -> Color(0xFFDEB887)
                        else -> Color(0xFF8B4513)
                    }

                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .background(bgColor)
                            .clickable(enabled = isMyTurn) {
                                if (selectedSquare == null) {
                                    val isMyPiece = (sideToMove == piece.pieceSide)
                                    if (piece != Piece.NONE && isMyPiece) {
                                        selectedSquare = square
                                    }
                                } else {
                                    val move = legalMoves.find { it.from == selectedSquare && it.to == square }
                                    if (move != null) {
                                        board.doMove(move)
                                        onMove(move)
                                        selectedSquare = null
                                    } else {
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
    // kchesslib doesn't have a direct san(move) method in all versions, 
    // but move.toString() usually returns UCI. 
    // Let's use a simple manual SAN generator logic for common cases 
    // or rely on the library if available.
    // In kchesslib, we can use the move's SAN from the board history after doing it.
    val san = this.backup.last()?.move?.toString() ?: move.toString()
    // Check for mate/check in the library state
    return if (this.isMated) "$san#" else if (this.isKingAttacked) "$san+" else san
}

fun pieceToUnicode(piece: Piece): String {
    return when (piece) {
        Piece.WHITE_PAWN -> "♟"
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
