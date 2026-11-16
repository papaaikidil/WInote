package id.hananote

import android.app.Application
import id.hananote.data.CrashLog

/** Titik mulai aplikasi: penangkap galat dipasang paling awal supaya galat apa pun tercatat. */
class HanaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
