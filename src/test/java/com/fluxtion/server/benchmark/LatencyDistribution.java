/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.benchmark;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Random;

public class LatencyDistribution {

    private int nanos[] = new int[10];
    private int micros_10[] = new int[10];
    private int micros[] = new int[10];
    private int millis[] = new int[10];
    private int seconds[] = new int[10];
    private long sum;
    private final StringBuilder stringBuilder = new StringBuilder(1024);
    private int negativeCount;


    public static void main(String[] args) {
        LatencyDistribution distribution = new LatencyDistribution();

        long startTime = System.nanoTime();

        distribution.addReading(800);
        distribution.addReading(50);
        distribution.addReading(999);

        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            int randomReading = random.nextInt(1_000_000_000);
            distribution.addReading(randomReading);
        }

        distribution.printResultsToConsole(startTime, System.nanoTime());
    }

    public void addReading(long timeNanos) {
        if (timeNanos < 0) {
            negativeCount++;
        } else if (timeNanos < 1_000) {
            int bucket = Math.toIntExact(timeNanos / 100);
            nanos[bucket] = ++nanos[bucket];
        } else if (timeNanos < 100_000) {
            int bucket = Math.toIntExact(timeNanos / 10_000);
            micros_10[bucket] = ++micros_10[bucket];
        } else if (timeNanos < 1_000_000) {
            int bucket = Math.toIntExact(timeNanos / 100_000);
            micros[bucket] = ++micros[bucket];
        } else if (timeNanos < 1_000_000_000) {
            int bucket = Math.toIntExact(timeNanos / 100_000_000);
            millis[bucket] = ++millis[bucket];
        } else {
            int bucket = Math.toIntExact(timeNanos / 100_000_000_000L);
            seconds[bucket] = ++seconds[bucket];
        }

        sum += timeNanos;
    }

    public void reset() {
        Arrays.fill(nanos, 0);
        Arrays.fill(micros_10, 0);
        Arrays.fill(micros, 0);
        Arrays.fill(millis, 0);
        sum = 0;
        negativeCount = 0;
    }

    public void printResultsToConsole(long startTime, long endTime) {
        PrintWriter out = new PrintWriter(System.out);
        publishResults(out, startTime, endTime);
    }

    public void publishResults(PrintWriter writer, long startTime, long endTime) {
        stringBuilder.setLength(0);
        stringBuilder.append("latency results\n")
                .append("bucket       :  count\n-----------------------\n");

        for (int i = 0; i < nanos.length; i++) {
            String pad = i < 9 ? " " : "";
            stringBuilder.append(pad).append((i + 1) * 100).append(" nanos    :  ").append(nanos[i]).append("\n");
        }

        for (int i = 0; i < micros_10.length; i++) {
            String pad = i < 9 ? " " : "";
            stringBuilder.append(pad).append((i + 1) * 10).append(" micros    :  ").append(micros_10[i]).append("\n");
        }

        for (int i = 0; i < micros.length; i++) {
            String pad = i < 9 ? " " : "";
            stringBuilder.append(pad).append((i + 1) * 100).append(" micros   :  ").append(micros[i]).append("\n");
        }

        for (int i = 0; i < millis.length; i++) {
            String pad = i < 9 ? " " : "";
            stringBuilder.append(pad).append((i + 1) * 100).append(" millis   :  ").append(millis[i]).append("\n");
        }

        for (int i = 0; i < seconds.length; i++) {
            String pad = i < 9 ? " " : "";
            stringBuilder.append(pad).append((i + 1) * 100).append(" seconds  :  ").append(seconds[i]).append("\n");
        }

        int count = 0;
        for (int i = 0; i < nanos.length; i++) {
            count += nanos[i] + micros[i] + micros_10[i] + millis[i] + seconds[i];
        }

        stringBuilder.append("-----------------------------\n");
        stringBuilder.append("negative count:  ").append(negativeCount).append("\n");
        stringBuilder.append("total count   :  ").append(count).append("\n");
        stringBuilder.append("total latency :  ").append(sum).append("\n");
        stringBuilder.append("Avg latency   :  ").append(sum / count).append("\n");
        stringBuilder.append("Avg latency   :  ").append((endTime - startTime) / count).append("\n");

        writer.println(stringBuilder.toString());
        writer.flush();
    }

}
