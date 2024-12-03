package com.example.android_coding_teamproject

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class DeviceListActivity : AppCompatActivity() {

    private lateinit var deviceListView: ListView
    private lateinit var refreshButton: ImageButton
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private lateinit var handler: Handler
    private var scanRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        deviceListView = findViewById(R.id.device_list_view)
        refreshButton = findViewById(R.id.refresh_button)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        deviceListView.adapter = deviceAdapter

        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val device = deviceList[position]
            connectToDevice(device)
        }
        refreshButton.setOnClickListener {
            loadDeviceList()
        }

        loadDeviceList()
    }

    override fun onResume() {
        super.onResume()
        startBleScan()
    }

    override fun onPause() {
        super.onPause()
        stopBleScan()
    }

    private fun loadDeviceList() {
        Log.i("DeviceListActivity", "기기 목록을 새로 고침합니다.")
        deviceList.clear()
        deviceAdapter.clear()

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            startBleScan()
        }
    }

    private fun startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothLeScanner.startScan(scanCallback)
                Log.i("DeviceListActivity", "BLE 스캔을 시작합니다.")
            } else {
                requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
        } else {
            bluetoothLeScanner.startScan(scanCallback)
            Log.i("DeviceListActivity", "BLE 스캔을 시작합니다.")
        }
    }

    private fun stopBleScan() {
        bluetoothLeScanner.stopScan(scanCallback)
        Log.i("DeviceListActivity", "BLE 스캔을 중지합니다.")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { scanResult ->
                val device = scanResult.device
                val rssi = scanResult.rssi

                if (!deviceList.contains(device)) {
                    deviceList.add(device)
                    deviceList.sortByDescending { scanResult.rssi }
                    if (deviceList.size > 20) {
                        deviceList.subList(20, deviceList.size).clear()
                    }
                    updateDeviceListView()
                }
            }
        }
    }

    private fun updateDeviceListView() {
        val deviceNames = deviceList.map { device ->
            device.name ?: "Unnamed Device"
        }
        deviceAdapter.clear()
        deviceAdapter.addAll(deviceNames)
        deviceAdapter.notifyDataSetChanged()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("연결 확인")
            .setMessage("${device.name}에 연결하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                Log.i("DeviceListActivity", "장치에 연결을 시도합니다.")
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    gatt = device.connectGatt(this, false, gattCallback)
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        PERMISSION_REQUEST_CODE
                    )
                }
            }
            .setNegativeButton("아니요") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("DeviceListActivity", "BLE 장치에 연결되었습니다.")
                gatt?.discoverServices() // 서비스 검색 시작
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("DeviceListActivity", "BLE 장치와 연결이 끊어졌습니다.")
                gatt?.disconnect()
                gatt?.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("DeviceListActivity", "GATT 서비스가 발견되었습니다.")

                val intent = Intent(this@DeviceListActivity, MainActivity::class.java)

                gatt?.device?.let { device ->
                    Log.i("DeviceListActivity", "연결된 장치: ${device.name}")
                    intent.putExtra("connectedDevice", device)
                }

                startActivity(intent)
                finish()
            } else {
                Log.e("DeviceListActivity", "GATT 서비스 검색에 실패했습니다.")
            }
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            startBleScan()
        } else {
            Log.e("DeviceListActivity", "필수 권한이 거부되었습니다.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startBleScan()
            } else {
                Toast.makeText(this, "BLUETOOTH_CONNECT 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val PERMISSION_REQUEST_CODE = 2
    }
}