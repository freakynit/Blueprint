package com.freakynit.benchmark;

import com.freakynit.blueprint.Blueprint;
import com.freakynit.blueprint.StdUtils;
import org.openjdk.jmh.annotations.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
public class BenchmarkRunner {
    private Blueprint engine;
    private Blueprint.Template template;
    private Map<String, Object> context;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String templateFileName = "full.blu";  // or small.blu

        String templateStr = new BufferedReader(new InputStreamReader(
                BenchmarkRunner.class.getClassLoader().getResourceAsStream(templateFileName),
                StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        context = templateFileName.equals("full.blu") ? SampleTemplateData.getContextForFull() : SampleTemplateData.getContextForEmail();

        engine = new Blueprint();

        if(templateFileName.equals("full.blu")) {
            new StdUtils().registerAll(engine);
        }

        template = engine.compile(templateStr);
    }

    @Benchmark
    @Threads(1)
    public String benchmarkSingleThread() {
        return template.render(context);
    }

    @Benchmark
    @Threads(4)
    public String benchmarkFourThreads() {
        return template.render(context);
    }
}
