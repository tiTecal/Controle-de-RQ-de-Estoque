package br.com.tecal.almoxarifado

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.tecal.almoxarifado.security.SecurityGuard
import br.com.tecal.almoxarifado.ui.screens.AdminSettingsDialog
import br.com.tecal.almoxarifado.ui.screens.AnimatedSplashScreen
import br.com.tecal.almoxarifado.ui.screens.KioskDashboardScreen
import br.com.tecal.almoxarifado.ui.screens.WarehouseLoginScreen
import br.com.tecal.almoxarifado.ui.screens.WithdrawerCredentialsDialog
import br.com.tecal.almoxarifado.ui.theme.TecalAlmoxarifadoTheme
import br.com.tecal.almoxarifado.ui.theme.TecalNavyDark
import br.com.tecal.almoxarifado.ui.viewmodel.KioskViewModel

class MainActivity : ComponentActivity() {

    private var isKioskPinned by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantém a tela do tablet ao lado da porta do almoxarifado sempre acesa e bloqueia capturas/miniaturas de tela
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        isKioskPinned = checkIsInLockTaskMode()

        val integrity = SecurityGuard.checkEnvironmentIntegrity(this)
        val app = application as TecalAlmoxarifadoApp

        setContent {
            TecalAlmoxarifadoTheme {
                if (!integrity.safe) {
                    Surface(
                        color = TecalNavyDark,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Ambiente Não Autorizado",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = integrity.reason ?: "Execução bloqueada pela política de segurança corporativa da Tecal.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    return@TecalAlmoxarifadoTheme
                }

                val kioskViewModel: KioskViewModel = viewModel(
                    factory = KioskViewModel.Factory(
                        repository = app.withdrawalRepository,
                        sessionPreferences = app.sessionPreferences
                    )
                )
                val state by kioskViewModel.uiState.collectAsStateWithLifecycle()
                var showSplash by rememberSaveable { mutableStateOf(true) }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!state.isEstoqueLoggedIn) {
                        WarehouseLoginScreen(
                            bffBaseUrl = state.bffBaseUrl,
                            isLoading = state.isLoggingInEstoque,
                            errorMessage = state.loginEstoqueError,
                            onLoginClick = kioskViewModel::loginEstoque,
                            onOpenAdminClick = { kioskViewModel.setShowAdminDialog(true) }
                        )
                    } else {
                        KioskDashboardScreen(
                            state = state,
                            onSearchChange = kioskViewModel::updateSearchQuery,
                            onRefreshClick = { kioskViewModel.refreshPendingMovements(silent = false) },
                            onSelectMovement = kioskViewModel::selectMovement,
                            onClearSelection = kioskViewModel::clearSelectedMovement,
                            onToggleItem = kioskViewModel::toggleItemChecked,
                            onToggleAllItems = kioskViewModel::toggleCheckAll100Percent,
                            onOpenWithdrawerAuth = kioskViewModel::openWithdrawerAuthDialog,
                            onDismissBanner = kioskViewModel::dismissCompletedBanner,
                            onOpenAdminSettings = { kioskViewModel.setShowAdminDialog(true) }
                        )
                    }

                    // Tela de Inicialização animada da Tecal sobreposta com transição fluida
                    AnimatedVisibility(
                        visible = showSplash,
                        exit = fadeOut(animationSpec = tween(durationMillis = 450)) +
                            scaleOut(
                                targetScale = 1.05f,
                                animationSpec = tween(durationMillis = 450)
                            )
                    ) {
                        AnimatedSplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    }
                }

                // Modal de seleção do colaborador retirante e senha individual para dar baixa no movimento 1.1.10
                val selectedMov = state.selectedMovement
                if (state.showWithdrawerAuthDialog && selectedMov != null) {
                    WithdrawerCredentialsDialog(
                        movement = selectedMov,
                        totalItemsCount = state.selectedItems.size,
                        estoqueUsers = state.almoxUsers,
                        isLoadingUsers = state.isLoadingUsers,
                        isLookingUpCpf = state.isLookingUpCpf,
                        exactCpfMatches = state.exactCpfMatches,
                        isSubmitting = state.isConfirmingWithdrawal,
                        errorMessage = state.withdrawerAuthError,
                        onLookupExactCpf = kioskViewModel::lookupByExactCpf,
                        onConfirm = kioskViewModel::confirmarBaixaComLoginDoRetirante,
                        onDismiss = kioskViewModel::dismissWithdrawerAuthDialog
                    )
                }

                // Modal Administrativo (Gestão de Acessos, IP do BFF, Modo Quiosque e Logout)
                if (state.showAdminDialog) {
                    AdminSettingsDialog(
                        currentBffUrl = state.bffBaseUrl,
                        isEstoqueLoggedIn = state.isEstoqueLoggedIn,
                        isKioskLocked = isKioskPinned,
                        almoxUsers = state.almoxUsers,
                        isLoadingUsers = state.isLoadingUsers,
                        isSavingUser = state.isSavingUser,
                        userManagementError = state.userManagementError,
                        userManagementSuccess = state.userManagementSuccess,
                        onVerifyPin = kioskViewModel::verifyAdminPin,
                        onSaveConfig = kioskViewModel::saveAdminServerConfig,
                        onRefreshUsers = kioskViewModel::loadAlmoxUsers,
                        onSaveUser = kioskViewModel::saveAlmoxUser,
                        onToggleUserStatus = kioskViewModel::toggleAlmoxUserStatus,
                        onToggleKioskMode = { toggleKioskLockTask() },
                        onLogoutEstoque = kioskViewModel::logoutEstoque,
                        onDismiss = { kioskViewModel.setShowAdminDialog(false) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isKioskPinned = checkIsInLockTaskMode()
    }

    private fun toggleKioskLockTask() {
        try {
            if (checkIsInLockTaskMode()) {
                stopLockTask()
                isKioskPinned = false
                Toast.makeText(this, "Modo Quiosque desativado.", Toast.LENGTH_SHORT).show()
            } else {
                startLockTask()
                isKioskPinned = true
                Toast.makeText(this, "Tela fixada em Modo Quiosque.", Toast.LENGTH_SHORT).show()
            }
        } catch (ex: Exception) {
            Toast.makeText(
                this,
                "Não foi possível alterar o Modo Quiosque: ${ex.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkIsInLockTaskMode(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}
