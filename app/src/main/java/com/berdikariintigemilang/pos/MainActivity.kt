package com.berdikariintigemilang.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.berdikariintigemilang.pos.ui.navigation.PosNavGraph
import com.berdikariintigemilang.pos.ui.theme.PosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosTheme {
                PosNavGraph()
            }
        }
    }
}
