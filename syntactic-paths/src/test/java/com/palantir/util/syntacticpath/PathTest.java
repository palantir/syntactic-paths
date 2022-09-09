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

import static com.palantir.logsafe.testing.Assertions.assertThatLoggableExceptionThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.IOException;
import java.util.Objects;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class PathTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void test_constructorNormalizes() {
        assertThat(new Path("a//b")).isEqualTo(new Path("a/b"));
    }

    @Test
    public void testConstructor_illegalCharacters() {
        String path = new String(new char[] {'a', 0, 'b'});
        assertThatLoggableExceptionThrownBy(() -> new Path(path))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Path contains illegal characters: {path=a\u0000b}")
                .hasExactlyArgs(UnsafeArg.of("path", "a\u0000b"));
    }

    @Test
    public void testConstructor_illegalSegments() {
        for (String illegal : new String[] {"."}) {
            String path = "a/" + illegal + "/b";
            assertThatLoggableExceptionThrownBy(() -> new Path(path))
                    .isInstanceOf(SafeIllegalArgumentException.class)
                    .hasMessage("Path contains illegal segments: {segments=[a, ., b]}")
                    .hasExactlyArgs(UnsafeArg.of("segments", ImmutableList.of("a", ".", "b")));
        }
    }

    @Test
    public void testConstructor_normalizesSlashes() {
        assertThat(new Path("a//b").toString()).isEqualTo("a/b");
    }

    @Test
    public void testConstructor_doesNotNormalizeBackwardsPaths() {
        assertThat(new Path("a/../..b/..").toString()).isEqualTo("a/../..b/..");
    }

    @Test
    public void testToAbsolutePath() {
        Path path = new Path("a/b").toAbsolutePath();
        assertThat(path.isAbsolute()).isTrue();
        assertThat(path).isEqualTo(new Path("/a/b"));
    }

    @Test
    public void testIsFolder() {
        assertThat(new Path("/").isFolder()).isTrue();
        assertThat(new Path("/a/").isFolder()).isTrue();
        assertThat(new Path("a/").isFolder()).isTrue();
        assertThat(new Path("a").isFolder()).isFalse();
    }

    @Test
    public void testNormalize() {
        assertThat(new Path("/a/b").normalize()).isEqualTo(new Path("/a/b"));
        assertThat(new Path("a/b").normalize()).isEqualTo(new Path("a/b"));
        assertThat(new Path("/").normalize()).isEqualTo(new Path("/"));
        assertThat(new Path("").normalize()).isEqualTo(new Path(""));

        assertThat(new Path("/a/../b").normalize()).isEqualTo(new Path("/b"));
        assertThat(new Path("a/../b").normalize()).isEqualTo(new Path("b"));
        assertThat(new Path("/a/..").normalize()).isEqualTo(new Path("/"));
        assertThat(new Path("a/..").normalize()).isEqualTo(new Path(""));
        assertThat(new Path("/..").normalize()).isEqualTo(new Path("/"));
        assertThat(new Path("..").normalize()).isEqualTo(new Path(""));

        assertThat(new Path("/a/b/c/../..").normalize()).isEqualTo(new Path("/a"));
        assertThat(new Path("a/b/c/../..").normalize()).isEqualTo(new Path("a"));
        assertThat(new Path("/../..").normalize()).isEqualTo(new Path("/"));
        assertThat(new Path("../..").normalize()).isEqualTo(new Path(""));

        assertThat(new Path("/a/..b").normalize()).isEqualTo(new Path("/a/..b"));
    }

    @Test
    public void testRelativize() {
        assertThat(Path.ROOT_PATH.relativize(new Path("/a/b"))).isEqualTo(new Path("a/b"));
        assertThat(new Path("/a").relativize(new Path("/a/b"))).isEqualTo(new Path("b"));
        assertThat(new Path("/a/b/c").relativize(new Path("/a/b/c/d/e/f"))).isEqualTo(new Path("d/e/f"));
        assertThat(new Path("a/b/c").relativize(new Path("a/b/c/d/e/f"))).isEqualTo(new Path("d/e/f"));

        assertThat(new Path("/a/b/c").relativize("/a/b/c/d/e/f")).isEqualTo(new Path("d/e/f"));

        for (String path : ImmutableList.of("/", "/a/b", "a/b")) {
            assertThatLoggableExceptionThrownBy(() -> new Path(path).relativize(new Path(path)))
                    .isInstanceOf(SafeIllegalArgumentException.class)
                    .hasMessage(String.format(
                            "Relativize requires this path to be a proper prefix of the other path:"
                                    + " {left=%s, right=%s}",
                            path, path))
                    .hasExactlyArgs(UnsafeArg.of("left", new Path(path)), UnsafeArg.of("right", new Path(path)));
        }
    }

    @Test
    public void testRelativize_normalizesFirst() {
        assertThat(new Path("a/b/..").relativize(new Path("a/b"))).isEqualTo(new Path("b"));
        assertThat(new Path("a/b").relativize(new Path("a/b/c/d/.."))).isEqualTo(new Path("c"));
    }

    @Test
    public void testRelativize_preservesIsFolderOfOtherPath() {
        assertThat(new Path("a/").relativize(new Path("a/b/")).isFolder()).isTrue();
        assertThat(new Path("a").relativize(new Path("a/b/")).isFolder()).isTrue();

        assertThat(new Path("a/").relativize(new Path("a/b")).isFolder()).isFalse();
        assertThat(new Path("a").relativize(new Path("a/b")).isFolder()).isFalse();
    }

    @Test
    public void testRelativize_differentPaths() {
        assertThatLoggableExceptionThrownBy(() -> new Path("/a").relativize(new Path("a/b")))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Cannot relativize absolute vs relative path: {left=/a, right=a/b}")
                .hasExactlyArgs(UnsafeArg.of("left", new Path("/a")), UnsafeArg.of("right", new Path("a/b")));
    }

    @Test
    public void testRelativize_noPrefix() {
        assertThatLoggableExceptionThrownBy(() -> new Path("/a/b/c").relativize(new Path("/a/b/d")))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Relativize requires this path to be a proper prefix of the other path:"
                        + " {left=/a/b/c, right=/a/b/d}")
                .hasExactlyArgs(UnsafeArg.of("left", new Path("/a/b/c")), UnsafeArg.of("right", new Path("/a/b/d")));
    }

    @Test
    public void testRelativize_differentLengths() {
        assertThatLoggableExceptionThrownBy(() -> new Path("/a/b/c").relativize(new Path("/a/b")))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Relativize requires this path to be a proper prefix of the other path:"
                        + " {left=/a/b/c, right=/a/b}")
                .hasExactlyArgs(UnsafeArg.of("left", new Path("/a/b/c")), UnsafeArg.of("right", new Path("/a/b")));
    }

    @Test
    public void test_utf8() {
        assertThat(new Path("/a/¡")).isEqualTo(new Path("/a/¡"));
    }

    @Test
    public void testGetParent() {
        assertThat(new Path("/a/b/c").getParent()).isEqualTo(new Path("/a/b/"));
        assertThat(new Path("/a/b/c/").getParent()).isEqualTo(new Path("/a/b/"));
        assertThat(new Path("a/b/c").getParent()).isEqualTo(new Path("a/b/"));
        assertThat(new Path("a/b/c/").getParent()).isEqualTo(new Path("a/b/"));
        assertThat(new Path("/a").getParent()).isEqualTo(Path.ROOT_PATH);
        assertThat(new Path("a").getParent()).isNull();
        assertThat(new Path("/").getParent()).isNull();
    }

    @Test
    public void testGetParent_normalizesFirst() {
        assertThat(new Path("/a/b/..").getParent()).isEqualTo(new Path("/"));
        assertThat(new Path("/a/..").getParent()).isNull();
    }

    @Test
    public void testResolve() {
        assertThat(new Path("/a").resolve(new Path("b"))).isEqualTo(new Path("/a/b"));
        assertThat(new Path("/a").resolve(new Path("/b"))).isEqualTo(new Path("/b"));
        assertThat(new Path("/a/b").resolve(new Path("/c/d"))).isEqualTo(new Path("/c/d"));
        assertThat(new Path("a").resolve(new Path("b"))).isEqualTo(new Path("a/b"));

        assertThat(new Path("/a/b").resolve("/c/d")).isEqualTo(new Path("/c/d"));
    }

    @Test
    public void testResolve_doesNotNormalizePaths() {
        assertThat(new Path("a/b").resolve(new Path(".."))).isEqualTo(new Path("a/b/.."));
        assertThat(new Path("a/b").resolve(new Path("../.."))).isEqualTo(new Path("a/b/../.."));

        assertThat(new Path("a/b/..").resolve(new Path("c"))).isEqualTo(new Path("a/b/../c"));
    }

    @Test
    public void testResolve_preservesIsFolderOfOtherPath() {
        assertThat(new Path("a").resolve(new Path("/")).isFolder()).isTrue();
        assertThat(new Path("a").resolve(new Path("b/")).isFolder()).isTrue();

        assertThat(new Path("a").resolve(new Path("b")).isFolder()).isFalse();
        assertThat(new Path("a/").resolve(new Path("b")).isFolder()).isFalse();
    }

    @Test
    public void testGetRoot() {
        assertThat(new Path("/a").getRoot()).isEqualTo(Path.ROOT_PATH);
        assertThat(new Path("a").getRoot()).isNull();
    }

    @Test
    public void testStartsWith() {
        assertThat(new Path("a").startsWithSegment(new Path(""))).isTrue();
        assertThat(new Path("a/b").startsWithSegment(new Path("a"))).isTrue();
        assertThat(new Path("/a/b").startsWithSegment(new Path("/a"))).isTrue();
        assertThat(new Path("/a/b").startsWithSegment(new Path("/a/b"))).isTrue();

        assertThat(new Path("/a/b").startsWithSegment(new Path("a"))).isFalse();
        assertThat(new Path("/a/b").startsWithSegment(new Path("/a/b/c"))).isFalse();
    }

    @Test
    public void testStartsWithSegment_normalizesFirst() {
        assertThat(new Path("../a").startsWithSegment(new Path("a"))).isTrue();
        assertThat(new Path("a/b").startsWithSegment(new Path("a/b/c/.."))).isTrue();
    }

    @Test
    public void testEndsWithSegment() {
        assertThat(new Path("/a").endsWithSegment(new Path(""))).isTrue();
        assertThat(new Path("/a/b").endsWithSegment(new Path("b"))).isTrue();
        assertThat(new Path("a/b").endsWithSegment(new Path("b"))).isTrue();
        assertThat(new Path("a/b").endsWithSegment(new Path("a/b"))).isTrue();
        assertThat(new Path("/a/b").endsWithSegment(new Path("/a/b"))).isTrue();
        assertThat(new Path("/a/b").endsWithSegment(new Path("a/b"))).isTrue();

        assertThat(new Path("/a/b/").endsWithSegment(new Path("b"))).isTrue();
        assertThat(new Path("/a/b/").endsWithSegment(new Path("b/"))).isTrue();
        assertThat(new Path("/a/b").endsWithSegment(new Path("b"))).isTrue();
        assertThat(new Path("/a/b").endsWithSegment(new Path("b/"))).isTrue();

        assertThat(new Path("a/b").endsWithSegment(new Path("/a/b"))).isFalse();
        assertThat(new Path("/a/b").endsWithSegment(new Path("/b"))).isFalse();
        assertThat(new Path("/a/b").endsWithSegment(new Path("/a/b/c"))).isFalse();
    }

    @Test
    public void testEndsWithSegment_normalizesFirst() {
        assertThat(new Path("/a/b/..").endsWithSegment(new Path("a"))).isTrue();
        assertThat(new Path("/a").endsWithSegment(new Path("a/b/.."))).isTrue();
    }

    @Test
    public void testGetFileName() {
        assertThat(new Path("/a/b/").getFileName()).isEqualTo(new Path("b"));
        assertThat(new Path("/a/b").getFileName()).isEqualTo(new Path("b"));
        assertThat(new Path("/a/").getFileName()).isEqualTo(new Path("a"));
        Path path = new Path("a");
        assertThat(path).isSameAs(path.getFileName());
    }

    @Test
    public void testGetFileName_normalizesFirst() {
        assertThat(new Path("/a/b/..").getFileName()).isEqualTo(new Path("a"));
    }

    @Test
    public void testToStringAndSerialization() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        for (String path : new String[] {"/a/b", "a/b", "", "/", "a/"}) {
            assertThat(new Path(path).toString()).isEqualTo(path);
            assertThat(mapper.writeValueAsString(new Path(path))).isEqualTo("\"" + path + "\"");
            assertThat(mapper.readValue("\"" + path + "\"", Path.class)).isEqualTo(new Path(path));
        }
    }

    @Test
    public void testGetSegments() {
        assertThat(new Path("/a/b").getSegments()).containsExactly("a", "b");
        assertThat(new Path("a/b").getSegments()).containsExactly("a", "b");
        assertThat(new Path("a/b/").getSegments()).containsExactly("a", "b");
        assertThat(new Path("a//b").getSegments()).containsExactly("a", "b");
        assertThat(new Path("a/a/b").getSegments()).containsExactly("a", "a", "b");
        assertThat(new Path("").getSegments()).hasSize(0);
    }

    @Test
    @SuppressWarnings("DeprecatedGuavaObjects") // testing back-compat
    public void testEqualsAndHashCode() {
        Path path = new Path("/a/b/c/d");
        assertThat(path).isEqualTo(new Path("/a/b/c/d"));
        assertThat(path).isNotEqualTo(new Path("/a/b/c"));

        assertThat(path.hashCode()).isEqualTo(Objects.hash(path.getSegments().toArray()));
        assertThat(path.hashCode())
                .isEqualTo(com.google.common.base.Objects.hashCode(
                        path.getSegments().toArray()));
        assertThat(path.hashCode()).isEqualTo(path.getSegments().hashCode());
    }
}
