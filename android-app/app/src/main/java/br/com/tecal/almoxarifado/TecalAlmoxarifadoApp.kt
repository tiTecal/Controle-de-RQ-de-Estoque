package br.com.tecal.almoxarifado

import android.app.Application
import br.com.tecal.almoxarifado.data.local.AppDatabase
import br.com.tecal.almoxarifado.data.local.SessionPreferences
import br.com.tecal.almoxarifado.data.remote.DynamicRetrofitClient
import br.com.tecal.almoxarifado.data.repository.WithdrawalRepository

class TecalAlmoxarifadoApp : Application() {

    lateinit var sessionPreferences: SessionPreferences
        private set

    lateinit var withdrawalRepository: WithdrawalRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionPreferences = SessionPreferences(this)
        val database = AppDatabase.getInstance(this)
        val retrofitClient = DynamicRetrofitClient(sessionPreferences)
        withdrawalRepository = WithdrawalRepository(
            retrofitClient = retrofitClient,
            dao = database.withdrawalDao(),
            sessionPreferences = sessionPreferences
        )
    }
}

