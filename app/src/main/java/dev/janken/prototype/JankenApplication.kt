package dev.janken.prototype

import android.app.Application
import android.os.SystemClock
import dev.janken.data.androidMatchHistory
import dev.janken.prototype.di.gameModule
import dev.janken.prototype.mvi.GameClock
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

class JankenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val preferences = AndroidSettingsStorage(this)
        startKoin {
            modules(
                gameModule(GameClock { SystemClock.uptimeMillis() }, preferences) { androidMatchHistory(this@JankenApplication) },
                module {
                    single { preferences }
                    viewModelOf(::GameViewModel)
                },
            )
        }
    }
}
