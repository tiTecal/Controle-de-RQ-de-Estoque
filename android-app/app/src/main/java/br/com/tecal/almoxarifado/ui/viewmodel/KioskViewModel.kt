package br.com.tecal.almoxarifado.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.tecal.almoxarifado.data.local.SessionPreferences
import br.com.tecal.almoxarifado.data.model.AlmoxarifadoUserDto
import br.com.tecal.almoxarifado.data.model.ConfirmWithdrawalResponse
import br.com.tecal.almoxarifado.data.model.MovementItemDto
import br.com.tecal.almoxarifado.data.model.PendingMovementDto
import br.com.tecal.almoxarifado.data.model.UpsertUserRequest
import br.com.tecal.almoxarifado.data.repository.ApiOutcome
import br.com.tecal.almoxarifado.data.repository.WithdrawalRepository
import br.com.tecal.almoxarifado.security.SecurityGuard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class KioskUiState(
    val isEstoqueLoggedIn: Boolean = false,
    val usuarioEstoque: String = "",
    val modoSimulacaoRm: Boolean = false,
    val bffBaseUrl: String = SessionPreferences.DEFAULT_BFF_URL,

    // Login inicial do estoque
    val isLoggingInEstoque: Boolean = false,
    val loginEstoqueError: String? = null,

    // Lista de movimentos 1.1.10 pendentes (sem retirado_por, data_retirada, hora_retirada)
    val isLoadingMovements: Boolean = false,
    val isOfflineCache: Boolean = false,
    val listError: String? = null,
    val searchQuery: String = "",
    val allPendingMovements: List<PendingMovementDto> = emptyList(),

    // Movimento 1.1.10 indicado pelo usuário do estoque e seus itens (100%)
    val selectedMovement: PendingMovementDto? = null,
    val isLoadingItems: Boolean = false,
    val selectedItems: List<MovementItemDto> = emptyList(),
    val checkedItemSeqs: Set<Int> = emptySet(),
    val itemsError: String? = null,

    // Modal de confirmação de baixa (login e senha do usuário retirante)
    val showWithdrawerAuthDialog: Boolean = false,
    val isConfirmingWithdrawal: Boolean = false,
    val withdrawerAuthError: String? = null,
    val activeIdempotencyKey: String = "",

    // Feedback de baixa concluída
    val lastCompletedWithdrawal: ConfirmWithdrawalResponse? = null,

    // Painel administrativo e busca segura por CPF exato (sem expor CPFs na rede)
    val showAdminDialog: Boolean = false,
    val almoxUsers: List<AlmoxarifadoUserDto> = emptyList(),
    val isLoadingUsers: Boolean = false,
    val isLookingUpCpf: Boolean = false,
    val exactCpfMatches: List<AlmoxarifadoUserDto> = emptyList(),
    val isSavingUser: Boolean = false,
    val userManagementError: String? = null,
    val userManagementSuccess: String? = null
) {
    val filteredMovements: List<PendingMovementDto>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return allPendingMovements
            return allPendingMovements.filter { mov ->
                mov.numeroMov.contains(q, ignoreCase = true) ||
                    mov.codTmv.contains(q, ignoreCase = true) ||
                    mov.numeroRequisicao1113.contains(q, ignoreCase = true) ||
                    mov.solicitante.contains(q, ignoreCase = true) ||
                    mov.retiradoPor.contains(q, ignoreCase = true) ||
                    mov.nomeCCusto.contains(q, ignoreCase = true) ||
                    mov.codCCusto.contains(q, ignoreCase = true) ||
                    mov.idMov.toString().contains(q)
            }
        }

    val totalPendingItemsCount: Int
        get() = allPendingMovements.sumOf { it.qtdItens }

    val is100PercentChecked: Boolean
        get() = selectedMovement != null && !selectedMovement.jaRetirado

    val checkProgressFraction: Float
        get() = 1f
}

