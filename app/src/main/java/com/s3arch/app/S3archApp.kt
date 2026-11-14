package com.s3arch.app

import android.app.Application
import com.s3arch.app.util.CrashLog

/** Point d'entree : conserve la trace d'un plantage pour le journal du demarrage suivant. */
class S3archApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        CrashLog.replay(this)
    }
}
