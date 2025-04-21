import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.AggregatedHttpResponse
import com.linecorp.armeria.common.HttpResponse
import com.google.common.util.concurrent.AtomicDouble
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.annotation.Get
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry


class MetricCollectorService {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val gauges = mutableMapOf<String, AtomicDouble>()

    private val targets = listOf(
        "http://localhost:8080/metrics",
        "http://localhost:8080/autoconfig",
        "http://localhost:8080/beans",
        "http://localhost:8080/configprops",
        "http://localhost:8080/env",
        "http://localhost:8080/info",
        "http://localhost:8080/health",
        "http://localhost:8080/heapdump",
        "http://localhost:8080/threaddump",
        "http://localhost:8080/mappings",
    )

    private val client = WebClient.of()

    @Get("/collect")
    fun collectMetrics() {
        targets.forEach { target ->
            try {
                val response: AggregatedHttpResponse = client.get(target).aggregate().join()
                val body = response.contentUtf8()

                val mem = Regex("\"mem\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                val memFree =
                    Regex("\"mem.free\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                val heapUsed =
                    Regex("\"heap.used\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                val threads =
                    Regex("\"threads\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()

                mem?.let {
                    val key = "legacy.mem.total:$target"
                    val gauge = gauges.computeIfAbsent(key) {
                        val holder = AtomicDouble(it.toDouble())
                        meterRegistry.gauge(
                            "legacy.mem.total",
                            Tags.of("target", target),
                            holder
                        ) { h -> h.get() }
                        holder
                    }
                    gauge.set(it)
                }
                memFree?.let {
                    val key = "legacy.mem.free:$target"
                    val gauge = gauges.computeIfAbsent(key) {
                        val holder = AtomicDouble(it.toDouble())
                        meterRegistry.gauge(
                            "legacy.mem.free",
                            Tags.of("target", target),
                            holder
                        ) { h -> h.get() }
                        holder
                    }
                    gauge.set(it)
                }
                heapUsed?.let {
                    val key = "legacy.heap.used:$target"
                    val gauge = gauges.computeIfAbsent(key) {
                        val holder = AtomicDouble(it.toDouble())
                        meterRegistry.gauge(
                            "legacy.heap.used",
                            Tags.of("target", target),
                            holder
                        ) { h -> h.get() }
                        holder
                    }
                    gauge.set(it)
                }
                threads?.let {
                    val key = "legacy.threads:$target"
                    val gauge = gauges.computeIfAbsent(key) {
                        val holder = AtomicDouble(it.toDouble())
                        meterRegistry.gauge(
                            "legacy.threads",
                            Tags.of("target", target),
                            holder
                        ) { h -> h.get() }
                        holder
                    }
                    gauge.set(it)
                }
                println("수집 완료: $target")
            } catch (e: Exception) {
                println("수집 실패: $target → ${e.message}")
            }
        }
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
