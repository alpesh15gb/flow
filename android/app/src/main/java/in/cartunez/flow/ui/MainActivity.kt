package `in`.cartunez.flow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

        // Pop fragment backstack on back press (for PartyDetail / ManageParties)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Nav listener is the single place that swaps fragments
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> { loadFragment(HomeFragment());    true }
                R.id.nav_history -> { loadFragment(HistoryFragment()); true }
                R.id.nav_slips   -> { loadFragment(SlipsFragment());   true }
                else             -> false
            }
        }

        if (savedInstanceState == null) {
            // Triggers the listener above — no separate loadFragment call needed
            binding.bottomNav.selectedItemId = R.id.nav_home
        }

        handleShareIntent(intent)
        requestSmsPermission()
        scheduleSyncWorker()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            // Switch to Slips tab, then pass URI to SlipsFragment once it's ready
            binding.bottomNav.selectedItemId = R.id.nav_slips
            val slipsFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? SlipsFragment
            if (slipsFragment != null) {
                slipsFragment.pendingShareUri = uri
            } else {
                // Fragment not attached yet — post to let it settle
                binding.root.post {
                    (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? SlipsFragment)
                        ?.let { it.pendingShareUri = uri }
                    ?: run {
                        SlipReviewSheet.newInstance(uri).show(supportFragmentManager, "slip_review")
                    }
                }
            }
        }
    }

    /** Called by HomeFragment's "See all →" link */
    fun showHistory() {
        binding.bottomNav.selectedItemId = R.id.nav_history
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
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
