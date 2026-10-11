package com.example.haptivoice.utils

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

// =============================================================
// DISPOSITIVO BLE
// =============================================================
data class BleDevice(
    val name: String,
    val address: String
)
// =============================================================
// BLUETOOTH MANAGER
// =============================================================
class BluetoothManager(
    private val context: Context
) {

    companion object {

        /*
         * Estos UUID son exactamente los mismos
         * que configuramos en el ESP32.
         */

        val SERVICE_UUID: UUID =
            UUID.fromString(
                "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
            )

        val CHARACTERISTIC_UUID: UUID =
            UUID.fromString(
                "beb5483e-36e1-4688-b7f5-ea07361b26a8"
            )

        /*
         * Descriptor estándar de BLE utilizado
         * para activar las notificaciones.
         */

        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )

        // El sistema permite conectar como máximo dos guantes.
        const val MAX_CONNECTED_GLOVES = 2
    }

    // =========================================================
    // BLUETOOTH DEL TELÉFONO
    // =========================================================

    private val androidBluetoothManager: android.bluetooth.BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? android.bluetooth.BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        androidBluetoothManager?.adapter
    }

    private val bluetoothLeScanner
        get() = bluetoothAdapter?.bluetoothLeScanner

    // =========================================================
    // CONEXIONES GATT
    // =========================================================

    /*
     * Guardamos una conexión independiente por cada ESP32.
     * La dirección Bluetooth identifica cada guante.
     */

    private val bluetoothGatts =
        mutableMapOf<String, BluetoothGatt>()

    /*
     * Evita solicitar varias veces la conexión
     * al mismo dispositivo mientras todavía está conectando.
     */

    private val pendingConnections =
        mutableSetOf<String>()

    private val connectionLock = Any()

    // =========================================================
    // ESTADOS PARA JETPACK COMPOSE
    // =========================================================

    private val _scannedDevices =
        MutableStateFlow<List<BleDevice>>(
            emptyList()
        )

    val scannedDevices:
            StateFlow<List<BleDevice>> =
        _scannedDevices.asStateFlow()


    private val _connectedGlovesCount =
        MutableStateFlow(0)

    val connectedGlovesCount:
            StateFlow<Int> =
        _connectedGlovesCount.asStateFlow()


    /*
     * Direcciones Bluetooth de los guantes
     * que están realmente conectados.
     */

    private val _connectedDeviceAddresses =
        MutableStateFlow<Set<String>>(
            emptySet()
        )

    val connectedDeviceAddresses:
            StateFlow<Set<String>> =
        _connectedDeviceAddresses.asStateFlow()

    // Direcciones de los guantes silenciados.
    private val _mutedDeviceAddresses =
        MutableStateFlow<Set<String>>(emptySet())

    val mutedDeviceAddresses: StateFlow<Set<String>> =
        _mutedDeviceAddresses.asStateFlow()


    private val _incomingMessage =
        MutableStateFlow(
            "Esperando datos del ESP32..."
        )

    val incomingMessage:
            StateFlow<String> =
        _incomingMessage.asStateFlow()

    // =========================================================
