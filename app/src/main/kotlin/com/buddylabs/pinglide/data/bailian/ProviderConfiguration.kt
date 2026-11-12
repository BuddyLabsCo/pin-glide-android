package com.buddylabs.pinglide.data.bailian

import com.buddylabs.pinglide.generated.http.model.ModelConfiguration
import com.buddylabs.pinglide.generated.http.model.ModelCredential
import com.buddylabs.pinglide.generated.http.model.ModelOTPField
import com.buddylabs.pinglide.generated.http.model.ModelOTPSchema
import com.buddylabs.pinglide.generated.http.model.ModelResponseFormat
import java.time.Instant
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun ModelCredential.validate(now: Instant) {
    require(credential.apiKey.isNotBlank())
    require(Instant.parse(credential.expiresAt).minusSeconds(30).isAfter(now))
    require(configuration.provider == ModelConfiguration.Provider.bailian)
    require(configuration.region == ModelConfiguration.Region.cnMinusBeijing)
    configuration.endpoint()
    val inference = configuration.inference
    require(inference.model.isNotBlank() && !inference.enableThinking)
    val format = inference.responseFormat
    require(format.type == ModelResponseFormat.Type.json_schema)
    require(format.jsonSchema.name.isNotBlank() && format.jsonSchema.strict)
    val schema = format.jsonSchema.schema
    require(schema.type == ModelOTPSchema.Type.`object` && !schema.additionalProperties)
    require(schema.required == listOf("otp"))
    require(
        schema.properties.otp.type?.toSet() ==
            setOf(ModelOTPField.Type.string, ModelOTPField.Type.`null`)
    )
}

internal fun ModelConfiguration.endpoint(): HttpUrl {
    val base = baseUrl.toHttpUrl()
    require(base.isHttps && base.port == 443)
    require(base.username.isEmpty() && base.password.isEmpty())
    require(base.query == null && base.fragment == null)
    require(
        base.host == "dashscope.aliyuncs.com" ||
            base.host.endsWith(".cn-beijing.maas.aliyuncs.com")
    )
    require(base.encodedPath.trimEnd('/') == "/compatible-mode/v1")
    return base.newBuilder().encodedPath("/compatible-mode/v1/chat/completions").build()
}
