package dev.shashank.profiler.starter.web;

import dev.shashank.profiler.core.report.HotMethodsReport;

/** JSON view of one hot-method row: a frame ranked by self time. */
public record HotMethodDto(String className, String methodName, int line, long selfCount, double percentOfTotal) {

    public static HotMethodDto from(HotMethodsReport.Entry entry) {
        return new HotMethodDto(
                entry.frame().className(),
                entry.frame().methodName(),
                entry.frame().line(),
                entry.selfCount(),
                Math.round(entry.percentOfTotal() * 10.0) / 10.0);
    }
}
