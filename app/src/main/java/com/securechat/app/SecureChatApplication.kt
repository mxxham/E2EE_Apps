package com.securechat.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class. Annotated with [@HiltAndroidApp] to trigger Hilt's
 * code generation and install the global component.
 *
 * Also implements [Configuration.Provider] to plug in [HiltWorkerFactory] so
 * WorkManager workers can receive @Inject constructor parameters.
 */
@HiltAndroidApp
class SecureChatApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
