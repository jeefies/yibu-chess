package cn.yibu.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import cn.yibu.chess.ui.ChessApp

class MainActivity : ComponentActivity() {
    private val model: GameViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChessApp(model) }
    }
    override fun onStart() { super.onStart(); model.resumeForeground() }
    override fun onStop() { model.pauseForBackground(); super.onStop() }
}
