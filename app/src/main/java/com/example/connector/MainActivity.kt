package com.example.connector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.connector.ui.theme.ConnectorTheme


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.*



class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var selectedDevice: BluetoothDevice? = null

    // State variables for Compose UI
    private val connectionStatus = mutableStateOf("غير متصل") // "Not Connected"
    private val availableDevices = mutableStateListOf<BluetoothDevice>()
    private val isConnecting = mutableStateOf(false)
    private val isConnected = mutableStateOf(false)
    private val errorMessage = mutableStateOf<String?>(null)
    private val showDeviceListDialog = mutableStateOf(false)

    // Activity Result Launcher for Bluetooth Enable Intent
    private val requestBluetoothEnable =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled by user.")
                scanForDevices() // Scan for devices after enabling
            } else {
                Log.d(TAG, "User denied Bluetooth enable request.")
                errorMessage.value = "يجب تفعيل البلوتوث لاستخدام التطبيق" // "Bluetooth must be enabled to use the app"
            }
        }

    // Activity Result Launcher for Permissions
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allGranted = false
                    Log.e(TAG, "Permission denied: ${it.key}")
                }
            }
            if (allGranted) {
                Log.d(TAG, "All required Bluetooth permissions granted.")
                initializeBluetooth() // Proceed with Bluetooth setup
            } else {
                Log.e(TAG, "One or more Bluetooth permissions were denied.")
                errorMessage.value = "أذونات البلوتوث ضرورية لتشغيل التطبيق" // "Bluetooth permissions are required for the app to function"
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            ArduinoBluetoothTheme { // Use the defined theme
                MainScreen(
                    connectionStatus = connectionStatus.value,
                    isConnected = isConnected.value,
                    onConnectClicked = {
                        if (hasRequiredBluetoothPermissions()) {
                            if (bluetoothAdapter?.isEnabled == true) {
                                scanForDevices() // Refresh list before showing
                                showDeviceListDialog.value = true
                            } else {
                                initializeBluetooth() // Prompt to enable BT if off
                            }
                        } else {
                            requestBluetoothPermissions() // Request permissions if missing
                        }
                    },
                    onDisconnectClicked = { disconnect() },
                    onSendCommand = { command -> sendCommand(command) },
                    isConnecting = isConnecting.value,
                    errorMessage = errorMessage.value,
                    onDismissError = { errorMessage.value = null } // Allow dismissing error messages
                )

                if (showDeviceListDialog.value) {
                    DeviceListDialog(
                        devices = availableDevices,
                        onDeviceSelected = { device ->
                            selectedDevice = device
                            showDeviceListDialog.value = false
                            connectToDevice(device)
                        },
                        onDismissRequest = { showDeviceListDialog.value = false }
                    )
                }
            }
        }


        // Check and request permissions on startup
        if (!hasRequiredBluetoothPermissions()) {
            requestBluetoothPermissions()
        } else {
            initializeBluetooth()
        }


    }



    override fun onDestroy() {
        super.onDestroy()
        disconnect() // Ensure disconnection on activity destroy
    }

    // --- Bluetooth Permission Handling ---

    private fun hasRequiredBluetoothPermissions(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // For older versions, BLUETOOTH and BLUETOOTH_ADMIN are install-time
            // ACCESS_FINE_LOCATION might be needed for discovery on API 23-30
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                // Add location permission if targeting API 23-30 and discovery doesn't work
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) Manifest.permission.ACCESS_FINE_LOCATION else "" // Add dummy empty string if not needed
            ).filter { it.isNotEmpty() }.toTypedArray()
        }
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH, // Although install-time, check anyway
                Manifest.permission.BLUETOOTH_ADMIN, // Although install-time, check anyway
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) Manifest.permission.ACCESS_FINE_LOCATION else ""
            ).filter { it.isNotEmpty() }.toTypedArray()
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting runtime permissions: ${permissionsToRequest.joinToString()}")
            requestMultiplePermissions.launch(permissionsToRequest)
        } else {
            Log.d(TAG, "All required permissions already granted.")
            initializeBluetooth() // Permissions are granted, proceed
        }
    }

    // --- Bluetooth Initialization and Device Discovery ---

    @SuppressLint("MissingPermission") // Permissions checked before calling
    private fun initializeBluetooth() {
        errorMessage.value = null // Clear previous errors
        if (bluetoothAdapter == null) {
            errorMessage.value = "الجهاز لا يدعم البلوتوث" // "Device does not support Bluetooth"
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            // Request to enable Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Check for CONNECT permission before launching intent on Android 12+
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "BLUETOOTH_CONNECT permission needed to request enable.")
                        errorMessage.value = "إذن BLUETOOTH_CONNECT مطلوب لتفعيل البلوتوث."
                        requestBluetoothPermissions() // Re-request permissions
                        return // Stop initialization until permission is granted
                    }
                }
                // Launch the intent to enable Bluetooth
                requestBluetoothEnable.launch(enableBtIntent)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException when requesting Bluetooth enable", e)
                errorMessage.value = "خطأ في أذونات تفعيل البلوتوث" // "Permission error enabling Bluetooth"
                requestBluetoothPermissions() // Re-request permissions
            }
        } else {
            Log.d(TAG, "Bluetooth is already enabled.")
            // Bluetooth is enabled, list paired devices initially
            scanForDevices() // List paired devices
        }
    }

    @SuppressLint("MissingPermission") // Permissions checked before calling
    private fun scanForDevices() {
        if (!hasRequiredBluetoothPermissions()) {
            errorMessage.value = "أذونات البلوتوث مطلوبة للبحث عن الأجهزة" // "Bluetooth permissions required to scan for devices"
            // Don't request here directly, let the UI trigger flow handle it
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            errorMessage.value = "يجب تفعيل البلوتوث أولاً" // "Bluetooth must be enabled first"
            initializeBluetooth() // Prompt to enable
            return
        }

        Log.d(TAG, "Scanning for paired devices...")
        availableDevices.clear() // Clear previous list
        var foundDevices = false
        try {
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            if (pairedDevices.isNullOrEmpty()) {
                Log.d(TAG, "No paired devices found.")
                // Optionally, you could start discovery here if needed, but requires more handling
                // bluetoothAdapter?.startDiscovery()
                // Remember to handle discovery results via BroadcastReceiver
                errorMessage.value = "لم يتم العثور على أجهزة مقترنة. يرجى إقران جهاز HC-05 أولاً في إعدادات البلوتوث."
            } else {
                pairedDevices.forEach { device ->
                    val deviceName = device.name ?: "Unknown Device"
                    val deviceAddress = device.address
                    Log.d(TAG, "Found paired device: $deviceName [$deviceAddress]")
                    availableDevices.add(device)
                    foundDevices = true
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during Bluetooth scan", e)
            errorMessage.value = "خطأ في أذونات البحث عن الأجهزة" // "Permission error scanning devices"
            requestBluetoothPermissions() // Re-request permissions
        }
        if (!foundDevices && availableDevices.isEmpty()) {
            Log.d(TAG, "No paired devices available after scan.")
            // Keep the error message about pairing first if still no devices
            if (errorMessage.value == null) {
                errorMessage.value = "لا توجد أجهزة مقترنة متاحة. يرجى الاقتران أولاً."
            }
        }
    }

    // --- Bluetooth Connection ---

    @SuppressLint("MissingPermission") // Permissions checked before calling
    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasRequiredBluetoothPermissions()) {
            errorMessage.value = "أذونات البلوتوث مطلوبة للاتصال" // "Bluetooth permissions required to connect"
            requestBluetoothPermissions()
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            errorMessage.value = "يجب تفعيل البلوتوث أولاً" // "Bluetooth must be enabled first"
            initializeBluetooth()
            return
        }

        isConnecting.value = true
        connectionStatus.value = "جاري الاتصال بـ ${device.name ?: device.address}..." // "Connecting to..."
        errorMessage.value = null

        // Ensure any previous connection is closed
        disconnect()

        CoroutineScope(Dispatchers.IO).launch {
            var connected = false
            try {
                Log.d(TAG, "Attempting to connect to ${device.name ?: device.address}")
                // Get a BluetoothSocket to connect with the given BluetoothDevice
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)

                // Cancel discovery because it otherwise slows down the connection.
                // Requires BLUETOOTH_SCAN on API 31+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothAdapter?.cancelDiscovery()
                    } else {
                        Log.w(TAG, "BLUETOOTH_SCAN permission missing, cannot cancel discovery.")
                        // Proceeding without cancelling discovery might slow down connection
                    }
                } else {
                    // Requires BLUETOOTH_ADMIN pre-API 31
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothAdapter?.cancelDiscovery()
                    } else {
                        Log.w(TAG, "BLUETOOTH_ADMIN permission missing, cannot cancel discovery.")
                    }
                }

                bluetoothSocket?.connect() // Blocking call
                outputStream = bluetoothSocket?.outputStream
                connected = true
                Log.d(TAG, "Connection successful to ${device.name ?: device.address}")

            } catch (e: IOException) {
                Log.e(TAG, "IOException during connection attempt", e)
                withContext(Dispatchers.Main) {
                    errorMessage.value = "فشل الاتصال بـ ${device.name ?: device.address}. تأكد من أن الجهاز قيد التشغيل وفي النطاق."
                    // "Connection failed to... Make sure the device is on and in range."
                }
                disconnect() // Close socket on error
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during connection attempt", e)
                withContext(Dispatchers.Main) {
                    errorMessage.value = "خطأ في أذونات الاتصال بالبلوتوث" // "Bluetooth connection permission error"
                    requestBluetoothPermissions() // Re-request permissions
                }
                disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during connection attempt", e)
                withContext(Dispatchers.Main) {
                    errorMessage.value = "حدث خطأ غير متوقع أثناء الاتصال." // "An unexpected error occurred during connection."
                }
                disconnect()
            }

            withContext(Dispatchers.Main) {
                isConnecting.value = false
                if (connected) {
                    isConnected.value = true
                    connectionStatus.value = "متصل بـ ${device.name ?: device.address}" // "Connected to..."
                } else {
                    isConnected.value = false
                    connectionStatus.value = "فشل الاتصال" // "Connection Failed"
                }
            }
        }
    }

    // --- Send Command ---

    private fun sendCommand(command: String) {
        if (bluetoothSocket?.isConnected == true && outputStream != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    outputStream?.write(command.toByteArray())
                    outputStream?.flush() // Ensure data is sent
                    Log.d(TAG, "Sent command: $command")
                    // Optionally update UI or show confirmation
                    withContext(Dispatchers.Main) {
                        // You could show a temporary confirmation message here if needed
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending command: $command", e)
                    withContext(Dispatchers.Main) {
                        errorMessage.value = "خطأ في إرسال الأمر. حاول إعادة الاتصال." // "Error sending command. Try reconnecting."
                        disconnect() // Disconnect on send error
                    }
                }
            }
        } else {
            errorMessage.value = "غير متصل بجهاز. يرجى الاتصال أولاً." // "Not connected to a device. Please connect first."
            Log.w(TAG, "SendCommand called but not connected.")
        }
    }

    // --- Disconnect ---

    private fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d(TAG, "Bluetooth socket closed.")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing Bluetooth socket", e)
        } finally {
            outputStream = null
            bluetoothSocket = null
            selectedDevice = null
            // Update UI state on the main thread
            CoroutineScope(Dispatchers.Main).launch {
                isConnected.value = false
                isConnecting.value = false
                connectionStatus.value = "غير متصل" // "Not Connected"
            }
        }
    }


}

