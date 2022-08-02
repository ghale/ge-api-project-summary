package com.gradle.enterprise.summary.writer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class OutputWriter {

    protected void writeStringToFile(String fileName, String string) throws IOException {
        Path filePath = Paths.get(fileName);
        if (filePath.getParent() != null) {
            filePath.getParent().toFile().mkdirs();
        }
        Files.write(filePath, string.getBytes());
    }
}
