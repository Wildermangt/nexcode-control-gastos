package com.nexcode.gastos.presentation.voz.retell

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Llamada de voz con Leandro, el agente de Retell AI compartido por las apps
 * de Nexcode.
 *
 *  1. Le pide la llamada al servidor `leandro-voz`, que es quien guarda la API
 *     key de Retell. La app nunca la tiene.
 *  2. Con el token que devuelve, se conecta por WebRTC al proxy de Retell. A
 *     partir de ahí el audio va y viene solo.
 *
 * El permiso RECORD_AUDIO tiene que estar concedido antes de [iniciar].
 */
class LlamadaConLeandro(
    private val context: Context,
    private val urlDelServidor: String,
    private val app: String = "gastos",
    private val http: OkHttpClient = OkHttpClient()
) {
    sealed interface Estado {
        data object Inactiva : Estado
        data object Conectando : Estado
        data object EnCurso : Estado
        data class Fallo(val motivo: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Inactiva)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private var fabrica: PeerConnectionFactory? = null
    private var conexion: PeerConnection? = null
    private var urlSesiones = ""
    private var idSesion = ""
    private var tokenDeAcceso = ""

    suspend fun iniciar() {
        if (_estado.value == Estado.Conectando || _estado.value == Estado.EnCurso) return
        if (urlDelServidor.isBlank()) {
            _estado.value = Estado.Fallo("Falta leandro.servidorUrl en local.properties")
            return
        }
        _estado.value = Estado.Conectando
        try {
            conectar(pedirLlamada())
            _estado.value = Estado.EnCurso
        } catch (e: Exception) {
            cerrarLocal()
            _estado.value = Estado.Fallo(e.message ?: "No se pudo iniciar la llamada")
        }
    }

    suspend fun colgar() {
        val sesion = idSesion
        cerrarLocal()
        if (sesion.isNotEmpty()) {
            runCatching { enviar("$urlSesiones/$sesion", "DELETE", null) }
        }
        _estado.value = Estado.Inactiva
    }

    // ------------------------------------------------------------------ 1

    private class DatosDeLlamada(
        val idLlamada: String,
        val token: String,
        val servidoresIce: List<PeerConnection.IceServer>
    )

    private suspend fun pedirLlamada(): DatosDeLlamada {
        val cuerpo = withContext(Dispatchers.IO) {
            val peticion = Request.Builder()
                .url(urlDelServidor.trimEnd('/') + "/llamada")
                .post(JSONObject().put("app", app).toString().toRequestBody(JSON))
                .build()
            http.newCall(peticion).execute().use { r ->
                check(r.isSuccessful) { "El servidor de voz respondió ${r.code}" }
                r.body?.string().orEmpty()
            }
        }

        val json = JSONObject(cuerpo)
        val ice = json.optJSONArray("ice_servers") ?: JSONArray()
        val servidores = (0 until ice.length()).map { i ->
            val s = ice.getJSONObject(i)
            val urls = when (val u = s.get("urls")) {
                is JSONArray -> (0 until u.length()).map { u.getString(it) }
                else -> listOf(u.toString())
            }
            PeerConnection.IceServer.builder(urls)
                .setUsername(s.optString("username", ""))
                .setPassword(s.optString("credential", ""))
                .createIceServer()
        }
        return DatosDeLlamada(json.getString("call_id"), json.getString("access_token"), servidores)
    }

    // ------------------------------------------------------------------ 2

    private suspend fun conectar(datos: DatosDeLlamada) {
        tokenDeAcceso = datos.token
        activarAltavoz()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        val modulo = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        val f = PeerConnectionFactory.builder().setAudioDeviceModule(modulo).createPeerConnectionFactory()
        fabrica = f

        val config = PeerConnection.RTCConfiguration(datos.servidoresIce).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = f.createPeerConnection(config, Observador())
            ?: error("No se pudo crear la conexión WebRTC")
        conexion = pc

        pc.addTrack(f.createAudioTrack("mic", f.createAudioSource(MediaConstraints())), listOf("mic"))
        pc.createDataChannel("control", DataChannel.Init()) // tiene que existir antes de la oferta

        val oferta = pc.crearOferta()
        pc.fijar(oferta, local = true)
        delay(1_000) // tiempo para reunir candidatos ICE, como indica Retell

        urlSesiones = "https://api.retellai.com/webrtc-proxy/${datos.idLlamada}/v1/webrtc/sessions"
        val respuesta = JSONObject(
            enviar(
                urlSesiones, "POST",
                JSONObject().put("identity", "client").put("sdp", pc.localDescription.description).toString()
            )
        )
        idSesion = respuesta.getString("session_id")
        pc.fijar(SessionDescription(SessionDescription.Type.ANSWER, respuesta.getString("sdp")), local = false)
    }

    private fun cerrarLocal() {
        restaurarAudio() // también si la llamada falló antes de crear la conexión
        val pc = conexion ?: return // se limpia primero: close() puede volver a entrar
        conexion = null
        idSesion = ""
        pc.close()
        fabrica?.dispose()
        fabrica = null
    }

    // ---------------------------------------------------------- altavoz

    private var audioActivo = false
    private var volumenPrevio = -1

    /**
     * Leandro funciona como un asistente de voz: se le habla con el teléfono en la mano,
     * así que la respuesta sale por el altavoz y no por el auricular de oreja. El volumen
     * de llamada suele estar muy bajo (1 de 8 en las pruebas): si está por debajo del
     * 70 % se sube y al colgar se devuelve como estaba.
     */
    private fun activarAltavoz() {
        val a = audio()
        a.mode = AudioManager.MODE_IN_COMMUNICATION // activa la cancelación de eco
        audioActivo = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            a.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { a.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            a.isSpeakerphoneOn = true
        }
        val maximo = a.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val actual = a.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val deseado = (maximo * 0.7).roundToInt()
        if (actual < deseado) {
            runCatching { a.setStreamVolume(AudioManager.STREAM_VOICE_CALL, deseado, 0) }
                .onSuccess { volumenPrevio = actual }
        }
    }

    private fun restaurarAudio() {
        if (!audioActivo) return
        audioActivo = false
        val a = audio()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            a.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            a.isSpeakerphoneOn = false
        }
        if (volumenPrevio >= 0) {
            runCatching { a.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volumenPrevio, 0) }
            volumenPrevio = -1
        }
        a.mode = AudioManager.MODE_NORMAL
    }

    private suspend fun enviar(url: String, metodo: String, json: String?): String =
        withContext(Dispatchers.IO) {
            val peticion = Request.Builder().url(url)
                .method(metodo, json?.toRequestBody(JSON))
                .header("Authorization", "Bearer $tokenDeAcceso")
                .build()
            http.newCall(peticion).execute().use { r ->
                check(r.isSuccessful) { "Retell respondió ${r.code}" }
                r.body?.string().orEmpty()
            }
        }

    private fun audio() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Cuando el agente cuelga o se cae la red, la pantalla se entera aquí. */
    private inner class Observador : PeerConnection.Observer {
        override fun onConnectionChange(nuevo: PeerConnection.PeerConnectionState) {
            if (nuevo == PeerConnection.PeerConnectionState.DISCONNECTED ||
                nuevo == PeerConnection.PeerConnectionState.FAILED ||
                nuevo == PeerConnection.PeerConnectionState.CLOSED
            ) {
                // Nunca cerrar desde el hilo de WebRTC: puede bloquearse.
                Thread {
                    cerrarLocal()
                    _estado.value = Estado.Inactiva
                }.start()
            }
        }
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(p0: IceCandidate?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

// ------------- Envolturas de corrutina para la API de callbacks -------------

private suspend fun PeerConnection.crearOferta(): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(object : SdpSimple() {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(error: String?) =
                cont.resumeWithException(IllegalStateException("Oferta: $error"))
        }, MediaConstraints())
    }

private suspend fun PeerConnection.fijar(sdp: SessionDescription, local: Boolean) {
    val listo = CompletableDeferred<Unit>()
    val observador = object : SdpSimple() {
        override fun onSetSuccess() {
            listo.complete(Unit)
        }
        override fun onSetFailure(error: String?) {
            listo.completeExceptionally(IllegalStateException("SDP: $error"))
        }
    }
    if (local) setLocalDescription(observador, sdp) else setRemoteDescription(observador, sdp)
    listo.await()
}

private open class SdpSimple : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
