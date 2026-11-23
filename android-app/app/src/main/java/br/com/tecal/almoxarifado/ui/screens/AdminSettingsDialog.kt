package br.com.tecal.almoxarifado.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.tecal.almoxarifado.data.model.AlmoxarifadoUserDto
import br.com.tecal.almoxarifado.security.SecurityGuard
import br.com.tecal.almoxarifado.ui.theme.TecalBlueAccent
import br.com.tecal.almoxarifado.ui.theme.TecalBlueSubtle
import br.com.tecal.almoxarifado.ui.theme.TecalErrorBg
import br.com.tecal.almoxarifado.ui.theme.TecalNavy
import br.com.tecal.almoxarifado.ui.theme.TecalNavyDark
import br.com.tecal.almoxarifado.ui.theme.TecalSuccessBg
import br.com.tecal.almoxarifado.ui.theme.TecalSuccessGreen
import br.com.tecal.almoxarifado.ui.theme.TecalTextMuted
import br.com.tecal.almoxarifado.ui.theme.TecalTextPrimary
import br.com.tecal.almoxarifado.ui.theme.TecalTextSecondary

@Composable
fun AdminSettingsDialog(
    currentBffUrl: String,
    isEstoqueLoggedIn: Boolean,
    isKioskLocked: Boolean,
    almoxUsers: List<AlmoxarifadoUserDto>,
    isLoadingUsers: Boolean,
    isSavingUser: Boolean,
    userManagementError: String?,
    userManagementSuccess: String?,
    onVerifyPin: (pin: String, onResult: (valid: Boolean, errorMsg: String?) -> Unit) -> Unit,
    onSaveConfig: (newUrl: String, newPin: String?) -> Unit,
    onRefreshUsers: () -> Unit,
    onSaveUser: (login: String, nomeCompleto: String, senha: String?, perfil: String, ativo: Boolean) -> Unit,
    onToggleUserStatus: (login: String, novoAtivo: Boolean) -> Unit,
    onToggleKioskMode: () -> Unit,
    onLogoutEstoque: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinUnlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var isVerifyingPin by remember { mutableStateOf(false) }
    var pinErrorMsg by remember { mutableStateOf<String?>(null) }
    var urlErrorMsg by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableIntStateOf(if (isEstoqueLoggedIn) 0 else 1) }

    var urlInput by remember { mutableStateOf(currentBffUrl) }
    var newPinInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            modifier = Modifier
                .widthIn(max = if (pinUnlocked && isEstoqueLoggedIn) 820.dp else 520.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(TecalNavyDark, TecalNavy)
                            )
                        )
                        .padding(horizontal = 22.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Painel Administrativo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Gestão de Acessos & Terminal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                }

                if (!pinUnlocked) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Informe o PIN administrativo do terminal:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TecalTextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                pinInput = it
                                pinErrorMsg = null
                            },
                            label = { Text("PIN Administrativo") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            enabled = !isVerifyingPin,
                            isError = pinErrorMsg != null,
                            shape = RoundedCornerShape(12.dp),
                            supportingText = {
                                if (!pinErrorMsg.isNullOrBlank()) {
                                    Text(pinErrorMsg!!)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss, enabled = !isVerifyingPin) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                enabled = !isVerifyingPin && pinInput.isNotBlank(),
                                onClick = {
                                    isVerifyingPin = true
                                    pinErrorMsg = null
                                    onVerifyPin(pinInput) { valid, errMsg ->
                                        isVerifyingPin = false
                                        if (valid) {
                                            pinUnlocked = true
                                        } else {
                                            pinErrorMsg = errMsg ?: "PIN incorreto. Tente novamente."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TecalNavy),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isVerifyingPin) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Validando...", fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Desbloquear Painel", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    if (isEstoqueLoggedIn) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color(0xFFF8FAFC),
                            contentColor = TecalNavy
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Usuários", fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Servidor & Quiosque", fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            )
                        }
                    }

                    if (isEstoqueLoggedIn && selectedTab == 0) {
                        UsersManagementTab(
                            users = almoxUsers,
                            isLoading = isLoadingUsers,
                            isSaving = isSavingUser,
                            errorMessage = userManagementError,
                            successMessage = userManagementSuccess,
                            onRefresh = onRefreshUsers,
                            onSaveUser = onSaveUser,
                            onToggleStatus = onToggleUserStatus
                        )
                    } else {
                        TerminalServerTab(
                            urlInput = urlInput,
                            newPinInput = newPinInput,
                            urlErrorMsg = urlErrorMsg ?: userManagementError,
                            isEstoqueLoggedIn = isEstoqueLoggedIn,
                            isKioskLocked = isKioskLocked,
                            onUrlChange = {
                                urlInput = it
                                urlErrorMsg = null
                            },
                            onNewPinChange = { newPinInput = it },
                            onToggleKioskMode = onToggleKioskMode,
                            onLogoutEstoque = {
                                onDismiss()
                                onLogoutEstoque()
                            },
                            onDismiss = onDismiss,
                            onSaveConfig = {
                                val raw = urlInput.trim()
                                val normalized = (if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw")
                                    .let { if (it.endsWith("/")) it else "$it/" }
                                if (!SecurityGuard.isSafeCorporateUrl(normalized)) {
                                    urlErrorMsg = "URL rejeitada: informe HTTPS ou IP da rede privada corporativa."
                                } else {
                                    urlErrorMsg = null
                                    onSaveConfig(normalized, newPinInput)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersManagementTab(
    users: List<AlmoxarifadoUserDto>,
    isLoading: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onRefresh: () -> Unit,
    onSaveUser: (login: String, nomeCompleto: String, senha: String?, perfil: String, ativo: Boolean) -> Unit,
    onToggleStatus: (login: String, novoAtivo: Boolean) -> Unit
) {
    var loginInput by remember { mutableStateOf("") }
    var nomeInput by remember { mutableStateOf("") }
    var senhaInput by remember { mutableStateOf("") }
    var perfilInput by remember { mutableStateOf("RETIRANTE") }
    var isEditingExisting by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        val isWideForm = maxWidth >= 560.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            // Formulário de Cadastro / Edição de Usuário
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isEditingExisting) Icons.Default.Edit else Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = TecalNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEditingExisting) {
                                    "Editando: $loginInput"
                                } else {
                                    "Novo / Atualizar Usuário"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = TecalNavy
                            )
                        }

                        if (isEditingExisting) {
                            TextButton(
                                onClick = {
                                    loginInput = ""
                                    nomeInput = ""
                                    senhaInput = ""
                                    perfilInput = "RETIRANTE"
                                    isEditingExisting = false
                                }
                            ) {
                                Text("Limpar")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isWideForm) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = loginInput,
                                onValueChange = { loginInput = it },
                                label = { Text("Login (Ex: carlos.silva)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(0.9f)
                            )
                            OutlinedTextField(
                                value = nomeInput,
                                onValueChange = { nomeInput = it },
                                label = { Text("Nome Completo") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.3f)
                            )
                            OutlinedTextField(
                                value = senhaInput,
                                onValueChange = { senhaInput = it },
                                label = {
                                    Text(if (isEditingExisting) "Nova Senha (opc.)" else "Senha")
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = loginInput,
                                onValueChange = { loginInput = it },
                                label = { Text("Login (Ex: carlos.silva)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = nomeInput,
                                onValueChange = { nomeInput = it },
                                label = { Text("Nome Completo") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = senhaInput,
                                onValueChange = { senhaInput = it },
                                label = {
                                    Text(if (isEditingExisting) "Nova Senha (opcional)" else "Senha")
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isWideForm) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Perfil:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TecalTextSecondary
                                )
                                listOf("RETIRANTE", "ESTOQUE", "AMBOS").forEach { role ->
                                    FilterChip(
                                        selected = perfilInput == role,
                                        onClick = { perfilInput = role },
                                        label = { Text(role, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TecalNavy,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    onSaveUser(loginInput, nomeInput, senhaInput, perfilInput, true)
                                    loginInput = ""
                                    nomeInput = ""
                                    senhaInput = ""
                                    isEditingExisting = false
                                },
                                enabled = !isSaving && loginInput.isNotBlank() && nomeInput.isNotBlank() &&
                                    (isEditingExisting || senhaInput.isNotEmpty()),
                                colors = ButtonDefaults.buttonColors(containerColor = TecalSuccessGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isEditingExisting) "Salvar Alterações" else "Salvar Usuário",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("RETIRANTE", "ESTOQUE", "AMBOS").forEach { role ->
                                    FilterChip(
                                        selected = perfilInput == role,
                                        onClick = { perfilInput = role },
                                        label = { Text(role, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TecalNavy,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    onSaveUser(loginInput, nomeInput, senhaInput, perfilInput, true)
                                    loginInput = ""
                                    nomeInput = ""
                                    senhaInput = ""
                                    isEditingExisting = false
                                },
                                enabled = !isSaving && loginInput.isNotBlank() && nomeInput.isNotBlank() &&
                                    (isEditingExisting || senhaInput.isNotEmpty()),
                                colors = ButtonDefaults.buttonColors(containerColor = TecalSuccessGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isEditingExisting) "Salvar Alterações" else "Salvar Usuário",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = TecalErrorBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (!successMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = TecalSuccessBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = successMessage,
                    color = TecalSuccessGreen,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Usuários Cadastrados (${users.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = TecalNavy
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Recarregar")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TecalNavy)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                users.forEach { u ->
                    val isRmUser = u.origemRm || u.login.startsWith("rm_")
                    Surface(
                        color = if (u.ativo) Color.White else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isRmUser) {
                                            if (u.chapa.isNotBlank()) "Matrícula ${u.chapa}" else "Colaborador RM"
                                        } else {
                                            u.login
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (u.ativo) TecalNavy else TecalTextMuted
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = when {
                                            isRmUser -> TecalBlueSubtle
                                            u.perfil == "AMBOS" -> Color(0xFFEDE9FE)
                                            u.perfil == "ESTOQUE" -> TecalBlueSubtle
                                            else -> TecalSuccessBg
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isRmUser) "RM (ERP)" else u.perfil,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isRmUser -> TecalNavy
                                                u.perfil == "AMBOS" -> Color(0xFF6D28D9)
                                                u.perfil == "ESTOQUE" -> TecalBlueAccent
                                                else -> TecalSuccessGreen
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = u.nomeCompleto,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TecalTextPrimary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isRmUser) {
                                    TextButton(
                                        enabled = !isSaving,
                                        onClick = {
                                            loginInput = u.login
                                            nomeInput = u.nomeCompleto
                                            senhaInput = ""
                                            perfilInput = u.perfil
                                            isEditingExisting = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Editar / Senha")
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Switch(
                                        checked = u.ativo,
                                        enabled = !isSaving,
                                        onCheckedChange = { novoAtivo ->
                                            onToggleStatus(u.login, novoAtivo)
                                        }
                                    )
                                } else {
                                    Text(
                                        text = "Somente Leitura",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TecalTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TerminalServerTab(
    urlInput: String,
    newPinInput: String,
    urlErrorMsg: String?,
    isEstoqueLoggedIn: Boolean,
    isKioskLocked: Boolean,
    onUrlChange: (String) -> Unit,
    onNewPinChange: (String) -> Unit,
    onToggleKioskMode: () -> Unit,
    onLogoutEstoque: () -> Unit,
    onDismiss: () -> Unit,
    onSaveConfig: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            label = { Text("URL do Servidor Corporativo (BFF)") },
            placeholder = { Text("http://servidor-interno:8080/") },
            singleLine = true,
            isError = !urlErrorMsg.isNullOrBlank(),
            supportingText = {
                if (!urlErrorMsg.isNullOrBlank()) {
                    Text(urlErrorMsg)
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = newPinInput,
            onValueChange = onNewPinChange,
            label = { Text("Novo PIN Administrativo (opcional, mín. 4 dígitos)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onToggleKioskMode,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.PhonelinkLock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isKioskLocked) {
                    "Desafixar Tela (Sair do Modo Quiosque)"
                } else {
                    "Fixar Tela na Porta (Ativar Modo Quiosque)"
                },
                fontWeight = FontWeight.SemiBold
            )
        }

        if (isEstoqueLoggedIn) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onLogoutEstoque,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Encerrar Sessão do Operador do Estoque",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSaveConfig,
                enabled = urlInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TecalNavy),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Salvar Configuração", fontWeight = FontWeight.Bold)
            }
        }
    }
}
