package `in`.cartunez.flow.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import `in`.cartunez.flow.FlowApp
import `in`.cartunez.flow.R
import `in`.cartunez.flow.data.Transaction
import `in`.cartunez.flow.databinding.ActivityMainBinding
import `in`.cartunez.flow.sync.SyncWorker
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app by lazy { application as FlowApp }
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            `in`.cartunez.flow.data.TransactionRepository(
                app.database.transactionDao(), app.apiService, app.prefsStore
            ),
            app.prefsStore
        )
    }
    private lateinit var txAdapter: TransactionAdapter

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted — SMS receiver will handle it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
        observeViewModel()
        requestSmsPermission()
        scheduleSyncWorker()
    }

    private fun setupRecyclerView() {
        txAdapter = TransactionAdapter()
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = txAdapter
        }
    }

    private fun setupButtons() {
        binding.btnAddSale.setOnClickListener { showAddDialog(TxType.sale) }
        binding.btnAddExpense.setOnClickListener { showAddDialog(TxType.expense) }
        binding.btnAddPurchase.setOnClickListener { showAddDialog(TxType.purchase) }
        binding.btnSync.setOnClickListener { viewModel.sync() }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Authenticated -> { /* nothing */ }
                is AuthState.NeedsAuth     -> viewModel.authenticate(app.apiService)
                is AuthState.Error         -> Toast.makeText(this, state.msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.summary.observe(this) { s ->
            binding.tvSales.text     = formatAmount(s.sales)
            binding.tvExpenses.text  = formatAmount(s.expenses)
            binding.tvPurchases.text = formatAmount(s.purchases)
            binding.tvProfit.text    = formatAmount(s.profit)
            binding.tvProfit.setTextColor(
                ContextCompat.getColor(this, if (s.profit >= 0) R.color.green else R.color.red)
            )
        }

        viewModel.transactions.observe(this) { list ->
            txAdapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.syncing.observe(this) { syncing ->
            binding.progressSync.visibility = if (syncing) View.VISIBLE else View.GONE
            binding.btnSync.isEnabled = !syncing
        }

        // Pending suggestion from SMS / WhatsApp
        viewModel.pendingTx.observe(this) { pending ->
            pending ?: return@observe
            AlertDialog.Builder(this)
                .setTitle("${pending.source.uppercase()} — Auto Detected")
                .setMessage("${pending.type.replaceFirstChar { it.uppercase() }}: ₹${pending.amount}\n${pending.note}\n\nSave this transaction?")
                .setPositiveButton("Save") { _, _ -> viewModel.acceptPending() }
                .setNegativeButton("Dismiss") { _, _ -> viewModel.dismissPending() }
                .setCancelable(false)
                .show()
        }
    }

    private fun showAddDialog(type: TxType) {
        val dialog = AddTransactionDialog.newInstance(type.name)
        dialog.onSave = { amount, note ->
            viewModel.addTransaction(
                Transaction(amount = amount, type = type.name, note = note.ifBlank { null }, date = LocalDate.now().toString())
            )
        }
        dialog.show(supportFragmentManager, "add_tx")
    }

    private fun requestSmsPermission() {
        val perms = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions.launch(perms)
        }
    }

    private fun scheduleSyncWorker() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(60, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "flow_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    private fun formatAmount(v: Double): String {
        val abs = Math.abs(v)
        return (if (v < 0) "-" else "") + "₹" + String.format("%,.0f", abs)
    }

    enum class TxType { sale, expense, purchase }
}
