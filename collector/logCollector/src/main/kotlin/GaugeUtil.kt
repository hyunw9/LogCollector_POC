import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.AtomicDouble
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.AggregatedHttpResponse
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException


val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
val client = WebClient.of()
val gauges = mutableMapOf<String, AtomicDouble>()
val objectMapper = ObjectMapper()

fun setGauge(metric: String, target: String, value: Double) {
    println("setGauge called: $metric, [$target] = $value")
    val label = simplifyTarget(target)  // 예: URL → alias
    val key = "$metric:$label"

    val gauge: AtomicDouble = gauges.computeIfAbsent(key) {
        val holder = AtomicDouble(0.0)
        meterRegistry.gauge(metric, Tags.of("target", label), holder) { it.get() }
        holder
    }
    gauge.set(value)
}

fun simplifyTarget(url: String): String =
    url.replace("http://localhost:9090", "legacy-a") // 필요 시 맵핑

suspend fun aggregateResponse(client: WebClient, url: String): AggregatedHttpResponse =
    suspendCancellableCoroutine { cont ->
        client.get(url).aggregate().whenComplete { response, throwable ->
            if (throwable != null) cont.resumeWithException(throwable)
            else cont.resume(response) {}
        }
    }
