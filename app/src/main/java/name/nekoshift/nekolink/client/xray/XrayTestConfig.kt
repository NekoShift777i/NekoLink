package name.nekoshift.nekolink.client.xray

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object XrayTestConfig {

    fun createVlessTunConfig(
        context: Context,
        tunFd: Int,
        mtu: Int = 1500
    ): String {
        val outboundConfig = context.assets
            .open("xray-test.json")
            .bufferedReader()
            .use { it.readText() }

        val config = JSONObject(createTunConfig(tunFd, mtu))

        config.put(
            "outbounds",
            JSONArray().put(JSONObject(outboundConfig))
        )

        return config.toString()
    }

    fun createTunConfig(tunFd: Int, mtu: Int = 1500): String {
        return """
            {
                "env": {
                    "xray.tun.fd": "$tunFd"
                },
                "log": {
                    "loglevel": "debug"
                },
                "inbounds": [
                    {
                        "tag": "tun-in",
                        "protocol": "tun",
                        "settings": {
                            "name": "nekolink0",
                            "mtu": $mtu
                        }
                    }
                ],
                "outbounds": [
                    {
                        "tag": "direct",
                        "protocol": "freedom",
                        "settings": {}
                    }
                ]
            }
        """.trimIndent()
    }

    val config: String = """
        {
            "log": {
                "loglevel": "warning"
            },
            "inbounds": [],
            "outbounds": [
                {
                    "tag": "direct",
                    "protocol": "freedom",
                    "settings": {}
                }
            ]
        }
    """.trimIndent()
}