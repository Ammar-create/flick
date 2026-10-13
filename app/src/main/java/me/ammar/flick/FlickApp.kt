package me.ammar.flick

import android.app.Application
import me.ammar.flick.engine.ActionDispatcher
import me.ammar.flick.engine.RepoSyncManager
import me.ammar.flick.engine.ShizukuBridge
import me.ammar.flick.service.FlickAccessibilityService

class FlickApp : Application() {

    companion object {
        lateinit var instance: FlickApp
            private set
    }

    lateinit var repoSyncManager: RepoSyncManager
        private set

    lateinit var actionDispatcher: ActionDispatcher
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        ShizukuBridge.init()
        repoSyncManager = RepoSyncManager(this)
        actionDispatcher = ActionDispatcher(this, repoSyncManager) {
            FlickAccessibilityService.instance
        }
    }
}
