import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.AggregatedHttpResponse
import com.linecorp.armeria.common.HttpResponse
import com.google.common.util.concurrent.AtomicDouble
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.annotation.Get
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import java.util.concurrent.CompletableFuture


class MetricCollectorService {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val gauges = mutableMapOf<String, AtomicDouble>()

    private val targets = listOf(
        "http://localhost:9090/metrics",
        "http://localhost:9090/autoconfig",
        "http://localhost:9090/beans",
        "http://localhost:9090/configprops",
        "http://localhost:9090/env",
        "http://localhost:9090/info",
        "http://localhost:9090/health",
        "http://localhost:9090/heapdump",
        "http://localhost:9090/threaddump",
        "http://localhost:9090/mappings",
    )

    private val client = WebClient.of()

    @Get("/collect")
    fun collectMetrics(): HttpResponse {
        val futures = targets.map { target ->
            client.get(target).aggregate().thenAccept { response ->
                val body = response.contentUtf8()

                val mem = Regex("\"mem\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                val memFree = Regex("\"mem.free\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                val heapUsed = Regex("\"heap.used\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                val threads = Regex("\"threads\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()

                fun registerMetric(name: String, value: Double?) {
                    if (value == null) return

                    val key = "$name:$target"
                    val gauge = gauges.computeIfAbsent(key) {
                        val holder = AtomicDouble(value)
                        meterRegistry.gauge(name, Tags.of("target", target), holder) { h -> h.get() }
                        holder
                    }
                    gauge.set(value)
                }


                registerMetric("legacy.mem.total", mem)
                registerMetric("legacy.mem.free", memFree)
                registerMetric("legacy.heap.used", heapUsed)
                registerMetric("legacy.threads", threads)

                println("Collect: $target")
            }
        }

        // 모든 비동기 수집이 끝날 때까지 기다림
        CompletableFuture.allOf(*futures.toTypedArray()).join()

        return HttpResponse.of("Collected (${targets.size}개)")
    }

    @Get("/metrics")
    fun export(): HttpResponse =
        HttpResponse.of(meterRegistry.scrape())
}

fun main() {
    val service = Server.builder()
        .http(8081)
        .annotatedService(MetricCollectorService())
        .build()
    service.start().join()
}
