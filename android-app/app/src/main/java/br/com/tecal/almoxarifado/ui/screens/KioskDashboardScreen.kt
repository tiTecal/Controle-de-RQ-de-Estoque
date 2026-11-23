package br.com.tecal.almoxarifado.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tecal.almoxarifado.R
import br.com.tecal.almoxarifado.data.model.ConfirmWithdrawalResponse
import br.com.tecal.almoxarifado.data.model.MovementItemDto
import br.com.tecal.almoxarifado.data.model.PendingMovementDto
import br.com.tecal.almoxarifado.ui.theme.TecalBlueAccent
import br.com.tecal.almoxarifado.ui.theme.TecalBlueSubtle
import br.com.tecal.almoxarifado.ui.theme.TecalCardBorder
import br.com.tecal.almoxarifado.ui.theme.TecalErrorBg
import br.com.tecal.almoxarifado.ui.theme.TecalNavy
import br.com.tecal.almoxarifado.ui.theme.TecalNavyDark
import br.com.tecal.almoxarifado.ui.theme.TecalSuccessBg
import br.com.tecal.almoxarifado.ui.theme.TecalSuccessGreen
import br.com.tecal.almoxarifado.ui.theme.TecalSurfaceBg
import br.com.tecal.almoxarifado.ui.theme.TecalTextMuted
import br.com.tecal.almoxarifado.ui.theme.TecalTextPrimary
import br.com.tecal.almoxarifado.ui.theme.TecalTextSecondary
import br.com.tecal.almoxarifado.ui.theme.TecalWarningAmber
import br.com.tecal.almoxarifado.ui.theme.TecalWarningBg
import br.com.tecal.almoxarifado.ui.viewmodel.KioskUiState
import java.util.Locale