// --- Composable UI Elements ---

@Composable
fun MainScreen(
    connectionStatus: String,
    isConnected: Boolean,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
    onSendCommand: (String) -> Unit,
    isConnecting: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Arduino Bluetooth Controller", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "الحالة: $connectionStatus", style = MaterialTheme.typography.bodyLarge) // "Status: ..."
        Spacer(modifier = Modifier.height(16.dp))

        // Show Connect/Disconnect Button
        if (isConnected) {
            Button(onClick = onDisconnectClicked, enabled = !isConnecting) {
                Text("قطع الاتصال") // "Disconnect"
            }
        } else {
            Button(onClick = onConnectClicked, enabled = !isConnecting) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري الاتصال...") // "Connecting..."
                } else {
                    Text("اتصال بجهاز") // "Connect to Device"
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ON/OFF Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { onSendCommand("A") },
                enabled = isConnected // Enable only when connected
            ) {
                Text("ON")
            }
            Button(
                onClick = { onSendCommand("B") },
                enabled = isConnected // Enable only when connected
            ) {
                Text("OFF")
            }
        }

        // Error Message Display
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text("خطأ") }, // "Error"
                text = { Text(errorMessage) },
                confirmButton = {
                    Button(onClick = onDismissError) {
                        Text("حسناً") // "OK"
                    }
                }
            )
        }
    }
}

