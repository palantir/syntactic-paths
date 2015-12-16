/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uschi2000.paths;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * OS-independent implementation of Unix-style syntactic paths, analogous to {@code sun.nio.fs.UnixPath}. Major semantic
 * differences: (1) all paths consistent of UTF8 characters, independently of locale or other OS settings, (2) no
 * backwards paths (e.g., {@code a/..b/}).
 */
public final class Path implements Comparable<Path> {

    public static final Path ROOT_PATH = new Path(ImmutableList.<String>of(), true);
    public static final char SEPARATOR = '/';

    private static final Splitter PATH_SPLITTER = Splitter.on(SEPARATOR).omitEmptyStrings();
    static final Joiner PATH_JOINER = Joiner.on(SEPARATOR);
    private static final char[] ILLEGAL_CHARS = new char[] {0};
    private static final List<String> ILLEGAL_SEGMENTS = ImmutableList.of(".", "..");

    private final List<String> segments;
    private final int size;
    private final boolean isAbsolute;
    private final Supplier<String> stringRepresentation;

    private Path(final List<String> segments, final boolean isAbsolute) {
        this.segments = segments;
        this.size = segments.size();
        this.isAbsolute = isAbsolute;
        stringRepresentation = Suppliers.memoize(new Supplier<String>() {
            @Override
            public String get() {
                if (isAbsolute) {
                    return "/" + PATH_JOINER.join(segments);
                } else {
                    return PATH_JOINER.join(segments);
                }
            }
        });
    }

    Path(String input) {
        this(checkAndSplit(input), input.startsWith("/"));
    }

    private static List<String> checkAndSplit(String path) {
        return checkSegments(ImmutableList.copyOf(PATH_SPLITTER.split(checkCharacters(path))));
    }

    private static String checkCharacters(String path) {
        if (StringUtils.containsAny(path, ILLEGAL_CHARS)) {
            throw new IllegalArgumentException("Path contains illegal characters: " + path);
        }
        return path;
    }

    private static List<String> checkSegments(List<String> segments) {
        if (!Collections.disjoint(segments, ILLEGAL_SEGMENTS)) {
            throw new IllegalArgumentException("Path contains illegal segments: " + segments);
        }
        return segments;
    }

    /**
     * Returns the {@link Path#ROOT_PATH root path} if the given path is absolute, or {@code null} else.
     */
    public Path getRoot() {
        if (size > 0 && isAbsolute) {
            return ROOT_PATH;
        } else {
            return null;
        }
    }

    /**
     * Returns the last segment of the given path, or {@code null} if is has no last segment.
     */
    public Path getFileName() {
        if (segments.isEmpty()) {
            return null;
        }

        if (size == 1 && !isAbsolute) {
            return this;
        } else {
            return new Path(segments.get(size - 1));
        }
    }

    /**
     * For absolute paths, returns the second to last segment or the {@link Path#ROOT_PATH root path} if there is no
     * such segment; for relative paths, returns the second to last segment or null if there is no such segment.
     */
    public Path getParent() {
        if (size == 0) {
            return null;
        } else if (size == 1) {
            return getRoot(); // null if path is relative
        } else {
            return new Path(segments.subList(0, size - 1), isAbsolute);
        }
    }

    /**
     * Returns true iff this path is absolute, i.e., iff it starts with {@link Path#SEPARATOR the path separator '/'}.
     */
    public boolean isAbsolute() {
        return isAbsolute;
    }

    /**
     * If the given path is {@link Path#isAbsolute absolute}, trivially returns the other path; else, returns the path
     * obtained by concatenating the segments of this path and of the other path.
     */
    public Path resolve(Path other) {
        if (other.isAbsolute) {
            return other;
        } else {
            return new Path(ImmutableList.copyOf(Iterables.concat(this.segments, other.segments)), this.isAbsolute);
        }
    }

    /** Equivalent to {@code resolve(new Path(other)}. */
    public Path resolve(String other) {
        return resolve(new Path(other));
    }

    /**
     * Returns the suffix of the given path as seen relative from this path: If the given paths or of the same type
     * (i.e., relative/absolute) and if the segments of this path are a prefix of the segments of the other path, then
     * it returns the relative path defined by the suffix of segments of the other path that are not a common prefix
     * between the two paths; throws otherwise.
     * <p>
     * For example, if this path is {@code /a/b} and the other path is {@code /a/b/c/d}, returns {@code c/d}.
     */
    public Path relativize(Path other) {
        if (this.isAbsolute() != other.isAbsolute()) {
            throw new IllegalArgumentException("Cannot relativize absolute vs relative path: " + this + " vs " + other);
        }
        if (!this.segments.equals(other.segments.subList(0, this.size))) {
            throw new IllegalArgumentException(
                    "Relativize requires this path to be a prefix of the other path: " + this + " vs " + other);
        }

        if (this.size == 0 && !this.isAbsolute) {
            return other;
        }

        return new Path(other.segments.subList(this.size, other.size), false);
    }

    /** Equivalent to {@code relativize(new Path(other)}. */
    public Path relativize(String other) {
        return relativize(new Path(other));
    }

    /**
     * Returns true iff the segments of the given other path are a prefix (including equality) of the segments of this
     * path, unless this path is relative and the other path is absolute. For example, {@code /a/b/c} starts with {@code
     * /a/b} but not with {@code a/b}.
     */
    public boolean startsWith(Path other) {
        if (this.size < other.size) {
            return false;
        }

        if (this.isAbsolute != other.isAbsolute) {
            return false;
        } else {
            return this.toString().startsWith(other.toString());
        }
    }

    /**
     * Returns true iff the segments of the given other path are a suffix (including equality) of the segments of this
     * path, unless this path is relative and the other path is absolute.
     */
    public boolean endsWith(Path other) {
        if (this.size < other.size) {
            return false;
        }

        // Same length
        if (this.size == other.size) {
            return this.toString().endsWith(other.toString());
        }

        // Other is shorter than this path
        if (other.isAbsolute) {
            return false;
        } else {
            return this.toString().endsWith(other.toString());
        }
    }

    /**
     * If this path is absolute, trivially returns this path; else returns this path resolved against the {@link
     * Path#ROOT_PATH root path}.
     */
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }

        return ROOT_PATH.resolve(this);
    }

    @Override
    public int compareTo(Path other) {
        return this.toString().compareTo(other.toString());
    }

    @Override
    public boolean equals(Object ob) {
        if ((ob != null) && (ob instanceof Path)) {
            return compareTo((Path) ob) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(segments.toArray());
    }

    @Override
    public String toString() {
        return stringRepresentation.get();
    }
}
