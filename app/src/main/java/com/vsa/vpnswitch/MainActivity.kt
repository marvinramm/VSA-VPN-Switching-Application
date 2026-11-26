package com.vsa.vpnswitch

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vsa.vpnswitch.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var watcher: WifiWatcher

    /** Der nächste offene Einrichtungsschritt (oder null, wenn alles passt). */
    private var nextFix: (() -> Unit)? = null

    private val foregroundPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            restartWatcher()
            refresh()
            if (hasFineLocation() && needsBackgroundLocation()) askBackgroundLocation()
        }

    private val wireguardPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast(getString(R.string.toast_wg_permission_denied))
            refresh()
        }

    private val backgroundPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh()
            restartServiceIfEnabled()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        watcher = WifiWatcher(this) { refreshCurrentWifi() }

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }

        b.switchEnabled.isChecked = prefs.enabled
        b.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggleAutomation(checked) }

        b.buttonAddCurrent.setOnClickListener { addCurrentWifi() }
        b.buttonAddManual.setOnClickListener { addManual() }
        b.editSsid.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addManual(); true } else false
        }

        b.sliderDelay.value = prefs.leaveDelaySec.coerceIn(0, 120).let { it - it % 5 }.toFloat()
        updateDelayLabel()
        b.sliderDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.leaveDelaySec = value.toInt()
                updateDelayLabel()
            }
        }

        setupVpnCard()

        b.buttonConnect.setOnClickListener { sendManual(true) }
        b.buttonDisconnect.setOnClickListener { sendManual(false) }
        b.buttonFix.setOnClickListener { nextFix?.invoke() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MonitorState.status.collect { status ->
                    b.textStatus.text = when {
                        !prefs.enabled -> getString(R.string.status_stopped)
                        status.running && status.text.isNotBlank() -> status.text
                        else -> getString(R.string.status_starting)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        restartWatcher()
        refresh()
        // Falls der Dienst vom System beendet wurde: wieder starten.
        if (prefs.enabled) WifiMonitorService.startIfEnabled(this)
    }

    override fun onPause() {
        watcher.stop()
        super.onPause()
    }

    // ---------- Automatik ----------

    private fun onToggleAutomation(checked: Boolean) {
        if (checked && prefs.homeSsids.isEmpty()) {
            toast(getString(R.string.toast_add_home_first))
            b.switchEnabled.isChecked = false
            return
        }
        prefs.enabled = checked
        if (checked) {
            prefs.lastVpnOn = null
            WifiMonitorService.startIfEnabled(this)
            if (missingSetupSteps().isNotEmpty()) nextFix?.invoke()
        } else {
            WifiMonitorService.stop(this)
        }
        refresh()
    }

    private fun restartServiceIfEnabled() {
        if (prefs.enabled) {
            WifiMonitorService.stop(this)
            WifiMonitorService.startIfEnabled(this)
        }
    }

    // ---------- Heim-WLANs ----------

    private fun addCurrentWifi() {
        if (!hasFineLocation()) {
            toast(getString(R.string.toast_need_location))
            requestForegroundPermissions()
            return
        }
        if (!isLocationEnabled()) {
            toast(getString(R.string.toast_enable_location))
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        val ssid = watcher.knownSsids.firstOrNull()
        if (ssid == null) {
            toast(getString(if (watcher.hasWifi) R.string.toast_ssid_not_ready else R.string.toast_no_wifi))
            return
        }
        addSsid(ssid)
    }

    private fun addManual() {
        val ssid = b.editSsid.text?.toString()?.trim().orEmpty()
        if (ssid.isEmpty()) return
        addSsid(ssid)
        b.editSsid.setText("")
    }

    private fun addSsid(ssid: String) {
        if (ssid in prefs.homeSsids) {
            toast(getString(R.string.toast_ssid_exists, ssid))
            return
        }
        prefs.homeSsids = prefs.homeSsids + ssid
        toast(getString(R.string.toast_ssid_saved, ssid))
        refresh()
    }

    private fun removeSsid(ssid: String) {
        prefs.homeSsids = prefs.homeSsids - ssid
        if (prefs.homeSsids.isEmpty() && prefs.enabled) {
            prefs.enabled = false
            b.switchEnabled.isChecked = false
            WifiMonitorService.stop(this)
        }
        refresh()
    }

    // ---------- Anzeige ----------

    private fun refresh() {
        renderSsids()
        refreshCurrentWifi()
        renderSetup()
        if (!prefs.enabled) b.textStatus.text = getString(R.string.status_stopped)
    }

    private fun renderSsids() {
        val ssids = prefs.homeSsids.sorted()
        b.chipsSsids.removeAllViews()
        for (ssid in ssids) {
            val chip = Chip(this).apply {
                text = ssid
                isCloseIconVisible = true
                setOnCloseIconClickListener { removeSsid(ssid) }
            }
            b.chipsSsids.addView(chip)
        }
        b.textNoSsids.visibility = if (ssids.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshCurrentWifi() {
        val ssid = watcher.knownSsids.firstOrNull()
        b.textCurrentWifi.text = when {
            ssid != null && ssid in prefs.homeSsids -> getString(R.string.current_wifi_home, ssid)
            ssid != null -> getString(R.string.current_wifi, ssid)
            watcher.hasWifi -> getString(R.string.current_wifi_unreadable)
            else -> getString(R.string.current_wifi_none)
        }
    }

    private fun updateDelayLabel() {
        b.textDelay.text = getString(R.string.delay_label, b.sliderDelay.value.toInt())
    }

    private data class SetupStep(val description: String, val fix: () -> Unit)

    private fun missingSetupSteps(): List<SetupStep> {
        val steps = mutableListOf<SetupStep>()
        vpnSetupStep()?.let { steps += it }
        if (!hasFineLocation()) {
            steps += SetupStep(getString(R.string.setup_location_missing)) {
                requestForegroundPermissions()
            }
        } else if (needsBackgroundLocation()) {
            steps += SetupStep(getString(R.string.setup_background_location)) {
                askBackgroundLocation()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            steps += SetupStep(getString(R.string.setup_notifications)) {
                requestForegroundPermissions()
            }
        }
        if (!isLocationEnabled()) {
            steps += SetupStep(getString(R.string.setup_location_off)) {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
        if (!isIgnoringBatteryOptimizations()) {
            steps += SetupStep(getString(R.string.setup_battery)) {
                requestIgnoreBatteryOptimizations()
            }
        }
        return steps
    }

    private fun renderSetup() {
        val steps = missingSetupSteps()
        if (steps.isEmpty()) {
            b.cardSetup.visibility = View.GONE
            nextFix = null
        } else {
            b.cardSetup.visibility = View.VISIBLE
            b.textSetup.text = steps.joinToString("\n") { "• ${it.description}" }
            nextFix = steps.first().fix
        }
    }

    // ---------- Berechtigungen ----------

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasFineLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun needsBackgroundLocation() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun isLocationEnabled(): Boolean =
        LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java))

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun requestForegroundPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        foregroundPermLauncher.launch(perms.toTypedArray())
    }

    private fun askBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bg_dialog_title)
            .setMessage(R.string.bg_dialog_message)
            .setPositiveButton(R.string.dialog_continue) { _, _ ->
                backgroundPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    // ---------- VPN-Dienst ----------

    private fun setupVpnCard() {
        b.editWgTunnel.setText(prefs.wireguardTunnel)
        b.editCustomPackage.setText(prefs.customPackage)
        b.editCustomClass.setText(prefs.customReceiverClass)
        b.editCustomConnect.setText(prefs.customConnectAction)
        b.editCustomDisconnect.setText(prefs.customDisconnectAction)
        b.editCustomExtras.setText(prefs.customExtras)

        val radioFor = mapOf(
            VpnProvider.TAILSCALE to b.radioTailscale.id,
            VpnProvider.WIREGUARD to b.radioWireguard.id,
            VpnProvider.CUSTOM to b.radioCustom.id,
        )
        b.radioProvider.check(radioFor.getValue(prefs.provider))
        renderProviderFields()
        b.radioProvider.setOnCheckedChangeListener { _, checkedId ->
            val provider = radioFor.entries.first { it.value == checkedId }.key
            if (provider != prefs.provider) {
                prefs.provider = provider
                prefs.lastVpnOn = null
            }
            renderProviderFields()
            refresh()
        }

        b.buttonSaveVpn.setOnClickListener {
            prefs.wireguardTunnel = b.editWgTunnel.text?.toString()?.trim().orEmpty()
            prefs.customPackage = b.editCustomPackage.text?.toString()?.trim().orEmpty()
            prefs.customReceiverClass = b.editCustomClass.text?.toString()?.trim().orEmpty()
            prefs.customConnectAction = b.editCustomConnect.text?.toString()?.trim().orEmpty()
            prefs.customDisconnectAction = b.editCustomDisconnect.text?.toString()?.trim().orEmpty()
            prefs.customExtras = b.editCustomExtras.text?.toString().orEmpty()
            toast(getString(R.string.toast_saved))
            refresh()
        }
    }

    private fun renderProviderFields() {
        val provider = prefs.provider
        b.groupWireguard.visibility = if (provider == VpnProvider.WIREGUARD) View.VISIBLE else View.GONE
        b.groupCustom.visibility = if (provider == VpnProvider.CUSTOM) View.VISIBLE else View.GONE
        b.buttonSaveVpn.visibility = if (provider == VpnProvider.TAILSCALE) View.GONE else View.VISIBLE
    }

    private fun vpnSetupStep(): SetupStep? {
        val problem = VpnController.configProblem(this, prefs) ?: return null
        val provider = prefs.provider
        val pkg = provider.packageName
        return when {
            pkg != null && !VpnController.isInstalled(this, pkg) ->
                SetupStep(problem) { openStore(pkg) }

            provider == VpnProvider.WIREGUARD && prefs.wireguardTunnel.isNotBlank() &&
                !VpnController.hasWireGuardPermission(this) ->
                SetupStep(problem) { wireguardPermLauncher.launch(VpnController.WIREGUARD_PERMISSION) }

            else -> SetupStep(problem) {
                toast(getString(R.string.toast_fill_vpn_settings))
            }
        }
    }

    private fun sendManual(on: Boolean) {
        when (val result = VpnController.setConnected(this, prefs, on)) {
            is SendResult.Sent -> {
                val name = prefs.provider.displayName(this)
                toast(getString(if (on) R.string.toast_connect_sent else R.string.toast_disconnect_sent, name))
            }
            is SendResult.Failed -> {
                toast(result.reason)
                vpnSetupStep()?.fix?.invoke()
            }
        }
    }

    private fun openStore(pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        runCatching { startActivity(market) }.onFailure { runCatching { startActivity(web) } }
    }

    // ---------- Hilfsfunktionen ----------

    private fun restartWatcher() {
        watcher.stop()
        watcher.start()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