@SuppressLint("MissingPermission") // Permissions should be checked before calling scanForDevices which populates this
@Composable
fun DeviceListDialog(
    devices: List<BluetoothDevice>,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f), // Adjust width as needed
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("اختر جهاز HC-05", style = MaterialTheme.typography.titleMedium) // "Select HC-05 Device"
                Spacer(modifier = Modifier.height(16.dp))

                if (devices.isEmpty()) {
                    Text("لم يتم العثور على أجهزة مقترنة. يرجى الاقتران في إعدادات البلوتوث.")
                    // "No paired devices found. Please pair in Bluetooth settings."
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { // Limit height
                        items(devices) { device ->
                            // Basic check for permission before accessing name/address
                            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                            } else {
                                ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
                            }

                            if (hasPermission) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onDeviceSelected(device) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${device.name ?: "جهاز غير معروف"}\n${device.address}") // "Unknown Device"
                                }
                                Divider()
                            } else {
                                // Handle case where permission might be missing unexpectedly
                                Text("Permission missing for device: ${device.address}")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismissRequest, modifier = Modifier.align(Alignment.End)) {
                    Text("إلغاء") // "Cancel"
                }
            }
        }
    }
}


// UUID for Serial Port Profile (SPP) - Standard for HC-05
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val TAG = "MainActivity"

// --- Theme Placeholder ---
@Composable
fun ArduinoBluetoothTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}