import com.fasterxml.jackson.databind.ObjectMapper
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.AggregatedHttpResponse
import com.linecorp.armeria.common.HttpResponse
import com.google.common.util.concurrent.AtomicDouble
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.annotation.Get
import com.linecorp.armeria.server.logging.LoggingService
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.CompletableFuture
import kotlin.math.log


class MetricCollectorService {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val objectMapper = ObjectMapper()

    @Volatile
    var cachedScrape: String = ""

    private val gauges = mutableMapOf<String, AtomicDouble>()

    private val targets = listOf(
        "http://localhost:9090/metrics",
//        "http://localhost:9090/autoconfig",
//        "http://localhost:9090/beans",
//        "http://localhost:9090/configprops",
//        "http://localhost:9090/env",
//        "http://localhost:9090/info",
//        "http://localhost:9090/health",
//        "http://localhost:9090/heapdump",
//        "http://localhost:9090/threaddump",
//        "http://localhost:9090/mappings",
    )

    private val client = WebClient.of()

    @Get("/collect")
    suspend fun collectMetrics(): HttpResponse = coroutineScope {
        val jobs = targets.map { target ->
            async {
                try {
                    val response = aggregateResponse(client, target)
                    val body = response.contentUtf8()
                    when {
                        target.endsWith("/metrics") -> parseMetrics(body, target)
//                    target.endsWith("/health") -> parseHealth(body, target)
//                        target.endsWith("/beans") -> parseBeans(body, target)
//                    target.endsWith("/threaddump") -> parseThreadDump(body, target)
//                        else -> println("Unknown endpoint: $target")
                    }
                    println("Collected metrics from $target")

                } catch (e: Exception) {
                    println("Error collecting metrics from $target: ${e.message}")
                }
            }
        }
        jobs.awaitAll()
        println(cachedScrape)
        HttpResponse.of("Collected metrics from all targets")
    }

    @Get("/metrics")
    fun export(): HttpResponse =
        HttpResponse.of(meterRegistry.scrape())

    fun parseMetrics(body: String, target: String) {
        val json = objectMapper.readTree(body)
        json.fields().forEach { (key, value) ->
            setGauge("legacy_$key", target, value.asDouble())
        }
    }

    fun parseHealth(body: String, target: String) {
        val json = objectMapper.readTree(body)
        val status = json["status"]?.asText()
        val value = if (status == "UP") 1.0 else 0.0
        setGauge("legacy_health", target, value)
    }

    fun parseBeans(body: String, target: String) {
        val json = objectMapper.readTree(body)
        val total = json["contents"]?.fields()?.asSequence()
            ?.mapNotNull { (_, ctx) -> ctx["beans"]?.size() }
            ?.sum() ?: return
        setGauge("legacy_bean.count", target, total.toDouble())
    }

    fun parseThreadDump(body: String, target: String) {
        val json = objectMapper.readTree(body)
        val threads = json["threads"] ?: return
        val total = threads.size()
        val runnable = threads.count { it["threadState"]?.asText() == "RUNNABLE" }
        setGauge("legacy_threads.total", target, total.toDouble())
        setGauge("legacy_threads.runnable", target, runnable.toDouble())
    }
}

fun main() {
    val service = Server.builder()
        .http(8081)
        .annotatedService(MetricCollectorService())
        .annotatedService(LoggingService.newDecorator())
        .build()
    service.start().join()
}
