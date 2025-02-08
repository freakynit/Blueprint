package com.freakynit.usage;

import com.freakynit.benchmark.SampleTemplateData;
import com.freakynit.blueprint.Blueprint;
import com.freakynit.blueprint.StdUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class SampleUsage {
    public static void main(String[] args) {
        String templateFileName = "full.blu";  // or small.blu

        String templateStr = new BufferedReader(new InputStreamReader(
                SampleUsage.class.getClassLoader().getResourceAsStream(templateFileName),
                StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        Map<String, Object> context = templateFileName.equals("full.blu") ? SampleTemplateData.getContextForFull() : SampleTemplateData.getContextForEmail();

        Blueprint engine = new Blueprint();

        if(templateFileName.equals("full.blu")) {
            new StdUtils().registerAll(engine);
        }

        String output = engine.render(templateStr, context);
        System.out.println("Rendered output:\n");
        System.out.println(output);

        // Or, compile once and re-use the same with different data sets
        // Blueprint.Template template = engine.compile(templateStr);
        // output = template.render(context1);
        // output = template.render(context2);
        // output = template.render(context3);
    }
}