// SILENCIAR GUANTES
// =========================================================

    fun toggleMute(deviceAddress: String) {

        synchronized(connectionLock) {

            // Solo se puede mutear un guante conectado.
            if (deviceAddress !in _connectedDeviceAddresses.value) {
                return
            }

            val mutedAddresses =
                _mutedDeviceAddresses.value.toMutableSet()

            if (deviceAddress in mutedAddresses) {
                mutedAddresses.remove(deviceAddress)
            } else {
                mutedAddresses.add(deviceAddress)
            }

            _mutedDeviceAddresses.value = mutedAddresses.toSet()
        }
    }

    // Indica si se permite reproducir la voz de este guante.
    fun shouldSpeakFromDevice(deviceAddress: String): Boolean {
        return deviceAddress in _connectedDeviceAddresses.value &&
                deviceAddress !in _mutedDeviceAddresses.value
    }

    // =========================================================
    // ACTUALIZAR GUANTES CONECTADOS
    // =========================================================

    private fun updateConnectedDevice(
        deviceAddress: String,
        connected: Boolean
    ) {

        synchronized(connectionLock) {

            val currentAddresses =
                _connectedDeviceAddresses
                    .value
                    .toMutableSet()

            if (connected) {
                currentAddresses.add(deviceAddress)
            } else {
                currentAddresses.remove(deviceAddress)
                // Al desconectar, eliminamos también su estado de muteo.
                _mutedDeviceAddresses.value =
                    _mutedDeviceAddresses.value - deviceAddress
            }

            _connectedDeviceAddresses.value =
                currentAddresses.toSet()

            _connectedGlovesCount.value =
                currentAddresses.size
        }
    }

    // =========================================================
    // ESCANEO BLE
    // =========================================================

    private val scanCallback =
        object : ScanCallback() {

            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                super.onScanResult(
                    callbackType,
                    result
                )

                val device =
                    result.device

                val deviceName =
                    result.scanRecord?.deviceName
                        ?: device.name
                        ?: return

                // Solo mostramos dispositivos que tengan un nombre válido.
                if (deviceName.isBlank()){
                    return
                }


                val deviceAddress =
                    device.address

                val newDevice =
                    BleDevice(
                        name = deviceName,
                        address = deviceAddress
                    )

                val currentDevices =
                    _scannedDevices
                        .value
                        .toMutableList()

                /*
                 * Evitamos que un mismo dispositivo
                 * aparezca muchas veces.
                 */

                val alreadyExists =
                    currentDevices.any {
                        it.address ==
                                deviceAddress
                    }

                if (!alreadyExists) {

                    currentDevices.add(
                        newDevice
                    )

                    _scannedDevices.value =
                        currentDevices
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {

                super.onScanFailed(
                    errorCode
                )

                _incomingMessage.value =
                    "Error al escanear Bluetooth: $errorCode"
            }
        }

    // =========================================================
    // CALLBACK DE LA CONEXIÓN BLE
    // =========================================================

    private val gattCallback =
        object : BluetoothGattCallback() {

            // -------------------------------------------------
            // CONEXIÓN / DESCONEXIÓN
            // -------------------------------------------------

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                super.onConnectionStateChange(
                    gatt,
                    status,
                    newState
                )

                val deviceAddress =
                    gatt.device.address

                if (
                    newState ==
                    BluetoothProfile.STATE_CONNECTED
                ) {

                    synchronized(connectionLock) {
                        pendingConnections.remove(
                            deviceAddress
                        )

                        bluetoothGatts[deviceAddress] =
                            gatt
                    }

                    updateConnectedDevice(
                        deviceAddress = deviceAddress,
                        connected = true
                    )

                    _incomingMessage.value =
                        "ESP32 conectado. Buscando servicios..."

                    /*
                     * Una vez conectado,
                     * buscamos los servicios BLE
                     * disponibles en el ESP32.
                     */

                    gatt.discoverServices()

                    /*
                     * Si ya tenemos los dos guantes,
                     * no necesitamos continuar escaneando.
                     */

                    if (
                        _connectedGlovesCount.value >=
                        MAX_CONNECTED_GLOVES
                    ) {
                        stopScan()
                    }

                } else if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    synchronized(connectionLock) {

                        pendingConnections.remove(
                            deviceAddress
                        )

                        bluetoothGatts.remove(
                            deviceAddress
                        )
                    }

                    updateConnectedDevice(
                        deviceAddress = deviceAddress,
                        connected = false
                    )

                    gatt.close()

                    if (_connectedGlovesCount.value == 0) {

                        _incomingMessage.value =
                            "ESP32 desconectado."

                    } else {

                        _incomingMessage.value =
                            "Guantes conectados: ${_connectedGlovesCount.value}"
                    }
                }
            }

            // -------------------------------------------------
            // SERVICIOS DESCUBIERTOS
            // -------------------------------------------------

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                super.onServicesDiscovered(
                    gatt,
                    status
                )

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    _incomingMessage.value =
                        "Error al descubrir servicios BLE."

                    return
                }

                /*
                 * Buscamos el mismo SERVICE_UUID
                 * que configuramos en el ESP32.
                 */

                val service =
                    gatt.getService(
                        SERVICE_UUID
                    )

                if (service == null) {

                    _incomingMessage.value =
                        "No se encontró el servicio del ESP32."

                    return
                }

                /*
                 * Dentro del servicio buscamos
                 * nuestra característica.
                 */

                val characteristic =
                    service.getCharacteristic(
                        CHARACTERISTIC_UUID
                    )

                if (characteristic == null) {

                    _incomingMessage.value =
                        "No se encontró la característica del ESP32."

                    return
                }

                /*
                 * Android comienza a escuchar
                 * las notificaciones de esta característica.
                 */

                val notificationEnabled =
                    gatt.setCharacteristicNotification(
                        characteristic,
                        true
                    )

                if (!notificationEnabled) {

                    _incomingMessage.value =
                        "No se pudieron activar las notificaciones BLE."

                    return
                }

                /*
                 * Ahora habilitamos el descriptor 0x2902,
                 * exactamente lo que hicimos manualmente
                 * en nRF Connect al activar Notify.
                 */

                val descriptor =
                    characteristic.getDescriptor(
                        CLIENT_CHARACTERISTIC_CONFIG_UUID
                    )

                if (descriptor == null) {

                    _incomingMessage.value =
                        "No se encontró el descriptor de notificaciones."

                    return
                }

                enableNotifications(
                    gatt,
                    descriptor
                )
            }

            // -------------------------------------------------
            // MENSAJE RECIBIDO - ANDROID 13+
            // -------------------------------------------------

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {

                super.onCharacteristicChanged(
                    gatt,
                    characteristic,
                    value
                )

                if (
                    characteristic.uuid ==
                    CHARACTERISTIC_UUID
                ) {

                    handleIncomingMessage(
                        value
                    )
                }
            }

            // -------------------------------------------------
            // MENSAJE RECIBIDO - ANDROID ANTIGUO
            // -------------------------------------------------

            @Deprecated(
                "Usado para compatibilidad con versiones antiguas de Android"
            )
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {

                super.onCharacteristicChanged(
                    gatt,
                    characteristic
                )

                if (
                    characteristic.uuid ==
                    CHARACTERISTIC_UUID
                ) {

                    @Suppress("DEPRECATION")
                    val value =
                        characteristic.value
                            ?: return

                    handleIncomingMessage(
                        value
                    )
                }
            }
        }

    // =========================================================
    // PROCESAR MENSAJE DEL ESP32
    // =========================================================

    private fun handleIncomingMessage(
        data: ByteArray
    ) {

        /*
         * El ESP32 manda bytes.
         *
         * Nosotros los convertimos nuevamente
         * a texto UTF-8.
         */

        val message =
            data.toString(
                Charsets.UTF_8
            )

        _incomingMessage.value =
            message
    }

    // =========================================================
    // ACTIVAR NOTIFICACIONES
    // =========================================================

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor
    ) {

        /*
         * Desde Android 13 existe una
         * forma nueva de escribir el descriptor.
         */

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE
            )

        } else {

            /*
             * Para Android 12 o anteriores.
             */

            @Suppress("DEPRECATION")
            run {
                descriptor.value =
                    BluetoothGattDescriptor
                        .ENABLE_NOTIFICATION_VALUE

                gatt.writeDescriptor(
                    descriptor
                )
            }
        }

        _incomingMessage.value =
            "ESP32 conectado. Esperando mensaje..."
    }

    // =========================================================
    // COMPROBAR BLUETOOTH
    // =========================================================

    fun isBluetoothAvailable(): Boolean {

        return bluetoothAdapter != null
    }

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(): Boolean {

        if (!hasConnectPermission()) {
            return false
        }

        return bluetoothAdapter
            ?.isEnabled == true
    }

    // =========================================================
    // PERMISOS
    // =========================================================

    fun hasScanPermission(): Boolean {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) ==
                    PackageManager.PERMISSION_GRANTED

        } else {

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasConnectPermission(): Boolean {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) ==
                    PackageManager.PERMISSION_GRANTED

        } else {

            true
        }
    }

    // =========================================================
    // COMENZAR ESCANEO
    // =========================================================

    @SuppressLint("MissingPermission")
    fun startScan() {

        if (!hasScanPermission()) {

            _incomingMessage.value =
                "No se otorgó permiso para buscar dispositivos Bluetooth."

            return
        }

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            _incomingMessage.value =
                "Este dispositivo no soporta Bluetooth."

            return
        }

        if (!adapter.isEnabled) {

            _incomingMessage.value =
                "Bluetooth está desactivado."

            return
        }

        /*
         * Limpiamos dispositivos antiguos
         * antes de volver a buscar.
         */

        // Conservamos los dispositivos conectados al iniciar otro escaneo.
        _scannedDevices.value =
            _scannedDevices.value.filter { device ->
                device.address in _connectedDeviceAddresses.value
            }

        bluetoothLeScanner
            ?.startScan(
                scanCallback
            )
    }

    // =========================================================
    // DETENER ESCANEO
    // =========================================================

    @SuppressLint("MissingPermission")
    fun stopScan() {

        if (!hasScanPermission()) {
            return
        }

        bluetoothLeScanner
            ?.stopScan(
                scanCallback
            )
    }

    // =========================================================
    // CONECTARSE AL ESP32
    // =========================================================

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        deviceAddress: String
    ) {

        if (!hasConnectPermission()) {

            _incomingMessage.value =
                "No se otorgó permiso para conectarse por Bluetooth."

            return
        }

        if (
            _connectedDeviceAddresses
                .value
                .contains(deviceAddress)
        ) {

            _incomingMessage.value =
                "Este guante ya está conectado."

            return
        }

        val pendingCount =
            synchronized(connectionLock) {
                pendingConnections.size
            }

        if (
            _connectedGlovesCount.value +
            pendingCount >=
            MAX_CONNECTED_GLOVES
        ) {

            _incomingMessage.value =
                "Ya hay dos guantes conectados."

            return
        }

        val addedToPending =
            synchronized(connectionLock) {
                pendingConnections.add(
                    deviceAddress
                )
            }

        if (!addedToPending) {

            _incomingMessage.value =
                "Este guante ya se está conectando."

            return
        }

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            synchronized(connectionLock) {
                pendingConnections.remove(
                    deviceAddress
                )
            }

            _incomingMessage.value =
                "Bluetooth no disponible."

            return
        }

        try {

            /*
             * Obtenemos el dispositivo mediante
             * su dirección Bluetooth.
             */

            val device =
                adapter.getRemoteDevice(
                    deviceAddress
                )

            _incomingMessage.value =
                "Conectando con ${device.name ?: "ESP32"}..."

            /*
             * Cada guante mantiene su propia
             * conexión GATT.
             */

            val gatt =
                device.connectGatt(
                    context,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )

            synchronized(connectionLock) {
                bluetoothGatts[deviceAddress] =
                    gatt
            }

        } catch (
            exception: IllegalArgumentException
        ) {

            synchronized(connectionLock) {
                pendingConnections.remove(
                    deviceAddress
                )
            }

            _incomingMessage.value =
                "Dirección Bluetooth inválida."
        }
    }

    // =========================================================
    // DESCONECTAR
    // =========================================================

    @SuppressLint("MissingPermission")
    fun disconnectDevice(
        deviceAddress: String
    ) {

        if (!hasConnectPermission()) {
            return
        }

        val gatt =
            synchronized(connectionLock) {
                bluetoothGatts[deviceAddress]
            }

        if (gatt == null) {

            _incomingMessage.value =
                "El guante seleccionado no está conectado."

            return
        }

        gatt.disconnect()
    }

    // =========================================================
    // ENVIAR MENSAJES
    // =========================================================

    fun sendMessage(
        message: String
    ) {

        /*
         * Por ahora no lo necesitamos.
         *
         * Nuestro objetivo actual es:
         *
         * ESP32 -> Android
         *
         * Más adelante podemos agregar
         * Android -> ESP32 si fuera necesario.
         */
    }

    // =========================================================
    // SIMULACIÓN
    // =========================================================

    fun simulateIncomingMessage(
        message: String
    ) {

        /*
         * Esta función ya no es necesaria
         * para la prueba real, pero podemos
         * dejarla para tests.
         */

        _incomingMessage.value =
            message
    }
}