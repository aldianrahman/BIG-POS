package com.berdikariintigemilang.pos.ui.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.berdikariintigemilang.pos.ui.components.BrandLogos
import com.berdikariintigemilang.pos.ui.components.FullScreenLoading
import com.berdikariintigemilang.pos.ui.components.PrimaryButton

@Composable
fun ShiftOpenScreen(
    onShiftOpen: () -> Unit,
    viewModel: ShiftOpenViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var cash by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.goToMain.collect { onShiftOpen() }
    }

    if (state.checking) {
        FullScreenLoading()
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandLogos(modifier = Modifier.padding(bottom = 16.dp))
            Text("Buka Shift", style = MaterialTheme.typography.headlineSmall)
            if (state.cashierName.isNotBlank()) {
                Text(
                    "Kasir: ${state.cashierName}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                "Masukkan modal/uang kas awal di laci",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            OutlinedTextField(
                value = cash,
                onValueChange = { v -> cash = v.filter { it.isDigit() } },
                label = { Text("Modal Awal (Rp)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }

            PrimaryButton(
                text = "BUKA SHIFT",
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                loading = state.submitting,
                enabled = cash.isNotBlank(),
                onClick = { viewModel.openShift(cash.toDoubleOrNull() ?: 0.0) }
            )
        }
    }
}
