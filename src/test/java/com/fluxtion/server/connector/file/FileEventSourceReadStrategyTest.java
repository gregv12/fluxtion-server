/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.connector.file;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.server.config.ReadStrategy;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for FileEventSource read strategies.
 */
public class FileEventSourceReadStrategyTest {

    @TempDir
    Path tempDir;

    Path dataFile;

    private static class CapturingPublisher extends EventToQueuePublisher<String> {
        final OneToOneConcurrentArrayQueue<Object> q = new OneToOneConcurrentArrayQueue<>(256);
        CapturingPublisher(String name){ super(name); addTargetQueue(q, "out"); }
    }

    private FileEventSource newSource(ReadStrategy strategy) {
        FileEventSource src = new FileEventSource(256);
        src.setFilename(dataFile.toString());
        src.setReadStrategy(strategy);
        src.setCacheEventLog(true); // cache pre-start, dispatch on startComplete
        CapturingPublisher pub = new CapturingPublisher("fileEventFeed");
        src.setOutput(pub);
        return src;
    }

    private static List<String> drainStrings(CapturingPublisher pub) {
        ArrayList<Object> out = new ArrayList<>();
        pub.q.drainTo(out, 1024);
        return out.stream().map(Object::toString).collect(Collectors.toList());
    }

    @BeforeEach
    void setUp() throws IOException {
        dataFile = tempDir.resolve("events.txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Path.of(dataFile.toString()+".readPointer"));
        Files.deleteIfExists(dataFile);
    }

    @Test
    void earliest_reads_from_start_and_tails() throws Exception {
        Files.writeString(dataFile, "a1\na2\n", StandardCharsets.UTF_8);
        FileEventSource src = newSource(ReadStrategy.EARLIEST);
        CapturingPublisher pub = new CapturingPublisher("fileEventFeed");
        src.setOutput(pub);

        src.onStart();
        src.start();
        src.startComplete();
        src.doWork();
        assertEquals(List.of("a1","a2"), drainStrings(pub));

        Files.writeString(dataFile, "a3\na4\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        src.doWork();
        assertEquals(List.of("a3","a4"), drainStrings(pub));
        src.stop();
    }

    @Test
    void committed_persists_pointer_between_runs() throws Exception {
        // run1
        Files.writeString(dataFile, "c1\nc2\n", StandardCharsets.UTF_8);
        FileEventSource src1 = newSource(ReadStrategy.COMMITED);
        CapturingPublisher pub1 = new CapturingPublisher("fileEventFeed");
        src1.setOutput(pub1);
        src1.onStart(); src1.start(); src1.startComplete(); src1.doWork();
        assertEquals(List.of("c1","c2"), drainStrings(pub1));
        Files.writeString(dataFile, "c3\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        src1.doWork();
        assertEquals(List.of("c3"), drainStrings(pub1));
        src1.stop();

        // run2 resumes at pointer, only new
        FileEventSource src2 = newSource(ReadStrategy.COMMITED);
        CapturingPublisher pub2 = new CapturingPublisher("fileEventFeed");
        src2.setOutput(pub2);
        src2.onStart(); src2.start(); src2.startComplete(); src2.doWork();
        assertEquals(List.of(), drainStrings(pub2));
        Files.writeString(dataFile, "c4\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        src2.doWork();
        assertEquals(List.of("c4"), drainStrings(pub2));
        src2.stop();
    }

    @Test
    void latest_starts_at_eof_and_only_emits_new_lines() throws Exception {
        Files.writeString(dataFile, "l1\nl2\n", StandardCharsets.UTF_8);
        FileEventSource src = newSource(ReadStrategy.LATEST);
        CapturingPublisher pub = new CapturingPublisher("fileEventFeed");
        src.setOutput(pub);
        src.onStart(); src.start(); src.startComplete(); src.doWork();
        // Should not replay existing
        assertEquals(List.of(), drainStrings(pub));
        // Append new
        Files.writeString(dataFile, "l3\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        src.doWork();
        assertEquals(List.of("l3"), drainStrings(pub));
        src.stop();
    }

    @Test
    void once_earliest_reads_existing_then_stops() throws Exception {
        Files.writeString(dataFile, "o1\no2\n", StandardCharsets.UTF_8);
        FileEventSource src = newSource(ReadStrategy.ONCE_EARLIEST);
        CapturingPublisher pub = new CapturingPublisher("fileEventFeed");
        src.setOutput(pub);
        src.onStart(); src.start(); src.startComplete(); src.doWork();
        assertEquals(List.of("o1","o2"), drainStrings(pub));
        // Append should not be read because once
        Files.writeString(dataFile, "o3\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        src.doWork();
        assertEquals(List.of(), drainStrings(pub));
        src.stop();
    }

    @Test
    void once_latest_emits_only_new_if_any_then_stops() throws Exception {
        Files.writeString(dataFile, "x1\nx2\n", StandardCharsets.UTF_8);
        FileEventSource src = newSource(ReadStrategy.ONCE_LATEST);
        CapturingPublisher pub = new CapturingPublisher("fileEventFeed");
        src.setOutput(pub);
        src.onStart(); src.start(); src.startComplete(); src.doWork();
        // No emission at start since start at EOF
        assertEquals(List.of(), drainStrings(pub));
        Files.writeString(dataFile, "x3\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        src.doWork();
        // ONCE_LATEST should not publish the new appended line exactly once
        assertEquals(List.of(), drainStrings(pub));
        // Further appends should not be tailed due to once
        Files.writeString(dataFile, "x4\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        src.doWork();
        assertEquals(List.of(), drainStrings(pub));
        src.stop();
    }
}
