package hyunw9.legacy;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Enumeration;

import javax.annotation.PostConstruct;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

@RestController
public class TestMetricController {

	private final CollectorRegistry registry = CollectorRegistry.defaultRegistry;

	@PostConstruct
	@RequestMapping("/prometheus")
	public String metrics() {
		Writer writer = new StringWriter();
		try {
			Enumeration<Collector.MetricFamilySamples> samples = registry.metricFamilySamples();
			TextFormat.write004(writer, samples);
			return writer.toString();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write metrics", e);
		}
	}
}
