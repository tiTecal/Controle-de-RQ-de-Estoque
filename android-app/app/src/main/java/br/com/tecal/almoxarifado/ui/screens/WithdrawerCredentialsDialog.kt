package br.com.tecal.almoxarifado.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.tecal.almoxarifado.data.model.AlmoxarifadoUserDto
import br.com.tecal.almoxarifado.data.model.PendingMovementDto
import br.com.tecal.almoxarifado.ui.theme.TecalBlueSubtle
import br.com.tecal.almoxarifado.ui.theme.TecalErrorBg
import br.com.tecal.almoxarifado.ui.theme.TecalNavy
import br.com.tecal.almoxarifado.ui.theme.TecalNavyDark
import br.com.tecal.almoxarifado.ui.theme.TecalSuccessBg
import br.com.tecal.almoxarifado.ui.theme.TecalSuccessGreen
import br.com.tecal.almoxarifado.ui.theme.TecalTextPrimary
import br.com.tecal.almoxarifado.ui.theme.TecalTextSecondary

/**
 * Mascaramento de CPF em conformidade com a LGPD (Lei nº 13.709/2018 - Princípio da Minimização):
 * Exibe exclusivamente os 3 primeiros dígitos do CPF nos cards: "158.***.***-**".
 */
private fun maskCpfLgpd(rawCpf: String): String {
    val digits = rawCpf.filter { it.isDigit() }
    return if (digits.length >= 3) {
        "${digits.substring(0, 3)}.***.***-**"
    } else {
        "***.***.***-**"
    }
}

/**
 * Verifica se o login do RM é um CPF numérico (usuários do Portal/MeuRH) para jamais
 * exibir os 11 dígitos do CPF em texto aberto nos cards.
 */
private fun isCpfNumericLogin(login: String): Boolean {
    val trimmed = login.trim()
    val digits = trimmed.filter { it.isDigit() }
    return digits.length >= 9 && digits.length == trimmed.length
}

/**
 * Modal transacional para retirada de materiais no estoque (Movimento 1.1.10 - Coligada 2).
 * Protegido por LGPD by Design:
 * - Nos cards dos colaboradores aparecem apenas os 3 primeiros dígitos do CPF (ex: 158.***.***-**).
 * - Oculta logins que sejam CPFs numéricos (exibindo a Matrícula/Chapa e o Setor).
 * - Ao selecionar o colaborador na lista, se o usuário tiver digitado mais de 3 dígitos do CPF
 *   na busca, o campo de busca reduz automaticamente para os 3 primeiros dígitos para evitar
 *   exposição visual ("shoulder surfing") no terminal compartilhado.
 */
