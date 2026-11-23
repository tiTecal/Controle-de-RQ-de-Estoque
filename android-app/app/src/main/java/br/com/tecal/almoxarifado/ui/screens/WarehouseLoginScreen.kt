package br.com.tecal.almoxarifado.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.tecal.almoxarifado.R
import br.com.tecal.almoxarifado.ui.theme.TecalBlueAccent
import br.com.tecal.almoxarifado.ui.theme.TecalErrorBg
import br.com.tecal.almoxarifado.ui.theme.TecalNavy
import br.com.tecal.almoxarifado.ui.theme.TecalNavyDark
import br.com.tecal.almoxarifado.ui.theme.TecalNavyLight
import br.com.tecal.almoxarifado.ui.theme.TecalSuccessEmerald

@Composable
fun WarehouseLoginScreen(
    bffBaseUrl: String,
    isLoading: Boolean,
    errorMessage: String?,
    onLoginClick: (String, String) -> Unit,
    onOpenAdminClick: () -> Unit
) {
    var login by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var senhaVisivel by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(TecalNavyDark, TecalNavy, TecalNavyLight)
                )
            )
            .imePadding()
    ) {
        val isWideTablet = maxWidth >= 820.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = if (isWideTablet) 980.dp else 540.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                if (isWideTablet) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Painel Esquerdo: Identidade Industrial Tecal & Fluxo Operacional
                        BrandSidePanel(
                            bffBaseUrl = bffBaseUrl,
                            modifier = Modifier
                                .weight(0.46f)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(TecalNavy, TecalNavyDark)
                                    )
                                )
                                .padding(28.dp)
                        )

                        // Painel Direito: Formulário de Autenticação da Sessão do Estoque
                        LoginFormContent(
                            bffBaseUrl = bffBaseUrl,
                            login = login,
                            senha = senha,
                            senhaVisivel = senhaVisivel,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            showCompactBrand = false,
                            onLoginChange = { login = it },
                            onSenhaChange = { senha = it },
                            onToggleSenhaVisivel = { senhaVisivel = !senhaVisivel },
                            onLoginSubmit = { onLoginClick(login, senha) },
                            onOpenAdminClick = onOpenAdminClick,
                            modifier = Modifier
                                .weight(0.54f)
                                .padding(28.dp)
                        )
                    }
                } else {
                    LoginFormContent(
                        bffBaseUrl = bffBaseUrl,
                        login = login,
                        senha = senha,
                        senhaVisivel = senhaVisivel,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        showCompactBrand = true,
                        onLoginChange = { login = it },
                        onSenhaChange = { senha = it },
                        onToggleSenhaVisivel = { senhaVisivel = !senhaVisivel },
                        onLoginSubmit = { onLoginClick(login, senha) },
                        onOpenAdminClick = onOpenAdminClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandSidePanel(
    bffBaseUrl: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .width(104.dp)
                        .height(56.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_tecal_full),
                            contentDescription = "Logo Tecal Engenharia",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "TECAL ENGENHARIA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Gestão de Estoque • TOTVS RM",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF93C5FD)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Terminal Quiosque de Retirada de Materiais",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Controle em tempo real da expedição física dos movimentos 1.1.10 integrado ao ERP corporativo da Tecal.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(26.dp))

            OperationalStepRow(
                icon = Icons.Default.CheckCircleOutline,
                title = "1. Fila Automática 1.1.10",
                subtitle = "Exibe movimentos faturados a partir da requisição 1.1.13 pendentes de assinatura."
            )
            Spacer(modifier = Modifier.height(14.dp))
            OperationalStepRow(
                icon = Icons.Default.CheckCircleOutline,
                title = "2. Conferência 100% Integral",
                subtitle = "Checklist visual item a item com trava contra retiradas parciais."
            )
            Spacer(modifier = Modifier.height(14.dp))
            OperationalStepRow(
                icon = Icons.Default.VerifiedUser,
                title = "3. Baixa Auditada com Assinatura",
                subtitle = "Registra colaborador retirante, data e hora oficial com autenticação individual."
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = TecalSuccessEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Servidor BFF Configurado",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = bffBaseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationalStepRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = TecalBlueAccent.copy(alpha = 0.25f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}

@Composable
private fun LoginFormContent(
    bffBaseUrl: String,
    login: String,
    senha: String,
    senhaVisivel: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    showCompactBrand: Boolean,
    onLoginChange: (String) -> Unit,
    onSenhaChange: (String) -> Unit,
    onToggleSenhaVisivel: () -> Unit,
    onLoginSubmit: () -> Unit,
    onOpenAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        if (showCompactBrand) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_tecal_full),
                    contentDescription = "Tecal Engenharia",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(64.dp)
                )
            }
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Abertura de Sessão do Estoque",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TecalNavy
                )
                Text(
                    text = "Autentique o operador responsável pelo terminal da porta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            color = Color(0xFFF1F5F9),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "A sessão iniciada manterá o tablet ativo em modo quiosque para atendimento contínuo dos colaboradores na porta do almoxarifado.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF334155),
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = login,
            onValueChange = onLoginChange,
            label = { Text("Login do Operador de Estoque") },
            placeholder = { Text("Ex: almoxarife") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TecalNavy) },
            singleLine = true,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TecalNavy,
                unfocusedContainerColor = Color(0xFFF8FAFC)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = senha,
            onValueChange = onSenhaChange,
            label = { Text("Senha do Estoque") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TecalNavy) },
            trailingIcon = {
                IconButton(onClick = onToggleSenhaVisivel) {
                    Icon(
                        imageVector = if (senhaVisivel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Alternar visibilidade da senha"
                    )
                }
            },
            visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TecalNavy,
                unfocusedContainerColor = Color(0xFFF8FAFC)
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (login.isNotBlank() && senha.isNotEmpty() && !isLoading) {
                        onLoginSubmit()
                    }
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = TecalErrorBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Button(
            onClick = onLoginSubmit,
            enabled = !isLoading && login.isNotBlank() && senha.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TecalNavy),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "CONECTANDO AO SERVIDOR...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "INICIAR SESSÃO DO ESTOQUE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        HorizontalDivider(color = Color(0xFFE2E8F0))
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Servidor Corporativo (BFF)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = bffBaseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TecalNavy
                )
            }
            OutlinedButton(
                onClick = onOpenAdminClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Configurar Terminal")
            }
        }
    }
}
