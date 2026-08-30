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
    // CONEXIÓN GATT
    // =========================================================

    /*
     * Esta variable mantendrá la conexión
     * activa con nuestro ESP32.
     */
    private var bluetoothGatt: BluetoothGatt? = null


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


    private val _incomingMessage =
        MutableStateFlow(
            "Esperando datos del ESP32..."
        )

    val incomingMessage:
            StateFlow<String> =
        _incomingMessage.asStateFlow()

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
                    device.name
                        ?: "Dispositivo BLE sin nombre"


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


                if (
                    newState ==
                    BluetoothProfile.STATE_CONNECTED
                ) {

                    _connectedGlovesCount.value = 1

                    _incomingMessage.value =
                        "ESP32 conectado. Buscando servicios..."

                    /*
                     * Una vez conectado,
                     * buscamos los servicios BLE
                     * disponibles en el ESP32.
                     */
                    gatt.discoverServices()


                } else if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    _connectedGlovesCount.value = 0

                    _incomingMessage.value =
                        "ESP32 desconectado."

                    gatt.close()


                    if (
                        bluetoothGatt === gatt
                    ) {
                        bluetoothGatt = null
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
                    status != BluetoothGatt.GATT_SUCCESS
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
                characteristic:
                BluetoothGattCharacteristic,
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
                characteristic:
                BluetoothGattCharacteristic
            ) {

                super.onCharacteristicChanged(
                    gatt,
                    characteristic
                )


                if (
                    characteristic.uuid ==
                    CHARACTERISTIC_UUID
                ) {

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
            descriptor.value =
                BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE

            @Suppress("DEPRECATION")
            gatt.writeDescriptor(
                descriptor
            )
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

        // Usamos una referencia local porque bluetoothAdapter es una propiedad lazy.
        val adapter = bluetoothAdapter

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
        _scannedDevices.value =
            emptyList()


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


        // Usamos una referencia local porque bluetoothAdapter es una propiedad lazy.
        val adapter = bluetoothAdapter

        if (adapter == null) {

            _incomingMessage.value =
                "Bluetooth no disponible."

            return
        }
        /*
         * Dejamos de buscar porque ya
         * seleccionamos el dispositivo.
         */
        stopScan()


        try {

            /*
             * Obtenemos el dispositivo mediante
             * su dirección Bluetooth.
             */
            val device =
                adapter
                    .getRemoteDevice(
                        deviceAddress
                    )


            _incomingMessage.value =
                "Conectando con ${device.name ?: "ESP32"}..."


            /*
             * Aquí ocurre la conexión BLE real.
             */
            bluetoothGatt =
                device.connectGatt(
                    context,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )


        } catch (
            exception: IllegalArgumentException
        ) {

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


        bluetoothGatt?.disconnect()
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
