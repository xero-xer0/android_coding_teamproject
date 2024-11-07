package com.example.android_coding_teamproject

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DeviceListActivity : AppCompatActivity() {

    private lateinit var deviceListView: ListView
    private lateinit var refreshButton: ImageButton
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var connectedDeviceName: String? = null

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

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                loadDeviceList()
                handler.postDelayed(this, 20000)
            }
        }, 20000)

        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val device = deviceList[position]
            connectToDevice(device)
        }

        refreshButton.setOnClickListener {
            loadDeviceList()
        }

        loadDeviceList()
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.startScan(scanCallback)
                Log.i("DeviceListActivity", "BLE 스캔을 시작합니다.")
            } else {
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
            }
        } else {
            bluetoothLeScanner.startScan(scanCallback)
            Log.i("DeviceListActivity", "BLE 스캔을 시작합니다.")
        }
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
            val deviceName = device.name ?: "Unnamed Device"
            if (connectedDeviceName != null && device.name == connectedDeviceName) {
                "Connected: $deviceName"
            } else {
                deviceName
            }
        }
        deviceAdapter.clear()
        deviceAdapter.addAll(deviceNames)
        deviceAdapter.notifyDataSetChanged()
    }

    // DeviceListActivity.kt 내 connectToDevice 함수 수정
    private fun connectToDevice(device: BluetoothDevice) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("연결 확인")
            .setMessage("${device.name}에 연결하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                Log.i("DeviceListActivity", "장치에 연결을 시도합니다.")
                gatt = device.connectGatt(this, false, gattCallback)
                // 연결된 장치 정보를 MainActivity로 전달
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("connectedDevice", device)
                startActivity(intent)
            }
            .setNegativeButton("아니요") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("DeviceListActivity", "BLE 장치에 연결되었습니다.")
                gatt?.discoverServices() // 서비스 검색 시작
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("DeviceListActivity", "BLE 장치와 연결이 끊어졌습니다.")
                gatt?.close() // GATT 리소스를 해제
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("DeviceListActivity", "GATT 서비스가 발견되었습니다.")
            } else {
                Log.e("DeviceListActivity", "GATT 서비스 검색에 실패했습니다.")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.value?.let { value ->
                    Log.i("DeviceListActivity", "특성 읽기 성공: ${value.contentToString()}")
                }
            } else {
                Log.e("DeviceListActivity", "특성 읽기에 실패했습니다.")
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

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
}