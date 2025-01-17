/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.index.translog.transfer;

import org.opensearch.common.Nullable;
import org.opensearch.common.io.stream.BytesStreamInput;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.internal.io.IOUtils;
import org.opensearch.index.translog.BufferedChecksumStreamInput;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Snapshot of a single file that gets transferred
 *
 * @opensearch.internal
 */
public class FileSnapshot implements Closeable {

    private final String name;
    @Nullable
    private final FileChannel fileChannel;
    @Nullable
    private Path path;
    @Nullable
    private byte[] content;

    private FileSnapshot(Path path) throws IOException {
        Objects.requireNonNull(path);
        this.name = path.getFileName().toString();
        this.path = path;
        this.fileChannel = FileChannel.open(path, StandardOpenOption.READ);
    }

    private FileSnapshot(String name, byte[] content) {
        Objects.requireNonNull(name);
        this.name = name;
        this.content = content;
        this.fileChannel = null;
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public long getContentLength() throws IOException {
        return fileChannel == null ? content.length : fileChannel.size();
    }

    public InputStream inputStream() throws IOException {
        return fileChannel != null
            ? new BufferedChecksumStreamInput(
                new InputStreamStreamInput(Channels.newInputStream(fileChannel), fileChannel.size()),
                path.toString()
            )
            : new BufferedChecksumStreamInput(new BytesStreamInput(content), name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, content, path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileSnapshot other = (FileSnapshot) o;
        return Objects.equals(this.name, other.name)
            && Objects.equals(this.content, other.content)
            && Objects.equals(this.path, other.path);
    }

    @Override
    public String toString() {
        return new StringBuilder("FileInfo [").append(" name = ")
            .append(name)
            .append(", path = ")
            .append(path.toUri())
            .append("]")
            .toString();
    }

    @Override
    public void close() throws IOException {
        IOUtils.close(fileChannel);
    }

    /**
     * Snapshot of a single file with primary term that gets transferred
     *
     * @opensearch.internal
     */
    public static class TransferFileSnapshot extends FileSnapshot {

        private final long primaryTerm;

        public TransferFileSnapshot(Path path, long primaryTerm) throws IOException {
            super(path);
            this.primaryTerm = primaryTerm;
        }

        public TransferFileSnapshot(String name, byte[] content, long primaryTerm) throws IOException {
            super(name, content);
            this.primaryTerm = primaryTerm;
        }

        public long getPrimaryTerm() {
            return primaryTerm;
        }

        @Override
        public int hashCode() {
            return Objects.hash(primaryTerm, super.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            if (super.equals(o)) {
                if (this == o) return true;
                if (getClass() != o.getClass()) return false;
                TransferFileSnapshot other = (TransferFileSnapshot) o;
                return Objects.equals(this.primaryTerm, other.primaryTerm);
            }
            return false;
        }
    }

    /**
     * Snapshot of a single .tlg file that gets transferred
     *
     * @opensearch.internal
     */
    public static final class TranslogFileSnapshot extends TransferFileSnapshot {

        private final long generation;

        public TranslogFileSnapshot(long primaryTerm, long generation, Path path) throws IOException {
            super(path, primaryTerm);
            this.generation = generation;
        }

        public long getGeneration() {
            return generation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(generation, super.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            if (super.equals(o)) {
                if (this == o) return true;
                if (getClass() != o.getClass()) return false;
                TranslogFileSnapshot other = (TranslogFileSnapshot) o;
                return Objects.equals(this.generation, other.generation);
            }
            return false;
        }
    }

    /**
     * Snapshot of a single .ckp file that gets transferred
     *
     * @opensearch.internal
     */
    public static final class CheckpointFileSnapshot extends TransferFileSnapshot {

        private final long generation;

        private final long minTranslogGeneration;

        public CheckpointFileSnapshot(long primaryTerm, long generation, long minTranslogGeneration, Path path) throws IOException {
            super(path, primaryTerm);
            this.minTranslogGeneration = minTranslogGeneration;
            this.generation = generation;
        }

        public long getGeneration() {
            return generation;
        }

        public long getMinTranslogGeneration() {
            return minTranslogGeneration;
        }

        @Override
        public int hashCode() {
            return Objects.hash(generation, minTranslogGeneration, super.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            if (super.equals(o)) {
                if (this == o) return true;
                if (getClass() != o.getClass()) return false;
                CheckpointFileSnapshot other = (CheckpointFileSnapshot) o;
                return Objects.equals(this.minTranslogGeneration, other.minTranslogGeneration)
                    && Objects.equals(this.generation, other.generation);
            }
            return false;
        }
    }
}