class KioskViewModel(
    private val repository: WithdrawalRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        KioskUiState(
            isEstoqueLoggedIn = sessionPreferences.isEstoqueLoggedIn,
            usuarioEstoque = sessionPreferences.usuarioEstoque.orEmpty(),
            modoSimulacaoRm = sessionPreferences.modoSimulacaoRm,
            bffBaseUrl = sessionPreferences.bffBaseUrl
        )
    )
    val uiState: StateFlow<KioskUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var bannerDismissJob: Job? = null
    private var cpfLookupJob: Job? = null
    private var activeAdminToken: String? = null

    init {
        if (sessionPreferences.isEstoqueLoggedIn) {
            startAutoRefreshLoop()
            loadAlmoxUsers()
        }
    }

    fun loginEstoque(login: String, senha: String) {
        if (login.isBlank() || senha.isEmpty()) {
            _uiState.update { it.copy(loginEstoqueError = "Preencha o login e a senha do usuário do estoque.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingInEstoque = true, loginEstoqueError = null) }
            when (val res = repository.loginSessaoEstoque(login, senha)) {
                is ApiOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoggingInEstoque = false,
                            isEstoqueLoggedIn = true,
                            usuarioEstoque = res.data.usuarioEstoque,
                            modoSimulacaoRm = res.data.modoSimulacaoRm,
                            loginEstoqueError = null
                        )
                    }
                    startAutoRefreshLoop()
                    loadAlmoxUsers()
                }
                is ApiOutcome.Error -> {
                    _uiState.update {
                        it.copy(isLoggingInEstoque = false, loginEstoqueError = res.message)
                    }
                }
            }
        }
    }

    fun logoutEstoque() {
        autoRefreshJob?.cancel()
        cpfLookupJob?.cancel()
        activeAdminToken = null
        sessionPreferences.clearEstoqueSession()
        viewModelScope.launch {
            repository.clearLocalSecurityCache()
        }
        _uiState.update {
            KioskUiState(
                isEstoqueLoggedIn = false,
                bffBaseUrl = sessionPreferences.bffBaseUrl
            )
        }
    }

    fun refreshPendingMovements(silent: Boolean = false) {
        if (!_uiState.value.isEstoqueLoggedIn) return
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoadingMovements = true, listError = null) }
            }
            when (val res = repository.fetchPending1110Movements()) {
                is ApiOutcome.Success -> {
                    _uiState.update { current ->
                        val updatedSelected = current.selectedMovement?.let { sel ->
                            res.data.find { it.idMov == sel.idMov }
                        }
                        current.copy(
                            isLoadingMovements = false,
                            isOfflineCache = res.fromOfflineCache,
                            listError = null,
                            allPendingMovements = res.data,
                            selectedMovement = updatedSelected,
                            selectedItems = if (updatedSelected == null) emptyList() else current.selectedItems,
                            checkedItemSeqs = if (updatedSelected == null) emptySet() else current.checkedItemSeqs
                        )
                    }
                }
                is ApiOutcome.Error -> {
                    if (res.isSessionExpired) {
                        logoutEstoque()
                        _uiState.update { it.copy(loginEstoqueError = res.message) }
                    } else {
                        _uiState.update {
                            it.copy(isLoadingMovements = false, listError = res.message)
                        }
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectMovement(movement: PendingMovementDto) {
        // Regra Tecal: O usuário não pode selecionar outras movimentações a não ser as 1.1.10
        if (movement.codTmv.trim() != "1.1.10") {
            return
        }
        _uiState.update {
            it.copy(
                selectedMovement = movement,
                isLoadingItems = true,
                selectedItems = emptyList(),
                checkedItemSeqs = emptySet(),
                itemsError = null,
                showWithdrawerAuthDialog = false,
                withdrawerAuthError = null
            )
        }
        viewModelScope.launch {
            when (val res = repository.fetchMovementItems(movement.idMov)) {
                is ApiOutcome.Success -> {
                    _uiState.update { current ->
                        if (current.selectedMovement?.idMov != movement.idMov) return@update current
                        current.copy(
                            isLoadingItems = false,
                            selectedItems = res.data,
                            checkedItemSeqs = res.data.map { it.nSeqItmMov }.toSet(),
                            itemsError = null
                        )
                    }
                }
                is ApiOutcome.Error -> {
                    if (res.isSessionExpired) {
                        logoutEstoque()
                    } else {
                        _uiState.update {
                            it.copy(isLoadingItems = false, itemsError = res.message)
                        }
                    }
                }
            }
        }
    }

    fun clearSelectedMovement() {
        _uiState.update {
            it.copy(
                selectedMovement = null,
                selectedItems = emptyList(),
                checkedItemSeqs = emptySet(),
                itemsError = null,
                showWithdrawerAuthDialog = false,
                withdrawerAuthError = null
            )
        }
    }

    fun toggleItemChecked(nSeqItmMov: Int) {
        _uiState.update { current ->
            val updated = current.checkedItemSeqs.toMutableSet()
            if (!updated.add(nSeqItmMov)) {
                updated.remove(nSeqItmMov)
            }
            current.copy(checkedItemSeqs = updated)
        }
    }

    fun toggleCheckAll100Percent() {
        _uiState.update { current ->
            val allSeqs = current.selectedItems.map { it.nSeqItmMov }.toSet()
            val nextSet = if (current.checkedItemSeqs.size == allSeqs.size) emptySet() else allSeqs
            current.copy(checkedItemSeqs = nextSet)
        }
    }

    fun openWithdrawerAuthDialog() {
        val current = _uiState.value
        if (!current.is100PercentChecked || current.selectedMovement == null) return
        cpfLookupJob?.cancel()
        _uiState.update {
            it.copy(
                showWithdrawerAuthDialog = true,
                withdrawerAuthError = null,
                isLookingUpCpf = false,
                exactCpfMatches = emptyList(),
                activeIdempotencyKey = UUID.randomUUID().toString()
            )
        }
        if (current.almoxUsers.isEmpty()) {
            loadAlmoxUsers()
        }
    }

    fun dismissWithdrawerAuthDialog() {
        if (_uiState.value.isConfirmingWithdrawal) return
        cpfLookupJob?.cancel()
        _uiState.update {
            it.copy(
                showWithdrawerAuthDialog = false,
                withdrawerAuthError = null,
                isLookingUpCpf = false,
                exactCpfMatches = emptyList(),
                activeIdempotencyKey = ""
            )
        }
    }

    fun lookupByExactCpf(cpfDigits: String) {
        val clean = cpfDigits.filter { it.isDigit() }
        cpfLookupJob?.cancel()
        if (clean.length != 11) {
            _uiState.update { it.copy(isLookingUpCpf = false, exactCpfMatches = emptyList()) }
            return
        }
        cpfLookupJob = viewModelScope.launch {
            _uiState.update { it.copy(isLookingUpCpf = true, exactCpfMatches = emptyList()) }
            when (val res = repository.lookupEmployeeByExactCpf(clean)) {
                is ApiOutcome.Success -> {
                    _uiState.update {
                        it.copy(isLookingUpCpf = false, exactCpfMatches = res.data)
                    }
                }
                is ApiOutcome.Error -> {
                    if (res.isSessionExpired) {
                        logoutEstoque()
                    } else {
                        _uiState.update {
                            it.copy(isLookingUpCpf = false, exactCpfMatches = emptyList())
                        }
                    }
                }
            }
        }
    }

    fun confirmarBaixaComLoginDoRetirante(loginRetirante: String, senhaRetirante: String) {
        val current = _uiState.value
        val selected = current.selectedMovement ?: return
        if (!current.is100PercentChecked) return

        if (loginRetirante.isBlank() || senhaRetirante.isEmpty()) {
            _uiState.update {
                it.copy(withdrawerAuthError = "Informe seu login e senha para confirmar a retirada.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConfirmingWithdrawal = true, withdrawerAuthError = null) }
            val outcome = repository.confirmarBaixa100PorCento(
                idMov = selected.idMov,
                loginRetirante = loginRetirante,
                senhaRetirante = senhaRetirante,
                idempotencyKey = current.activeIdempotencyKey.ifBlank { UUID.randomUUID().toString() }
            )

            when (outcome) {
                is ApiOutcome.Success -> {
                    _uiState.update { state ->
                        val updatedList = state.allPendingMovements.map { mov ->
                            if (mov.idMov == selected.idMov) {
                                mov.copy(
                                    jaRetirado = true,
                                    retiradoPor = outcome.data.retiradoPor,
                                    dataRetirada = outcome.data.dataRetirada,
                                    horaRetirada = outcome.data.horaRetirada
                                )
                            } else {
                                mov
                            }
                        }
                        state.copy(
                            isConfirmingWithdrawal = false,
                            showWithdrawerAuthDialog = false,
                            withdrawerAuthError = null,
                            activeIdempotencyKey = "",
                            allPendingMovements = updatedList,
                            selectedMovement = updatedList.find { it.idMov == selected.idMov },
                            lastCompletedWithdrawal = outcome.data
                        )
                    }
                    refreshPendingMovements(silent = true)
                    scheduleSuccessBannerDismiss()
                }
                is ApiOutcome.Error -> {
                    when {
                        outcome.isSessionExpired -> {
                            logoutEstoque()
                            _uiState.update { it.copy(loginEstoqueError = outcome.message) }
                        }
                        outcome.isAlreadyWithdrawn -> {
                            _uiState.update { state ->
                                state.copy(
                                    isConfirmingWithdrawal = false,
                                    showWithdrawerAuthDialog = false,
                                    listError = outcome.message
                                )
                            }
                            refreshPendingMovements(silent = true)
                        }
                        else -> {
                            // Mantém a sessão do estoque logada e exibe o erro dentro do modal de confirmação!
                            _uiState.update {
                                it.copy(
                                    isConfirmingWithdrawal = false,
                                    withdrawerAuthError = outcome.message
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun dismissCompletedBanner() {
        bannerDismissJob?.cancel()
        _uiState.update { it.copy(lastCompletedWithdrawal = null) }
    }

    fun setShowAdminDialog(show: Boolean) {
        if (!show) {
            activeAdminToken = null
        }
        _uiState.update {
            it.copy(
                showAdminDialog = show,
                userManagementError = null,
                userManagementSuccess = null
            )
        }
        if (show && _uiState.value.isEstoqueLoggedIn) {
            loadAlmoxUsers()
        }
    }

    fun verifyAdminPin(
        pin: String,
        onResult: (valid: Boolean, errorMsg: String?) -> Unit
    ) {
        val cleanPin = pin.trim()
        if (cleanPin.length < 4) {
            onResult(false, "Informe um PIN com pelo menos 4 dígitos.")
            return
        }
        viewModelScope.launch {
            when (val res = repository.verifyAdminPinRemote(cleanPin)) {
                is ApiOutcome.Success -> {
                    activeAdminToken = res.data.adminToken
                    sessionPreferences.updateOfflineAdminPin(cleanPin)
                    onResult(true, null)
                }
                is ApiOutcome.Error -> {
                    if (res.isNetworkFailure && sessionPreferences.verifyOfflineAdminPin(cleanPin)) {
                        // Permite configuração emergencial de IP offline quando o BFF ainda não está acessível
                        activeAdminToken = null
                        onResult(true, null)
                    } else {
                        activeAdminToken = null
                        onResult(false, res.message)
                    }
                }
            }
        }
    }

    fun saveAdminServerConfig(newBffUrl: String, newPin: String?) {
        val trimmed = newBffUrl.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val normalized = if (withScheme.endsWith("/")) withScheme else "$withScheme/"

        if (!SecurityGuard.isSafeCorporateUrl(normalized)) {
            _uiState.update {
                it.copy(
                    userManagementError = "URL rejeitada: utilize apenas HTTPS ou IP privado da rede corporativa."
                )
            }
            return
        }

        sessionPreferences.bffBaseUrl = normalized
        if (!newPin.isNullOrBlank() && newPin.trim().length >= 4) {
            sessionPreferences.updateOfflineAdminPin(newPin.trim())
        }
        activeAdminToken = null
        _uiState.update {
            it.copy(
                bffBaseUrl = sessionPreferences.bffBaseUrl,
                showAdminDialog = false,
                loginEstoqueError = null,
                listError = null,
                userManagementError = null
            )
        }
        if (_uiState.value.isEstoqueLoggedIn) {
            refreshPendingMovements(silent = false)
        }
    }

    fun loadAlmoxUsers() {
        if (!_uiState.value.isEstoqueLoggedIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUsers = true, userManagementError = null) }
            when (val res = repository.fetchUsers()) {
                is ApiOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingUsers = false,
                            almoxUsers = res.data,
                            userManagementError = null
                        )
                    }
                }
                is ApiOutcome.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingUsers = false,
                            userManagementError = res.message
                        )
                    }
                }
            }
        }
    }

    fun saveAlmoxUser(
        login: String,
        nomeCompleto: String,
        senha: String?,
        perfil: String,
        ativo: Boolean
    ) {
        val token = activeAdminToken
        if (token.isNullOrBlank()) {
            _uiState.update {
                it.copy(userManagementError = "Sessão administrativa necessária. Feche e desbloqueie o painel com o PIN.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingUser = true,
                    userManagementError = null,
                    userManagementSuccess = null
                )
            }
            val req = UpsertUserRequest(
                login = login.trim().lowercase(),
                nomeCompleto = nomeCompleto.trim(),
                senha = senha?.takeIf { it.isNotEmpty() },
                perfil = perfil,
                ativo = ativo
            )
            when (val res = repository.upsertUser(token, req)) {
                is ApiOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingUser = false,
                            userManagementSuccess = "Usuário '${res.data.login}' salvo com sucesso!"
                        )
                    }
                    loadAlmoxUsers()
                }
                is ApiOutcome.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingUser = false,
                            userManagementError = res.message
                        )
                    }
                }
            }
        }
    }

    fun toggleAlmoxUserStatus(login: String, novoStatusAtivo: Boolean) {
        val token = activeAdminToken
        if (token.isNullOrBlank()) {
            _uiState.update {
                it.copy(userManagementError = "Sessão administrativa necessária. Feche e desbloqueie o painel com o PIN.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingUser = true,
                    userManagementError = null,
                    userManagementSuccess = null
                )
            }
            when (val res = repository.updateUserStatus(token, login, novoStatusAtivo)) {
                is ApiOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingUser = false,
                            userManagementSuccess = "Status de '${res.data.login}' atualizado para ${if (res.data.ativo) "ATIVO" else "INATIVO"}."
                        )
                    }
                    loadAlmoxUsers()
                }
                is ApiOutcome.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingUser = false,
                            userManagementError = res.message
                        )
                    }
                }
            }
        }
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            refreshPendingMovements(silent = false)
            while (isActive && _uiState.value.isEstoqueLoggedIn) {
                delay(120_000L)
                // Evita recarregar no meio da digitação de login/senha do retirante no modal
                if (!_uiState.value.showWithdrawerAuthDialog && !_uiState.value.isConfirmingWithdrawal) {
                    refreshPendingMovements(silent = true)
                }
            }
        }
    }

    private fun scheduleSuccessBannerDismiss() {
        bannerDismissJob?.cancel()
        bannerDismissJob = viewModelScope.launch {
            delay(8_000L)
            _uiState.update { it.copy(lastCompletedWithdrawal = null) }
        }
    }

    class Factory(
        private val repository: WithdrawalRepository,
        private val sessionPreferences: SessionPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KioskViewModel(repository, sessionPreferences) as T
        }
    }
}
