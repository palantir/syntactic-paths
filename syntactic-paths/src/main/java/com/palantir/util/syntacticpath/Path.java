/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
 *
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

package com.palantir.util.syntacticpath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * OS-independent implementation of Unix-style syntactic paths, loosely analogous to {@code sun.nio.fs.UnixPath}.
 * <p/>
 * Syntactically, paths are composed of segments (which are arbitrary UTF strings that do not contain {@code '/'} and
 * are not {@code '.'}) separated by the {@link #SEPARATOR separator character '/'}. For example, {@code foo/bar/baz} is
 * a path.
 * <p/>
 * Every path is either {@link #isAbsolute() absolute} or relative and is either a {@link #isFolder folder} or not a
 * folder: absolute paths start with {@link #SEPARATOR "/"} and folders end with {@link #SEPARATOR "/"}. The path {@link
 * #ROOT_PATH root path "/"} is an absolute folder, the empty path {@code ""} is a relative non-folder. For example,
 * {@code /foo/bar/} is an absolute folder path with segments {@code foo} and {@code bar}, and {@code foo/bar/baz} is a
 * relative non-folder path with segments {@code foo, bar, baz}.
 * <p/>
 * The special segment {@code ".."} is a "backwards" segment similar to Unix/Linux paths; paths containing {@code ".."}
 * can explicitly get {@link #normalize() normalized} in order to collapse the backwards references. For example, {@code
 * new Path("foo/bar/../baz").normalize()} is the path {@code "foo/baz"}.
 * <p/>
 * The {@link #toString() string representation} of a path is the inverse to the {@link #Path(String) constructor},
 * i.e., for any valid path string {@code s} it holds that {@code s.equals(new Path(s).toString()}.
 */
@Unsafe
public final class Path implements Comparable<Path> {

    public static final Path ROOT_PATH = new Path(ImmutableList.of(), true, true);
    public static final char SEPARATOR_CHAR = '/';
    public static final String SEPARATOR = "/";
    public static final String BACKWARDS_PATH = "..";

    private static final Splitter PATH_SPLITTER = Splitter.on(SEPARATOR_CHAR).omitEmptyStrings();
    static final Joiner PATH_JOINER = Joiner.on(SEPARATOR_CHAR).skipNulls();
    private static final char ILLEGAL_CHARACTER = (char) 0;
    private static final String ILLEGAL_SEGMENT = ".";

    private final List<String> segments;
    private final int size;
    private final boolean isAbsolute;
    private final boolean isFolder;

    // lazily memoize string and normalized Path representations
    private transient String stringRepresentation;
    private transient Path normalizedPath;

    private Path(final Iterable<String> segments, final boolean isAbsolute, boolean isFolder) {
        this.segments = ImmutableList.copyOf(segments);
        this.size = this.segments.size();
        this.isAbsolute = isAbsolute;
        this.isFolder = isFolder;
    }

    @JsonCreator
    Path(String input) {
        this(checkAndSplit(input), input.startsWith(SEPARATOR), input.endsWith(SEPARATOR));
    }

    private static List<String> checkAndSplit(String path) {
        Preconditions.checkNotNull(path, "path cannot be null");
        return checkSegments(PATH_SPLITTER.splitToList(checkCharacters(path)));
    }

    private static String checkCharacters(String path) {
        if (Strings.isNullOrEmpty(path) || path.indexOf(ILLEGAL_CHARACTER) == -1) {
            return path;
        }
        throw new SafeIllegalArgumentException("Path contains illegal characters", UnsafeArg.of("path", path));
    }

    private static List<String> checkSegments(List<String> segments) {
        if (segments.contains(ILLEGAL_SEGMENT)) {
            throw new SafeIllegalArgumentException(
                    "Path contains illegal segments",
                    UnsafeArg.of("segments", segments),
                    SafeArg.of("illegalSegment", ILLEGAL_SEGMENT));
        } else {
            return segments;
        }
    }

    private Path normalizeInternal() {
        Deque<String> normalSegments = new ArrayDeque<>(size);
        for (String segment : segments) {
            if (segment.equals(BACKWARDS_PATH)) {
                normalSegments.pollLast();
            } else {
                normalSegments.add(segment);
            }
        }
        return new Path(normalSegments, isAbsolute, isFolder);
    }

    /**
     * Returns a normalized version of this path, i.e., a path in which all backward navigation is resolved. For
     * example, {@code /a/b/..} resolves to {@code /a}. The normalized path is absolute iff this path is absolute.
     * Backward navigation in empty paths is a no-op, e.g., {@code a/../..} normalizes to the empty path.
     */
    public Path normalize() {
        Path path = this.normalizedPath;
        if (path == null) {
            path = normalizeInternal();
            this.normalizedPath = path;
        }
        return path;
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

    /** Returns true iff the string representation of this path ends with {@link #SEPARATOR "/"}. */
    public boolean isFolder() {
        return isFolder;
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
     * Returns the path represented by the {@code [0, size-1]} first segments of this (normalized) path, or null if
     * there is no such path. The returned path is a {@link #isFolder() folder}.
     */
    public Path getParent() {
        Path normal = normalize();

        if (normal.size == 0) {
            return null;
        } else if (normal.size == 1) {
            return getRoot(); // null if path is relative
        } else {
            return new Path(normal.segments.subList(0, normal.size - 1), normal.isAbsolute, true);
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
     * #normalize() normalized}. The resulting path is a {@link #isFolder() folder} iff the other path is a folder.
     */
    public Path resolve(Path other) {
        if (other.isAbsolute) {
            return other;
        } else {
            return new Path(Iterables.concat(this.segments, other.segments), this.isAbsolute, other.isFolder);
        }
    }

    /** Equivalent to {@code resolve(new Path(other)}. */
    public Path resolve(String other) {
        return resolve(new Path(other));
    }

    /**
     * Returns the suffix of the (normalized) given path as seen relative from this (normalized) path: If the given
     * paths or of the same type (i.e., relative/absolute) and if the segments of this path are a proper prefix of the
     * segments of the other path, then it returns the relative path defined by the suffix of segments of the other path
     * that are not a common prefix between the two paths; throws otherwise. Relativization is performed with respect to
     * the {@link #normalize() normalized} versions of this and the other path.
     * <p/>
     * For example, if this path is {@code /a/b} and the other path is {@code /a/b/c/d}, returns {@code c/d}.
     */
    public Path relativize(Path other) {
        Path left = this.normalize();
        Path right = other.normalize();

        if (left.isAbsolute() != right.isAbsolute()) {
            throw new SafeIllegalArgumentException(
                    "Cannot relativize absolute vs relative path",
                    UnsafeArg.of("left", left),
                    UnsafeArg.of("right", right));
        }
        if (left.size >= right.size || !left.segments.equals(right.segments.subList(0, left.size))) {
            throw new SafeIllegalArgumentException(
                    "Relativize requires this path to be a proper prefix of the other path",
                    UnsafeArg.of("left", left),
                    UnsafeArg.of("right", right));
        }

        if (left.size == 0 && !left.isAbsolute) {
            return right;
        }

        return new Path(right.segments.subList(left.size, right.size), false, right.isFolder);
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
    public boolean startsWithSegment(Path other) {
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
     * {@code a/b} ends with {@code b} and {@code a/b/..} ends with {@code a}. Note that this method is agnostic of
     * whether one of the two paths is a {@link #isFolder() folder}, e.g., path {@code a/b} ends with {@code b/}.
     */
    public boolean endsWithSegment(Path other) {
        Path left = this.normalize();
        Path right = other.normalize();

        if (left.size < right.size) {
            return false;
        } else if (left.size == right.size) {
            if (!left.isAbsolute() && right.isAbsolute) {
                return false;
            } else {
                return left.segments.equals(right.segments);
            }
        } else {
            if (right.isAbsolute) {
                return false;
            } else {
                return left.segments.subList(left.size - right.size, left.size).equals(right.segments);
            }
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

    /**
     * Returns the segments of this path, i.e., the list of directories and the file name (if they exist). For example,
     * the segments of path {@code /abc/def} are {@code abc} and {@code def}.
     */
    public List<String> getSegments() {
        return segments;
    }

    @Override
    public int compareTo(Path other) {
        return this.toString().compareTo(other.toString());
    }

    @Override
    public boolean equals(Object ob) {
        if (ob instanceof Path) {
            return compareTo((Path) ob) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    private String toStringInternal() {
        if (size == 0) {
            return isAbsolute ? SEPARATOR : "";
        } else {
            String prefix = isAbsolute ? SEPARATOR : "";
            String suffix = isFolder ? SEPARATOR : "";

            return prefix + PATH_JOINER.join(segments) + suffix;
        }
    }

    /** Returns the string representation of this (non-normalized) path. */
    @JsonValue
    @Override
    public String toString() {
        String path = this.stringRepresentation;
        if (path == null) {
            path = toStringInternal();
            this.stringRepresentation = path;
        }
        return path;
    }
}
