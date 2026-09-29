package com.babycatbe.nevesestoque.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.babycatbe.nevesestoque.ui.theme.LocalNevesDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.babycatbe.nevesestoque.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    state: AuthUiState,
    onUsernameLogin: (String, String) -> Unit,
    onGoogleLogin: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val busy = state.status == AuthStatus.Loading
    val configured = state.status != AuthStatus.ConfigMissing
    val canSubmit = configured && !busy && username.isNotBlank() && password.isNotEmpty()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp)) {
            // A arte oficial tem fundo branco: no Escuro ela vira uma placa arredondada centralizada,
            // em vez de um retângulo branco solto. No Claro o layout é o mesmo de antes.
            val darkTheme = LocalNevesDarkTheme.current
            Image(
                painter = painterResource(R.drawable.neves_brand_light),
                contentDescription = "Panificadora Neves",
                contentScale = ContentScale.Fit,
                modifier = if (darkTheme) {
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(140.dp)
                        .aspectRatio(BRAND_LIGHT_ASPECT_RATIO)
                        .clip(RoundedCornerShape(16.dp))
                } else Modifier.fillMaxWidth().height(140.dp),
            )
            Text(
                text = "NEVES • ESTOQUE",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Entrar no sistema",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Use seu usuário e senha. Contas mestre também podem entrar pelo Google.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            state.errorMessage?.let { message ->
                Surface(
                    color = if (state.status == AuthStatus.DeviceBlocked)
                        MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                ) {
                    Text(text = message, modifier = Modifier.padding(14.dp))
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nome de usuário") },
                enabled = configured && !busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                enabled = configured && !busy,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (canSubmit) onUsernameLogin(username, password)
                }),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Button(
                onClick = { onUsernameLogin(username, password) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(if (busy) "Entrando…" else "Entrar") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            OutlinedButton(
                onClick = onGoogleLogin,
                enabled = configured && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Entrar com Google") }

            if (!configured) {
                Text(
                    text = "Esta build foi compilada sem a configuração pública do Supabase.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** Proporção da arte neves_brand_light (1536 × 1048). */
private const val BRAND_LIGHT_ASPECT_RATIO = 1536f / 1048f
