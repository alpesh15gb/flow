package `in`.cartunez.flow.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.*
import `in`.cartunez.flow.R
import `in`.cartunez.flow.databinding.ActivityMainBinding
import `in`.cartunez.flow.sync.SyncWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled by SmsReceiver */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            showHome()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> { showHome(); true }
                R.id.nav_history -> { showHistory(); true }
                else             -> false
            }
        }

        requestSmsPermission()
        scheduleSyncWorker()
    }

    fun showHome() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HomeFragment())
            .commit()
    }

    fun showHistory() {
        binding.bottomNav.selectedItemId = R.id.nav_history
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HistoryFragment())
            .commit()
    }

    private fun requestSmsPermission() {
        val perms = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions.launch(perms)
        }
    }

    private fun scheduleSyncWorker() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "flow_sync", ExistingPeriodicWorkPolicy.KEEP, req
        )
    }
}
