package com.gradle.enterprise.summary.logging;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.util.FileUtil;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.File;

public class LazyFileAppender<E> extends FileAppender<E> {

    private static final Lock lock = new ReentrantLock();

    @Override
    public void openFile(String file_name) throws IOException {
        lock.lock();
        try {
            File file = new File(file_name);
            boolean result = FileUtil.createMissingParentDirectories(file);
            if (!result) {
                addError("Failed to create parent directories for [" + file.getAbsolutePath() + "]");
            }

            LazyFileOutputStream lazyFos = new LazyFileOutputStream(file, append);
            setOutputStream(lazyFos);
        } finally {
            lock.unlock();
        }
    }

}
