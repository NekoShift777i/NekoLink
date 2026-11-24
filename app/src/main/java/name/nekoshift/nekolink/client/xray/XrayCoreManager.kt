package name.nekoshift.nekolink.client.xray

import libXray.LibXray
import org.json.JSONObject

import android.util.Log

object XrayCoreManager {

    fun getApiVersion(): Long {
        return LibXray.LibXrayAPIVersion
    }

    fun getVersion(): String {
        return invoke("xrayVersion").getString("version")
    }

    fun testConfig(config: String) {
        invoke(
            method = "testXray",
            payload = JSONObject().put("xrayJson", config)
        )
    }

    fun start(config: String) {
        invoke(
            method = "runXray",
            payload = JSONObject().put("xrayJson", config)
        )
    }

    fun stop() {
        invoke("stopXray")
    }

    fun isRunning(): Boolean {
        return invoke("getXrayState").getBoolean("running")
    }

    private fun invoke(
        method: String,
        payload: JSONObject? = null
    ): JSONObject {
        val request = JSONObject()
            .put("apiVersion", getApiVersion())
            .put("method", method)

        if (payload != null) {
            request.put("payload", payload)
        }

        val response = JSONObject(
            LibXray.invoke(request.toString())
        )

        Log.d(
            "XrayCoreManager",
            "Xray API response ($method): $response"
        )

        if (!response.getBoolean("success")) {
            throw IllegalStateException(
                response.optString("error")
                    .ifBlank { "Ошибка выполнения метода $method" }
            )
        }

        return response.optJSONObject("data") ?: JSONObject()
    }
}