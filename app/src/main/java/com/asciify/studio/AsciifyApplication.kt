package com.asciify.studio

import android.app.Application
import com.asciify.studio.data.*

class AsciifyApplication : Application() {
    lateinit var store: StudioStore; private set
    lateinit var exports: ExportRepository; private set
    override fun onCreate() {
        super.onCreate()
        store = StudioStore(this)
        exports = ExportRepository(store)
    }
}
