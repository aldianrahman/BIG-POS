package com.berdikariintigemilang.pos.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.berdikariintigemilang.pos.core.util.Formatters
import com.berdikariintigemilang.pos.data.cart.CartLine

/**
 * Dialog ubah harga satuan satu baris keranjang. Kasir memasukkan username &
 * password sales berwenang (id 95/99/118) lalu harga baru per pcs. Harga baru
 * tidak boleh melebihi harga master. Verifikasi & penerapan dilakukan di
 * [PosViewModel.confirmPriceEdit]; [submitting]/[error] berasal dari ViewModel.
 */
@Composable
fun PriceEditDialog(
    line: CartLine,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (username: String, password: String, newPrice: Double) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }

    val newPrice = priceText.toDoubleOrNull() ?: 0.0
    val priceTooHigh = newPrice > line.masterPrice
    val fieldsFilled = username.isNotBlank() && password.isNotBlank() && priceText.isNotBlank()
    val canSubmit = fieldsFilled && newPrice > 0.0 && !priceTooHigh && !submitting

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Ubah Harga",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        line.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Harga master: ${Formatters.rupiah(line.masterPrice)} / pcs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "Verifikasi sales berwenang",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    // Jangan .trim() per ketukan: spasi yang baru diketik selalu
                    // jadi trailing dan langsung terbuang, sehingga username ber-
                    // spasi mustahil dimasukkan. Spasi di ujung dirapikan saat
                    // verifikasi (AuthRepository.verifyPriceEditor).
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { v -> priceText = v.filter { it.isDigit() }.take(12) },
                    label = { Text("Harga baru / pcs") },
                    singleLine = true,
                    enabled = !submitting,
                    isError = priceTooHigh,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors()
                )
                if (priceTooHigh) {
                    Text(
                        "Harga tidak boleh lebih besar dari harga master",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username, password, newPrice) },
                enabled = canSubmit
            ) {
                if (submitting) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "  Memverifikasi…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Simpan Harga",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canSubmit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(
                    "Batal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)
