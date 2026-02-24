package `in`.cartunez.flow

import android.app.Application
import `in`.cartunez.flow.data.AppDatabase
import `in`.cartunez.flow.data.PrefsStore
import `in`.cartunez.flow.data.SlipsRepository
import `in`.cartunez.flow.network.ApiService
import `in`.cartunez.flow.network.RetrofitClient

class FlowApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val apiService: ApiService by lazy { RetrofitClient.create() }
    val prefsStore: PrefsStore by lazy { PrefsStore(this) }
    val slipsRepository: SlipsRepository by lazy {
        SlipsRepository(database.partyDao(), database.slipDao(), database.slipCollectionDao(),
            apiService, prefsStore)
    }

    companion object {
        lateinit var instance: FlowApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