@Composable
fun KioskDashboardScreen(
    state: KioskUiState,
    onSearchChange: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onSelectMovement: (PendingMovementDto) -> Unit,
    onClearSelection: () -> Unit,
    onToggleItem: (Int) -> Unit,
    onToggleAllItems: () -> Unit,
    onOpenWithdrawerAuth: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenAdminSettings: () -> Unit
) {
    val topSafeInsets = WindowInsets.statusBars
        .union(WindowInsets.displayCutout)
        .union(WindowInsets.navigationBars)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    val bodySafeInsets = WindowInsets.navigationBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TecalSurfaceBg)
    ) {
        // 1. Barra Superior Responsiva (adapta automaticamente para celular em pé, deitado ou tablet)
        SleekKioskHeader(
            usuarioEstoque = state.usuarioEstoque,
            isOffline = state.isOfflineCache,
            isLoading = state.isLoadingMovements,
            onRefresh = onRefreshClick,
            onOpenSettings = onOpenAdminSettings,
            safeInsets = topSafeInsets
        )

        // 2. Corpo Principal protegido contra sobreposição das barras do Android
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(bodySafeInsets)
        ) {
            if (state.isOfflineCache) {
                Surface(
                    color = TecalWarningBg,
                    border = BorderStroke(1.dp, TecalWarningAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = TecalWarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Modo Offline • Dados salvos no aparelho",
                                color = TecalWarningAmber,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = onRefreshClick,
                            shape = RoundedCornerShape(6.dp),
                            color = TecalWarningAmber
                        ) {
                            Text(
                                text = "Reconectar",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            state.lastCompletedWithdrawal?.let { completed ->
                SuccessBanner(completed = completed, onDismiss = onDismissBanner)
            }

            if (!state.listError.isNullOrBlank()) {
                Surface(
                    color = TecalErrorBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.listError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = onRefreshClick,
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = "Repetir",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Conteúdo Principal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (state.selectedMovement == null) {
                    MovementsListCard(
                        movements = state.filteredMovements,
                        searchQuery = state.searchQuery,
                        isLoading = state.isLoadingMovements,
                        listError = state.listError,
                        onRetry = onRefreshClick,
                        onSearchChange = onSearchChange,
                        onSelectMovement = onSelectMovement,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MovementDetailFullScreen(
                        movement = state.selectedMovement,
                        items = state.selectedItems,
                        isLoadingItems = state.isLoadingItems,
                        itemsError = state.itemsError,
                        onClose = onClearSelection,
                        onConfirmWithdrawal = onOpenWithdrawerAuth,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Top Bar totalmente responsivo:
 * - Em celulares em pé (compacto): Exibe Título + Status Online na esquerda e botões de ícone rápidos na direita.
 * - Em telas largas (deitado/tablet): Exibe também o badge do operador e texto no botão Atualizar.
 */
@Composable
private fun SleekKioskHeader(
    usuarioEstoque: String,
    isOffline: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    safeInsets: WindowInsets
) {
    Surface(
        color = TecalNavyDark,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(safeInsets)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val isCompactPhone = maxWidth < 600.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Lado Esquerdo: Marca Tecal + Status Coligada 2
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        modifier = Modifier
                            .width(58.dp)
                            .height(34.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_tecal_mark),
                                contentDescription = "Tecal Engenharia",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "TECAL ENGENHARIA",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isOffline) TecalWarningAmber else Color(0xFF34D399))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isOffline) {
                                    "Offline • Mov. 1.1.10"
                                } else {
                                    "Coligada 2 • Mov. 1.1.10"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOffline) Color(0xFFFDE68A) else Color(0xFFA7F3D0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Lado Direito: Controles adaptados à largura da tela
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isCompactPhone) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = usuarioEstoque.ifBlank { "almoxarife" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !isLoading,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Atualizar", modifier = Modifier.size(15.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Atualizar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    } else {
                        // Em celular em pé: botão compacto de atualizar
                        Surface(
                            onClick = { if (!isLoading) onRefresh() },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(15.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Atualizar",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurações",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ETAPA 1: Lista das Movimentações 1.1.10 da Coligada 2 (100% responsiva para celular em pé ou deitado).
 */
@Composable
private fun MovementsListCard(
    movements: List<PendingMovementDto>,
    searchQuery: String,
    isLoading: Boolean,
    listError: String?,
    onRetry: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectMovement: (PendingMovementDto) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterTab by remember { mutableIntStateOf(0) }

    val only1110 = remember(movements) { movements.filter { it.codTmv.trim() == "1.1.10" } }
    val pendingCount = remember(only1110) { only1110.count { !it.jaRetirado } }
    val withdrawnCount = remember(only1110) { only1110.count { it.jaRetirado } }

    val displayedMovements = remember(only1110, filterTab) {
        when (filterTab) {
            1 -> only1110.filter { !it.jaRetirado }
            2 -> only1110.filter { it.jaRetirado }
            else -> only1110
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, TecalCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 640.dp

            Column(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchTextField(
                            searchQuery = searchQuery,
                            onSearchChange = onSearchChange,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterTabButton(
                                label = "Todos",
                                count = only1110.size,
                                isSelected = filterTab == 0,
                                onClick = { filterTab = 0 }
                            )
                            FilterTabButton(
                                label = "Pendentes",
                                count = pendingCount,
                                isSelected = filterTab == 1,
                                onClick = { filterTab = 1 }
                            )
                            FilterTabButton(
                                label = "Baixados",
                                count = withdrawnCount,
                                isSelected = filterTab == 2,
                                onClick = { filterTab = 2 }
                            )
                        }
                    }
                } else {
                    // Em celular em pé: Busca na linha 1, Abas de filtro ocupando largura igual na linha 2
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchTextField(
                            searchQuery = searchQuery,
                            onSearchChange = onSearchChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterTabButton(
                                label = "Todos",
                                count = only1110.size,
                                isSelected = filterTab == 0,
                                onClick = { filterTab = 0 },
                                modifier = Modifier.weight(1f)
                            )
                            FilterTabButton(
                                label = "Pendentes",
                                count = pendingCount,
                                isSelected = filterTab == 1,
                                onClick = { filterTab = 1 },
                                modifier = Modifier.weight(1f)
                            )
                            FilterTabButton(
                                label = "Baixados",
                                count = withdrawnCount,
                                isSelected = filterTab == 2,
                                onClick = { filterTab = 2 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(color = TecalCardBorder)

                if (displayedMovements.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val hasConnectionError = !listError.isNullOrBlank() && only1110.isEmpty()
                            Icon(
                                imageVector = if (hasConnectionError) Icons.Default.WifiOff else Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = if (hasConnectionError) MaterialTheme.colorScheme.error else Color(0xFFCBD5E1),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when {
                                    hasConnectionError -> "Não foi possível carregar os movimentos"
                                    searchQuery.isNotBlank() || filterTab != 0 ->
                                        "Nenhum movimento 1.1.10 encontrado para este filtro."
                                    else -> "Nenhum movimento 1.1.10 encontrado na Coligada 2."
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasConnectionError) MaterialTheme.colorScheme.error else TecalTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            if (hasConnectionError) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = listError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TecalTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onRetry,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TecalNavy)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reconectar agora",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedMovements, key = { "${it.codColigada}_${it.idMov}" }) { mov ->
                            CleanMovementItemCard(
                                movement = mov,
                                isCompactPortrait = !isWideScreen,
                                onClick = { onSelectMovement(mov) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTextField(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = {
            Text(
                text = "Buscar ordem, solicitante ou centro de custo...",
                style = MaterialTheme.typography.bodySmall,
                color = TecalTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TecalNavy,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Limpar",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TecalNavy,
            unfocusedBorderColor = Color(0xFFCBD5E1),
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        ),
        modifier = modifier
    )
}

@Composable
private fun FilterTabButton(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) TecalNavy else Color.White,
        border = BorderStroke(1.dp, if (isSelected) TecalNavy else Color(0xFFCBD5E1)),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else TecalTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) Color.White.copy(alpha = 0.22f) else Color(0xFFF1F5F9)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) Color.White else TecalNavy,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Card de Movimento 1.1.10 adaptativo:
 * - Em celular em pé (isCompactPortrait = true): Empilha os dados verticalmente com botão largo na base, sem espremer texto!
 * - Em tela deitada/tablet (isCompactPortrait = false): Exibe os dados à esquerda e o botão de ação à direita.
 */
@Composable
private fun CleanMovementItemCard(
    movement: PendingMovementDto,
    isCompactPortrait: Boolean,
    onClick: () -> Unit
) {
    val isWithdrawn = movement.jaRetirado

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWithdrawn) Color(0xFFF8FAFC) else Color.White
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isWithdrawn) Color(0xFFBBF7D0) else Color(0xFFCBD5E1)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isCompactPortrait) {
            // LAYOUT CELULAR EM PÉ (VERTICAL): 100% livre de quebras de texto
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Linha 1: Número da Ordem + Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ordem Nº ${movement.numeroMov}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TecalNavy,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TecalBlueSubtle
                        ) {
                            Text(
                                text = "${movement.qtdItens} ${if (movement.qtdItens == 1) "item" else "itens"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = TecalNavy,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    MovementStatusBadge(isWithdrawn = isWithdrawn)
                }

                // Linha 2: Solicitante
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TecalNavy,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Solicitante: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = TecalTextSecondary
                    )
                    Text(
                        text = movement.solicitante.ifBlank { "Não informado" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = TecalTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Linha 3: Centro de Custo
                if (movement.nomeCCusto.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = TecalBlueAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = movement.nomeCCusto,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TecalTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Linha 4: Data e Requisição 1.1.13
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (movement.dataEmissao.isNotBlank()) {
                        Text(
                            text = "Emissão: ${formatIsoDateBr(movement.dataEmissao)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TecalTextMuted,
                            maxLines = 1
                        )
                    }
                    if (movement.numeroRequisicao1113.isNotBlank()) {
                        Text(text = "•", color = Color(0xFFCBD5E1))
                        Text(
                            text = "Req: Nº ${movement.numeroRequisicao1113}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TecalBlueAccent,
                            maxLines = 1
                        )
                    }
                }

                if (movement.retiradoPor.isNotBlank()) {
                    Text(
                        text = "Retirado por ${movement.retiradoPor} (${movement.dataRetirada} ${movement.horaRetirada})".trim(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TecalSuccessGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Linha 5: Botão Largo de Ação na Base do Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isWithdrawn) TecalSuccessBg else TecalNavy,
                    border = if (isWithdrawn) BorderStroke(1.dp, TecalSuccessGreen.copy(alpha = 0.4f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = if (isWithdrawn) "VER ITENS DA ORDEM" else "ABRIR E DAR BAIXA",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isWithdrawn) TecalSuccessGreen else Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isWithdrawn) TecalSuccessGreen else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            // LAYOUT HORIZONTAL (DEITADO / TABLET)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ordem Nº ${movement.numeroMov}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TecalNavy,
                            maxLines = 1
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TecalBlueSubtle
                        ) {
                            Text(
                                text = "${movement.qtdItens} ${if (movement.qtdItens == 1) "ITEM" else "ITENS"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = TecalNavy,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        MovementStatusBadge(isWithdrawn = isWithdrawn)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = TecalNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Solicitante: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = TecalTextSecondary
                            )
                            Text(
                                text = movement.solicitante.ifBlank { "Não informado" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TecalTextPrimary,
                                maxLines = 1
                            )
                        }

                        if (movement.nomeCCusto.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = null,
                                    tint = TecalBlueAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = movement.nomeCCusto,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TecalTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (movement.dataEmissao.isNotBlank()) {
                            Text(
                                text = "Emissão: ${formatIsoDateBr(movement.dataEmissao)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TecalTextMuted,
                                maxLines = 1
                            )
                        }

                        if (movement.numeroRequisicao1113.isNotBlank()) {
                            Text(text = "•", color = Color(0xFFCBD5E1))
                            Text(
                                text = "Req. Origem: Nº ${movement.numeroRequisicao1113}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TecalBlueAccent,
                                maxLines = 1
                            )
                        }

                        if (movement.retiradoPor.isNotBlank()) {
                            Text(text = "•", color = Color(0xFFCBD5E1))
                            Text(
                                text = "Retirado por ${movement.retiradoPor} (${movement.dataRetirada} ${movement.horaRetirada})".trim(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TecalSuccessGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isWithdrawn) TecalSuccessBg else TecalNavy,
                    border = if (isWithdrawn) BorderStroke(1.dp, TecalSuccessGreen.copy(alpha = 0.4f)) else null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isWithdrawn) "VER ITENS" else "ABRIR E DAR BAIXA",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isWithdrawn) TecalSuccessGreen else Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isWithdrawn) TecalSuccessGreen else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementStatusBadge(isWithdrawn: Boolean) {
    if (isWithdrawn) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = TecalSuccessBg,
            border = BorderStroke(1.dp, TecalSuccessGreen.copy(alpha = 0.35f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TecalSuccessGreen,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Baixado",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TecalSuccessGreen,
                    maxLines = 1
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = TecalWarningBg,
            border = BorderStroke(1.dp, TecalWarningAmber.copy(alpha = 0.35f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TecalWarningAmber,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Pendente",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TecalWarningAmber,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * ETAPA 2: Visualização dos Itens da Movimentação e Botão Direto de Baixa (Sem Seleção de Itens).
 */
@Composable
private fun MovementDetailFullScreen(
    movement: PendingMovementDto,
    items: List<MovementItemDto>,
    isLoadingItems: Boolean,
    itemsError: String?,
    onClose: () -> Unit,
    onConfirmWithdrawal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWithdrawn = movement.jaRetirado

    BoxWithConstraints(modifier = modifier) {
        val isLandscapeSplit = maxWidth >= 680.dp

        if (isLandscapeSplit) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // COLUNA ESQUERDA (64%): Lista de Materiais da Movimentação
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, TecalCardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .weight(0.64f)
                        .fillMaxHeight()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Materiais da Ordem Nº ${movement.numeroMov}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = TecalNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TecalBlueSubtle
                            ) {
                                Text(
                                    text = "${items.size} ${if (items.size == 1) "ITEM" else "ITENS"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TecalNavy,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = TecalCardBorder)

                        ItemsChecklistLazyColumn(
                            items = items,
                            isLoadingItems = isLoadingItems,
                            itemsError = itemsError,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }

                // COLUNA DIREITA (36%): Resumo da Ordem + Botão Direto "ASSINAR E DAR BAIXA"
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, TecalCardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onClose,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.5.dp, TecalNavy.copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TecalNavy),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Voltar para a Lista",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }

                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Ordem Nº ${movement.numeroMov}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TecalNavy,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        MovementStatusBadge(isWithdrawn = isWithdrawn)
                                    }

                                    Text(
                                        text = "Solicitante: ${movement.solicitante.ifBlank { "Não informado" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TecalTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (movement.nomeCCusto.isNotBlank()) {
                                        Text(
                                            text = "C. Custo: ${movement.nomeCCusto}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TecalTextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (movement.numeroRequisicao1113.isNotBlank()) {
                                        Text(
                                            text = "Req. 1.1.13: Nº ${movement.numeroRequisicao1113}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TecalBlueAccent,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        if (isWithdrawn) {
                            Surface(
                                color = TecalSuccessBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TecalSuccessGreen.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = TecalSuccessGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Retirada Já Concluída",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TecalSuccessGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Retirado por: ${movement.retiradoPor}\nEm: ${movement.dataRetirada} às ${movement.horaRetirada}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF14532D)
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onConfirmWithdrawal,
                                enabled = !isLoadingItems,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TecalSuccessGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ASSINAR E DAR BAIXA",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // LAYOUT CELULAR EM PÉ (VERTICAL)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, TecalCardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Cabeçalho Compacto da Ordem no Celular em Pé
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = onClose,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Voltar",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = TecalBlueSubtle
                                ) {
                                    Text(
                                        text = "${items.size} ${if (items.size == 1) "item" else "itens"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TecalNavy,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                MovementStatusBadge(isWithdrawn = isWithdrawn)
                            }
                        }

                        Text(
                            text = "Ordem Nº ${movement.numeroMov}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TecalNavy,
                            maxLines = 1
                        )

                        Text(
                            text = "Solicitante: ${movement.solicitante.ifBlank { "Não informado" }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TecalTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (movement.nomeCCusto.isNotBlank()) {
                            Text(
                                text = movement.nomeCCusto,
                                style = MaterialTheme.typography.labelSmall,
                                color = TecalTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(color = TecalCardBorder)

                    ItemsChecklistLazyColumn(
                        items = items,
                        isLoadingItems = isLoadingItems,
                        itemsError = itemsError,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    HorizontalDivider(color = TecalCardBorder)

                    // Rodapé Fixo no Celular em Pé com Botão Largo
                    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            if (isWithdrawn) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Ordem Já Baixada",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TecalSuccessGreen
                                    )
                                    Text(
                                        text = "Retirado por ${movement.retiradoPor} em ${movement.dataRetirada} às ${movement.horaRetirada}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TecalTextMuted
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onConfirmWithdrawal,
                                    enabled = !isLoadingItems,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TecalSuccessGreen,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ASSINAR E DAR BAIXA",
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
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

@Composable
private fun ItemsChecklistLazyColumn(
    items: List<MovementItemDto>,
    isLoadingItems: Boolean,
    itemsError: String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        when {
            isLoadingItems -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TecalNavy, strokeWidth = 3.dp)
                    }
                }
            }

            !itemsError.isNullOrBlank() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = itemsError, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            items.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Nenhum item encontrado nesta ordem.", color = TecalTextSecondary)
                    }
                }
            }

            else -> {
                items(items, key = { it.nSeqItmMov }) { item ->
                    CleanItemChecklistRow(item = item)
                }
            }
        }
    }
}

/**
 * Linha de Produto Responsiva para Celular em Pé ou Deitado.
 */
@Composable
private fun CleanItemChecklistRow(
    item: MovementItemDto
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFF1F5F9)
            ) {
                Text(
                    text = "#${item.nSeqItmMov}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TecalNavy,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descricaoProduto,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TecalTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Cód: ${item.codigoPrd.ifBlank { item.idPrd.toString() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TecalTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TecalBlueSubtle,
                border = BorderStroke(1.dp, TecalBlueAccent.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "${formatQty(item.quantidade)} ${item.codUnd}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TecalNavy,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SuccessBanner(
    completed: ConfirmWithdrawalResponse,
    onDismiss: () -> Unit
) {
    Surface(
        color = TecalSuccessBg,
        border = BorderStroke(1.dp, TecalSuccessGreen.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = TecalSuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Baixa concluída! Ordem Nº ${completed.numeroMov}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TecalSuccessGreen
                    )
                    Text(
                        text = "Retirado por ${completed.retiradoPor} (${completed.dataRetirada} ${completed.horaRetirada})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF14532D)
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar",
                    tint = TecalSuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatQty(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value).replace('.', ',')
    }
}

private fun formatIsoDateBr(rawIso: String): String {
    val clean = rawIso.trim()
    if (clean.length >= 10 && clean[4] == '-' && clean[7] == '-') {
        val yyyy = clean.substring(0, 4)
        val mm = clean.substring(5, 7)
        val dd = clean.substring(8, 10)
        if (clean.length >= 16) {
            val hhmm = clean.substring(11, 16)
            if (hhmm != "00:00") {
                return "$dd/$mm/$yyyy $hhmm"
            }
        }
        return "$dd/$mm/$yyyy"
    }
    return clean
}
