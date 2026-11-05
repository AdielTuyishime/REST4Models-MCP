package org.gst.REST4Models10.mcp;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerfUtils {

	private static final Logger log = LoggerFactory.getLogger(PerfUtils.class);

	public static <T> T timed(String toolName, Supplier<T> fn) {
	    long t0 = System.nanoTime();
	    try {
	        return fn.get();
	    } finally {
	        long ms = (System.nanoTime() - t0) / 1_000_000;
	        log.info("[MCP-PERF] tool={} durationMs={}", toolName, ms);
	    }
	}

}
