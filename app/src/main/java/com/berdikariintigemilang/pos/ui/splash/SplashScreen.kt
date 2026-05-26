package com.berdikariintigemilang.pos.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.ui.components.BrandLogos

@Composable
fun SplashScreen(
    onNavigate: (SplashDestination) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val destination by viewModel.destination.collectAsState()

    LaunchedEffect(destination) {
        destination?.let(onNavigate)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandLogos()
            Text(
                text = "POS BIG · LUBY",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp)
            )
            CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp).size(32.dp))
        }
    }
}
