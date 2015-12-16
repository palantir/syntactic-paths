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
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * OS-independent implementation of Unix-style syntactic paths, analogous to {@code sun.nio.fs.UnixPath}. Major semantic
 * differences: all paths consistent of UTF8 characters, independently of locale or other OS settings.
 */
public final class Path implements Comparable<Path> {

    public static final Path ROOT_PATH = new Path(ImmutableList.<String>of(), true);
    public static final char SEPARATOR = '/';
    public static final String BACKWARDS_PATH = "..";

    private static final Splitter PATH_SPLITTER = Splitter.on(SEPARATOR).omitEmptyStrings();
    static final Joiner PATH_JOINER = Joiner.on(SEPARATOR);
    private static final char[] ILLEGAL_CHARS = new char[] {0};
    private static final List<String> ILLEGAL_SEGMENTS = ImmutableList.of(".");

    private final List<String> segments;
    private final int size;
    private final boolean isAbsolute;
    private final Supplier<String> stringRepresentation;
    private final Supplier<Path> normalizedPath;

    private Path(final List<String> segments, final boolean isAbsolute) {
        this.segments = segments;
        this.size = segments.size();
        this.isAbsolute = isAbsolute;
        stringRepresentation = Suppliers.memoize(new Supplier<String>() {
            @Override
            public String get() {
                return toStringInternal();
            }
        });
        normalizedPath = Suppliers.memoize(new Supplier<Path>() {
            @Override
            public Path get() {
                return normalizeInternal();
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

    private Path normalizeInternal() {
        LinkedList<String> normalSegments = Lists.newLinkedList();
        for (String segment : segments) {
            if (segment.equals(BACKWARDS_PATH)) {
                if (!normalSegments.isEmpty()) {
                    normalSegments.removeLast();
                } else {
                    // Nothing to do.
                }
            } else {
                normalSegments.add(segment);
            }
        }
        return new Path(normalSegments, isAbsolute);
    }

    /**
     * Returns a normalized version of this path, i.e., a path in which all backward navigation is resolved. For
     * example, {@code /a/b/..} resolves to {@code /a}. The normalized path is absolute iff this path is absolute.
     * Backward navigation in empty paths is a no-op, e.g., {@code a/../..} normalizes to the empty path.
     */
    public Path normalize() {
        return normalizedPath.get();
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
     * Returns the last segment of the normalized path, or {@code null} if it has no last segment.
     */
    public Path getFileName() {
        Path normal = normalize();

        if (normal.segments.isEmpty()) {
            return null;
        }

        if (normal.size == 1 && !normal.isAbsolute) {
            return this;
        } else {
            return new Path(normal.segments.get(normal.size - 1));
        }
    }

    /**
     * For absolute paths, returns the second to last segment of the normalized path, or the {@link Path#ROOT_PATH root
     * path} if there is no such segment; for relative paths, returns the second to last segment of the normalized path
     * or null if there is no such segment.
     */
    public Path getParent() {
        Path normal = normalize();

        if (normal.size == 0) {
            return null;
        } else if (normal.size == 1) {
            return getRoot(); // null if path is relative
        } else {
            return new Path(normal.segments.subList(0, normal.size - 1), normal.isAbsolute);
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
     * obtained by concatenating the segments of this path and of the other path. The returned path is not {@link
     * #normalize() normalized}.
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
     * Returns the suffix of the (normalized) given path as seen relative from this (normalized) path: If the given
     * paths or of the same type (i.e., relative/absolute) and if the segments of this path are a prefix of the segments
     * of the other path, then it returns the relative path defined by the suffix of segments of the other path that are
     * not a common prefix between the two paths; throws otherwise. Relativization is performed with respect to the
     * {@link #normalize() normalized} versions of this and the other path.
     * <p/>
     * For example, if this path is {@code /a/b} and the other path is {@code /a/b/c/d}, returns {@code c/d}.
     */
    public Path relativize(Path other) {
        Path left = this.normalize();
        Path right = other.normalize();

        if (left.isAbsolute() != right.isAbsolute()) {
            throw new IllegalArgumentException("Cannot relativize absolute vs relative path: " + left + " vs " + right);
        }
        if (left.segments.size() > right.segments.size()
                || !left.segments.equals(right.segments.subList(0, left.size))) {
            throw new IllegalArgumentException(
                    "Relativize requires this path to be a prefix of the other path: " + left + " vs " + right);
        }

        if (left.size == 0 && !left.isAbsolute) {
            return right;
        }

        return new Path(right.segments.subList(left.size, right.size), false);
    }

    /** Equivalent to {@code relativize(new Path(other)}. */
    public Path relativize(String other) {
        return relativize(new Path(other));
    }

    /**
     * Returns true iff the segments of the given (normalized) other path are a prefix (including equality) of the
     * segments of this (normalized) path, unless this path is relative and the other path is absolute. For example,
     * {@code /a/b/c} starts with {@code /a/b} but not with {@code a/b}, and {@code /a/../b} starts with {@code b} but
     * not with {@code a}.
     */
    public boolean startsWith(Path other) {
        Path left = this.normalize();
        Path right = other.normalize();

        if (left.size < right.size) {
            return false;
        }

        if (left.isAbsolute != right.isAbsolute) {
            return false;
        } else {
            return left.toString().startsWith(right.toString());
        }
    }

    /**
     * Returns true iff the segments of the given (normalized) other path are a suffix (including equality) of the
     * segments of this (normalized) path, unless this path is relative and the other path is absolute. For example,
     * {@code a/b} ends with {@code b} and {@code a/b/..} ends with {@code a}.
     */
    public boolean endsWith(Path other) {
        Path left = this.normalize();
        Path right = other.normalize();

        if (left.size < right.size) {
            return false;
        }

        // Same length
        if (left.size == right.size) {
            return left.toString().endsWith(right.toString());
        }

        // Other is shorter than left path
        if (right.isAbsolute) {
            return false;
        } else {
            return left.toString().endsWith(right.toString());
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

    private String toStringInternal() {
        if (isAbsolute) {
            return "/" + PATH_JOINER.join(segments);
        } else {
            return PATH_JOINER.join(segments);
        }
    }

    /** Returns the string representation of this (non-normalized) path. */
    @Override
    public String toString() {
        return stringRepresentation.get();
    }
}