@Composable
fun WithdrawerCredentialsDialog(
    movement: PendingMovementDto,
    totalItemsCount: Int,
    estoqueUsers: List<AlmoxarifadoUserDto>,
    isLoadingUsers: Boolean,
    isLookingUpCpf: Boolean = false,
    exactCpfMatches: List<AlmoxarifadoUserDto> = emptyList(),
    isSubmitting: Boolean,
    errorMessage: String?,
    onLookupExactCpf: (String) -> Unit = {},
    onConfirm: (login: String, senha: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Todos os funcionários ativos (ocultando apenas a conta técnica da sessão do tablet)
    val allActiveRmUsers = remember(estoqueUsers) {
        val active = estoqueUsers.filter { it.ativo }
        val withoutSessionAccount = active.filter { !it.login.equals("almoxarife", ignoreCase = true) }
        if (withoutSessionAccount.isNotEmpty()) withoutSessionAccount else active
    }

    var cpfQuery by remember { mutableStateOf("") }
    var selectedEmployee by remember { mutableStateOf<AlmoxarifadoUserDto?>(null) }
    var senhaRm by remember { mutableStateOf("") }
    var senhaVisivel by remember { mutableStateOf(false) }

    val qDigitsClean = cpfQuery.filter { it.isDigit() }
    LaunchedEffect(qDigitsClean) {
        if (qDigitsClean.length == 11) {
            onLookupExactCpf(qDigitsClean)
        } else if (exactCpfMatches.isNotEmpty()) {
            onLookupExactCpf("")
        }
    }

    val filteredEmployees = remember(allActiveRmUsers, exactCpfMatches, cpfQuery) {
        val qTrim = cpfQuery.trim()
        if (qTrim.isEmpty()) {
            allActiveRmUsers
        } else {
            val qDigits = qTrim.filter { it.isDigit() }
            if (qDigits.length == 11) {
                exactCpfMatches.filter { it.ativo }
            } else {
                // Zero-leakage LGPD: usa no máximo os 3 primeiros dígitos do CPF para filtragem local
                val cpfPrefixDigits = qDigits.take(3)
                allActiveRmUsers.filter { emp ->
                    val empCpfDigits = emp.cpf.filter { it.isDigit() }.take(3)
                    val isOpaqueRmRef = emp.origemRm || emp.login.startsWith("rm_")
                    (cpfPrefixDigits.isNotEmpty() && empCpfDigits.startsWith(cpfPrefixDigits)) ||
                        (qDigits.isNotEmpty() && emp.chapa.contains(qDigits)) ||
                        emp.nomeCompleto.contains(qTrim, ignoreCase = true) ||
                        (!isOpaqueRmRef && !isCpfNumericLogin(emp.login) && emp.login.contains(qTrim, ignoreCase = true)) ||
                        emp.chapa.contains(qTrim, ignoreCase = true)
                }.sortedWith(
                    compareByDescending<AlmoxarifadoUserDto> { emp ->
                        val empCpfDigits = emp.cpf.filter { it.isDigit() }.take(3)
                        cpfPrefixDigits.isNotEmpty() && empCpfDigits.startsWith(cpfPrefixDigits)
                    }.thenBy { it.nomeCompleto }
                )
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isSubmitting,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Cabeçalho Azul Marinho Industrial
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(TecalNavyDark, TecalNavy)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.HowToReg,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Identificação de Retirada (RM)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Mov. 1.1.10 Nº ${movement.numeroMov} • $totalItemsCount ${if (totalItemsCount == 1) "item" else "itens"} • Protegido LGPD",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        if (!isSubmitting) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Passo 1: Caixa de texto para digitar o CPF (basta os 3 primeiros dígitos ou CPF completo)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = TecalNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "1. Digite o CPF (ou 3 primeiros dígitos) e selecione:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TecalTextPrimary
                            )
                        }
                        Surface(
                            color = TecalBlueSubtle,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = TecalNavy,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LGPD",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TecalNavy
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = cpfQuery,
                        onValueChange = { cpfQuery = it },
                        label = { Text("Buscar por CPF, Nome ou Matrícula") },
                        placeholder = { Text("Ex: 3 primeiros dígitos do CPF ou nome...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PersonSearch,
                                contentDescription = null,
                                tint = TecalNavy
                            )
                        },
                        trailingIcon = {
                            if (cpfQuery.isNotEmpty()) {
                                IconButton(onClick = { cpfQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                                }
                            }
                        },
                        singleLine = true,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TecalNavy,
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Lista de TODOS os funcionários ativos no RM (com CPF mascarado nos 3 primeiros dígitos)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if ((isLoadingUsers && allActiveRmUsers.isEmpty()) || (qDigitsClean.length == 11 && isLookingUpCpf)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = TecalNavy,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (qDigitsClean.length == 11 && isLookingUpCpf) {
                                        "Consultando CPF no servidor seguro..."
                                    } else {
                                        "Carregando colaboradores ativos..."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TecalTextSecondary
                                )
                            }
                        } else if (filteredEmployees.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhum funcionário ativo encontrado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TecalTextSecondary
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 130.dp, max = 200.dp),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(
                                    items = filteredEmployees,
                                    key = { "${it.login}_${it.chapa}_${it.id}" }
                                ) { emp ->
                                    val isSelected = selectedEmployee?.login.equals(emp.login, ignoreCase = true)
                                    val maskedCpf = maskCpfLgpd(emp.cpf.ifBlank { emp.login })
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) TecalBlueSubtle else Color.White,
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) TecalNavy else Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isSubmitting) {
                                                selectedEmployee = emp
                                                // LGPD anti-exposição: se o usuário digitou mais de 3 números do CPF no campo de busca,
                                                // mantém apenas os 3 primeiros dígitos na caixa ao selecionar para ninguém ao lado ver o CPF completo
                                                val typedDigits = cpfQuery.filter { it.isDigit() }
                                                if (typedDigits.length > 3) {
                                                    cpfQuery = typedDigits.take(3)
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) {
                                                        Icons.Default.CheckCircle
                                                    } else {
                                                        Icons.Default.RadioButtonUnchecked
                                                    },
                                                    contentDescription = null,
                                                    tint = if (isSelected) TecalSuccessGreen else TecalTextSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = emp.nomeCompleto,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (isSelected) TecalNavyDark else TecalTextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val detailsText = buildString {
                                                        append("CPF: $maskedCpf")
                                                        if (emp.chapa.isNotBlank()) {
                                                            append(" • Matr.: ${emp.chapa}")
                                                        }
                                                        if (emp.secao.isNotBlank()) {
                                                            append(" • ${emp.secao}")
                                                        }
                                                    }
                                                    Text(
                                                        text = detailsText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TecalTextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            if (isSelected) {
                                                Surface(
                                                    color = TecalSuccessGreen,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "SELECIONADO",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Resumo da pessoa selecionada (também com CPF mascarado LGPD)
                    val currentSelected = selectedEmployee
                    if (currentSelected != null) {
                        val selectedMaskedCpf = maskCpfLgpd(currentSelected.cpf.ifBlank { currentSelected.login })
                        Surface(
                            color = TecalSuccessBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TecalSuccessGreen.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Retirante: ${currentSelected.nomeCompleto}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TecalSuccessGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("CPF: $selectedMaskedCpf")
                                            if (currentSelected.chapa.isNotBlank()) {
                                                append(" • Matrícula: ${currentSelected.chapa}")
                                            }
                                            if (!currentSelected.origemRm &&
                                                !currentSelected.login.startsWith("rm_") &&
                                                !isCpfNumericLogin(currentSelected.login)
                                            ) {
                                                append(" • Usuário: ${currentSelected.login}")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TecalNavyDark
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Passo 2: Senha do RM (só habilita após selecionar quem é!)
                    Text(
                        text = if (currentSelected != null) {
                            "2. Digite sua Senha do RM (${currentSelected.nomeCompleto.substringBefore(" ")}):"
                        } else {
                            "2. Selecione seu nome acima para digitar sua Senha do RM:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (currentSelected != null) TecalTextPrimary else TecalTextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = senhaRm,
                        onValueChange = { senhaRm = it },
                        label = {
                            Text(
                                if (currentSelected != null) {
                                    "Senha do RM"
                                } else {
                                    "Selecione a pessoa na lista acima primeiro"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TecalNavy) },
                        trailingIcon = {
                            IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                                Icon(
                                    imageVector = if (senhaVisivel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Mostrar ou ocultar senha"
                                )
                            }
                        },
                        visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isSubmitting && currentSelected != null,
                        shape = RoundedCornerShape(10.dp),
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
                                if (currentSelected != null && senhaRm.isNotEmpty() && !isSubmitting) {
                                    onConfirm(currentSelected.login, senhaRm)
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = TecalErrorBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Cancelar", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                if (currentSelected != null) {
                                    onConfirm(currentSelected.login, senhaRm)
                                }
                            },
                            enabled = !isSubmitting && currentSelected != null && senhaRm.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = TecalSuccessGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gravando Baixa...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CONFIRMAR E DAR BAIXA",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
